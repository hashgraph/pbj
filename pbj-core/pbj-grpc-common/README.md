# PBJ GRPC Helidon

This project produces a module with a common code used by both PBJ gRPC client and server
that may not be suitable for the generic and lightweight `pbj-runtime` module due to extra dependencies.
Examples of such code include:
- common classes that depend on Helidon libraries,
- code with external, potentially heavy dependencies, such as compressor/decompressor implementations

It produces `pbj-grpc-common-VERSION.jar`
