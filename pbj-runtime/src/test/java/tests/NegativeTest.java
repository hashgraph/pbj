package tests;

public class NegativeTest {
    // Take a valid protobuf, and send 1 byte, then 2 bytes, and so forth until all bytes - 1. All calls
    // should fail, though they may fail in different ways.

    // There should also be a test that specifically send a varint of 10+ bytes in a row with the
    // continuation bit set.

    // There should be a test for forwards compatibility where valid protobuf is sent to a parser that
    // doesn't know about all the different types of fields.

    // Test where a duplicate field is included in the protobuf bytes (for each different field type)
    // The last data in the stream should win. See https://developers.google.com/protocol-buffers/docs/encoding#optional
    // "Normally, an encoded message would never have more than one instance of a non-repeated field. However,
    // parsers are expected to handle the case in which they do. For numeric types and strings, if the same field
    // appears multiple times, the parser accepts the last value it sees. For embedded message fields, the parser
    // merges multiple instances of the same field, as if with the Message::MergeFrom method – that is, all singular
    // scalar fields in the latter instance replace those in the former, singular embedded messages are merged, and
    // repeated fields are concatenated. The effect of these rules is that parsing the concatenation of two encoded
    // messages produces exactly the same result as if you had parsed the two messages separately and merged the
    // resulting objects." - The Spec
    //
    // "Note that although there's usually no reason to encode more than one key-value pair for a packed repeated field,
    // parsers must be prepared to accept multiple key-value pairs. In this case, the payloads should be concatenated.
    // Each pair must contain a whole number of elements." - The Spec
}
