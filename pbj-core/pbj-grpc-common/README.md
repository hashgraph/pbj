# PBJ gRPC Common

Shared code used by both the PBJ gRPC client and server that is not suitable for the lightweight `pbj-runtime` module due to extra dependencies. Examples include:
- Common classes that depend on Helidon libraries
- Compression implementations (gzip, zstd) with external dependencies

It produces `pbj-grpc-common-VERSION.jar`.

For the overall gRPC architecture, see [architecture.md](../../docs/architecture.md#grpc-architecture).
