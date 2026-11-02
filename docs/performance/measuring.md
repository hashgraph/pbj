# Measuring PBJ performance

How to get numbers you can trust for PBJ changes, learned while building the fast code generation stack (#991 to
#1035). Most of the mistakes listed here were made at least once. The JIT side has its own page,
[jit-and-inlining.md](jit-and-inlining.md).

## Decide on the target hardware, steer anywhere

- **Decide on the server CPU you deploy on (for us, AMD EPYC), never on a laptop.** Apple Silicon was right about the
  direction most of the time, but not always:
  - the reverse writer's 2-4× slowdown on out-of-cache object graphs did not reproduce on Apple Silicon at all;
  - varint writer variants ranked differently on the two;
  - a change that looked neutral on a Mac made transaction view scans 12% slower on EPYC, and the reverse.
- **Apple Silicon is fine for steering:** profiling, quick A/Bs, checking a hypothesis. Then confirm on the server
  before anything is merged or reverted.
- **Use two identical machines for final numbers** and compare only within a machine. Two EPYC 9275F machines agreed
  within ~3% on every benchmark; the same jar on two JDK patch levels need not.

## Run benchmarks so noise can't win

- JMH settings that held up: `-f 3 -wi 3 -w 1 -i 3 -r 2` (3 forks, 3 × 1 s warm-up, 3 × 2 s measurement), plus `-prof
  gc` for bytes allocated per operation. Use more forks or iterations when the error bars overlap the effect.
- **Interleave A and B** (A, B, A, B), never A, A then B, B. Drift (thermal, other tenants, turbo budgets) then shows up
  as disagreement between rounds instead of as a fake effect.
- **Nothing else on the machine.** A parallel Gradle build turned a 4% improvement into an apparent 2× regression. Check
  the load average before and after (under ~3 on an idle 18-core laptop), and don't run two JMH processes at once.
- **Pin to one core complex on big servers** (`taskset -c` with the cores of one CCD and their SMT siblings, for example
  `0-2,24-26` on a 24-core EPYC 9275F), so the scheduler can't move the benchmark across caches. Anything else running
  on the machine during a benchmark, even pinned to another CCD, is a reason to re-run it.
- **Call an effect real only if it repeats in every round, in the same direction.** Block-level view scans moved ±5%
  between rounds; differences smaller than that need more rounds or a second machine.

## Measure the thing users run

- **Cached sizes hide work.** PBJ model objects cache `protobufSize()`, so encoding the same objects repeatedly times
  only the write. Rebuild the objects for every invocation (`@Setup(Level.Invocation)`) or measure the whole producer
  path (build + encode) separately. `Level.Invocation` adds its own bias (GC of the rebuilt objects lands in the timed
  region), so also report the producer path end to end.
- **Time each codec from its own types.** Build the input untimed (model objects for PBJ, values for writers), time only
  encode and decode. Converting between a shared model and each codec's types swamped the codec cost when it was inside
  the timed region.
- **Sinks must be cheap.** `hashCode()` on a PBJ model walks the whole tree; use a static field or
  `System.identityHashCode` to keep results alive.
- **"Read every field" is its own benchmark.** Decoding into objects and reading through views do different work; a scan
  that sums every field (a checksum both sides must reproduce) keeps them honest and comparable.
- **Two workloads, not one.** The synthetic HAPI block (#1028) and an independent block-stream workload (deeply nested
  block items, state changes, map keys and values, oneofs at every level, signed transactions; no maps or wrapper types)
  agreed on every keep or drop decision but not on the size of the gaps (view scans 3.2× vs 2.3× the reference), because
  HAPI has more small nested messages per byte.
- **The reference must be strong.** A hand-written reference codec is the target that tells you how far off PBJ is. The
  first HAPI reference wrote front to back with plain varints and made PBJ look closer than it was; it was replaced by a
  back-to-front writer with the best varint code, as fast as a hand-written codec gets.
- **Allocation tests need a warm JIT.** A test that asserts zero bytes allocated can see allocation while code is
  still interpreted or not yet escape-analysed: one CI run of `BlockWriterTest` measured 144 B. It now takes the best
  of several warm rounds; do the same in any allocation assertion.
- **Byte identity first.** Every fast path must produce exactly the bytes of the plain codec (and of `protoc`), checked
  by tests before any timing. A faster wrong encoding is worthless, and several bugs (wrapper lengths for `-0.0`,
  `uint32 ≥ 2^31`) were found this way.

## Profile before optimising

- **async-profiler with `ann`** marks each frame `_[j]` (a separately compiled method: a real call) or `_[i]` (inlined).
  Use `event=cpu,interval=1ms,ann,collapsed` and summarise the collapsed stacks:
  - self time by leaf frame;
  - for a hot `_[j]` frame, **which callers** call it (the fix is often in the caller);
  - for views and parsers, attribute cursor frames (`ArrayCursor.*`) to the nearest generated frame (`index`, `wrap`, an
    accessor, a cursor's `next`), which splits the cost into "indexing", "wrapping" and "reading".
- **Allocation profiles** (`event=alloc`) show which objects remain; compare against what the model has to allocate.
- **Ask the JIT directly.** `-XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=quiet
  -XX:CompileCommand='PrintInlining,com.example.Foo$View::index'` prints the inlining tree of just that method, without
  the interleaving that global `-XX:+PrintInlining` produces. See [jit-and-inlining.md](jit-and-inlining.md) for how to
  read it.
- **Bound the gain with a flag before engineering it.** Running the same jar with `-XX:MaxInlineSize=300` (which forces
  the inlining you are considering) showed that inlining the varint reader into views was worth at most 12-13% of a
  scan. It was worth engineering; other candidates were not.
- **`javap -c -p`** gives bytecode sizes. A unit test that fails when a hot runtime method grows past its inline budget
  keeps it there (`GeneratedMethodSizeTest` does this for the 8,000-byte limit).

## Remote runs

- Long runs go on the server detached (`nohup setsid ./run.sh > log 2>&1 < /dev/null &`), with results fetched later;
  never depend on an interactive session staying up.
- If SSH is rate limited, use one multiplexed connection (`ControlMaster=auto`, `ControlPersist`) for the upload, start
  and fetch, and `rsync --partial` for large jars.
- A check like `pgrep -f run.sh` inside `ssh host "… ./run.sh …"` matches its own command line, so it always reports
  "already running". Check with `ps` after starting instead, or bracket a letter of the pattern (`[r]un.sh`) and keep
  the start command out of the same line.

