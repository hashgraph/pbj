// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The reference codec agrees with model objects on every item and transaction of the synthetic block: it encodes the
 * same bytes, and scanning and decoding both produce the same field sums as {@link ScanObjects}. See
 * {@link SyntheticBlockTest} for the corresponding checks of model objects, writers and views.
 */
final class ReferenceCodecTest {
    private static SyntheticBlock block;
    private static List<BlockItem> items;
    private static Bytes encodedBlock;
    private static List<Bytes> transactions;

    @BeforeAll
    static void generate() {
        block = SyntheticBlock.generate();
        items = block.buildAll();
        encodedBlock = Block.PROTOBUF.toBytes(Block.newBuilder().items(items).build());
        transactions = SyntheticBlock.transactions();
    }

    @Test
    void encodesEveryItemByteIdentical() {
        final ReferenceCodec codec = new ReferenceCodec();
        final SyntheticBlock.Cursor cursor = block.cursor();
        for (int i = 0; i < items.size(); i++) {
            final BlockItem item = items.get(i);
            final Bytes expected = BlockItem.PROTOBUF.toBytes(item);
            final int length = codec.encodeItem(cursor);
            assertEquals(expected, Bytes.wrap(codec.buffer(), 0, length), "item " + i);
        }
    }

    @Test
    void encodesWholeBlockByteIdentical() {
        final ReferenceCodec codec = new ReferenceCodec();
        final int length = codec.encodeBlock(block);
        assertEquals(encodedBlock, Bytes.wrap(codec.buffer(), 0, length));
    }

    @Test
    void scansEveryItemToTheSameSum() {
        final ReferenceCodec codec = new ReferenceCodec();
        for (int i = 0; i < items.size(); i++) {
            final BlockItem item = items.get(i);
            final long expected = ScanObjects.item(item);
            final byte[] encoded = BlockItem.PROTOBUF.toBytes(item).toByteArray();
            assertEquals(expected, codec.scanItem(encoded, 0, encoded.length), "item " + i);
            final ReferenceCodec.RBlockItem decoded = codec.decodeItem(encoded, 0, encoded.length);
            assertEquals(expected, decoded.sum(), "decoded item " + i);
        }
    }

    @Test
    void scansAndDecodesTheWholeBlockToTheSameSum() {
        final ReferenceCodec codec = new ReferenceCodec();
        final long expected = ScanObjects.items(items);
        final byte[] encoded = encodedBlock.toByteArray();
        assertEquals(expected, codec.scanBlock(encoded, 0, encoded.length));
        final ReferenceCodec.RBlock decoded = codec.decodeBlock(encoded, 0, encoded.length);
        assertEquals(expected, ReferenceCodec.scan(decoded));
    }

    @Test
    void scansAndDecodesEveryTransactionToTheSameSum() throws Exception {
        final ReferenceCodec codec = new ReferenceCodec();
        for (int i = 0; i < transactions.size(); i++) {
            final Bytes transaction = transactions.get(i);
            final long expected = ScanObjects.transaction(transaction);
            final byte[] encoded = transaction.toByteArray();
            assertEquals(expected, codec.scanTransaction(encoded, 0, encoded.length), "transaction " + i);
            final ReferenceCodec.RSignedTx decoded = codec.decodeTransaction(encoded, 0, encoded.length);
            assertEquals(expected, ReferenceCodec.scan(decoded), "decoded transaction " + i);
        }
    }

    @Test
    void worksOnASmallerBlockToo() throws Exception {
        final SyntheticBlock small = SyntheticBlock.generate(200, 20_26_10_30L);
        final List<BlockItem> smallItems = small.buildAll();
        final Bytes smallEncoded =
                Block.PROTOBUF.toBytes(Block.newBuilder().items(smallItems).build());
        final ReferenceCodec codec = new ReferenceCodec();
        final int length = codec.encodeBlock(small);
        assertEquals(smallEncoded, Bytes.wrap(codec.buffer(), 0, length));
        final byte[] encoded = smallEncoded.toByteArray();
        assertEquals(ScanObjects.items(smallItems), codec.scanBlock(encoded, 0, encoded.length));
        assertEquals(ScanObjects.items(smallItems), ReferenceCodec.scan(codec.decodeBlock(encoded, 0, encoded.length)));

        final List<Bytes> smallTransactions = SyntheticBlock.transactions(200, 20_26_10_31L);
        for (final Bytes transaction : smallTransactions) {
            final long expected = ScanObjects.transaction(transaction);
            final byte[] txBytes = transaction.toByteArray();
            assertEquals(expected, codec.scanTransaction(txBytes, 0, txBytes.length));
            assertEquals(expected, ReferenceCodec.scan(codec.decodeTransaction(txBytes, 0, txBytes.length)));
        }
    }
}
