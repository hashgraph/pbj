# PBJ varint reading algorithm

Using the `VarIntByteArrayReadBench` in this project, a number of different algorithms for reading/parsing/decoding
varint values were tested. The algorithm used in PBJ builds on top of the standard LEB128 algorithm
with the following enhancements:
* The internal `for()` loop for reading individual bytes is unrolled into sequential reads for every byte
* Explicit `limit` checks are replaced with a "negative", mutable `lim` value and an `s` flag that essentially
  is equal to the sign bit of the `lim`, making it equal to 1 while we're reading bytes within the `limit`,
  and `0` once the algorithms goes past the `limit`. The value of `s` is used for incrementing the position
  index, hence allowing the algorithm to skip the limit checks and simply don't advance the reading position
  past the limit.

Note that the "negative limit" trick is only applicable to array- and/or buffer-based parsing methods where
the proper limit can be calculated upfront. When reading varints from a stream, the actual limit cannot be
determined and the algorithm actually has to perform checks on the EOF condition. And therefore, its performance
goes back to the regular LEB128 algorithm, still slightly enhanced thanks to the unrolled loop.

Here's an implementation of the algorithm with additional comments to explain non-trivial constructs:

```java
    private long readVarInt(byte[] bytes, int pos, boolean zigZag) {
        byte b = bytes[pos];
        if ((b & 0x80) == 0) { return zigZag ? (b >>> 1) ^ -(b & 1) : b; }

        // The "negative lim" trick. In general, varint could occupy up to 10 bytes.
        // But we've read 1 byte above already. Therefore, we can read at most 9 bytes from here,
        // still respecting the hard-limit which is the array length in this sample implementation:
        byte lim = (byte) - Math.min(bytes.length - pos - 1, 9);
        
        // varint32 negative numbers are expanded to longs by the varint encoding specification.
        // Therefore, we have no choice but support long (aka varint64) parsing, even for non-long varints.
        // So this must be a long variable, not an int.
        long v = b & 0x7f;

        // The derivative of the "negative lim" trick above. This is effectively the sign bit of the `lim`.
        // While the `lim` is negative, the sign bit is equal to 1. Once we're past the `lim`, the sign
        // flips to 0 and remains zero (because we can only read up to 9 bytes, so we're guaranteed
        // not to overflow the byte.)
        byte s = (byte) (((lim & 0x80) >>> 7) & 0xFF);

        // Using the "negative lim" trick, we increment the `pos` by `s`. So we actually increment it
        // as long as we're within the limit, and we stop incrementing it once we're past the limit
        // and s is zero.
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= b << 7; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        // We're incrementing our "negative lim" value and recompute the new `s` value here.
        // The rest of this method just reads all the bytes one by one and basically performs the LEB128
        // decoding.
        // NOTE: if we ever run past the limit, the lim becomes non-negative, and s is set to 0.
        // At this point, we keep reading the exact same last byte over and over. The byte itself is negative,
        // and therefore we never `return` a value. After "trying to read" all the remaining 9 bytes,
        // we throw an exception at the very end.
        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 7;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= b << 14; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 14;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= b << 21; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= (b & 0x7f) << 21;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 28; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }
        
        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 28;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 35; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 35;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 42; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 42;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 49; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 49;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 56; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }

        s = (byte) (((++lim & 0x80) >>> 7) & 0xFF);
        v |= ((long) b & 0x7f) << 56;
        b = bytes[pos += s];
        if ((b & 0x80) == 0) { v |= (long) b << 63; return zigZag ? (v >>> 1) ^ -(v & 1) : v; }
        
        throw new DataEncodingException("Malformed var int");
    }
```

