// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The view accessors that read in place: bytes fields byte by byte, or through a view; wrappers without boxing. */
final class ViewInPlaceAccessorsTest {
    private static final TransactionBody BODY = TransactionBody.newBuilder()
            .transactionID(TransactionID.newBuilder()
                    .accountID(AccountID.newBuilder().accountNum(1234).build())
                    .build())
            .transactionFee(100_000)
            .atomicBatch(AtomicBatchTransactionBody.newBuilder()
                    .transactions(List.of(Bytes.wrap(new byte[] {1, 2, 3}), Bytes.EMPTY))
                    .build())
            .build();

    @Test
    void aBytesFieldIsReadThroughTheViewOfTheMessageItHolds() throws ParseException {
        final Bytes body = TransactionBody.PROTOBUF.toBytes(BODY);
        final Bytes signed = SignedTransaction.PROTOBUF.toBytes(
                SignedTransaction.newBuilder().bodyBytes(body).build());
        // over a Bytes, and over a plain array with the message in the middle of it
        final byte[] padded = new byte[(int) signed.length() + 20];
        signed.getBytes(0, padded, 10, (int) signed.length());
        for (final SignedTransaction.View view :
                List.of(new SignedTransaction.View().wrap(signed), new SignedTransaction.View().wrap(padded, 10, (int)
                                signed.length()))) {
            final TransactionBody.View bodyView = new TransactionBody.View();
            assertSame(bodyView, view.bodyBytesAs(bodyView));
            assertEquals(BODY, bodyView.toModel());
            assertEquals(1234, bodyView.transactionID().accountID().accountNum());
            final int length = view.bodyBytesLength();
            assertEquals(body.length(), length);
            for (int i = 0; i < length; i++) {
                assertEquals(body.getByte(i), view.bodyBytesByteAt(i));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> view.bodyBytesByteAt(length));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bodyBytesByteAt(-1));
        }
    }

    @Test
    void anAbsentBytesFieldIsAnEmptyMessage() throws ParseException {
        final SignedTransaction.View view =
                new SignedTransaction.View().wrap(SignedTransaction.PROTOBUF.toBytes(SignedTransaction.DEFAULT));
        final TransactionBody.View body = view.bodyBytesAs(new TransactionBody.View());
        assertEquals(TransactionBody.DEFAULT, body.toModel());
        assertFalse(body.hasTransactionID());
        assertThrows(IndexOutOfBoundsException.class, () -> view.bodyBytesByteAt(0));
    }

    @Test
    void aBytesFieldThatIsNotTheMessageFailsToWrap() throws ParseException {
        // a varint that runs to the end of the field
        final SignedTransaction.View view = new SignedTransaction.View()
                .wrap(SignedTransaction.PROTOBUF.toBytes(SignedTransaction.newBuilder()
                        .bodyBytes(Bytes.wrap(new byte[] {0x08, (byte) 0x80}))
                        .sigMap(com.hedera.hapi.node.base.SignatureMap.DEFAULT)
                        .build()));
        assertThrows(ParseException.class, () -> view.bodyBytesAs(new TransactionBody.View()));
    }

    @Test
    void repeatedBytesElementsAreReadInPlace() throws ParseException {
        final TransactionBody.View view = new TransactionBody.View().wrap(TransactionBody.PROTOBUF.toBytes(BODY));
        final var transactions = view.atomicBatch().transactions();
        assertEquals(true, transactions.next());
        assertEquals(3, transactions.valueLength());
        assertEquals(1, transactions.valueByteAt(0));
        assertEquals(3, transactions.valueByteAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> transactions.valueByteAt(3));
        assertEquals(true, transactions.next());
        assertThrows(IndexOutOfBoundsException.class, () -> transactions.valueByteAt(0));
    }

    @Test
    void wrapperFieldsReadWithoutBoxing() throws ParseException {
        final SingletonUpdateChange.View set = new SingletonUpdateChange.View()
                .wrap(SingletonUpdateChange.PROTOBUF.toBytes(SingletonUpdateChange.newBuilder()
                        .entityNumberValue(42L)
                        .build()));
        assertEquals(42L, set.entityNumberValueOrElse(-1));
        assertEquals(42L, set.entityNumberValue());
        final SingletonUpdateChange.View zero = new SingletonUpdateChange.View()
                .wrap(SingletonUpdateChange.PROTOBUF.toBytes(
                        SingletonUpdateChange.newBuilder().entityNumberValue(0L).build()));
        assertEquals(0L, zero.entityNumberValueOrElse(-1));
        assertEquals(0L, zero.entityNumberValue());
        final SingletonUpdateChange.View absent = new SingletonUpdateChange.View()
                .wrap(SingletonUpdateChange.PROTOBUF.toBytes(SingletonUpdateChange.DEFAULT));
        assertEquals(-1L, absent.entityNumberValueOrElse(-1));
        assertNull(absent.entityNumberValue());
    }

    @Test
    void childViewsAreReadAfterWrapperFieldsAndBeforeThem() throws ParseException {
        // wrapper fields are read with a cursor of their own, so the view's cursor still spans the message for the
        // child views wrapped after them
        final Bytes encoded = TokenUpdateTransactionBody.PROTOBUF.toBytes(TokenUpdateTransactionBody.newBuilder()
                .token(TokenID.newBuilder().tokenNum(7).build())
                .expiry(Timestamp.newBuilder().seconds(99).build())
                .memo("a memo")
                .build());
        final TokenUpdateTransactionBody.View view = new TokenUpdateTransactionBody.View().wrap(encoded);
        assertEquals("a memo", view.memo());
        assertEquals(7, view.token().tokenNum());
        assertEquals("a memo", view.memo());
        assertEquals(99, view.expiry().seconds());
        assertEquals(7, view.token().tokenNum());
    }
}
