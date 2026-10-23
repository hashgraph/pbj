// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.grpc.ServiceInterface.MethodType;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.GreeterInterface.GreeterMethod;

class GrpcMethodTypeTest {
    @Test
    void generatedMethodsExposeTheirDeclaredCardinality() {
        assertEquals(MethodType.UNARY, GreeterMethod.sayHello.type());
        assertEquals(MethodType.SERVER_STREAMING, GreeterMethod.sayHelloStreamReply.type());
        assertEquals(MethodType.CLIENT_STREAMING, GreeterMethod.sayHelloStreamRequest.type());
        assertEquals(MethodType.BIDI_STREAMING, GreeterMethod.sayHelloStreamBidi.type());
    }
}
