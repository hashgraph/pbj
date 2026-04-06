# PBJ Runtime

The core runtime library that all PBJ-generated code depends on. Builds into `pbj-runtime-VERSION.jar`.

For codec architecture, see [codecs.md](../../docs/codecs.md). For IO abstractions and streaming data, see [codecs.md - Streaming Data Abstractions](../../docs/codecs.md#streaming-data-abstractions). For usage examples, see [usage-guide.md](../../docs/usage-guide.md).

## Contents

- **Codec interfaces** — `Codec<T>` for protobuf binary, `JsonCodec<T>` for JSON serialization
- **IO abstractions** — `ReadableSequentialData`, `WritableSequentialData`, `BufferedData`, `Bytes` — a unified API for streams, buffers, and byte arrays
- **Serialization helpers** — `ProtoParserTools`, `ProtoWriterTools`, `ProtoArrayWriterTools`, `JsonTools`
- **Collection types** — `PbjMap<K,V>` (deterministic key ordering), `UnmodifiableArrayList<E>`, `OneOf<E>`
- **gRPC interfaces** — `ServiceInterface`, `Pipeline`, `GrpcClient`, `GrpcCall`
- **Test utilities** — `ProtoTestTools` and `com.hedera.pbj.runtime.test` package (for generated test code only, not production use)

## Testing

```bash
cd pbj-core
./gradlew pbj-runtime:test
./gradlew pbj-runtime:jacocoTestReport   # coverage report in build/reports/jacoco/test/html/
```
