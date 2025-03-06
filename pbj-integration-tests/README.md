# PBJ Integration Tests
The integration tests is a separate gradle project as it uses the `pbj-compiler` plugin to generate code from 
test `.proto` files. The generated code is then compiled and tested as part of the project build. It uses the protobuf 
schemas from the Hedera network as extensive examples to help insure correctness.
## Everything protobuf message
For testing there is an `Everything` message that has every possible field type in it. This is used to test all the
protobuf field types and insure they are correctly encoded and decoded. As support is added to PBJ for more Protobuf 
features they should be added to this message.
## JMH Benchmarks
There are also JMH benchmarks that test the performance of the generated code. They also compare the performance of PBJ 
generated code to the standard Google Protobuf generated code. The benchmarks are run by running the `jmh` gradle task.
## Fuzz Testing
There is a fuzz test that generates random protobuf messages and then serializes and deserializes them. It then compares
the original message with the deserialized message to insure they are the same. This is run by running the `fuzzTest`