// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer.LengthReservation;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * HAPI block items through the generated writers, which only exist with every option on: whole blocks and single items
 * written from objects and from extracted values are byte-identical to the model's encoding, items can be hashed from
 * their ranges in the buffer as soon as they end, and none of it allocates.
 */
final class BlockWriterTest {
    private static final Path SAMPLE_BLOCK = Path.of("src/jmh/resources/000000000000000000000000000000030221.blk.gz");

    private static Block block;
    private static BlockItemValues values;

    @BeforeAll
    static void loadSampleBlock() throws Exception {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(SAMPLE_BLOCK))) {
            // parse, and encode again, so the reference bytes are the model's encoding
            block = Block.PROTOBUF.parse(Block.PROTOBUF.toBytes(Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()))));
        }
        values = BlockItemValues.extract(block.items());
    }

    /** SHA-384 of a block item's bytes after a 0x00 prefix, as a Merkle leaf is hashed. */
    private static byte[] leafHash(final MessageDigest digest, final byte[] array, final int offset, final int length)
            throws DigestException {
        final byte[] hash = new byte[48];
        digest.update((byte) 0);
        digest.update(array, offset, length);
        digest.digest(hash, 0, hash.length);
        return hash;
    }

    @Test
    void wholeBlockFromObjectsWithItemHashes() throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-384");
        for (final LengthReservation reservation : LengthReservation.values()) {
            final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1024, reservation);
            final Block.Writer writer = new Block.Writer().start(buffer);
            final List<BlockItem> items = block.items();
            for (final BlockItem item : items) {
                final BlockItem.Writer itemWriter = writer.addItems().writeFrom(item);
                itemWriter.end();
                // hashed as soon as it ends, from its range in the buffer, before the block is finished
                final byte[] expected = BlockItem.PROTOBUF.toBytes(item).toByteArray();
                assertArrayEquals(
                        leafHash(digest, expected, 0, expected.length),
                        leafHash(digest, buffer.array(), itemWriter.offset(), itemWriter.length()));
            }
            writer.end();
            assertEquals(Block.PROTOBUF.toBytes(block), buffer.bytes(), reservation.name());
            System.out.printf(
                    "Block of %,d items, %,d bytes, %s length reservation: %,d moves (%.4f per item)%n",
                    items.size(),
                    buffer.length(),
                    reservation,
                    buffer.shiftCount(),
                    buffer.shiftCount() / (double) items.size());
        }
    }

    @Test
    void itemsFromValuesAndObjectsAreTheModelsBytes() {
        final Map<String, Integer> kinds = new TreeMap<>();
        values.items().forEach(item -> kinds.merge(item.item().kind().name(), 1, Integer::sum));
        System.out.printf(
                "Extracted the values of %,d of %,d block items: %s%n",
                values.items().size(), block.items().size(), kinds);
        assertTrue(values.items().size() > block.items().size() * 0.95, "most items are covered");
        final BlockItemValues.Cursor cursor = values.cursor();
        for (final BlockItem item : values.items()) {
            assertEquals(item, values.build(cursor));
        }
        cursor.reset();
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final BlockItem.Writer writer = new BlockItem.Writer();
        for (final BlockItem item : values.items()) {
            buffer.reset();
            values.write(cursor, writer.start(buffer));
            writer.end();
            assertEquals(BlockItem.PROTOBUF.toBytes(item), buffer.bytes());
        }
    }

    @Test
    void writingDoesNotAllocate() throws NoSuchAlgorithmException {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final BlockItem.Writer writer = new BlockItem.Writer();
        final BlockItemValues.Cursor cursor = values.cursor();
        final int count = values.items().size();
        final List<BlockItem> items = block.items();
        // every item, several times, before measuring: the measured operations take branches that would otherwise
        // still be uncompiled, and deoptimizing allocates
        for (int pass = 0; pass < 5; pass++) {
            cursor.reset();
            for (int i = 0; i < count; i++) {
                buffer.reset();
                values.write(cursor, writer.start(buffer));
                writer.end();
            }
            for (int i = 0; i < items.size(); i++) {
                buffer.reset();
                writer.start(buffer).writeFrom(items.get(i)).end();
            }
        }
        cursor.reset();
        final long fromValues = AllocationTest.allocatedPerOperation(i -> {
            if (i % count == 0) {
                cursor.reset();
            }
            buffer.reset();
            values.write(cursor, writer.start(buffer));
            writer.end();
        });
        final long fromObjects = AllocationTest.allocatedPerOperation(i -> {
            buffer.reset();
            writer.start(buffer).writeFrom(items.get(i % items.size())).end();
        });
        // a whole block, hashing each item as it ends
        final MessageDigest digest = MessageDigest.getInstance("SHA-384");
        final byte[] hash = new byte[48];
        final ProtoWriteBuffer blockBuffer = new ProtoWriteBuffer(16);
        final Block.Writer blockWriter = new Block.Writer();
        final long[] blockAllocation = new long[1];
        for (int round = 0; round < 8; round++) {
            // reading the allocation counter allocates a little itself: measure that, and leave it out
            long before = allocatedBytes();
            final long measuring = allocatedBytes() - before;
            before = allocatedBytes();
            blockBuffer.reset();
            blockWriter.start(blockBuffer);
            for (int i = 0; i < items.size(); i++) {
                final BlockItem.Writer itemWriter = blockWriter.addItems().writeFrom(items.get(i));
                itemWriter.end();
                digest.update((byte) 0);
                digest.update(blockBuffer.array(), itemWriter.offset(), itemWriter.length());
                try {
                    digest.digest(hash, 0, hash.length);
                } catch (DigestException e) {
                    throw new IllegalStateException(e);
                }
            }
            blockWriter.end();
            blockAllocation[0] = allocatedBytes() - before - measuring;
        }
        System.out.printf(
                "Writer allocation: from values %d B/item, from objects %d B/item, whole block with hashing %d B%n",
                fromValues, fromObjects, blockAllocation[0]);
        assertEquals(0, fromValues, "writing block items from values");
        assertEquals(0, fromObjects, "writing block items from objects");
        assertEquals(0, blockAllocation[0], "writing and hashing a whole block, once warm");
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean())
                .getCurrentThreadAllocatedBytes();
    }
}
