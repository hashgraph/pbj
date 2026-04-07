# Getting Started with PBJ

This guide walks you through adding PBJ to a Gradle project, writing a `.proto` file, and using the generated Java classes.

For comprehensive usage patterns, see [usage-guide.md](usage-guide.md). For the full protobuf feature reference, see [protobuf-and-schemas.md](protobuf-and-schemas.md).

## 1. Add PBJ to Your Project

### Apply the Gradle Plugin

In your `build.gradle.kts`:

```kotlin
plugins {
    id("com.hedera.pbj.pbj-compiler") version "<version>"
}

dependencies {
    implementation("com.hedera.pbj:pbj-runtime:<version>")
}
```

### Configure the Plugin (Optional)

```kotlin
pbj {
    javaPackageSuffix = ".pbj"      // suffix appended to derived package names
    generateTestClasses = false      // disable generated unit tests (default: true)
}
```

The `javaPackageSuffix` is useful when you need PBJ-generated and `protoc`-generated classes to coexist in the same project under different packages.

## 2. Write a Proto File

Create a `.proto` file in `src/main/proto/`:

```protobuf
// src/main/proto/greeter.proto
syntax = "proto3";

package com.example.greeter;

option java_package = "com.example.greeter.protoc";
// <<<pbj.java_package = "com.example.greeter">>>

message HelloRequest {
  string name = 1;
  int32 count = 2;
}

message HelloReply {
  string message = 1;
}

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

The `// <<<pbj.java_package = "...">>>` comment tells PBJ to generate classes in a different package than `protoc` would use. This is optional but recommended if you need both generators.

## 3. Build

```bash
./gradlew assemble
```

Generated code appears in `build/generated/source/pbj-proto/main/java/`. For the example above:

```
com/example/greeter/
├── HelloRequest.java                    (model)
├── HelloReply.java                      (model)
├── GreeterServiceInterface.java         (gRPC service interface)
├── schema/
│   ├── HelloRequestSchema.java
│   └── HelloReplySchema.java
└── codec/
    ├── HelloRequestProtoCodec.java
    ├── HelloRequestJsonCodec.java
    ├── HelloReplyProtoCodec.java
    └── HelloReplyJsonCodec.java
```

## 4. Use Generated Classes

### Create Objects

PBJ model objects are immutable. Use the builder pattern to construct them:

```java
HelloRequest request = HelloRequest.newBuilder()
        .name("World")
        .count(5)
        .build();
```

### Read Fields

```java
String name = request.name();               // returns null if not set
String safeName = request.nameOrElse("");    // returns "" if not set
int count = request.count();                 // returns 0 if not set (primitive)
```

PBJ returns `null` for absent reference-type fields (unlike `protoc`, which returns defaults). This forces explicit handling of missing data.

### Modify Immutable Objects

Use `copyBuilder()` to create a modified copy:

```java
HelloRequest modified = request.copyBuilder()
        .count(10)
        .build();
// original request is unchanged
```

### Serialize and Deserialize

```java
// Protobuf binary
Bytes bytes = HelloRequest.PROTOBUF.toBytes(request);
HelloRequest parsed = HelloRequest.PROTOBUF.parse(bytes);

// JSON
String json = HelloRequest.JSON.toJSON(request);
HelloRequest fromJson = HelloRequest.JSON.parse(jsonReadableData);
```

### Handle Errors

```java
try {
    HelloRequest msg = HelloRequest.PROTOBUF.parse(untrustedInput);
} catch (ParseException e) {
    // Handle malformed or invalid protobuf data
}
```

## 5. Add gRPC Support (Optional)

To use PBJ's gRPC implementation on Helidon, add the server dependency:

```kotlin
dependencies {
    implementation("com.hedera.pbj:pbj-grpc-helidon:<version>")
}
```

Implement the generated service interface:

```java
public class GreeterServiceImpl implements GreeterServiceInterface {
    @Override
    public HelloReply sayHello(HelloRequest request) {
        return HelloReply.newBuilder()
                .message("Hello " + request.nameOrElse("stranger"))
                .build();
    }
}
```

Start a Helidon server:

```java
WebServer.builder()
        .port(8080)
        .addRouting(PbjRouting.builder().service(new GreeterServiceImpl()))
        .build()
        .start();
```

## Next Steps

- [Usage Guide](usage-guide.md) — comprehensive reference for all PBJ features
- [Protobuf & Schemas](protobuf-and-schemas.md) — type mappings, nullability, oneofs, maps, and PBJ-specific extensions
- [Architecture](architecture.md) — module structure and design decisions
