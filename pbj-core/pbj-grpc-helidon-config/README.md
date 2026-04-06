# PBJ Helidon gRPC Config

Configuration definition for the `pbj-grpc-helidon` module.

Helidon modules require a "config blueprint". An annotation processor takes that blueprint and generates metadata in META-INF and some code. The module then needs to compile against the generated code. Since Gradle cannot do this in a single build, the configuration is split into this separate module.

It produces `pbj-grpc-helidon-config-VERSION.jar`.
