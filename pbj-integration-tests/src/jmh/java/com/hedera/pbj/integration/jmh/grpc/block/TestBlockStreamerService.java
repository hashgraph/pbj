// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.grpc.block;

import com.hedera.pbj.integration.jmh.SampleBlockBench;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.grpc.Pipeline;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.NonNull;
import pbj.integration.tests.pbj.integration.tests.TestBlock;
import pbj.integration.tests.pbj.integration.tests.TestBlockItem;
import pbj.integration.tests.pbj.integration.tests.TestBlockRequest;
import pbj.integration.tests.pbj.integration.tests.TestBlockStreamerInterface;

public class TestBlockStreamerService implements TestBlockStreamerInterface {
    private final int maxBlockSize;

    private static final Set<Integer> PRINTED_MAX_BLOCK_SIZES = new HashSet<>();
    private final List<TestBlock> testBlocks = new ArrayList<>();

    public TestBlockStreamerService(int maxBlockSize) {
        this.maxBlockSize = maxBlockSize;

        final byte[] bytes;
        try (var in = new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(
                SampleBlockBench.class.getResourceAsStream("/000000000000000000000000000000030221.blk.gz"))))) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final TestBlock testBlock;
        try {
            testBlock = TestBlock.PROTOBUF.parse(Bytes.wrap(bytes));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        // split on maxBlockSize bytes boundary. Not extremely efficient, but that's done once just once per test.
        testBlocks.add(TestBlock.DEFAULT);
        for (int i = 0; i < testBlock.items().size(); i++) {
            final TestBlockItem item = testBlock.items().get(i);

            TestBlock block = testBlocks.getLast();

            // Try adding
            block = block.copyBuilder()
                    .items(Stream.concat(block.items().stream(), Stream.of(item))
                            .toList())
                    .build();
            if (block.protobufSize() <= this.maxBlockSize) {
                testBlocks.set(testBlocks.size() - 1, block);
            } else {
                // Try creating a new block
                block = TestBlock.newBuilder().items(List.of(item)).build();
                if (block.protobufSize() <= this.maxBlockSize) {
                    testBlocks.add(block);
                }
            }
            // The item is just too large for now. See https://github.com/hashgraph/pbj/issues/748
        }

        if (PRINTED_MAX_BLOCK_SIZES.add(maxBlockSize)) {
            System.err.println("\nTest blocks of maxBlockSize " + maxBlockSize + ":");
            for (int i = 0; i < testBlocks.size(); i++) {
                final TestBlock block = testBlocks.get(i);
                System.err.println("   " + i + ": " + block.items().size() + " items, " + block.protobufSize()
                        + " bytes total, with average item at "
                        + (block.protobufSize() / block.items().size()) + " bytes");
            }
        }
    }

    @Override
    public @NonNull TestBlock getBlock(@NonNull TestBlockRequest request) {
        return testBlocks.get(request.num() % testBlocks.size());
    }

    @Override
    public @NonNull Pipeline<? super TestBlockRequest> streamBlocks(@NonNull Pipeline<? super TestBlock> replies) {
        return new Pipeline<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}

            @Override
            public void onNext(TestBlockRequest request) throws RuntimeException {
                for (int i = 0; i < request.num(); i++) {
                    replies.onNext(testBlocks.get(i % testBlocks.size()));
                }
            }
        };
    }
}
