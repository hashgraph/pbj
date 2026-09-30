// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.CopyBuilderTracked;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.CacheableAccountID;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytesAndString;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.WideTrackedModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopyBuilderTrackingTest {
    @Test
    void chainsRetainTheRootAndSettersMarkCandidatesEvenWhenValuesAreRestored() {
        final var root =
                Everything.newBuilder().int32Number(1).text("unchanged").build();
        assertNull(root.$copyBuilderOrigin());
        assertSame(root, root.$untracked());
        final var middle = root.copyBuilder().int32Number(2).build();
        final var next = middle.copyBuilder()
                .int32Number(1)
                .bytesField(Bytes.wrap("new"))
                .build();
        assertSame(root, middle.$copyBuilderOrigin());
        assertSame(root, next.$copyBuilderOrigin());
        assertTrue(next.$copyBuilderFieldChanged(1));
        assertTrue(next.$copyBuilderFieldChanged(17));
        assertFalse(next.$copyBuilderFieldChanged(16));
        assertFalse(next.$copyBuilderFieldChanged(999));
        final var detached = next.$untracked();
        assertNotSame(next, detached);
        assertEquals(next, detached);
        assertNull(detached.$copyBuilderOrigin());
        assertFalse(detached.$copyBuilderFieldChanged(1));
        assertSame(detached, detached.copyBuilder().build().$copyBuilderOrigin());
    }

    @Test
    void oneofSettersAndClearMarkEveryAlternativeIncludingSelectedZero() {
        final var root = Everything.newBuilder().textOneOf("old").build();
        final var switched = root.copyBuilder().int32NumberOneOf(0).build();
        assertTrue(switched.$copyBuilderFieldChanged(100001));
        assertTrue(switched.$copyBuilderFieldChanged(100016));
        assertFalse(switched.$copyBuilderFieldChanged(1));
        final var builder = switched.copyBuilder();
        builder.clearOneofExample();
        final var unset = builder.build();
        assertSame(root, unset.$copyBuilderOrigin());
        assertTrue(unset.$copyBuilderFieldChanged(100016));
        assertEquals(
                Everything.OneofExampleOneOfType.UNSET, unset.oneofExample().kind());
    }

    @Test
    void messageBuilderListVarargsMapsAndOptionalWrapperSettersAreTracked() {
        final var root = Everything.DEFAULT;
        final var next = root.copyBuilder()
                .subObject(TimestampTest.newBuilder().seconds(123))
                .textList("a", "b")
                .int32NumberList(List.of(1, 2))
                .mapInt32ToString(Map.of(1, "one"))
                .int32Boxed(0)
                .subObjectOneOf(TimestampTest.newBuilder().seconds(456))
                .build();
        for (int field : List.of(15, 116, 100, 71, 1001, 100015)) {
            assertTrue(next.$copyBuilderFieldChanged(field), "field " + field);
        }
        assertFalse(next.$copyBuilderFieldChanged(16));
    }

    @Test
    void wideMasksAreImmutableSnapshotsAcrossBuilderReuseAndForks() {
        final var root = WideTrackedModel.DEFAULT;
        final var builder = root.copyBuilder().value1(1).value64(64);
        final var first = builder.build();
        final var second = builder.value65(65).build();
        final var fork = second.copyBuilder().value70(70).number(0).build();
        assertTrue(first.$copyBuilderFieldChanged(100));
        assertTrue(first.$copyBuilderFieldChanged(163));
        assertFalse(first.$copyBuilderFieldChanged(164));
        assertTrue(second.$copyBuilderFieldChanged(164));
        assertFalse(second.$copyBuilderFieldChanged(169));
        assertTrue(fork.$copyBuilderFieldChanged(169));
        assertTrue(fork.$copyBuilderFieldChanged(900));
        assertTrue(fork.$copyBuilderFieldChanged(901));
        assertSame(root, fork.$copyBuilderOrigin());
        assertFalse(fork.$untracked().$copyBuilderFieldChanged(100));
    }

    @Test
    void trackingHasNoEffectOnValueSemanticsCodecsOrCachedModels() throws Exception {
        final var root = CacheableAccountID.newBuilder().accountNum(123).build();
        final var tracked = root.copyBuilder().shardNum(1).build();
        final var plain =
                CacheableAccountID.newBuilder().shardNum(1).accountNum(123).build();
        assertEquals(plain, tracked);
        assertEquals(plain.hashCode(), tracked.hashCode());
        assertEquals(plain.toString(), tracked.toString());
        assertEquals(plain.protobufSize(), tracked.protobufSize());
        assertEquals(CacheableAccountID.PROTOBUF.toBytes(plain), CacheableAccountID.PROTOBUF.toBytes(tracked));
        assertEquals(CacheableAccountID.JSON.toJSON(plain), CacheableAccountID.JSON.toJSON(tracked));
        assertNull(CacheableAccountID.PROTOBUF
                .parse(CacheableAccountID.PROTOBUF.toBytes(tracked))
                .$copyBuilderOrigin());
        final var jsonParsed = CacheableAccountID.JSON.parse(Bytes.wrap(CacheableAccountID.JSON.toJSON(tracked)));
        assertNull(jsonParsed.$copyBuilderOrigin());
        assertSame(plain, CopyBuilderTracked.untracked(plain));
        assertEquals(plain, CopyBuilderTracked.untracked(tracked));
        assertEquals("text", CopyBuilderTracked.untracked("text"));
    }

    @Test
    void copyBuildersAndUntrackedCopiesPreserveUnknownFields() throws Exception {
        final var bytes = MessageWithBytesAndString.PROTOBUF.toBytes(
                new MessageWithBytesAndString(Bytes.wrap("known"), "unknown"));
        final var root =
                MessageWithBytes.PROTOBUF.parse(bytes.toReadableSequentialData(), false, true, Codec.DEFAULT_MAX_DEPTH);
        final var next = root.copyBuilder().build();
        assertFalse(next.getUnknownFields().isEmpty());
        assertEquals(root, next);
        assertEquals(bytes, MessageWithBytes.PROTOBUF.toBytes(next));
        assertEquals(bytes, MessageWithBytes.PROTOBUF.toBytes(next.$untracked()));
    }
}
