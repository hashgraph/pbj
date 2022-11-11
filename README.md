# pbj
A performance optimized Google Protocol Buffers code generator, parser, and Gradle module.

## Build & Tests Instructions

There is 2 gradle projects, the root project that builds two libraries:

  * **Compiler Gradle Plugin** `hedera.pbj-compiler.gradle.plugin-VERSION.jar`
  * **Runtime LIbrary** `pnj-runtime-VERSION.jar`

Running `gradle publish` will build the 2 libraries into a temp maven repo in `/build/testRepo` from there they 
can be picked up by the `intergration-tests` project. Running `gradle build` in `intergration-tests` directory will 
take those libraries and generate some code and tests output. The integration tests should be run and pass before commiting.