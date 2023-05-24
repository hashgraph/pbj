# pbj Protobuf Library
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

### Generated Model Objects
Each protobuf message is generated into a Java Record model object. With static instances for two codecs and a default 
value. There is an inner `Builder` class generated for fluid style building of the model object. There is also a 
`copyBuilder()` method that lets you easily create a derivative of this immutable record with some changes.
#### Protobuf Message Example
```
message Fraction {
    int64 numerator = 1;
    int64 denominator = 2;
}
```
#### Example generated model Java record
```
public record Fraction(
       long numerator,
       long denominator
){
    public static final Codec<Fraction> PROTOBUF = new com.hedera.hapi.node.base.codec.FractionProtoCodec();
    public static final JsonCodec<Fraction> JSON = new com.hedera.hapi.node.base.codec.FractionJsonCodec();
    public static final Fraction DEFAULT = newBuilder().build();

    public Builder copyBuilder() {...}
    public static Builder newBuilder() {...}
    
    public static final class Builder {...}
}
```
### Generated Codecs
Codecs are generated for each model class, one for Protobuf binary encoding and one for Protobuf JSON encoding. There 
are static instances accessible via `MyModelOject.PROTOBUF` and `MyModelOject.JSON`.
### Generated Unit Tests
For each generated model object there is a unit test generate that tests the protobuf and JSON codecs against the
*protoc* generated code to make sure they are 100% byte for byte binary compatible.

# pbj Data IO Library
`com.hedera.pbj.runtime.io`

As well as protobuf code generation, PBJ also provides a data IO library that provides an alternative to the default 
java.io & java.nio libraries. The PBJ Data IO library has some key design decisions, of which some are alternatives 
to the standard Java IO libraries:
 * The starting point was to provide an API that could unify Streams and Buffers. So a single parser could work on 
   either a InputStream or a ByteBuffer. This was needed as we have both uses cases in the Hedera Node project and 
   did not want to maintain two code paths.
 * End Of File(EOF) in streams is handled with a `EOFException` rather than by returning -1 from read bytes.
 * Provide an immutable alternative to byte[]. With the generated Record objects being immutable, it left any 
   byte[] fields as dangerous mutable exceptions. This is provided by the `Bytes` class. It tries hard to avoid
   leakage of the underlying byte[] array while trying to balance that with performance. 
### The key data IO Interfaces
 * **SequentialData** Represents sequential data which may either be buffered or streamed. Provides all the 
   position, limit and capacity management API but no reading or writing. It only allows reading or writing 
   in a forwards direction.
 * **RandomAccessData** Provides random access reading and writing at offsets.
 * **BufferedSequentialData** Extends SequentialData and RandomAccessData to make position movable and allowing 
   random access reading and writing.
 * **ReadableSequentialData** Extends SequentialData to provide reading API.  
 * **WritableSequentialData** Extends SequentialData to provide writing API.
### The key data IO Classes
 * **ReadableStreamingData** A ReadableSequentialData that reads from a InputStream.
 * **WritableStreamingData** A WritableSequentialData that writes to a OutputStream.
 * **BufferedData** A buffer that wraps a byte[] or ByteBuffer and implements BufferedSequentialData, 
   ReadableSequentialData, WritableSequentialData and RandomAccessData.
 * **RandomAccessSequenceAdapter** A RandomAccessData that wraps a RandomAccessData and provides a ReadableSequentialData.

## Build Libraries
Running `gradle build` in `pbj-core` directory will build the 2 libraries into the local maven repository for compiler and runtime.

## Run Integration Tests
Running `gradle build` in `pbj-integration-tests` will check out the latest proto source files from the
[hedera-protobufs](https://github.com/hashgraph/hedera-protobufs) repository and generate code using `pbj-compiler` then 
run all the 100k+ generated unit tests which takes a few minutes. The hedera protobufs provide a good coverage of
protobuf features and insure all the generated code is tested for hedera node use case.

**_These tests should be run before committing any new code for PBJ._**