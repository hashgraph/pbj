// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The synthetic block has the external workload's shape (checked here, so a change to the generator that moves it is
 * caught), and every way of producing and reading it agrees: model objects, the reverse writer, the generated writers
 * and the reference codec write the same bytes, and the objects, views and reference codec read the same sums.
 */
final class SyntheticBlockTest {
    private static SyntheticBlock block;
    private static List<BlockItem> items;
    private static Bytes encoded;

    @BeforeAll
    static void generate() {
        block = SyntheticBlock.generate();
        items = block.buildAll();
        encoded = Block.PROTOBUF.toBytes(Block.newBuilder().items(items).build());
    }

    private static void assertNear(
            final double expected, final double actual, final double tolerance, final String what) {
        assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + " is " + actual + ", expected " + expected + " ± " + tolerance);
    }

    @Test
    void hasTheShapeOfTheExternalWorkload() {
        final double transactions = SyntheticBlock.TRANSACTIONS;
        long stateChangesBytes = 0;
        int stateChangesItems = 0;
        for (final BlockItem item : items) {
            if (item.hasStateChanges()) {
                stateChangesBytes += BlockItem.PROTOBUF.measureRecord(item);
                stateChangesItems++;
            }
        }
        // per kind of item and of state change: how many, and their average size
        final java.util.Map<String, long[]> kinds = new java.util.TreeMap<>();
        for (final BlockItem item : items) {
            String kind = item.item().kind().toString();
            if (item.hasTransactionResult()) {
                final var r = item.transactionResultOrThrow();
                kind += r.tokenTransferLists().isEmpty()
                        ? (r.transferList() == null ? " plain" : " transfer list")
                        : " token transfers "
                                + r.tokenTransferLists().get(0).transfers().size();
            }
            final long[] k = kinds.computeIfAbsent(kind, x -> new long[2]);
            k[0]++;
            k[1] += BlockItem.PROTOBUF.measureRecord(item) + 2;
            if (item.hasStateChanges()) {
                for (final var change : item.stateChangesOrThrow().stateChanges()) {
                    String op = "  change " + change.changeOperation().kind();
                    if (change.hasMapUpdate()) {
                        op += " "
                                + change.mapUpdateOrThrow()
                                        .keyOrThrow()
                                        .keyChoice()
                                        .kind();
                        op += change.mapUpdateOrThrow().valueOrThrow().hasNftValue()
                                ? (change.mapUpdateOrThrow()
                                                        .valueOrThrow()
                                                        .nftValueOrThrow()
                                                        .nftId()
                                                == null
                                        ? " private"
                                        : " full")
                                : "";
                    }
                    final long[] c = kinds.computeIfAbsent(op, x -> new long[2]);
                    c[0]++;
                    c[1] += StateChange.PROTOBUF.measureRecord(change) + 2;
                }
            }
        }
        kinds.forEach((k, v) -> System.out.printf(
                "  %-60s %8.2f per transaction, %6.1f bytes%n", k, v[0] / transactions, (double) v[1] / v[0]));
        final double bytesPerTransaction = encoded.length() / transactions;
        final double itemsPerTransaction = items.size() / transactions;
        final double changesPerTransaction = block.stateChanges / transactions;
        final double stateChangesItemBytes = (double) stateChangesBytes / stateChangesItems;
        System.out.printf(
                "Synthetic block: %d transactions, %d items (%.2f per transaction), %d bytes (%.1f per transaction),"
                        + " %.2f state changes per transaction, %.1f bytes per state changes item%n",
                SyntheticBlock.TRANSACTIONS,
                items.size(),
                itemsPerTransaction,
                encoded.length(),
                bytesPerTransaction,
                changesPerTransaction,
                stateChangesItemBytes);
        // The external workload has about 680 bytes per transaction, 316 of them state changes. HAPI wraps every id in
        // a
        // message (AccountID, TokenID, NftID: about 3 more bytes each) and has nested timestamps with varint nanos
        // (about 4.5 more bytes than a fixed64): with about 36 ids and 4.5 timestamps per transaction's state
        // changes, that is about 127 bytes more for the same fields and values.
        assertNear(680 + 127, bytesPerTransaction, 30, "Bytes per transaction");
        assertNear(316 + 127, stateChangesItemBytes, 20, "Bytes per state changes item");
        assertNear(9.7, itemsPerTransaction, 0.2, "Items per transaction");
        assertNear(12.6, changesPerTransaction, 0.3, "State changes per transaction");
    }

    @Test
    void isTheSameEveryTime() {
        assertEquals(
                encoded,
                Block.PROTOBUF.toBytes(Block.newBuilder()
                        .items(SyntheticBlock.generate().buildAll())
                        .build()));
    }

    @Test
    void everyWriterWritesTheSameBytes() {
        final ReverseBuffer reverse = new ReverseBuffer(1 << 16);
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1 << 16);
        final BlockItem.Writer writer = new BlockItem.Writer();
        final SyntheticBlock.Cursor cursor = block.cursor();
        for (int i = 0; i < items.size(); i++) {
            final BlockItem item = items.get(i);
            final Bytes expected = BlockItem.PROTOBUF.toBytes(item);
            reverse.reset();
            BlockItem.PROTOBUF.writeReverse(item, reverse);
            assertEquals(expected, reverse.toBytes(), "writeReverse of item " + i);
            buffer.reset();
            SyntheticBlock.write(cursor, writer.start(buffer));
            writer.end();
            assertEquals(expected, buffer.toBytes(), "writer of item " + i);
        }
        final ProtoWriteBuffer blockBuffer = new ProtoWriteBuffer(1 << 16);
        final Block.Writer blockWriter = new Block.Writer().start(blockBuffer);
        cursor.reset();
        for (int i = 0; i < items.size(); i++) {
            SyntheticBlock.write(cursor, blockWriter.addItems());
        }
        blockWriter.end();
        assertEquals(encoded, blockBuffer.toBytes(), "whole block writer");
    }

    @Test
    void objectsAndViewsReadTheSameSums() throws Exception {
        final ScanViews views = new ScanViews();
        long total = 0;
        for (int i = 0; i < items.size(); i++) {
            final long sum = ScanObjects.item(items.get(i));
            assertEquals(sum, views.item(BlockItem.PROTOBUF.toBytes(items.get(i))), "item " + i);
            total += sum;
        }
        assertEquals(total, ScanObjects.items(Block.PROTOBUF.parse(encoded).items()));
        assertEquals(total, views.block(encoded));
        for (final Bytes transaction : SyntheticBlock.transactions()) {
            assertEquals(ScanObjects.transaction(transaction), views.transaction(transaction));
        }
    }

    @Test
    void transactionsHaveTheirShape() throws Exception {
        final List<Bytes> transactions = SyntheticBlock.transactions();
        long bytes = 0;
        for (final Bytes transaction : transactions) {
            bytes += transaction.length();
            ScanObjects.transaction(transaction);
        }
        System.out.printf("Synthetic transactions: %.1f bytes on average%n", (double) bytes / transactions.size());
        assertEquals(SyntheticBlock.TRANSACTIONS, transactions.size());
        assertEquals(transactions, SyntheticBlock.transactions());
    }
}
