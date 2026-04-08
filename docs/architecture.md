# PBJ Architecture

This document describes the high-level architecture of PBJ — its module structure, component relationships, data flows, and key design decisions.

For protobuf specification coverage, see [protobuf-and-schemas.md](protobuf-and-schemas.md). For compiler internals, see [code-generation.md](code-generation.md). For codec details, see [codecs.md](codecs.md), [codec-protobuf.md](codec-protobuf.md), and [codec-json.md](codec-json.md).

## System Overview

PBJ has three major subsystems:

1. **Compiler** — a Gradle plugin that parses `.proto` files and generates Java source code
2. **Runtime** — the library that generated code depends on at runtime (codecs, IO abstractions, collection types)
3. **gRPC** — a client and server implementation built on Helidon's HTTP/2 stack, with no `io.grpc` dependency

```
┌─────────────────────────────────────────────────────────────┐
│                      Build Time                             │
│  ┌────────────────┐    .proto files                         │
│  │  pbj-compiler  │ ──────────────────► Generated Java      │
│  │ (Gradle Plugin)│                     (models, codecs,    │
│  └────────────────┘                     schemas, tests)     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      Runtime                                │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────────┐  │
│  │ pbj-runtime  │◄──│ Generated    │──►│  Application    │  │
│  │ (codecs, IO, │   │ Code         │   │  Code           │  │
│  │  types)      │   └──────────────┘   └────────┬────────┘  │
│  └──────┬───────┘                               │           │
│         │                                       │           │
│  ┌──────┴────────────────────────────────────┐  │           │
│  │              gRPC Layer                   │◄─┘           │
│  │  ┌──────────────┐  ┌───────────────────┐  │              │
│  │  │ pbj-grpc-    │  │ pbj-grpc-client-  │  │              │
│  │  │ helidon      │  │ helidon           │  │              │
│  │  │ (server)     │  │ (client)          │  │              │
│  │  └──────┬───────┘  └────────┬──────────┘  │              │
│  │         └────────┬──────────┘             │              │
│  │           ┌──────┴───────┐                │              │
│  │           │ pbj-grpc-    │                │              │
│  │           │ common       │                │              │
│  │           │ (compression)│                │              │
│  │           └──────────────┘                │              │
│  └───────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

## Module Structure

PBJ is organized as two independent top-level Gradle projects. All modules under `pbj-core` use the Java Platform Module System (JPMS), except the compiler (which is a Gradle plugin and cannot be a JPMS module).

### `pbj-core/` — Main Library

| Module                       | JPMS Name                            | Purpose                                                                  |
|------------------------------|--------------------------------------|--------------------------------------------------------------------------|
| `pbj-compiler`               | _(none — Gradle plugin)_             | Parses `.proto` files, generates Java source code                        |
| `pbj-runtime`                | `com.hedera.pbj.runtime`             | Core runtime: codecs, IO abstractions, collection types, gRPC interfaces |
| `pbj-grpc-helidon`           | `com.hedera.pbj.grpc.helidon`        | gRPC server on Helidon WebServer (HTTP/2)                                |
| `pbj-grpc-helidon-config`    | `com.hedera.pbj.grpc.helidon.config` | Helidon annotation processor config for gRPC server                      |
| `pbj-grpc-client-helidon`    | `com.hedera.pbj.grpc.client.helidon` | gRPC client using Helidon HTTP/2 WebClient                               |
| `pbj-grpc-common`            | `com.hedera.pbj.grpc.common`         | Shared gRPC utilities (compression transformers)                         |
| `hiero-dependency-versions/` | —                                    | BOM for dependency version management                                    |

### `pbj-integration-tests/` — Validation Suite

A separate Gradle project that downloads the [Hiero protobufs](https://github.com/hiero-ledger/hiero-consensus-node) (tag v0.55.0) and generates code with both PBJ and Google `protoc`. Runs 100k+ tests to validate binary compatibility, plus JMH benchmarks and fuzz tests.

### Dependency Graph

```
pbj-compiler (build-time only, not a runtime dependency)

pbj-runtime
├── org.antlr:antlr4-runtime (JSON parser)
└── jdk.unsupported (Unsafe for optimized byte copies)

pbj-grpc-common
├── pbj-runtime
└── com.github.luben:zstd-jni (Zstandard compression)

pbj-grpc-helidon (server)
├── pbj-runtime
├── pbj-grpc-common
├── pbj-grpc-helidon-config
└── io.helidon.webserver.http2

pbj-grpc-client-helidon (client)
├── pbj-runtime
├── pbj-grpc-common
└── io.helidon.webclient.http2
```

The compiler has no runtime dependency on `pbj-runtime` — it generates code that imports runtime types, but the compiler itself only needs ANTLR and Gradle APIs.

## Compiler Architecture

The compiler is a Gradle plugin (plugin ID: `com.hedera.pbj.pbj-compiler`) that transforms `.proto` schema files into Java source code. It runs at build time as part of the Gradle compilation pipeline.

### Compilation Pipeline

```
.proto files (source + classpath JARs)
         │
         ▼
┌─────────────────────┐
│  Phase 1: Scan      │  Parse ALL proto files to build global
│  (LookupHelper)     │  type/package resolution tables
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  Phase 2: Parse     │  ANTLR lexer + parser per source file
│  (Protobuf3.g4)     │  → parse tree (not full AST)
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  Phase 3: Generate  │  Walk parse tree, run generators
│  (Generators)       │  per top-level definition
└─────────┬───────────┘
          │
          ▼
    Generated Java source
    (models, codecs, schemas, tests)
```

### Generated Artifacts

For each protobuf message, the compiler produces up to six Java files (model, schema, protobuf codec, JSON codec, XDR codec, and test). Enums and services each produce a single file. For full details on the generators and output structure, see [code-generation.md](code-generation.md#code-generators).

### Field Model

The compiler uses lightweight field records (`SingleField`, `OneOfField`, `MapField`) extracted directly from ANTLR parse tree contexts, rather than a full AST. For details, see [code-generation.md](code-generation.md#field-model-intermediate-representation).

## Runtime Library

The runtime (`pbj-runtime`) provides everything that generated code needs at runtime. It has four main areas:

### Codec Interfaces

The `Codec<T>` interface is the core serialization contract. Every generated message exposes singleton `PROTOBUF`, `JSON`, and `XDR` codec instances. Codecs provide parse, write, measure, and fast-equals operations with configurable safety limits (`maxSize` and `maxDepth`). For full details, see [codecs.md](codecs.md) and [codec-xdr.md](codec-xdr.md).

### IO Abstractions

Codecs read and write through abstract sequential data interfaces (`ReadableSequentialData`, `WritableSequentialData`), decoupling serialization from the underlying byte source. Concrete implementations include `BufferedData` (byte buffers), `ReadableStreamingData` (input streams), `WritableStreamingData` (output streams), and `Bytes` (immutable byte sequences). For details, see [codecs.md](codecs.md#streaming-data-abstractions).

### Collection Types

PBJ provides specialized collection types optimized for generated code:

- **`PbjMap<K, V>`** — an immutable map wrapper that exposes `getSortedKeys()` for deterministic iteration. Maps are always serialized with keys in natural sort order.
- **`UnmodifiableArrayList<E>`** — a list that starts mutable during parsing (elements added one at a time) and is frozen via `makeReadOnly()` when parsing completes. More efficient than copying into `Collections.unmodifiableList()`.
- **`Bytes`** — an immutable byte sequence. Wraps `byte[]` or `ByteBuffer` and prevents accidental mutation. Supports efficient `writeTo(MessageDigest)` and `writeTo(OutputStream)` without copying.
- **`OneOf<E>`** — a type-safe discriminated union for protobuf `oneof` fields. Stores a discriminator enum (`kind`) and the value. `ComparableOneOf<E>` adds `Comparable` support.

### Serialization Helpers

Static utility classes provide the low-level read/write primitives that generated codecs call:

- **`ProtoParserTools`** — reads varints, fixed-width values, strings, bytes, and handles unknown field skipping/extraction
- **`ProtoWriterTools`** — writes tags, scalars, length-delimited fields, and repeated fields (with packed encoding)
- **`ProtoArrayWriterTools`** — mirror of `ProtoWriterTools` that writes directly to `byte[]` for maximum performance (avoids virtual dispatch)
- **`JsonTools`** — JSON parsing (via ANTLR), field name conversion (snake_case to camelCase), string escaping, and typed value extraction

## gRPC Architecture

PBJ implements gRPC directly on Helidon's HTTP/2 stack, without depending on Google's `io.grpc` library. This provides low-level control over bytes and framing, eliminates a large transitive dependency tree, and allows fail-fast security behavior.

### Runtime gRPC Interfaces

The `pbj-runtime` module defines the gRPC abstractions that both client and server implement:

```
ServiceInterface              GrpcClient
  - serviceName()              - createCall(method, reqCodec, replyCodec, pipeline)
  - fullName()
  - methods()                 GrpcCall<Req, Reply>
  - open(method, options,       - sendRequest(req, endOfStream)
         pipeline)              - completeRequests()

Pipeline<T> (extends Flow.Subscriber<T>)
  - onNext(T)
  - clientEndStreamReceived()
  - onComplete()
  - onError(Throwable)
```

Generated service interfaces extend `ServiceInterface` and provide an `open()` method that routes to the correct RPC handler based on the method enum.

### gRPC Message Framing

Both client and server use the standard gRPC length-prefixed framing:

```
┌──────────┬──────────────┬─────────────────────┐
│ 1 byte   │ 4 bytes      │ N bytes             │
│ compress │ msg length   │ message payload     │
│ flag     │ (big-endian) │ (raw or compressed) │
└──────────┴──────────────┴─────────────────────┘
```

Compression is negotiated per-call via HTTP/2 headers:
- `grpc-accept-encoding` — compression algorithms the sender supports
- `grpc-encoding` — the algorithm used for this message

### Server (`pbj-grpc-helidon`)

The server integrates with Helidon WebServer as an HTTP/2 sub-protocol provider, discovered via `ServiceLoader`:

```
Helidon WebServer (HTTP/2)
    │
    ▼ (ServiceLoader)
PbjProtocolProvider
    │
    ▼ (creates per connection)
PbjProtocolSelector
    │
    ▼ (creates per HTTP/2 stream)
PbjProtocolHandler
    ├── Parses HTTP/2 headers (:method POST, :path /service/method)
    ├── Reads content-type, grpc-encoding, grpc-timeout headers
    ├── Buffers and deframes DATA frames
    ├── Decompresses payload if needed
    ├── Routes to ServiceInterface via PbjServiceRoute → PbjMethodRoute
    ├── Deserializes request via Codec
    ├── Invokes service method
    ├── Serializes response via Codec
    ├── Frames and compresses response
    └── Writes HTTP/2 DATA frames + trailers (grpc-status)
```

The server supports all four gRPC call types (unary, client-streaming, server-streaming, bidirectional) and handles deadline propagation via the `grpc-timeout` header.

### Client (`pbj-grpc-client-helidon`)

The client wraps Helidon's HTTP/2 WebClient:

```
PbjGrpcClient (implements GrpcClient)
    │
    ▼ (per call)
PbjGrpcCall<Req, Reply> (implements GrpcCall)
    ├── sendRequest(req, endOfStream)
    │   ├── Serializes via Codec
    │   ├── Compresses if negotiated
    │   ├── Frames as gRPC message
    │   └── Sends HTTP/2 DATA frame
    │
    └── Receives responses
        ├── PbjGrpcDatagramReader deframes DATA
        ├── Decompresses payload
        ├── Deserializes via Codec
        ├── Calls Pipeline.onNext(reply)
        └── On trailers: checks grpc-status, calls onComplete/onError
```

The client supports custom metadata (propagated as HTTP/2 headers), TLS configuration, and connection pooling via the underlying WebClient.

### Compression (`pbj-grpc-common`)

Compression algorithms are registered in a global `GrpcCompression` registry. Built-in support:

| Algorithm       | Header value | Implementation                                                |
|-----------------|--------------|---------------------------------------------------------------|
| Identity (none) | `identity`   | `IdentityGrpcTransformer`                                     |
| Gzip            | `gzip`       | `GzipGrpcTransformer`                                         |
| Zstandard       | `zstd`       | `ZstdGrpcTransformer` (in `pbj-grpc-common`, uses `zstd-jni`) |

Custom compression algorithms can be registered at runtime via `GrpcCompression.registerCompressor()`.

## Data Flow: End to End

### Proto to Running Service

```
 1. Developer writes .proto file
                │
 2. Gradle build triggers PbjCompilerTask
                │
 3. Compiler generates: Model + ProtoCodec + JsonCodec + Schema + ServiceInterface
                │
 4. Java compiler compiles generated + hand-written code
                │
 5. Application creates ServiceInterface implementation
                │
 6. Helidon WebServer starts, discovers PbjProtocolProvider
                │
 7. Client sends gRPC request (HTTP/2 POST)
                │
 8. PbjProtocolHandler deframes → Codec.parse() → ServiceInterface.open()
                │
 9. Service method returns response
                │
10. Codec.write() → PbjProtocolHandler frames → HTTP/2 response
```

### Serialization Round-Trip

```
Model object (e.g., HelloRequest)
    │
    ▼  Codec.write(obj, output)
WritableSequentialData ───► bytes on wire
    │                        (fields in ascending order,
    │                         maps sorted by key,
    │                         default fields omitted)
    │
    ▼  Codec.parse(input)
ReadableSequentialData ───► Model object
                            (unknown fields optionally preserved,
                             missing fields → null)
```

## Key Design Decisions

### Immutable Model Objects

Generated model classes are immutable — all fields are `final`, there are no setters, and construction goes through a `Builder`. This enables safe sharing across threads without synchronization, which is critical in a multi-threaded consensus node.

### Separation of Concerns

Each generated message produces four distinct classes (model, schema, proto codec, JSON codec) rather than bundling serialization logic into the model. This keeps each class focused, avoids polluting the model API with serialization details, and allows different codecs to be optimized independently.

### No `io.grpc` Dependency

PBJ implements the gRPC wire protocol directly on Helidon's HTTP/2 stack. This eliminates `io.grpc`'s large transitive dependency tree, gives PBJ full control over byte handling and framing, and enables features like custom compression and fail-fast security behavior that are difficult to achieve through `io.grpc`'s abstractions.

### Two Write Paths

Generated codecs provide both a streaming write path (`write(T, WritableSequentialData)`) and a byte-array write path (`write(T, byte[], offset)`). The array path avoids virtual dispatch overhead on the output stream and is measurably faster for small-to-medium messages. The streaming path handles cases where the output destination is not a simple byte array (network sockets, direct buffers).

### ANTLR for Both Proto and JSON Parsing

PBJ uses ANTLR4 for parsing both `.proto` schema files (at build time) and JSON data (at runtime). For proto files this is the natural choice. For JSON, the ANTLR approach builds a parse tree before walking it, which is simpler than streaming JSON parsing but means the entire JSON payload must fit in memory. This is acceptable for PBJ's target use case where messages are bounded by `maxSize`.

### Lazy Computation

Model objects defer expensive computations (`hashCode()`, `protobufEncodedSize()`) until first access, then cache the result. This avoids paying the cost in constructors — important when objects are created in hot paths but may never be hashed or measured. The caching uses a benign-race idiom (safe because the computation is idempotent and the fields are `int`).

## Technology Stack

| Component     | Technology                | Version |
|---------------|---------------------------|---------|
| Language      | Java (Temurin)            | 25      |
| Module system | JPMS                      | —       |
| Build         | Gradle (Kotlin DSL)       | 9.1.0   |
| Proto parsing | ANTLR                     | 4.13.2  |
| HTTP/2 server | Helidon WebServer         | 4.4.0   |
| HTTP/2 client | Helidon WebClient         | 4.4.0   |
| Compression   | zstd-jni                  | —       |
| Testing       | JUnit 5, Mockito, AssertJ | —       |
| Benchmarks    | JMH                       | —       |