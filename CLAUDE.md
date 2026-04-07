# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PBJ (Protobuf Java Library) is an alternative Google Protocol Buffers code generator, parser, and runtime library. It generates Java model objects from `.proto` schema files with a focus on performance, deterministic encoding, and minimal garbage. Built for use in the [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) project.

For full documentation, see [docs/](docs/) and [architecture](docs/architecture.md).

## Repository Structure

Two independent top-level Gradle projects. See [architecture.md](docs/architecture.md) for module details and dependency graph.

- **`pbj-core/`** — Main library: compiler, runtime, gRPC server/client/common, dependency BOM
- **`pbj-integration-tests/`** — Generates code from Hiero protobufs, runs 100k+ tests, JMH benchmarks, fuzz tests

## Build Commands

Each top-level project has its own Gradle wrapper. Run commands from the respective directory.

```bash
# Build core libraries
cd pbj-core
./gradlew assemble              # Compile only
./gradlew build                 # Compile + test
./gradlew qualityGate           # Apply formatting + all quality checks (spotless, checkstyle, PMD, detekt)

# Run a single test
./gradlew pbj-runtime:test --tests "com.hedera.pbj.runtime.SomeTestClass"
./gradlew pbj-runtime:test --tests "com.hedera.pbj.runtime.SomeTestClass.someMethod"

# Code formatting
./gradlew spotlessApply         # Auto-fix formatting
./gradlew spotlessCheck         # Check only

# Integration tests (run before committing)
cd pbj-integration-tests
./gradlew build                 # Generates code + runs 100k+ tests
./gradlew jmh                  # JMH benchmarks
```

## Code Style

- Max line length: **120 characters**
- File encoding: UTF-8, LF line endings
- Checkstyle config: `pbj-core/config/checkstyle/`
- PMD rules: `pbj-core/config/pmd/ruleset.xml`
- Spotless enforces formatting; run `spotlessApply` before committing

## Key Paths

- Proto files: `src/main/proto/`
- Generated code: `build/generated/source/pbj-proto/`
- ANTLR grammar: `pbj-compiler/src/main/antlr/com/hedera/hashgraph/protoparser/grammar/Protobuf3.g4`
- Plugin ID: `com.hedera.pbj.pbj-compiler`
- PBJ package comment: `// <<<pbj.java_package = "com.example.package">>>`

## Tech Stack

- **Java 25** (Temurin), full JPMS module system
- **Gradle 9.1.0** with Kotlin DSL
- **ANTLR 4.13.2** for protobuf grammar parsing
- **Helidon 4.4.0** for HTTP/2 and gRPC
- **JUnit 5**, Mockito, AssertJ for testing

## Key Design Principles

- Getters return `null` for absent fields (not defaults) — use `foo()` or `fooOrElse(default)`
- Fields always serialized in ascending field order; maps sorted by key (deterministic encoding)
- Wire format identical to Google `protoc` output
- Generated code should look hand-written and readable
- Generated model objects are immutable classes (not Records — see [protobuf-and-schemas.md](docs/protobuf-and-schemas.md#why-not-java-records))
