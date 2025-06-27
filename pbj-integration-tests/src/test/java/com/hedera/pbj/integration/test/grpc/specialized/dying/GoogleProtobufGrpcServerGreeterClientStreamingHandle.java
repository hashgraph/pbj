// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc.specialized.dying;

import com.hedera.pbj.integration.test.grpc.GoogleProtobufGrpcServerGreeterHandle;
import com.hedera.pbj.runtime.grpc.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/** A specialized GRPC server handle implementation for SeparateJVMRunner. */
public class GoogleProtobufGrpcServerGreeterClientStreamingHandle extends GoogleProtobufGrpcServerGreeterHandle {
    public GoogleProtobufGrpcServerGreeterClientStreamingHandle(int port) {
        super(port);
    }

    public static void main(String[] args) throws Exception {
        // This is an internal class. If someone calls it w/o proper args, let it error out as is.
        final int port = Integer.parseInt(args[0]);
        final boolean dieInOnComplete = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;

        final GoogleProtobufGrpcServerGreeterClientStreamingHandle handle =
                new GoogleProtobufGrpcServerGreeterClientStreamingHandle(port);
        handle.setSayHelloStreamRequest(replies -> {
            final List<HelloRequest> requests = new ArrayList<>();
            return new Pipeline<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE); // turn off flow control
                }

                @Override
                public void onNext(HelloRequest item) {
                    requests.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    replies.onError(throwable);
                }

                @Override
                public void onComplete() {
                    if (dieInOnComplete) {
                        // Die here and now, no clean up:
                        Runtime.getRuntime().halt(-666);
                    }
                    final HelloReply reply = HelloReply.newBuilder()
                            .message("Hello "
                                    + requests.stream().map(HelloRequest::name).collect(Collectors.joining(", "))
                                    + "!")
                            .build();
                    replies.onNext(reply);
                    replies.onComplete();
                }
            };
        });
        handle.start();

        // Tell the SeparateJVMRunner we're up and running:
        System.out.print('k');

        // Keep running main() until the process is killed externally.
        while (true) {
            Thread.sleep(1000);
        }
    }
}
