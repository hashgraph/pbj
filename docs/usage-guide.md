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

Mark a message as comparable to generate a `compareTo()` method:

```protobuf
message Timestamp {
    option (pbj.comparable) = true;
    int64 seconds = 1;
    int32 nanos = 2;
}
```

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

PBJ uses `Bytes` instead of `byte[]` for immutable byte sequences:

```java
import com.hedera.pbj.runtime.io.buffer.Bytes;

// Wrap a byte array (does not copy)
Bytes data = Bytes.wrap(byteArray);

// From hex string
Bytes hash = Bytes.fromHex("3de47629fe289fc7...");

// Convert back to byte array (copies)
byte[] array = data.toByteArray();

// Efficient digest without copying
data.writeTo(messageDigest);

// Get as ReadableSequentialData for parsing
ReadableSequentialData rsd = data.toReadableSequentialData();
```

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

## See Also

- [Protobuf & Schemas](protobuf-and-schemas.md) — full protobuf spec compliance, type mappings, nullability rules
- [Architecture](architecture.md) — module structure, dependency graph, design decisions
- [Codec Architecture](codecs.md) — shared codec interfaces and IO abstractions
