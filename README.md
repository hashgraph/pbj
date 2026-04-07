# PBJ Protobuf Java Library

An alternative Google Protocol Buffers code generator, parser, and Gradle plugin. The project has these design goals:

* **Modern Java Objects** — Generate clean, immutable Java classes with Record-style getters. Support latest Java version features.
* **Explicit Error Handling** — Return `null` from getters for absent fields, not default objects. This forces the developer to handle the absent case rather than silently ignoring it. Each field has two getter forms: `foo()` and `fooOrElse(defaultValue)`.
* **Performance Optimized** — Be as fast or faster than Google `protoc`-generated code.
* **Minimal Garbage** — Produce the minimum amount of garbage Java objects possible.
* **Identical Binary Wire Encoding** — Produce the same binary encoding as `protoc`.
* **Deterministic Binary Encoding** — Fields are always serialized in ascending field order and all maps are sorted by key. This ensures hashes and signatures of serialized objects are dependable.
* **Stable `hashCode()` and `equals()`** — Fields with default values are excluded from `hashCode()` and `equals()`, so adding new default-valued fields does not affect existing hash maps.
* **Value Classes Ready** — Keep [JEP 401: Value Classes and Objects](https://openjdk.org/jeps/401) in mind for future generated model objects.
* **Minimal Dependencies** — Only use third-party libraries when truly needed and well maintained.
* **Low-Level Protobuf API** — Provide a low-level API for manually reading/writing protobuf in buffers, byte arrays, and streams.
* **`io.grpc` Alternative** — Provide a gRPC implementation on Helidon SE with low-level access to bytes, no `io.grpc` dependency, and fail-fast security.
* **Clean Generated Code** — Generated code should be as clean and readable as if carefully written by hand.

These design goals often compete with each other, so this project strikes the right balance for use in the [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) project. There is still plenty of work to achieve these goals, but this is what the project strives for.

## Documentation

| Document | Description |
|----------|-------------|
| [Getting Started](docs/getting-started.md) | Quick-start guide: add PBJ to your project and use generated classes |
| [Usage Guide](docs/usage-guide.md) | Comprehensive reference for all PBJ features with real-world examples |
| [Architecture](docs/architecture.md) | Module structure, dependency graph, system overview, and design decisions |
| [Protobuf & Schemas](docs/protobuf-and-schemas.md) | Protobuf spec compliance, type mappings, nullability, and PBJ extensions |
| [Code Generation](docs/code-generation.md) | Compiler internals: pipeline, ANTLR grammar, generators |
| [Codec Architecture](docs/codecs.md) | Shared codec interfaces, IO abstractions, and design principles |
| [Protobuf Codec](docs/codec-protobuf.md) | Binary protobuf codec internals |
| [JSON Codec](docs/codec-json.md) | JSON codec internals |

## Project Structure

There are two top-level Gradle projects:

* **PBJ Core** `pbj-core/` — Main library with the following subprojects:
  * [**PBJ Compiler**](pbj-core/pbj-compiler/README.md) `pbj-compiler` — Gradle plugin that compiles `.proto` files to Java
  * [**PBJ Runtime**](pbj-core/pbj-runtime/README.md) `pbj-runtime` — Core runtime for generated code (codecs, IO, types)
  * [**gRPC Helidon**](pbj-core/pbj-grpc-helidon/README.md) `pbj-grpc-helidon` — gRPC server on Helidon SE
  * [**gRPC Helidon Config**](pbj-core/pbj-grpc-helidon-config/README.md) `pbj-grpc-helidon-config` — Helidon annotation processor config
  * [**gRPC Client Helidon**](pbj-core/pbj-grpc-client-helidon/README.md) `pbj-grpc-client-helidon` — gRPC client using Helidon HTTP/2
  * [**gRPC Common**](pbj-core/pbj-grpc-common/README.md) `pbj-grpc-common` — Shared gRPC utilities (compression, etc.)
* [**Integration Tests**](pbj-integration-tests/README.md) `pbj-integration-tests/` — Generates code from Hiero protobufs and runs 100k+ tests

## Build

Each top-level project has its own Gradle wrapper. Run commands from the respective directory.

```bash
# Build core libraries
cd pbj-core
./gradlew build

# Run integration tests (recommended before committing)
cd pbj-integration-tests
./gradlew build
```

## Long-Term Goals

* Support all protobuf features (including the `optional` keyword)
* Auto-mapping gRPC APIs to JSON REST APIs (gRPC transcoding)
* Performance optimizations (SIMD-based varint processing)
* Support for additional serialization formats
