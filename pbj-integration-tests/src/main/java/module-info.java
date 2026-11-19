// SPDX-License-Identifier: Apache-2.0
module com.hedera.pbj.integration.tests {
    requires com.hedera.pbj.grpc.client.helidon;
    requires com.hedera.pbj.runtime;
    requires com.google.common;
    requires com.google.protobuf;
    requires io.grpc.protobuf;
    requires io.grpc.stub;
    requires io.grpc;
    requires io.helidon.common.tls;
    requires io.helidon.webclient.api;
    requires org.antlr.antlr4.runtime;
    requires static com.github.spotbugs.annotations;

    exports pbj.integ.test.enumeration.defined.pbj.integration.tests;
    // used by the custom source set's writers, from a module of their own
    exports pbj.integ.test.writer.shared.pbj.integration.tests;
    exports pbj.integ.test.writer.shared.pbj.integration.tests.codec;
}
