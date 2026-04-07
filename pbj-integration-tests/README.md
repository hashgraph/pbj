# PBJ Integration Tests

A separate Gradle project that uses the `pbj-compiler` plugin to generate code from the [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) protobuf schemas. The generated code is compiled and tested as part of the build to ensure correctness across a wide range of protobuf features.

For build instructions, see the [main README](../README.md#build).

## Test Suites

- **Everything message** — An `Everything` protobuf message with every possible field type, used to validate encoding and decoding of all field types. New protobuf features should be added here.
- **JMH benchmarks** — Performance comparison of PBJ vs Google Protobuf generated code. Run with `./gradlew jmh`.
- **Fuzz testing** — Corrupts serialized data and verifies that parsing produces expected errors rather than crashes or silent data corruption. Run with `./gradlew fuzzTest`.
