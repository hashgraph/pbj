# PBJ Runtime
The PBJ Runtime project is made up of multiple parts. It builds into a single jar `pbj-runtime-VERSION.jar`.

# Codec Interfaces
These are the interfaces that are implemented by the generated code to provide the serialization and deserialization. 
There is a base `Codec` interface and two sub-interfaces `ProtobufCodec` and `JsonCodec`. The `ProtobufCodec` is used
for protobuf binary encoding and decoding and the `JsonCodec` is used for JSON encoding and decoding. The `Codec` 
interface is used by the `PBJ` library to provide a unified API for encoding and decoding.

# JsonTools, ProtobufParserTools and ProtoWriterTools
These are utility classes that provide some common functionality for the generated code. They also provide a low level 
API for manually encoding and decoding protobuf messages.

# PBJ Data IO Library `com.hedera.pbj.runtime.io`

As well as protobuf code generation, PBJ also provides a data IO library that provides an alternative to the default
java.io and java.nio libraries. The PBJ Data IO library has some key design decisions, of which some are alternatives
to the standard Java IO libraries.
* The starting point is to provide an API that will unify Streams and Buffers. So a single parser could work on
  either an InputStream or a ByteBuffer. This is needed as we have both uses cases in the Hiero Consensus Node project and
  do not wish to maintain two code paths in that codebase.
* End Of File(EOF) in streams is handled with a `EOFException` rather than by returning -1 from read bytes.
* We provide an immutable alternative to byte[]. With the generated Classes being immutable, it left any
  byte[] fields as dangerous mutable exceptions. This is provided by the `Bytes` class. It is designed to avoid
  leakage of the underlying byte[] array while trying to balance that with performance.
* We Provide some protobuf methods like `readVarint` and `writeVarint` that are not provided by the standard Java IO
  libraries. These should be minimized but can be expanded to provide fast path specializations for
  reading/writing from different data types like arrays, buffers and streams. ProtoC depends heavily on specialized fast
  paths for performance.
* We use Sun.misc.Unsafe for fast byte array copying and setting. This is only used if the Unsafe class is available. It
  is horrible but unavoidable when performance is critical. The intent is to replace this with better alternatives
  in future JDK versions.
* Special methods for avoiding data copying are added as needed for performance.
  This includes `Bytes.writeTo(MessageDigest digest)` which allows the contents of the Bytes to be digested/hashed
  without copying the contents.

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

# ProtoTestTools and `com/hedera/pbj/runtime/test` package
These are utility classes that provide some common functionality for the generated test code. They are not for use in
production code or for direct use by users. They are only for use in the generated test code.