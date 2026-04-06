# PBJ Usage Guide

This is a comprehensive reference for using PBJ in your projects. It covers all major usage patterns with real-world examples from the [Hiero Block Node](https://github.com/hiero-ledger/hiero-block-node) and [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) projects.

For a minimal quick-start, see [getting-started.md](getting-started.md). For protobuf specification details, see [protobuf-and-schemas.md](protobuf-and-schemas.md).

## Gradle Plugin Configuration

### Applying the Plugin

```kotlin
plugins {
    id("com.hedera.pbj.pbj-compiler") version "<version>"
}

dependencies {
    implementation("com.hedera.pbj:pbj-runtime:<version>")
}
```

### Plugin Options

```kotlin
pbj {
    javaPackageSuffix = ".pbj"      // appended to derived package names
    generateTestClasses = true       // generate JUnit 5 tests (default: true)
}
```

Setting `generateTestClasses = false` avoids requiring a dependency on Google Protobuf libraries (which the generated tests use for binary compatibility validation).

### Multiple Source Directories

PBJ supports multiple proto source directories with override precedence:

```kotlin
sourceSets {
    main {
        pbj {
            srcDir(layout.projectDirectory.dir("src/main/proto"))
            srcDir(layout.projectDirectory.dir("proto-overrides"))
            exclude("*.proto")  // exclude specific patterns
        }
    }
}
```

### Coexisting with protoc

PBJ and `protoc` can generate code from the same proto files into different packages:

```protobuf
package com.example;
option java_package = "com.example.protoc";           // protoc uses this
// <<<pbj.java_package = "com.example.pbj">>>          // PBJ uses this
```

## Proto File Conventions

### PBJ Package Override

The `// <<<pbj.java_package = "...">>>` comment overrides the Java package for all PBJ-generated classes in the file. This takes highest priority in package resolution:

```protobuf
syntax = "proto3";
package org.hiero.block.api;

option java_package = "org.hiero.block.api.protoc";
// <<<pbj.java_package = "org.hiero.block.api">>>
option java_multiple_files = true;
```

### Per-Definition Package Options

For finer control, override packages per definition type:

```protobuf
option (pbj.message_java_package) = "com.example.messages";
option (pbj.enum_java_package) = "com.example.enums";
option (pbj.service_java_package) = "com.example.services";
```

### Comparable Messages

Add the `pbj.comparable` option to generate a `compareTo()` method (the class will implement `Comparable<T>`). You can make all fields comparable or specify a subset:

```protobuf
// All fields are comparable
// <<<pbj.comparable = "seconds, nanos">>>
message Timestamp {
    int64 seconds = 1;
    int32 nanos = 2;
}

// Only a subset of fields — non-listed fields are ignored in comparison
// <<<pbj.comparable = "accountId, balance">>>
message Account {
    int64 accountId = 1;
    int64 balance = 2;
    string memo = 3;              // not included in compareTo()
    repeated int32 tags = 4;      // repeated fields cannot be comparable
}
```

The generated `compareTo()` compares fields in the order listed, using the appropriate comparison for each type (`Integer.compare()`, `Long.compareUnsigned()`, null-safe `String.compareTo()`, etc.). Nested message fields must themselves be comparable.

Supported in the comparable list: all scalar types, strings, bytes, enums, messages, oneOf fields, and wrapper types. Repeated fields and map fields cannot be included.

## Working with Generated Model Objects

### Creating Objects

Use the builder pattern — all model objects are immutable:

```java
// Simple object
HelloRequest request = HelloRequest.newBuilder()
        .name("World")
        .count(5)
        .build();

// Object with nested message
BlockProof proof = BlockProof.newBuilder()
        .block(blockNumber)
        .signedBlockProof(TssSignedBlockProof.newBuilder()
                .blockSignature(Bytes.wrap(signatureBytes)))
        .build();

// Object with repeated fields
Block block = Block.newBuilder()
        .blockItems(itemList)
        .build();
```

### Reading Fields

PBJ provides several accessor patterns for each field:

```java
// Direct access — returns null for absent reference types, default for primitives
String name = request.name();          // null if absent
int count = request.count();           // 0 if absent (Java primitive)

// Safe access with fallback
String name = request.nameOrElse("");  // "" if absent
int count = request.countOrElse(42);   // 42 if absent

// Throwing access — throws NullPointerException if absent
String name = request.nameOrThrow();

// Presence check
if (request.hasName()) { ... }

// Consumer-style access
request.ifName(name -> System.out.println(name));
```

**Important:** PBJ returns `null` for absent reference-type fields (unlike `protoc`, which returns defaults). For primitives (`int`, `long`, `boolean`, etc.), the getter returns the Java default when absent — use `fooOrElse()` to distinguish "not set" from "set to default."

### Modifying Immutable Objects

Use `copyBuilder()` to create a modified copy without affecting the original:

```java
EventCore modified = eventCore.copyBuilder()
        .birthRound(Long.max(eventCore.birthRound() - offset, 1))
        .build();
// eventCore is unchanged
```

### Default Instance

Every generated class has a `DEFAULT` singleton with all fields at their default values:

```java
HelloRequest empty = HelloRequest.DEFAULT;
```

### OneOf Fields

OneOf fields use a type-safe discriminated union:

```protobuf
message BlockRequest {
    oneof block_specifier {
        uint64 block_number = 1;
        bool retrieve_latest = 2;
    }
}
```

```java
// Check which variant is set
if (request.hasBlockNumber()) {
    long num = request.blockNumber();
} else if (request.hasRetrieveLatest() && request.retrieveLatest()) {
    // retrieve latest block
}

// Access the raw OneOf wrapper
OneOf<BlockRequest.BlockSpecifierOneOfType> specifier = request.blockSpecifier();
BlockRequest.BlockSpecifierOneOfType kind = specifier.kind();

// Cast with .as() when you know the type
long blockNum = specifier.as();  // cast to the active variant's type
```

### Repeated Fields

Repeated fields return immutable `List<T>`:

```java
// Reading
List<RosterEntry> entries = roster.rosterEntries();
for (RosterEntry entry : entries) {
    long nodeId = entry.nodeId();
}

// Building — pass a List to the builder
List<BlockItem> items = new ArrayList<>();
items.add(headerItem);
items.add(bodyItem);
Block block = Block.newBuilder()
        .blockItems(items)
        .build();
```

Empty repeated fields return `Collections.emptyList()`, never `null`.

### Map Fields

Map fields use `PbjMap<K, V>` with deterministic key ordering:

```java
PbjMap<String, Integer> settings = config.settings();

// Iterate in sorted key order (deterministic)
for (String key : settings.getSortedKeys()) {
    int value = settings.get(key);
}
```

## Serialization and Deserialization

### Protobuf Binary

Every generated class has a `PROTOBUF` codec for binary serialization:

```java
// Serialize to bytes
Bytes bytes = HelloRequest.PROTOBUF.toBytes(request);

// Parse from Bytes
HelloRequest parsed = HelloRequest.PROTOBUF.parse(bytes);

// Parse from ReadableSequentialData
HelloRequest parsed = HelloRequest.PROTOBUF.parse(readableData);
```

### JSON

Every generated class has a `JSON` codec:

```java
// Serialize to JSON string
String json = HelloRequest.JSON.toJSON(request);

// Pretty-print with indentation
String prettyJson = HelloRequest.JSON.toJSON(request, "  ", false);

// Parse from ReadableSequentialData
HelloRequest parsed = HelloRequest.JSON.parse(jsonData);
```

### Advanced Parsing Options

For untrusted input, customize safety limits:

```java
HelloRequest msg = HelloRequest.PROTOBUF.parse(
        input,              // ReadableSequentialData
        false,              // strictMode — throw on unknown fields?
        true,               // parseUnknownFields — preserve for round-trip?
        Codec.DEFAULT_MAX_DEPTH,  // max nesting depth (default: 512)
        maxMessageSize      // max field size in bytes (default: 2 MB)
);
```

Strict mode throws `UnknownFieldException` on unrecognized fields — useful for validation.

### Streaming I/O

Use `ReadableStreamingData` and `WritableStreamingData` for file or network I/O:

```java
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;

// Read from a file
try (var input = new ReadableStreamingData(new FileInputStream(file))) {
    MyMessage msg = MyMessage.PROTOBUF.parse(input);
}

// Write to a file
try (var output = new WritableStreamingData(new FileOutputStream(file))) {
    MyMessage.PROTOBUF.write(msg, output);
}
```

### The `Bytes` Type

PBJ uses `Bytes` instead of `byte[]` for immutable byte sequences. It implements `RandomAccessData` and `Comparable<Bytes>`.

```java
import com.hedera.pbj.runtime.io.buffer.Bytes;

// --- Creation ---
Bytes data = Bytes.wrap(byteArray);              // wrap byte[] (no copy)
Bytes data = Bytes.wrap(byteArray, offset, len); // wrap slice (no copy)
Bytes data = Bytes.wrap("hello");                // from UTF-8 string
Bytes data = Bytes.fromHex("3de47629...");       // from hex string
Bytes data = Bytes.fromBase64("SGVsbG8=");       // from Base64 string
Bytes merged = Bytes.merge(bytes1, bytes2);      // concatenate two Bytes

// --- Conversion ---
byte[] array = data.toByteArray();               // to byte[] (copies)
String hex = data.toHex();                       // to hex string
String b64 = data.toBase64();                    // to Base64 string
String utf8 = data.asUtf8String(0, data.length()); // decode as UTF-8

// --- Slicing and searching ---
Bytes slice = data.slice(offset, length);        // zero-copy view of sub-range
Bytes appended = data.append(moreBytes);         // concatenate (creates new Bytes)
Bytes copy = data.replicate();                   // defensive copy
boolean found = data.contains(needle);           // substring search
int pos = Bytes.indexOf(haystack, needle);       // find offset of needle

// --- I/O integration ---
ReadableSequentialData rsd = data.toReadableSequentialData();
InputStream is = data.toInputStream();           // zero-copy InputStream
data.writeTo(outputStream);                      // write to OutputStream
data.writeTo(byteBuffer);                        // write to ByteBuffer
data.writeTo(writableSequentialData);            // write to any WritableSequentialData

// --- Cryptographic operations (zero-copy) ---
data.writeTo(messageDigest);                     // feed into MessageDigest
data.writeTo(messageDigest, offset, length);     // feed slice
data.updateSignature(signature);                 // update java.security.Signature
data.updateSignature(signature, offset, length);
boolean valid = data.verifySignature(signature); // verify Signature

// --- Low-level access ---
byte b = data.getByte(offset);                   // single byte
int i = data.getInt(offset);                     // 4 bytes, big-endian
long l = data.getLong(offset);                   // 8 bytes, big-endian
int vi = data.getVarInt(offset, zigZag);         // protobuf varint
long vl = data.getVarLong(offset, zigZag);       // protobuf varlong

// --- Sorting ---
Bytes.SORT_BY_LENGTH          // Comparator: shorter first
Bytes.SORT_BY_SIGNED_VALUE    // Comparator: signed byte comparison
Bytes.SORT_BY_UNSIGNED_VALUE  // Comparator: unsigned byte comparison
```

`Bytes.EMPTY` is a singleton empty instance. The `compareTo()` method performs unsigned lexicographic comparison.

### `BufferedData` — In-Memory Buffers

`BufferedData` is a sealed class wrapping a `ByteBuffer` that implements both `ReadableSequentialData` and `WritableSequentialData`. It has two subclasses selected automatically:

- **`ByteArrayBufferedData`** — backed by a heap byte array. Use for short-lived, general-purpose buffers.
- **`DirectBufferedData`** — backed by off-heap (direct) memory. Use for long-lived buffers or when interacting with native I/O.

```java
import com.hedera.pbj.runtime.io.buffer.BufferedData;

// Heap allocation (most common)
BufferedData buf = BufferedData.allocate(1024);

// Off-heap allocation (for long-lived, performance-critical buffers)
BufferedData buf = BufferedData.allocateOffHeap(1024);

// Wrap existing data
BufferedData buf = BufferedData.wrap(byteArray);
BufferedData buf = BufferedData.wrap(byteBuffer);  // auto-selects subclass

// Use as both reader and writer
buf.writeInt(42);
buf.writeBytes(someBytes);
buf.flip();  // switch from writing to reading
int value = buf.readInt();

// Convert to other types
InputStream is = buf.toInputStream();  // zero-copy
```

`BufferedData.EMPTY_BUFFER` is a singleton empty read-only buffer.

### `WritableMessageDigest` — Hashing During Serialization

`WritableMessageDigest` wraps a `MessageDigest` as a `WritableSequentialData`, allowing you to compute a hash of serialized data as it's being written — without buffering the entire message first:

```java
import com.hedera.pbj.runtime.hashing.WritableMessageDigest;

MessageDigest md = MessageDigest.getInstance("SHA-384");
WritableMessageDigest wmd = new WritableMessageDigest(md);

// Write serialized data directly into the digest
MyMessage.PROTOBUF.write(message, wmd);

// Get the hash — no intermediate byte[] needed
byte[] hash = wmd.digest();  // also resets for reuse

// Or write the digest directly into a buffer
wmd.reset();
MyMessage.PROTOBUF.write(anotherMessage, wmd);
wmd.digestInto(outputBuffer, offset);
```

This is particularly useful in the consensus node where message hashes are computed during serialization for state proofs and signatures.

### Error Handling

Always catch `ParseException` when parsing untrusted input:

```java
try {
    MyMessage msg = MyMessage.PROTOBUF.parse(untrustedBytes);
} catch (ParseException e) {
    logger.warn("Failed to parse message: {}", e.getMessage());
    // Handle gracefully
}
```

## gRPC Services

PBJ's gRPC implementation runs on Helidon's HTTP/2 stack with no `io.grpc` dependency. For architecture details, see [architecture.md](architecture.md#grpc-architecture).

### Dependencies

```kotlin
// Server
implementation("com.hedera.pbj:pbj-grpc-helidon:<version>")

// Client
implementation("com.hedera.pbj:pbj-grpc-client-helidon:<version>")
```

### Implementing a Service

For each `service` in a proto file, PBJ generates a `*ServiceInterface`. Implement it with your business logic:

```java
public class GreeterServiceImpl implements GreeterServiceInterface {

    @Override
    public HelloReply sayHello(HelloRequest request) throws GrpcException {
        if (!request.hasName()) {
            throw new GrpcException(GrpcStatus.INVALID_ARGUMENT, "Name required");
        }
        return HelloReply.newBuilder()
                .message("Hello " + request.name())
                .build();
    }
}
```

### Unary RPC

For simple request/response RPCs, just implement the method — PBJ handles serialization:

```java
@Override
public HelloReply sayHello(HelloRequest request) {
    return HelloReply.newBuilder()
            .message("Hello " + request.nameOrElse("stranger"))
            .build();
}
```

### Server Streaming

The server sends multiple responses to a single request:

```java
@Override
public void sayHelloStream(
        HelloRequest request,
        Flow.Subscriber<? super HelloReply> replies) {
    for (int i = 0; i < 10; i++) {
        replies.onNext(HelloReply.newBuilder()
                .message("Hello " + request.name() + " " + i)
                .build());
    }
    replies.onComplete();
}
```

### Client Streaming

The client sends multiple requests; the server sends a single response:

```java
@Override
public Flow.Subscriber<HelloRequest> sayHelloCollect(
        Flow.Subscriber<HelloReply> response) {
    return new Flow.Subscriber<>() {
        private final List<String> names = new ArrayList<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HelloRequest item) {
            names.add(item.nameOrElse(""));
        }

        @Override
        public void onError(Throwable throwable) {
            response.onError(throwable);
        }

        @Override
        public void onComplete() {
            response.onNext(HelloReply.newBuilder()
                    .message("Hello " + String.join(", ", names))
                    .build());
            response.onComplete();
        }
    };
}
```

### Bidirectional Streaming

Both client and server stream messages concurrently:

```java
@Override
public Flow.Subscriber<HelloRequest> sayHelloBidi(
        Flow.Subscriber<HelloReply> replies) {
    return new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HelloRequest item) {
            replies.onNext(HelloReply.newBuilder()
                    .message("Hello " + item.name())
                    .build());
        }

        @Override
        public void onError(Throwable throwable) {
            replies.onError(throwable);
        }

        @Override
        public void onComplete() {
            replies.onComplete();
        }
    };
}
```

### Custom Request/Response Handling with Pipelines

For advanced control over serialization (e.g., custom size limits), override the `open()` method and use `Pipelines`:

```java
@Override
public Pipeline<? super Bytes> open(
        Method method,
        RequestOptions options,
        Pipeline<? super Bytes> replies) {
    return switch ((MyServiceMethod) method) {
        case myUnaryMethod ->
            Pipelines.<MyRequest, MyResponse>unary()
                    .mapRequest(bytes -> MyRequest.PROTOBUF.parse(
                            bytes.toReadableSequentialData(),
                            false, false,
                            Codec.DEFAULT_MAX_DEPTH,
                            customMaxSize))
                    .method(this::myUnaryMethod)
                    .mapResponse(MyResponse.PROTOBUF::toBytes)
                    .respondTo(replies)
                    .build();
    };
}
```

### Starting a Server

```java
WebServer.builder()
        .port(8080)
        .addRouting(PbjRouting.builder()
                .service(new GreeterServiceImpl())
                .service(new AnotherServiceImpl()))
        .build()
        .start();
```

### gRPC Compression

PBJ's gRPC layer negotiates compression via standard `grpc-encoding` / `grpc-accept-encoding` HTTP/2 headers. Two compressors are built-in and registered automatically:

| Algorithm | Header value | Notes |
|-----------|-------------|-------|
| Identity (none) | `identity` | Default, always available |
| Gzip | `gzip` | Always available |

#### Adding Zstandard (zstd) Compression

Zstd support is in the `pbj-grpc-common` module:

```kotlin
dependencies {
    implementation("com.hedera.pbj:pbj-grpc-common:<version>")
}
```

Register it at application startup:

```java
import com.hedera.pbj.grpc.common.compression.ZstdGrpcTransformer;

// Register with default compression level (3)
new ZstdGrpcTransformer().register("zstd");

// Or with a custom compression level (-5 to 22)
new ZstdGrpcTransformer(6).register("zstd");
```

#### Custom Compression Algorithms

Implement `GrpcCompression.Compressor` and `GrpcCompression.Decompressor` (or `GrpcCompression.GrpcTransformer` for both):

```java
import com.hedera.pbj.runtime.grpc.GrpcCompression;

// Register a custom compressor/decompressor
GrpcCompression.registerCompressor("snappy", mySnappyCompressor);
GrpcCompression.registerDecompressor("snappy", mySnappyDecompressor);

// Query available algorithms
Set<String> compressors = GrpcCompression.getCompressorNames();
Set<String> decompressors = GrpcCompression.getDecompressorNames();
```

Compression is negotiated automatically — the server selects the best algorithm from the client's `grpc-accept-encoding` header that it also supports.

## gRPC Client

### Creating a Client

```java
PbjGrpcClient grpcClient = PbjGrpcClient.builder()
        .host("localhost")
        .port(8080)
        .build();
```

### Making Unary Calls

```java
// Create the request
SubscribeStreamRequest request = SubscribeStreamRequest.newBuilder()
        .startBlockNumber(startBlock)
        .endBlockNumber(endBlock)
        .build();

// Create a call with codecs and a response handler
GrpcCall<SubscribeStreamRequest, SubscribeStreamResponse> call =
    grpcClient.createCall(
            "package.ServiceName/MethodName",
            SubscribeStreamRequest.PROTOBUF,    // request codec
            SubscribeStreamResponse.PROTOBUF,   // response codec
            responsePipeline,                    // handles responses
            Map.of());                           // metadata headers

// Send the request
call.sendRequest(request, true);  // true = end of stream (unary)
```

### Handling Streamed Responses

Implement a `Pipeline` (extends `Flow.Subscriber`) to handle responses:

```java
Pipeline<SubscribeStreamResponse> handler = new Pipeline<>() {
    @Override
    public void onNext(SubscribeStreamResponse response) {
        if (response.hasBlockItems()) {
            List<BlockItem> items = response.blockItems().blockItems();
            // process items
        } else if (response.hasStatus()) {
            // handle status update
        }
    }

    @Override
    public void onError(Throwable throwable) {
        logger.error("Stream error", throwable);
    }

    @Override
    public void onComplete() {
        logger.info("Stream completed");
    }
};
```

## Unparsed Types Pattern

A useful proto schema design pattern for performance-sensitive systems is defining "unparsed" message variants that use `bytes` fields instead of typed message fields. This allows passing data through without deserializing it.

### Defining Unparsed Messages

Define a parallel message where nested message fields are replaced with `bytes`:

```protobuf
// Regular (fully typed) message
message BlockItem {
    oneof item {
        BlockHeader block_header = 1;
        EventHeader event_header = 2;
        TransactionResult transaction_result = 5;
        BlockProof block_proof = 9;
    }
}

// Unparsed variant — same field numbers, but bytes instead of typed messages
message BlockItemUnparsed {
    oneof item {
        bytes block_header = 1;
        bytes event_header = 2;
        bytes transaction_result = 5;
        bytes block_proof = 9;
    }
}
```

Because both messages use the same field numbers and wire type 2 (length-delimited), they are wire-compatible — bytes serialized as `BlockItem` can be parsed as `BlockItemUnparsed` and vice versa.

### When to Use This Pattern

- **Pass-through services** — A proxy or relay that forwards data without inspecting every field
- **Deferred parsing** — Parse only the fields you need (e.g., read just the header), leave the rest as bytes
- **Forward compatibility** — An unparsed variant won't fail to parse when the inner message adds new fields in a newer schema version
- **Performance** — Avoid the cost of deserializing and re-serializing nested messages that you don't need to inspect

### Usage Example

```java
// Parse as unparsed — individual items remain as raw bytes
BlockUnparsed block = BlockUnparsed.PROTOBUF.parse(rawBytes);

// Inspect only what you need
for (BlockItemUnparsed item : block.blockItems()) {
    if (item.hasBlockHeader()) {
        // Parse just this one field when needed
        BlockHeader header = BlockHeader.PROTOBUF.parse(item.blockHeader());
        long blockNumber = header.number();
    }
    // Other items stay as bytes — no parsing cost
}

// Convert between parsed and unparsed when needed
Block fullyParsed = Block.PROTOBUF.parse(BlockUnparsed.PROTOBUF.toBytes(unparsedBlock));
```

This pattern is used extensively in the [Hiero Block Node](https://github.com/hiero-ledger/hiero-block-node) for streaming block data through the system efficiently.

## See Also

- [Protobuf & Schemas](protobuf-and-schemas.md) — full protobuf spec compliance, type mappings, nullability rules
- [Architecture](architecture.md) — module structure, dependency graph, design decisions
- [Codec Architecture](codecs.md) — shared codec interfaces and IO abstractions
