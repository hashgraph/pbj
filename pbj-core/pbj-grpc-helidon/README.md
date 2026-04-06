# PBJ gRPC Server (Helidon)

A gRPC server module for Helidon that enables native PBJ support — going directly to PBJ model objects without `protoc` intermediaries. Designed around a fail-fast security philosophy to minimize the server resources an attacker can consume on a single bad request.

This library, along with PBJ core, provides a complete replacement for Google Protobuf and `io.grpc` libraries, removing their large transitive dependency trees.

It produces `pbj-grpc-helidon-VERSION.jar`.

For detailed design documentation, see [docs/design.md](docs/design.md). For the overall gRPC architecture, see [architecture.md](../../docs/architecture.md#grpc-architecture). For usage examples, see [usage-guide.md](../../docs/usage-guide.md#grpc-services).
