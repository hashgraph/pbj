// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import org.junit.jupiter.api.Test;

/** A small message written in one call from its values writes what the model, and its own writer, write. */
final class InlineSetterTest {
    private static final long[] LONGS = {0, 1, -1, 127, 128, 16_383, 16_384, Long.MAX_VALUE, Long.MIN_VALUE};
    private static final int[] INTS = {0, 1, -1, 127, 128, 999_999_999, Integer.MAX_VALUE, Integer.MIN_VALUE};

    @Test
    void idsAreWrittenAsTheModelWritesThem() {
        final ProtoWriteBuffer inline = new ProtoWriteBuffer(64);
        final ProtoWriteBuffer opened = new ProtoWriteBuffer(64);
        final AccountAmount.Writer writer = new AccountAmount.Writer();
        for (final long shard : LONGS) {
            for (final long realm : LONGS) {
                for (final long num : LONGS) {
                    final AccountAmount model = AccountAmount.newBuilder()
                            .accountID(AccountID.newBuilder()
                                    .shardNum(shard)
                                    .realmNum(realm)
                                    .accountNum(num)
                                    .build())
                            .amount(num)
                            .build();
                    inline.reset();
                    writer.start(inline)
                            .accountID(shard, realm, num)
                            .amount(num)
                            .end();
                    opened.reset();
                    writer.start(opened)
                            .accountID()
                            .shardNum(shard)
                            .realmNum(realm)
                            .accountNum(num);
                    writer.amount(num).end();
                    final String what = shard + "." + realm + "." + num;
                    assertEquals(AccountAmount.PROTOBUF.toBytes(model), inline.toBytes(), what);
                    assertEquals(opened.toBytes(), inline.toBytes(), what);
                }
            }
        }
    }

    @Test
    void timestampsAreWrittenAsTheModelWritesThem() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(64);
        final TransactionID.Writer writer = new TransactionID.Writer();
        for (final long seconds : LONGS) {
            for (final int nanos : INTS) {
                final TransactionID model = TransactionID.newBuilder()
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(seconds)
                                .nanos(nanos)
                                .build())
                        .nonce(nanos)
                        .build();
                buffer.reset();
                writer.start(buffer)
                        .transactionValidStart(seconds, nanos)
                        .nonce(nanos)
                        .end();
                assertEquals(TransactionID.PROTOBUF.toBytes(model), buffer.toBytes(), seconds + "." + nanos);
            }
        }
    }

    @Test
    void repeatedElementsAreWrittenAsTheModelWritesThem() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(256);
        final com.hedera.hapi.node.token.TokenAssociateTransactionBody.Writer writer =
                new com.hedera.hapi.node.token.TokenAssociateTransactionBody.Writer();
        final java.util.List<com.hedera.hapi.node.base.TokenID> tokens = new java.util.ArrayList<>();
        writer.start(buffer).account(0, 0, 1001);
        for (final long num : LONGS) {
            tokens.add(com.hedera.hapi.node.base.TokenID.newBuilder()
                    .realmNum(num)
                    .tokenNum(num)
                    .build());
            writer.addTokens(0, num, num);
        }
        writer.end();
        final var model = com.hedera.hapi.node.token.TokenAssociateTransactionBody.newBuilder()
                .account(AccountID.newBuilder().accountNum(1001).build())
                .tokens(tokens)
                .build();
        assertEquals(
                com.hedera.hapi.node.token.TokenAssociateTransactionBody.PROTOBUF.toBytes(model), buffer.toBytes());
    }

    @Test
    void theFieldOrderIsChecked() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(64);
        final AccountAmount.Writer writer =
                new AccountAmount.Writer().start(buffer).amount(5);
        assertThrows(IllegalStateException.class, () -> writer.accountID(0, 0, 1));
    }
}
