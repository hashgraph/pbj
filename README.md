# pbj
An alternative Google Protocol Buffers code generator, parser, and Gradle module. The project has two design goals:
 * **Nice Java Record Objects** - parse the proto files into nice clean Java record objects. With as clean as API as possible.
 * **Performance Optimized** - be as fast or **faster** than the standard Google ProtoC generated code
 * **Minimal Garbage Generated** - produce the minimum amount of garbage Java objects as possible

These design goals often compete with each other so this project tries to strike the right balance for use in the 
Hedera Node project. The hope is that balance might well be useful in many other projects. There is still plenty of work 
to achieve these goals and will probably always we improvements that can be made but this is what the project is 
striving for.

There is 3 gradle projects, the root project that builds two libraries:

  * **PBJ Core** `pbj-core` which has 2 sub projects
    * **Compiler Gradle Plugin** `pbj-compiler` which produces `hedera.pbj-compiler.gradle.plugin-VERSION.jar`
    * **Runtime Library** `pbj-runtime` which produces `pnj-runtime-VERSION.jar`
  * **Integration Tests** `pbj-integration-tests` which uses PBJ with test .proto files to generate code and run generated unit tests 

## Build Libraries
Running `gradle build` in `pbj-core` directory will build the 2 libraries into the local maven repository for compiler and runtime.

## Run Integration Tests
Running `gradle build` in `pbj-integration-tests` will check out the latest proto source files from the
[hedera-protobufs](https://github.com/hashgraph/hedera-protobufs) repository and generate code using `pbj-compiler` then 
run all the 100k+ generated unit tests which takes a few minutes. 

**_These tests should be run before committing any new code for PBJ._**