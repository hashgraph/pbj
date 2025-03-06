# PBJ Integration Tests

The integration tests is a separate gradle project as it uses the `pbj-compiler` plugin to generate code from
test `.proto` files. The generated code is then compiled and tested as part of the project build. It uses the protobuf
schemas from the Hiero Consensus network as extensive examples to help insure correctness.

## Everything protobuf message

For testing there is an `Everything` message that has every possible field type in it. This is used to test all the
protobuf field types and insure they are correctly encoded and decoded. As support is added to PBJ for more Protobuf
features they should be added to this message.

## JMH Benchmarks

There are also JMH benchmarks that test the performance of the generated code. They also compare the performance of PBJ
generated code to the standard Google Protobuf generated code. The benchmarks are run by executing the `jmh` gradle task.

## Fuzz Testing

There is a fuzz test that tries to corrupt the serialized data and then parse it back, and ensures that parsing the
corrupted data produces expected errors instead of crashing or silently swallowing them. This is done to make sure there
is no way an attacker submitting protobuf through GRPC over the internet can crash the server or cause odd behavior.
This is run by executing the `fuzzTest`
