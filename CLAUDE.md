# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PBJ (Protobuf Java Library) is an alternative Google Protocol Buffers code generator, parser, and runtime library. It generates Java model objects from `.proto` schema files with a focus on performance, deterministic encoding, and minimal garbage. Built for use in the [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) project.

## Repository Structure

Two independent top-level Gradle projects:

- **`pbj-core/`** — Main library (build plugin: `org.hiero.gradle.build:0.6.3`)
  - `pbj-compiler` — Gradle plugin that compiles `.proto` files to Java classes using ANTLR4. Plugin ID: `com.hedera.pbj.pbj-compiler`. Not a JPMS module (no module-info.java).
  - `pbj-runtime` — Core runtime for generated code (codecs, IO, JSON/Protobuf parsers, `Bytes` immutable wrapper)
  - `pbj-grpc-helidon` — gRPC server on Helidon SE (zero third-party gRPC deps)
  - `pbj-grpc-helidon-config` — Helidon annotation processor config for gRPC
  - `pbj-grpc-client-helidon` — gRPC client using Helidon HTTP/2 webclient
  - `pbj-grpc-common` — Shared gRPC utilities (compression, etc.)
  - `hiero-dependency-versions/` — BOM for version management
- **`pbj-integration-tests/`** — Clones hedera-protobufs (tag v0.55.0), generates code with both PBJ and Google Protobuf, runs 100k+ tests for binary compatibility validation. Also contains JMH benchmarks and fuzz tests.

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

## Tech Stack

- **Java 25** (Temurin distribution), full JPMS module system
- **Gradle 9.1.0** with Kotlin DSL build scripts
- **ANTLR 4.13.2** for protobuf grammar parsing
- **Helidon 4.4.0** for HTTP/2 and gRPC
- **JUnit 5**, Mockito, AssertJ for testing

## Code Generation

- Proto files go in `src/main/proto/`
- Generated code outputs to `build/generated/source/pbj-proto/`
- Custom Java package override via comment in `.proto` files: `// <<<pbj.java_package = "com.example.package">>>`
- Generates: model classes (immutable, not Records), codecs (Protobuf + JSON), schemas, builders, and unit tests
- ANTLR grammar: `pbj-compiler/src/main/antlr/com/hedera/hashgraph/protoparser/grammar/Protobuf3.g4`

## Code Style

- Max line length: **120 characters**
- File encoding: UTF-8, LF line endings
- Checkstyle config: `pbj-core/config/checkstyle/`
- PMD rules: `pbj-core/config/pmd/ruleset.xml`
- Spotless enforces formatting; run `spotlessApply` before committing

## Key Design Principles

- Getters return `null` for absent fields (not defaults) — use `foo()` or `fooOrElse(default)`
- Fields always serialized in ascending field order; maps sorted by key (deterministic encoding)
- Wire format identical to Google protoc output
- Generated code should look hand-written and readable
