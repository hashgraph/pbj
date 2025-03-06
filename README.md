# PBJ Protobuf Java Library
An alternative Google Protocol Buffers code generator, parser, and Gradle module. The project has these design goals:
 * **Modern Nice Java Objects** - parse the proto files into nice clean Java objects. With as clean as API as possible. 
     Using the newer Java Record style getters and setters. Support latest LTS Java version features.
 * **Explicit Error Handling** - when a message field is not on the wire, return null from getter not default object. 
     This is like Java's choice of checked exceptions over unchecked exceptions. It forces the developer to handle 
     the error case rather than silently ignoring it. To make this easier, the generated model objects have two versions 
     of each getter method `foo()` and `fooOrElse(defaultValue)`.
 * **Performance Optimized** - be as fast or **faster** than the standard Google ProtoC generated code
 * **Minimal Garbage Generated** - produce the minimum amount of garbage Java objects as possible
 * **Identical Binary Wire Encoding** - produce the same binary encoding as the standard Google ProtoC generated code
 * **Deterministic Binary Encoding** - produce the same binary encoding for the same input every time. This means fields 
     are always serialized in ascending field order and all maps are sorted by key. This is needed so hashes of 
     serialized objects and signatures of objects are dependable.
 * **Stable hashCode() and equals()** - produce stable hashCode() and equals() methods for generated model objects that 
     are suitable for long term storage of objects in hash maps. This includes handling of new fields being added to 
     objects so, just like wire format, fields with default value are not included in hashCode() and equals() methods. 
     This allows new fields with default values to be added to key objects without affecting hashcode or equals. 
 * **Keep "[JEP 401: Value Classes and Objects](https://openjdk.org/jeps/401)" in mind** - If possible design generated 
     model objects to work as Value Classes when available.
 * **Minimal 3rd party dependencies** - only use 3rd party libraries when they are really needed and are well maintained
 * **Low level protobuf read/write API** - provide low level API for manually reading/writing protobuf in buffers, byte 
     arrays and streams. 
 * **IO.GRPC Alternative for Helidon SE** - provide an alternative to the IO.GRPC library that has low level access to bytes, no 3rd 
     party dependencies and prioritizes fail fast for security.
 * **Generate clean readable code** - the generated code should be as clean and readable as possible, as if it was 
     carefully written by hand.

These design goals often compete with each other so this project tries to strike the right balance for use in the 
[Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node) project. The hope is that balance might well be useful in many other projects. There is still plenty of work 
to achieve these goals, and will probably always be improvements that can be made, but this is what the project is 
striving for.

### There are 2 top level gradle projects, click any project to see more details:

  * ### **PBJ Core** `pbj-core` which has 4 subprojects
    * ### [**PBJ Protobuf Schema Compiler Gradle Plugin** `pbj-compiler`](pbj-core/pbj-compiler/README.md)
    * ### [**PBJ Runtime Library** `pbj-runtime`](pbj-core/pbj-runtime/README.md)
    * ### [**Grpc Helidon** `pbj-grpc-helidon`](pbj-core/pbj-grpc-helidon/README.md)
    * ### [**Grpc Helidon Config** `pbj-grpc-helidon-config](pbj-core/pbj-grpc-helidon-config/README.md)
  * ### [**Integration Tests** `pbj-integration-tests`](pbj-integration-tests/README.md) 

## Build Libraries
Running `gradle build` in `pbj-core` directory will build the libraries for compiler, runtime, and grpc.

## Run Integration Tests
Running `gradle build` in `pbj-integration-tests` will check out the latest proto source files from the
[hedera-protobufs](https://github.com/hashgraph/hedera-protobufs) repository as sample schemas and generate code using `pbj-compiler` then 
run all the 100k+ generated unit tests which takes a few minutes. The hedera protobufs provide a good coverage of
protobuf features and insure all the generated code is tested for hiero node use case.

**_These tests should be run before committing any new code for PBJ._**

## Long Term Goals
PBJ is a long term project with many goals. Here are some of the long term goals:
  * Support all Protobuf Features
  * Support new versions of Protobuf as possible
  * Generate GRPC Services
  * Built in GRPC-Web support
  * Auto mapping GRPC APIs to JSON REST APIs
  * JSON REST performance as good as GRPC