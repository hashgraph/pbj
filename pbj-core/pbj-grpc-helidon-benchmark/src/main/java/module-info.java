module com.hedera.pbj.grpc.helidon.benchmark {
    requires static com.github.spotbugs.annotations;

    requires com.google.protobuf;
    requires com.google.protobuf.util;
    requires io.grpc.protobuf;
    requires io.grpc.netty;
    requires io.grpc.stub;

    requires com.hedera.pbj.grpc.helidon;
    requires com.hedera.pbj.runtime;
    requires io.helidon.webserver;
    requires io.helidon.webserver.http2;
    requires io.helidon.metrics.api;
    requires java.net.http;

    exports com.hedera.pbj.grpc.helidon.benchmark.driver;
    exports javax.annotation;
}
