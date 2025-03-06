# PBJ Helidon GRPC Config `pbj-grpc-helidon-config`

This project contains just the configuration definition for the
`pbj-grpc-helidon` module.

Helidon modules require a "config blueprint". An annotation processor takes that
"blueprint" and generates some metadata in META-INF and some code. The module
then needs to compile against the generated code. Since Gradle is not capable of
doing this in a single build, we have to split the configuration into a separate
module, which produces `pbj-grpc-helidon-config-VERSION.jar`.
