# The JIT and generated code

What HotSpot (C2, JDK 25) does with PBJ's generated code, and the rules that follow. Most of PBJ's remaining performance
work is about these limits, not about arithmetic. How to observe them is in [measuring.md](measuring.md); the full list
of tried and reverted changes is in [§9 of the
plan](../fast-codegen-plan.md#9-lessons-already-learned-do-not-rediscover).

## The limits that matter

| Limit (default) | What it does | How it showed up in PBJ |
|---|---|---|
| `MaxInlineSize` = 35 bytes | Largest callee inlined at a call site C2 does **not** consider hot | Per-field helpers over 35 bytes were calls at most sites |
| `FreqInlineSize` = 325 bytes | Largest callee inlined at a **hot** call site | The 417-byte varint reader was never inlined anywhere |
| `InlineSmallCode` (~2,500 bytes of machine code) | A callee that is already compiled is only inlined if its compiled code is smaller | A 304-byte reader inlined at some sites of large view methods and not at others |
| `DesiredMethodLimit`, `MaxInlineLevel` | Stop inlining into a caller that is already large or deep | Copying every oneof alternative into a fast path made parsing slower |
| `HugeMethodLimit` = 8,000 bytes (`DontCompileHugeMethods`) | Methods over it are **never compiled** | HAPI's `TransactionBody` parser (15 KB) always ran interpreted; splitting made it 3.3× faster (#1015) |

## Hot and cold call sites

C2 decides "hot" **per call site**, from how often that site ran relative to how often its caller ran. The callee's own
size limit then follows: 325 bytes at a hot site, 35 at a cold one. Consequences:

- **A helper that is hot overall can still be a call everywhere.** `ArrayCursor.varint()` inlines (it is small), but its
  call to the multi-byte reader sits behind "the first byte has the continuation bit". In most messages most varints are
  one byte (tags, lengths, small numbers), so that branch is cold at almost every site, and the 275-byte multi-byte
  reader was "failed to inline: too big" in every view, even after it had been made small enough for a hot site.
- **The same helper inlines in one caller and not in another.** The parser's call sites for the reader were hot and
  inlined; the views' were not. Check each important caller with `CompileCommand=PrintInlining` (see
  [measuring.md](measuring.md#profile-before-optimising)).
- **A dedicated helper per field type gives the JIT the right frequency.** Reading 64-bit varint fields through their
  own `varint64()` (the whole chain in one 304-byte method) made those call sites hot in their own right, so C2 inlined
  them where values really are large: block view scans 7.5% faster on HAPI and 12% faster on the independent workload.
  It cost 12 ns per transaction on that workload's transaction scans (small messages, where the larger code was not
  worth it); HAPI's were flat.

## Reading `PrintInlining`

| Message | Meaning | What to do |
|---|---|---|
| `inline (hot)` | Inlined under `FreqInlineSize` | Nothing |
| `inline` | Inlined under `MaxInlineSize` | Nothing |
| `failed to inline: too big` | Cold site, callee over 35 bytes | Make the common path a tiny method, or make the site hot (a dedicated helper) |
| `failed to inline: hot method too big` | Hot site, callee over 325 bytes | Split: common path under 325, rare cases in a separate cold method |
| `failed to inline: callee is too large` / `already compiled into a big method` | `InlineSmallCode`: the callee's compiled code is too large | Smaller callee, or accept the call |
| `failed to inline: callee uses too much stack` | Too many live values | Usually harmless; avoid huge local-heavy helpers |

## What worked

- **Split hot readers into a small common path and a cold rest.** The varint reader (417 bytes) became a 2-8-byte XOR
  chain (~277 bytes) plus `varintSlow()` for 9-10-byte values, the near-the-end loop and every throw. Block decode got
  9% faster and transaction view scans 19% faster on EPYC on the independent workload, and 7% and 12.6% on HAPI
  (#1035).
  - Guard on **the array's** remaining length, not the cursor's limit: a view's limit is the end of a small message, so
    guarding on the limit sent the last fields of every child view down the slow path (8.5% of all scan samples landed
    there, on Apple Silicon).
  - Put the out-of-limit case in the cold method too, so the hot method constructs no exceptions.
- **Measure every split; the same idea can lose.** An earlier split of `varintWide` to under 325 bytes made parsing
  18-27% slower on EPYC (see [history.md](history.md)); the #1035 version, with the details above, made decode 7% and
  HAPI transaction view scans 12.6% faster. Inlining is a hypothesis until the server says otherwise.
- **Keep exception construction out of hot methods.** `throw new X(message + value)` adds dozens of bytes of bytecode to
  a method that may need to stay under 35 or 325.
- **Direct static calls for nested messages** instead of codec interface dispatch (#995, #997): no megamorphic call
  sites in generated code.
- **Split huge generated methods** into per-field helpers once a message has more than 32 fields
  (`GeneratedMethodSizeTest` keeps every generated method under 7,000 bytes, #1015).

## What did not work

- **Making a writer helper inlinable.** `writeVarIntWide` is 437 bytes and never inlined, so every 4-8-byte varint a
  writer wrote was a call. A 316-byte version that inlined made block encoding 5-9% and transaction encoding 9% slower
  on EPYC, and was neutral on HAPI (+2%). At every varint site of the large generated write methods, the extra code cost
  more than the call did. Writing nine-byte values inline (one 8-byte store plus a byte) was neutral on both workloads,
  even one full of int64 nanosecond times.
- **An 8-byte single-store varint in the forward writer**, even with slack in the writers' buffer making it safe:
  writing HAPI block items from values took 356 ns against 317 ns with exact-size stores on EPYC (#1023), and a later
  prototype on the independent workload (a single store for 3-8-byte values, plus a two-store 9-10-byte path) took
  895 ns per transaction against 875. It can only win in a reverse writer, where the store can run into the bytes not
  yet written.
- **Fast-path copies of every oneof alternative** in parsers: they blew the inlining budget of the parse method.

The rule that came out of it: **on the write side, call overhead isn't the cost, so a frame the JIT didn't inline is not
by itself a lever.** Look at what the profile says the time is spent *doing* before trying to inline it. On the read
side, where one helper (the varint reader) is a large share of all time, inlining it was worth it.

## Rules of thumb for generated code

- Per-field helpers called from many sites: at most 35 bytes.
- Hot runtime helpers: under 325 bytes, rare cases in a separate method.
- Generated methods: well under 8,000 bytes; split per field beyond ~32 fields.
- One `switch` per oneof; don't duplicate code per alternative.
- Verify with `PrintInlining` on the generated methods of a real schema, not with a micro-benchmark of the helper alone:
  inlining depends on the caller.

