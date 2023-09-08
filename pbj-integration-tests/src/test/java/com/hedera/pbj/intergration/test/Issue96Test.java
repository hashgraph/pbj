package com.hedera.pbj.intergration.test;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

final class Issue96Test {

    // ================================================================================================================
    // Verify common comments.
    @Test
    @DisplayName("Issue 96")
    void issue96() {
        final var unhexed = HexFormat.of().parseHex(testData);
        assertThrows(InvalidProtocolBufferException.class, () ->
                com.hederahashgraph.api.proto.java.ServicesConfigurationList.parseFrom(unhexed));
        assertThrows(Exception.class, () ->
                com.hedera.hapi.node.base.ServicesConfigurationList.PROTOBUF.parseStrict(
                        BufferedData.wrap(unhexed)));

    }

    @Test
    @DisplayName("Test Boxed Float")
    void testBoxedFloat() {
        Everything ev = Everything.newBuilder().floatBoxed(123.0f).build();
        Bytes bytes = Everything.PROTOBUF.toBytes(ev);
        Bytes b = bytes.slice(0, bytes.length() - 1);
        assertThrows(Exception.class, () ->
                Everything.PROTOBUF.parseStrict(
                        BufferedData.wrap(b.toByteArray())));
    }

    @Test
    @DisplayName("Test Boxed Int32")
    void testBoxedInt32() {
        Everything ev = Everything.newBuilder().int32Boxed(123).build();
        Bytes bytes = Everything.PROTOBUF.toBytes(ev);
        Bytes b = bytes.slice(0, bytes.length() - 1);
        assertThrows(Exception.class, () ->
                Everything.PROTOBUF.parseStrict(
                        BufferedData.wrap(b.toByteArray())));
    }

    @Test
    @DisplayName("Test Boxed Int64")
    void testBoxedInt64() {
        Everything ev = Everything.newBuilder().int64Boxed(12345678L).build();
        Bytes bytes = Everything.PROTOBUF.toBytes(ev);
        Bytes b = bytes.slice(0, bytes.length() - 1);
        assertThrows(Exception.class, () ->
                Everything.PROTOBUF.parseStrict(
                        BufferedData.wrap(b.toByteArray())));
    }

    @Test
    @DisplayName("Test Number Int64")
    void testNumberInt64() {
        Everything ev = Everything.newBuilder().int32Number(12345678).build();
        Bytes bytes = Everything.PROTOBUF.toBytes(ev);
        Bytes b = bytes.slice(0, bytes.length() - 1);
        assertThrows(Exception.class, () ->
                Everything.PROTOBUF.parseStrict(
                        BufferedData.wrap(b.toByteArray())));
    }

    @Test
    @DisplayName("Test String")
    void testString() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(3);
        seq.writeVarInt(10, false);
        seq.position(0);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readString(seq));
    }

    @Test
    @DisplayName("Test Bytes")
    void testBytes() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(3);
        seq.writeVarInt(10, false);
        seq.position(0);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readBytes(seq));
    }

    @Test
    @DisplayName("Test readInt32")
    void testInt32() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeVarInt(0xFFFFFFFF, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readInt32(seq));
    }

    @Test
    @DisplayName("Test readInt64")
    void testInt64() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeVarLong(0xFFFFFFFFFFFFFFFFL, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readInt32(seq));
    }

    @Test
    @DisplayName("Test readUint32")
    void testUint32() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeVarInt(0xFFFFFFFF, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readUint32(seq));
    }

    @Test
    @DisplayName("Test readUint64")
    void testUint64() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeVarLong(0xFFFFFFFFFFFFFFFFL, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readUint32(seq));
    }

    @Test
    @DisplayName("Test readBool")
    void testBool() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeVarInt(0x1, false);
        seq.position(0);
        seq.limit(0);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readBool(seq));
    }

    @Test
    @DisplayName("Test readEnum")
    void testEnum() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeVarInt(0x1, false);
        seq.position(0);
        seq.limit(0);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readEnum(seq));
    }

    @Test
    @DisplayName("Test readSint32")
    void testSint32() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeVarInt(0xFFFFFFFF, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readUint32(seq));
    }

    @Test
    @DisplayName("Test readSint64")
    void testSint64() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeVarLong(0xFFFFFFFFFFFFFFFFL, false);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readUint32(seq));
    }

    @Test
    @DisplayName("Test readSFixedInt32")
    void testSFixedInt32() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeInt(0xFFFFFFFF);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readSignedInt32(seq));
    }

    @Test
    @DisplayName("Test readFixedInt32")
    void testFixedInt32() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeUnsignedInt(0xFFFFFFF0);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readFixed32(seq));
    }

    @Test
    @DisplayName("Test readFloat")
    void testFloat() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeFloat(0xFFFFFFFF);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readFloat(seq));
    }

    @Test
    @DisplayName("Test readDouble")
    void testDouble() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeUnsignedInt(0xFFFFFFF0FFFFFFFFL);
        seq.position(0);
        seq.limit(7);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readDouble(seq));
    }

    @Test
    @DisplayName("Test readSFixedInt64")
    void testSFixedInt64() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(11);
        seq.writeLong(0xFFFFFFFFFFFFFFFFL);
        seq.position(0);
        seq.limit(7);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readSignedInt64(seq));
    }

    @Test
    @DisplayName("Test readFixedInt64")
    void testFixedInt364() {
        // Given a buffer where the first varInt is the length, and is longer than the number of bytes in the buffer.
        final var seq = BufferedData.allocate(30);
        seq.writeLong(0xFFFFFFF0FFFFFFFFL);
        seq.position(0);
        seq.limit(3);

        // When we parse that sequence, then we fail because there are not enough bytes to continue parsing.
        assertThrows(Exception.class, () -> ProtoParserTools.readFixed64(seq));
    }
    public static final String testData = "0a190a1266696c65732e6665655363686564756c657312033131310a310a29636f6e7472616374732e707265636f6d70696c652e687473456e61626c65546f6b656e4372656174651204747275650a230a1c746f6b656e732e6d6178546f6b656e4e616d6555746638427974657312033130300a1f0a16746f6b656e732e73746f726552656c734f6e4469736b120566616c73650a260a2072617465732e696e7472616461794368616e67654c696d697450657263656e74120232350a230a1e7374616b696e672e72657761726442616c616e63655468726573686f6c641201300a2a0a24636f6e7472616374732e6d6178526566756e6450657263656e744f664761734c696d6974120232300a2d0a267374616b696e672e726577617264486973746f72792e6e756d53746f726564506572696f647312033336350a1a0a146163636f756e74732e73797374656d41646d696e120235300a280a21666565732e746f6b656e5472616e7366657255736167654d756c7469706c69657212033338300a1c0a146175746f4372656174696f6e2e656e61626c65641204747275650a1e0a18666565732e6d696e436f6e67657374696f6e506572696f64120236300a1a0a1366696c65732e65786368616e6765526174657312033131320a280a1a626f6f7473747261702e72617465732e6e657874457870697279120a343130323434343830300a1a0a146163636f756e74732e667265657a6541646d696e120235380a1e0a166865646572612e666972737455736572456e746974791204313030310a370a1f636f6e7472616374732e73746f72616765536c6f745072696365546965727312143074696c3130304d2c3230303074696c3435304d0a270a2174726163656162696c6974792e6d61784578706f727473506572436f6e73536563120231300a220a1c6163636f756e74732e73797374656d556e64656c65746541646d696e120236300a280a1f636f6e7472616374732e616c6c6f774175746f4173736f63696174696f6e73120566616c73650a320a2b6865646572612e7265636f726453747265616d2e6e756d4f66426c6f636b486173686573496e537461746512033235360a2e0a256865646572612e776f726b666c6f772e766572696669636174696f6e54696d656f75744d53120532303030300a1c0a146163636f756e74732e73746f72654f6e4469736b1204747275650a280a216865646572612e616c6c6f77616e6365732e6d61784163636f756e744c696d697412033130300a2b0a256865646572612e616c6c6f77616e6365732e6d61785472616e73616374696f6e4c696d6974120232300a2b0a25636f6e73656e7375732e6d6573736167652e6d6178466f6c6c6f77696e675265636f726473120235300a2a0a236865646572612e7472616e73616374696f6e2e6d617856616c69644475726174696f6e12033138300a490a0c76657273696f6e2e68617069123953656d616e74696356657273696f6e5b6d616a6f723d302c206d696e6f723d34302c2070617463683d302c207072653d2c206275696c643d5d0a240a1d6163636f756e74732e7374616b696e675265776172644163636f756e7412033830300a310a2c6175746f72656e65772e6d61784e756d6265724f66456e746974696573546f52656e65774f7244656c6574651201320a380a217374616b696e672e6d61784461696c795374616b655265776172645468506572481213393232333337323033363835343737353830370a2b0a1f636f6e7472616374732e7265666572656e6365536c6f744c69666574696d65120833313533363030300a2d0a226c65646765722e6175746f52656e6577506572696f642e6d696e4475726174696f6e1207323539323030300a4d0a1076657273696f6e2e7365727669636573123953656d616e74696356657273696f6e5b6d616a6f723d302c206d696e6f723d34302c2070617463683d302c207072653d2c206275696c643d5d0a3a0a31636f6e7472616374732e707265636f6d70696c652e61746f6d696343727970746f5472616e736665722e656e61626c6564120566616c73650a220a14656e7469746965732e6d61784c69666574696d65120a333135333630303030300a260a1d636f6e7472616374732e65766d2e76657273696f6e2e64796e616d6963120566616c73650a2b0a22636f6e7472616374732e7369646563617256616c69646174696f6e456e61626c6564120566616c73650a210a1a6163636f756e74732e6e6f64655265776172644163636f756e7412033830310a180a11636f6e7472616374732e636861696e496412033239350a270a216c65646765722e6368616e6765486973746f7269616e2e6d656d6f727953656373120232300a290a21636f6e73656e7375732e6d6573736167652e6d61784279746573416c6c6f7765641204313032340a180a1166696c65732e61646472657373426f6f6b12033130310a200a1a6163636f756e74732e73797374656d44656c65746541646d696e120235390a380a30636f6e7472616374732e707265636f6d70696c652e6872634661636164652e6173736f63696174652e656e61626c65641204747275650a220a1b6163636f756e74732e6c6173745468726f74746c654578656d707412033130300a1e0a16746f6b656e732e6e6674732e617265456e61626c65641204747275650a1b0a10746f706963732e6d61784e756d6265721207313030303030300a200a1a6c65646765722e6e66745472616e73666572732e6d61784c656e120231300a2a0a25636f6e73656e7375732e6d6573736167652e6d6178507265636564696e675265636f7264731201330a190a117374616b696e672e6973456e61626c65641204747275650a260a1b746f6b656e732e6e6674732e6d6178416c6c6f7765644d696e74731207353030303030300a2f0a187374616b696e672e6d61785374616b6552657761726465641213353030303030303030303030303030303030300a2b0a1d626f6f7473747261702e72617465732e63757272656e74457870697279120a343130323434343830300a1e0a1766696c65732e7570677261646546696c654e756d62657212033135300a240a19636f6e7472616374732e64656661756c744c69666574696d651207373839303030300a260a217374616b696e672e666565732e6e6f646552657761726450657263656e746167651201300a200a19746f6b656e732e6d617853796d626f6c55746638427974657312033130300a250a1d736967732e657870616e6446726f6d496d6d757461626c6553746174651204747275650a170a127374616b696e672e726577617264526174651201300a2b0a1d626f6f7473747261702e73797374656d2e656e74697479457870697279120a313831323633373638360a1f0a196163636f756e74732e61646472657373426f6f6b41646d696e120235350a2b0a246865646572612e7265636f726453747265616d2e736964656361724d617853697a654d6212033235360a300a257363686564756c696e672e6d617845787069726174696f6e4675747572655365636f6e64731207353335363830300a2a0a21636f6e7472616374732e656e666f7263654372656174696f6e5468726f74746c65120566616c73650a1c0a14746f6b656e732e6d61785065724163636f756e741204313030300a1c0a1566696c65732e686170695065726d697373696f6e7312033132320a2d0a286865646572612e7265636f726453747265616d2e7369676e617475726546696c6556657273696f6e1201360a200a19746f6b656e732e6e6674732e6d6178517565727952616e676512033130300a1d0a176c65646765722e7472616e73666572732e6d61784c656e120231300a230a1a6163636f756e74732e626c6f636b6c6973742e656e61626c6564120566616c73650a200a1b72617465732e6d69646e69676874436865636b496e74657276616c1201310a2f0a2a74726163656162696c6974792e6d696e46726565546f557365644761735468726f74746c65526174696f1201390a340a266865646572612e7265636f726453747265616d2e73747265616d46696c6550726f6475636572120a636f6e63757272656e740a220a1c746f6b656e732e6e6674732e6d6178426174636853697a6557697065120231300a330a2b6865646572612e7265636f726453747265616d2e636f6d707265737346696c65734f6e4372656174696f6e1204747275650a1a0a127374616b696e672e706572696f644d696e731204313434300a240a1b6175746f72656e65772e6772616e744672656552656e6577616c73120566616c73650a2b0a1e636f6e7472616374732e6d61784b7650616972732e61676772656761746512093530303030303030300a220a1c746f6b656e732e6e6674732e6d6178426174636853697a654d696e74120231300a240a1d7374616b696e672e73756d4f66436f6e73656e7375735765696768747312033530300a210a1b746f6b656e732e6d6178437573746f6d46656573416c6c6f776564120231300a1c0a146c617a794372656174696f6e2e656e61626c65641204747275650a1b0a10746f6b656e732e6d61784e756d6265721207313030303030300a1d0a126163636f756e74732e6d61784e756d6265721207353030303030300a240a1c636f6e7472616374732e6974656d697a6553746f72616765466565731204747275650a230a1b6865646572612e616c6c6f77616e6365732e6973456e61626c65641204747275650a380a23626f6f7473747261702e6665655363686564756c65734a736f6e2e7265736f7572636512116665655363686564756c65732e6a736f6e0a2b0a246c65646765722e7265636f7264732e6d6178517565727961626c6542794163636f756e7412033138300a220a16636f6e7472616374732e6d6178476173506572536563120831353030303030300a300a28636f6e7472616374732e707265636f6d70696c652e6578706f72745265636f7264526573756c74731204747275650a1b0a156175746f52656e65772e746172676574547970657312025b5d0a270a22636f6e7472616374732e6d61784e756d5769746848617069536967734163636573731201300a280a20636f6e7472616374732e7468726f74746c652e7468726f74746c6542794761731204747275650a230a17746f6b656e732e6d617841676772656761746552656c73120831303030303030300a260a20626f6f7473747261702e72617465732e63757272656e7443656e744571756976120231320a290a236865646572612e7472616e73616374696f6e2e6d696e56616c69644475726174696f6e120231350a510a12636f6e7472616374732e7369646563617273123b5b434f4e54524143545f53544154455f4348414e47452c20434f4e54524143545f414354494f4e2c20434f4e54524143545f42595445434f44455d0a1b0a156c65646765722e66756e64696e674163636f756e74120239380a230a1a7363686564756c696e672e6c6f6e675465726d456e61626c6564120566616c73650a220a1a6c65646765722e6d61784175746f4173736f63696174696f6e731204353030300a1e0a16636f6e7472616374";
}