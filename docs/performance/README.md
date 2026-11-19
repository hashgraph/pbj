# Performance work on PBJ

What the fast code generation series (#991 to #1035) learned about making PBJ fast, so the next round starts from it
instead of rediscovering it. Read the rules below first, then the document for what you are changing.

| Document | Read it for |
|---|---|
| [measuring.md](measuring.md) | How to measure: the decision platform, JMH pitfalls, profiling recipes |
| [jit-and-inlining.md](jit-and-inlining.md) | HotSpot's inlining and method-size limits as they hit generated code |
| [format-comparison.md](format-comparison.md) | Protobuf against SBE and other formats; where the gap comes from; schema design |
| [zero-allocation.md](zero-allocation.md) | The garbage-free read and write paths, their APIs, what each costs, what's left |
| [history.md](history.md) | Every PR in the series: what changed, why, the numbers |
| [`../fast-codegen-plan.md`](../fast-codegen-plan.md) | The plan, its tiers and options; §9 is the list of tried-and-reverted lessons |

## Rules to remember

1. **Decide on AMD EPYC, iterate on Apple Silicon.** Several results reversed between the two: the reverse writer's
   out-of-cache slowdown, inlining a bigger varint reader, a two-pass reference encoder. Pin to one CCD of an idle
   machine, 3 forks, `-prof gc`, and confirm a close result on a second machine or a second workload.
2. **Measure on the real shape of the data.** A prototype and an isolated varint benchmark both pointed at a varint
   writer that lost on HAPI block items. Use the synthetic HAPI block (#1028) and its hand-written reference codec
   as the target.
3. **Know what a benchmark really times.** PBJ caches `protobufSize()`, so repeated encodes skip measuring; objects
   built in `@Setup(Level.Invocation)` are cache-hot; out-of-cache behaviour needs a working set larger than the
   caches.
4. **Keep every generated method under 8,000 bytes of bytecode.** HotSpot never compiles larger ones; one such method
   made `TransactionBody` parse 3.3× slower. `GeneratedMethodSizeTest` enforces 7,000.
5. **Keep hot helpers small enough to inline, and their rare cases out of line.** Splitting the varint reader's common
   path from its slow path paid; inlining a whole varint writer cost more in code size than the call saved. A frame
   the JIT didn't inline is not, by itself, a lever.
6. **Per-message cost is the cost.** HAPI nests a message for every id and timestamp. Count what each nested message
   costs on every path (open, check, patch, wrap, index, reset) before optimising anything per field.
7. **In-order fast paths, general fallbacks.** Protobuf writers emit ascending fields; expect the next tag, and fall
   back to a full, validating loop for anything else. The non-canonical-order test is what keeps the fallback right.
8. **Allocation is a test, not a hope.** Every option-on path is 0 B/op once warm, asserted by `AllocationTest` and
   `BlockWriterTest`; take the best warm round, as the JIT can still be compiling on a busy machine.
9. **Revert what doesn't win on EPYC, and write down why** in [§9 of the plan](../fast-codegen-plan.md), so nobody
   tries it again without new evidence.
10. **Byte-identical to protoc, always.** No speed-up is worth a different encoding: hashes depend on it.
