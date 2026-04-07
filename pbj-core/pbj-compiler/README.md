# PBJ Protobuf Schema Compiler Gradle Plugin

A Gradle plugin that compiles `.proto` schema files into Java model objects, codecs, schemas, and unit tests. Proto files in `src/main/proto` are compiled to `build/generated/source/pbj-proto` automatically during the Gradle build.

For usage examples, see [getting-started.md](../../docs/getting-started.md). For compiler internals, see [code-generation.md](../../docs/code-generation.md). For the generated API, see [usage-guide.md](../../docs/usage-guide.md).

## Usage

```kotlin
plugins {
    id("com.hedera.pbj.pbj-compiler")
}
```

## Configuration

```kotlin
pbj {
    javaPackageSuffix = ".pbj"      // suffix appended to derived package names
    generateTestClasses = false      // disable generated unit tests (default: true)
}

sourceSets {
    main {
        pbj {
            srcDir(...)
        }
    }
}
```

Setting `generateTestClasses = false` avoids requiring a Google Protobuf dependency (the generated tests validate binary compatibility with `protoc`).

## What Gets Generated

For each proto message, the compiler produces up to five files (model class, schema, protobuf codec, JSON codec, and unit test). Enums and services produce a single file each. See [code-generation.md](../../docs/code-generation.md#code-generators) for details.

## Key Design Decisions

- **Classes, not Records** — PBJ generates immutable classes rather than Java Records due to the need for lazy-computed cached fields (`hashCode()`, `protobufSize()`). This will be revisited when [JEP 526: Lazy Constants](https://openjdk.org/jeps/526) is finalized. See [protobuf-and-schemas.md](../../docs/protobuf-and-schemas.md#why-not-java-records).
- **PBJ package override** — A special comment `// <<<pbj.java_package = "...">>>` overrides the Java package, allowing PBJ and `protoc` to coexist. See [protobuf-and-schemas.md](../../docs/protobuf-and-schemas.md#package-resolution).
- **Clean generated code** — Generated code is formatted with clean indentation and comments, as if written by hand.
