// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.grpc.specialized;

import com.hedera.pbj.integration.test.grpc.GrpcServerGreeterHandle;
import com.hedera.pbj.runtime.grpc.Pipeline;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import pbj.integration.tests.pbj.integration.tests.HelloReply;
import pbj.integration.tests.pbj.integration.tests.HelloRequest;

/**
 * Utility to run a GRPC server in a separate process.
 * While it inherits from the GrpcServerGreeterHandle to allow calling start/stop,
 * it does NOT support setting service implementations directly. The specializedClass
 * must implement the specialized behavior for the service, and it will run in a separate JVM
 * using the exact same class path as the current JVM.
 */
public class SeparateJVMRunner extends GrpcServerGreeterHandle {
    private final Class<?> specializedClass;
    private final int port;
    private final List<String> args;

    private Process process;

    public SeparateJVMRunner(Class<?> specializedClass, int port, List<String> args) {
        this.specializedClass = specializedClass;
        this.port = port;
        this.args = args;
    }

    @Override
    public void start() {
        final String javaHome = System.getProperty("java.home");
        final Path javaPath = Paths.get(javaHome, "bin", "java");

        final ProcessBuilder pb = new ProcessBuilder(
                javaPath.toString(),
                "-cp",
                ManagementFactory.getRuntimeMXBean().getClassPath(),
                specializedClass.getName(),
                Integer.toString(port));
        if (args != null && !args.isEmpty()) {
            pb.command(Stream.concat(pb.command().stream(), args.stream()).toList());
        }

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        try {
            process = pb.start();

            // Block until the specializedClass main() indicates the server is running:
            process.inputReader().read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        process.destroy();
    }

    @Override
    public void stopNow() {
        process.destroyForcibly();
        process.onExit().join();
    }

    @Override
    public void setSayHello(Function<HelloRequest, HelloReply> sayHello) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSayHelloStreamReply(BiConsumer<HelloRequest, Pipeline<? super HelloReply>> sayHelloStreamReply) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSayHelloStreamRequest(
            Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSayHelloStreamBidi(
            Function<Pipeline<? super HelloReply>, Pipeline<? super HelloRequest>> sayHelloStreamBidi) {
        throw new UnsupportedOperationException();
    }
}
