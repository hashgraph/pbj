# PBJ GRPC Helidon

This project produces a module for Helidon that enables native support for PBJ gRPC services. The aim is to have full 
GRPC support going directly to PBJ model objects and not via protoc objects. This is also designed around a security
philosophy of fail fast. With the aim of minimizing the server resources an attacker might consume on a single bad request.

This library along with PBJ provide a complete replacement for Google Protobuf and IO.GRPC libraries. It is designed to 
remove the large transitive dependencies of the Google Protobuf and IO.GRPC libraries and provide a more secure and
performant alternative.

It produces `pbj-grpc-helidon-VERSION.jar`