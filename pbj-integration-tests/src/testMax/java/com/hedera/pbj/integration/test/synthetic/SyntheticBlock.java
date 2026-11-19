// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.TssSignedBlockProof;
import com.hedera.hapi.block.stream.output.BlockFooter;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.QueuePopChange;
import com.hedera.hapi.block.stream.output.QueuePushChange;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountTokenAssociation;
import com.hedera.hapi.node.base.BlockHashAlgorithm;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntries;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntry;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A deterministic synthetic block, built only from HAPI messages, with the structure and value sizes of an external
 * block-stream workload (an exchange): per transaction about 9.7 items, 680 encoded bytes and 12.6 state changes, with
 * small ids, 6-7 byte balances and 5-byte NFT serials. The sample {@code .blk.gz} blocks have a different mix (large
 * opaque transactions, big accounts and tokens), which is why results on them and on that workload disagree.
 *
 * <p>The block is generated as a flat stream of values ({@code long}s, and ranges of a byte heap for bytes values). Model
 * objects ({@link #build}), the generated writers ({@link #write}) and the reference codec all read the same stream, so
 * producing the block from its values can be measured all three ways. The stream of one item:
 *
 * <pre>
 * kind (the BlockItem field number), then by kind:
 *   1  BLOCK_HEADER        number, seconds, nanos
 *   4  SIGNED_TRANSACTION  offset, length
 *   5  TRANSACTION_RESULT  status, seconds, nanos, fee (0: absent),
 *                          transfers (0: no transfer list), [account, amount] * transfers,
 *                          token lists, [token, transfers, [account, amount] * transfers] * token lists
 *   7  STATE_CHANGES       seconds, nanos, changes, [state id, operation, values of the operation] * changes
 *   12 BLOCK_FOOTER        [offset, length] * 3
 *   9  BLOCK_PROOF         block, offset, length
 * operations (OP_*) and their values:
 *   OP_TOKEN_REL      account, token, balance, automatic association (0/1)
 *   OP_ACCOUNT        account, balance
 *   OP_NFT            token, serial, owner, spender, mint seconds, mint nanos
 *   OP_NFT_PRIVATE    token, serial, owner, mint seconds, mint nanos
 *   OP_NFT_DELETE     token, serial
 *   OP_SINGLETON      value
 *   OP_RECEIPT_PUSH   node id, valid start seconds, valid start nanos, account, status
 *   OP_QUEUE_POP      (none)
 * </pre>
 *
 * <p>Ids have shard and realm 0, as on mainnet, so only their numbers are set.
 */
public final class SyntheticBlock {
    /** Transactions per block, as in the external workload. */
    public static final int TRANSACTIONS = 20_000;

    public static final int KIND_BLOCK_HEADER = 1;
    public static final int KIND_SIGNED_TRANSACTION = 4;
    public static final int KIND_TRANSACTION_RESULT = 5;
    public static final int KIND_STATE_CHANGES = 7;
    public static final int KIND_BLOCK_PROOF = 9;
    public static final int KIND_BLOCK_FOOTER = 12;

    public static final int OP_TOKEN_REL = 0;
    public static final int OP_ACCOUNT = 1;
    public static final int OP_NFT = 2;
    public static final int OP_NFT_PRIVATE = 3;
    public static final int OP_NFT_DELETE = 4;
    public static final int OP_SINGLETON = 5;
    public static final int OP_RECEIPT_PUSH = 6;
    public static final int OP_QUEUE_POP = 7;

    /** {@code StateIdentifier} values of the states changed. */
    static final int STATE_ID_ENTITY_ID = 1;

    static final int STATE_ID_ACCOUNTS = 2;
    static final int STATE_ID_NFTS = 8;
    static final int STATE_ID_TOKEN_RELS = 9;
    static final int STATE_ID_TRANSACTION_RECEIPTS = 126;

    /** {@code ResponseCodeEnum.SUCCESS}. */
    static final int SUCCESS = 22;

    /** Mean state changes per transaction of each operation (index OP_*), from the external workload. */
    private static final double[] OP_MEANS = {2.94 + 0.78, 1.00, 1.40, 1.14, 2.43, 0.92, 1.00, 1.00};
    /** The share of {@code OP_TOKEN_REL} changes that are fee state (automatic association set). */
    private static final double FEE_STATE_SHARE = 0.78 / (2.94 + 0.78);
    /** Mean per transaction of balance-change results, and of each kind of order book result. */
    private static final double BALANCE_CHANGES = 2.94;

    private static final double ORDERS_ADDED = 1.16;

    private static final double TRADES = 0.51;
    private static final double ORDERS_DELETED = 0.96;
    /** The share of transactions that are batches, and how many times the average they carry. */
    private static final double BATCH_SHARE = 0.12;

    private static final double BATCH_FACTOR = 5;
    /** The multiplier of an ordinary transaction, so that the means hold over all of them. */
    private static final double ORDINARY = 1 / (1 - BATCH_SHARE + BATCH_SHARE * BATCH_FACTOR);

    // ----------------------------------------------------------------------------------------------- the values

    final long[] values;
    final byte[] heap;
    /** The number of block items. */
    public final int items;
    /** The number of state changes over the block. */
    public final int stateChanges;

    private SyntheticBlock(final long[] values, final byte[] heap, final int items, final int stateChanges) {
        this.values = values;
        this.heap = heap;
        this.items = items;
        this.stateChanges = stateChanges;
    }

    /** The heap that bytes values are ranges of. */
    public byte[] heap() {
        return heap;
    }

    /** A new cursor at the first item's values. */
    public Cursor cursor() {
        return new Cursor();
    }

    /** Reads the value stream in order. Reusable: {@link #reset()} goes back to the start. */
    public final class Cursor {
        private int next;

        /** Go back to the first item's values. */
        public void reset() {
            next = 0;
        }

        /** The next value. */
        public long l() {
            return values[next++];
        }

        /** The next value, as an int. */
        public int i() {
            return (int) values[next++];
        }

        /** The heap that bytes values are ranges of. */
        public byte[] heap() {
            return heap;
        }

        Bytes bytes() {
            final int offset = i();
            return Bytes.wrap(heap, offset, i());
        }
    }

    // ------------------------------------------------------------------------------------------------- generate

    /** Generate the block of {@value #TRANSACTIONS} transactions. */
    public static SyntheticBlock generate() {
        return generate(TRANSACTIONS, 20_26_10_30L);
    }

    /**
     * Generate a block.
     *
     * @param transactions the number of transactions
     * @param seed the random seed; the same seed gives the same block
     */
    public static SyntheticBlock generate(final int transactions, final long seed) {
        return new Generator(new Random(seed)).block(transactions);
    }

    private static final class Generator {
        final Random random;
        long[] values = new long[1 << 20];
        int count;
        byte[] heap = new byte[1 << 20];
        int heapLength;
        int items;
        int stateChanges;
        /** The hot accounts, which most transactions touch. */
        final long[] hotAccounts = new long[50];
        /** NFT serials (order ids) are sequential. */
        long nextSerial = 7_000_000_000L;

        long seconds = 1_800_000_000L;

        Generator(final Random random) {
            this.random = random;
            for (int i = 0; i < hotAccounts.length; i++) {
                hotAccounts[i] = 1000 + random.nextInt(9000);
            }
        }

        SyntheticBlock block(final int transactions) {
            item(KIND_BLOCK_HEADER);
            add(transactions);
            add(seconds);
            add(random.nextInt(1_000_000_000));
            for (int t = 0; t < transactions; t++) {
                transaction();
            }
            item(KIND_BLOCK_FOOTER);
            for (int i = 0; i < 3; i++) {
                bytes(48);
            }
            item(KIND_BLOCK_PROOF);
            add(transactions);
            bytes(96);
            return new SyntheticBlock(
                    Arrays.copyOf(values, count), Arrays.copyOf(heap, heapLength), items, stateChanges);
        }

        void transaction() {
            if (random.nextInt(4) == 0) {
                seconds++;
            }
            final double scale = random.nextDouble() < BATCH_SHARE ? ORDINARY * BATCH_FACTOR : ORDINARY;
            final long nanos = random.nextInt(1_000_000_000);
            // the signed transaction, kept opaque
            item(KIND_SIGNED_TRANSACTION);
            bytes(43 + random.nextInt(15));
            // its result
            item(KIND_TRANSACTION_RESULT);
            add(SUCCESS);
            add(seconds);
            add(nanos);
            add(fee());
            add(0);
            add(0);
            // balance changes: a token transfer between two accounts
            for (int i = count(BALANCE_CHANGES * scale); i > 0; i--) {
                result(nanos, false);
                add(1);
                add(token());
                add(2);
                final long quantity = quantity();
                add(account());
                add(quantity);
                add(account());
                add(-quantity);
            }
            // order book items: orders added and trades change one account's balance, deleted orders none
            for (int i = count(ORDERS_ADDED + TRADES); i > 0; i--) {
                final boolean trade = random.nextDouble() < TRADES / (ORDERS_ADDED + TRADES);
                result(nanos, true);
                add(1);
                add(token());
                add(1);
                add(account());
                add(trade ? price() : quantity());
            }
            for (int i = count(ORDERS_DELETED); i > 0; i--) {
                result(nanos, true);
                add(0);
            }
            // the anonymous result: a transfer list of 3
            item(KIND_TRANSACTION_RESULT);
            add(SUCCESS);
            add(seconds);
            add(nanos);
            add(0);
            add(3);
            for (int i = 0; i < 3; i++) {
                add(account());
                add(i == 2 ? -quantity() : quantity());
            }
            add(0);
            stateChanges(nanos, scale);
        }

        void result(final long nanos, final boolean fee) {
            item(KIND_TRANSACTION_RESULT);
            add(SUCCESS);
            add(seconds);
            add(nanos);
            add(fee ? fee() : 0);
            add(0);
        }

        void stateChanges(final long nanos, final double scale) {
            item(KIND_STATE_CHANGES);
            add(seconds);
            add(nanos);
            final int[] counts = new int[OP_MEANS.length];
            int total = 0;
            for (int op = 0; op < counts.length; op++) {
                // the receipt queue is pushed and popped once per transaction, batch or not
                counts[op] = op >= OP_RECEIPT_PUSH ? 1 : count(OP_MEANS[op] * scale);
                total += counts[op];
            }
            add(total);
            stateChanges += total;
            for (int op = 0; op < counts.length; op++) {
                for (int i = 0; i < counts[op]; i++) {
                    change(op);
                }
            }
        }

        void change(final int op) {
            add(
                    switch (op) {
                        case OP_TOKEN_REL -> STATE_ID_TOKEN_RELS;
                        case OP_ACCOUNT -> STATE_ID_ACCOUNTS;
                        case OP_SINGLETON -> STATE_ID_ENTITY_ID;
                        case OP_RECEIPT_PUSH, OP_QUEUE_POP -> STATE_ID_TRANSACTION_RECEIPTS;
                        default -> STATE_ID_NFTS;
                    });
            add(op);
            switch (op) {
                case OP_TOKEN_REL -> {
                    add(account());
                    add(token());
                    add(balance());
                    add(random.nextDouble() < FEE_STATE_SHARE ? 1 : 0);
                }
                case OP_ACCOUNT -> {
                    add(account());
                    add(balance());
                }
                case OP_NFT -> {
                    add(token());
                    add(nextSerial++);
                    add(account());
                    add(account());
                    add(seconds);
                    add(random.nextInt(1_000_000_000));
                }
                case OP_NFT_PRIVATE -> {
                    add(token());
                    add(nextSerial - 1 - random.nextInt(1000));
                    add(account());
                    add(seconds);
                    add(random.nextInt(1_000_000_000));
                }
                case OP_NFT_DELETE -> {
                    add(token());
                    add(nextSerial - 1 - random.nextInt(1000));
                }
                case OP_SINGLETON -> add(nextSerial);
                case OP_RECEIPT_PUSH -> {
                    add(3 + random.nextInt(28));
                    add(seconds);
                    add(random.nextInt(1_000_000_000));
                    add(account());
                    add(SUCCESS);
                }
                default -> {}
            }
        }

        // ------------------------------------------------------------------------------------------- value sizes

        /** A count with the given mean: its floor, plus one with the probability of its fraction. */
        int count(final double mean) {
            final int whole = (int) mean;
            return whole + (random.nextDouble() < mean - whole ? 1 : 0);
        }

        /** A number whose varint is {@code bytes} long. */
        long varintOf(final int bytes) {
            final long low = bytes == 1 ? 1 : 1L << (7 * (bytes - 1));
            final long high = 1L << (7 * bytes);
            return low + (long) (random.nextDouble() * (high - low));
        }

        /** Pick a size from cumulative shares, starting at {@code first} bytes. */
        int size(final int first, final double... cumulative) {
            final double r = random.nextDouble();
            for (int i = 0; i < cumulative.length; i++) {
                if (r < cumulative[i]) {
                    return first + i;
                }
            }
            return first + cumulative.length;
        }

        /**
         * Accounts: about 2 million, 50 of them hot (40% of uses, 2 varint bytes); overall 2 varint bytes 92% of the
         * time and 3 bytes 8%.
         */
        long account() {
            final double hot = 0.4;
            if (random.nextDouble() < hot) {
                return hotAccounts[random.nextInt(hotAccounts.length)];
            }
            return random.nextDouble() < (0.92 - hot) / (1 - hot) ? varintOf(2) : 16_384 + random.nextInt(1_983_616);
        }

        /** Tokens (asset ids): 1 varint byte 78% of the time, 2 bytes 22%. */
        long token() {
            return varintOf(size(1, 0.78));
        }

        /** Balances: 5 varint bytes 11%, 6 bytes 41%, 7 bytes 30%, 8 bytes 18%. */
        long balance() {
            return varintOf(size(5, 0.11, 0.52, 0.82));
        }

        /** Quantities, as zigzag {@code sint64}: 2 to 5 bytes, mostly 3 or 4. */
        long quantity() {
            return varintOf(size(2, 0.15, 0.5, 0.85)) >>> 1;
        }

        /** Prices, as zigzag {@code sint64}: 3 to 6 bytes. */
        long price() {
            return varintOf(size(3, 0.25, 0.5, 0.75)) >>> 1;
        }

        /** Fees: 4 or 5 varint bytes. */
        long fee() {
            return varintOf(size(4, 0.5));
        }

        // ------------------------------------------------------------------------------------------------- stream

        void item(final int kind) {
            items++;
            add(kind);
        }

        void add(final long value) {
            if (count == values.length) {
                values = Arrays.copyOf(values, count * 2);
            }
            values[count++] = value;
        }

        /** Random bytes on the heap, added as their offset and length. */
        void bytes(final int length) {
            if (heapLength + length > heap.length) {
                heap = Arrays.copyOf(heap, Math.max(heap.length * 2, heapLength + length));
            }
            for (int i = 0; i < length; i++) {
                heap[heapLength + i] = (byte) random.nextInt();
            }
            add(heapLength);
            add(length);
            heapLength += length;
        }
    }

    // --------------------------------------------------------------------------------------- model objects

    /** Build the next item's model objects, as a node does today. */
    public static BlockItem build(final Cursor c) {
        return switch (c.i()) {
            case KIND_BLOCK_HEADER ->
                BlockItem.newBuilder()
                        .blockHeader(BlockHeader.newBuilder()
                                .hapiProtoVersion(VERSION)
                                .softwareVersion(VERSION)
                                .number(c.l())
                                .blockTimestamp(timestamp(c))
                                .hashAlgorithm(BlockHashAlgorithm.SHA2_384)
                                .build())
                        .build();
            case KIND_SIGNED_TRANSACTION ->
                BlockItem.newBuilder().signedTransaction(c.bytes()).build();
            case KIND_TRANSACTION_RESULT ->
                BlockItem.newBuilder().transactionResult(transactionResult(c)).build();
            case KIND_STATE_CHANGES ->
                BlockItem.newBuilder().stateChanges(stateChanges(c)).build();
            case KIND_BLOCK_FOOTER ->
                BlockItem.newBuilder()
                        .blockFooter(BlockFooter.newBuilder()
                                .previousBlockRootHash(c.bytes())
                                .rootHashOfAllBlockHashesTree(c.bytes())
                                .startOfBlockStateRootHash(c.bytes())
                                .build())
                        .build();
            case KIND_BLOCK_PROOF ->
                BlockItem.newBuilder()
                        .blockProof(BlockProof.newBuilder()
                                .block(c.l())
                                .signedBlockProof(TssSignedBlockProof.newBuilder()
                                        .blockSignature(c.bytes())
                                        .build())
                                .build())
                        .build();
            default -> throw new IllegalStateException("Unknown item kind");
        };
    }

    /** Build every item of the block, as model objects. */
    public List<BlockItem> buildAll() {
        final Cursor c = cursor();
        final List<BlockItem> list = new ArrayList<>(items);
        for (int i = 0; i < items; i++) {
            list.add(build(c));
        }
        return list;
    }

    /** The HAPI and software version in block headers. */
    static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(66).patch(0).build();

    private static Timestamp timestamp(final Cursor c) {
        return Timestamp.newBuilder().seconds(c.l()).nanos(c.i()).build();
    }

    private static AccountID account(final long number) {
        return AccountID.newBuilder().accountNum(number).build();
    }

    private static TokenID token(final long number) {
        return TokenID.newBuilder().tokenNum(number).build();
    }

    private static List<AccountAmount> accountAmounts(final Cursor c, final int count) {
        final List<AccountAmount> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(AccountAmount.newBuilder()
                    .accountID(account(c.l()))
                    .amount(c.l())
                    .build());
        }
        return list;
    }

    private static TransactionResult transactionResult(final Cursor c) {
        final TransactionResult.Builder b = TransactionResult.newBuilder()
                .status(ResponseCodeEnum.fromProtobufOrdinal(c.i()))
                .consensusTimestamp(timestamp(c))
                .transactionFeeCharged(c.l());
        final int transfers = c.i();
        if (transfers > 0) {
            b.transferList(TransferList.newBuilder()
                    .accountAmounts(accountAmounts(c, transfers))
                    .build());
        }
        final int lists = c.i();
        if (lists > 0) {
            final List<TokenTransferList> tokenLists = new ArrayList<>(lists);
            for (int i = 0; i < lists; i++) {
                final TokenID token = token(c.l());
                tokenLists.add(TokenTransferList.newBuilder()
                        .token(token)
                        .transfers(accountAmounts(c, c.i()))
                        .build());
            }
            b.tokenTransferLists(tokenLists);
        }
        return b.build();
    }

    private static NftID nftId(final Cursor c) {
        final TokenID token = token(c.l());
        return NftID.newBuilder().tokenId(token).serialNumber(c.l()).build();
    }

    private static StateChanges stateChanges(final Cursor c) {
        final Timestamp consensus = timestamp(c);
        final int count = c.i();
        final List<StateChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final StateChange.Builder change = StateChange.newBuilder().stateId(c.i());
            final int op = c.i();
            switch (op) {
                case OP_TOKEN_REL -> {
                    final AccountID account = account(c.l());
                    final TokenID token = token(c.l());
                    change.mapUpdate(MapUpdateChange.newBuilder()
                            .key(MapChangeKey.newBuilder()
                                    .tokenRelationshipKey(AccountTokenAssociation.newBuilder()
                                            .accountId(account)
                                            .tokenId(token)
                                            .build())
                                    .build())
                            .value(MapChangeValue.newBuilder()
                                    .tokenRelationValue(TokenRelation.newBuilder()
                                            .tokenId(token)
                                            .accountId(account)
                                            .balance(c.l())
                                            .automaticAssociation(c.l() != 0)
                                            .build())
                                    .build())
                            .build());
                }
                case OP_ACCOUNT ->
                    change.mapUpdate(MapUpdateChange.newBuilder()
                            .key(MapChangeKey.newBuilder()
                                    .accountIdKey(account(c.l()))
                                    .build())
                            .value(MapChangeValue.newBuilder()
                                    .tokenRelationValue(TokenRelation.newBuilder()
                                            .balance(c.l())
                                            .build())
                                    .build())
                            .build());
                case OP_NFT, OP_NFT_PRIVATE -> {
                    final boolean full = op == OP_NFT;
                    final NftID id = nftId(c);
                    final Nft.Builder nft = full ? Nft.newBuilder().nftId(id) : Nft.newBuilder();
                    nft.ownerId(account(c.l()));
                    if (full) {
                        nft.spenderId(account(c.l()));
                    }
                    nft.mintTime(timestamp(c));
                    change.mapUpdate(MapUpdateChange.newBuilder()
                            .key(MapChangeKey.newBuilder().nftIdKey(id).build())
                            .value(MapChangeValue.newBuilder()
                                    .nftValue(nft.build())
                                    .build())
                            .build());
                }
                case OP_NFT_DELETE ->
                    change.mapDelete(MapDeleteChange.newBuilder()
                            .key(MapChangeKey.newBuilder().nftIdKey(nftId(c)).build())
                            .build());
                case OP_SINGLETON ->
                    change.singletonUpdate(SingletonUpdateChange.newBuilder()
                            .entityNumberValue(c.l())
                            .build());
                case OP_RECEIPT_PUSH -> {
                    final long node = c.l();
                    final Timestamp validStart = timestamp(c);
                    final AccountID payer = account(c.l());
                    change.queuePush(QueuePushChange.newBuilder()
                            .transactionReceiptEntriesElement(TransactionReceiptEntries.newBuilder()
                                    .entries(List.of(TransactionReceiptEntry.newBuilder()
                                            .nodeId(node)
                                            .transactionId(TransactionID.newBuilder()
                                                    .transactionValidStart(validStart)
                                                    .accountID(payer)
                                                    .build())
                                            .status(ResponseCodeEnum.fromProtobufOrdinal(c.i()))
                                            .build()))
                                    .build())
                            .build());
                }
                default -> change.queuePop(QueuePopChange.DEFAULT);
            }
            changes.add(change.build());
        }
        return StateChanges.newBuilder()
                .consensusTimestamp(consensus)
                .stateChanges(changes)
                .build();
    }

    // -------------------------------------------------------------------------------------------------- writers

    /**
     * Write the next item with the generated writers, straight from its values.
     *
     * @param c the cursor, at the item's values
     * @param w an open item writer, with nothing written yet
     */
    public static void write(final Cursor c, final BlockItem.Writer w) {
        switch (c.i()) {
            case KIND_BLOCK_HEADER -> {
                final BlockHeader.Writer header = w.blockHeader();
                header.hapiProtoVersion().minor(66);
                header.softwareVersion().minor(66);
                header.number(c.l());
                writeTimestamp(c, header.blockTimestamp());
                header.hashAlgorithm(BlockHashAlgorithm.SHA2_384);
            }
            case KIND_SIGNED_TRANSACTION -> w.signedTransaction(c.heap(), c.i(), c.i());
            case KIND_TRANSACTION_RESULT -> writeTransactionResult(c, w.transactionResult());
            case KIND_STATE_CHANGES -> writeStateChanges(c, w.stateChanges());
            case KIND_BLOCK_FOOTER -> {
                final BlockFooter.Writer footer = w.blockFooter();
                footer.previousBlockRootHash(c.heap(), c.i(), c.i());
                footer.rootHashOfAllBlockHashesTree(c.heap(), c.i(), c.i());
                footer.startOfBlockStateRootHash(c.heap(), c.i(), c.i());
            }
            case KIND_BLOCK_PROOF -> {
                final BlockProof.Writer proof = w.blockProof().block(c.l());
                proof.signedBlockProof().blockSignature(c.heap(), c.i(), c.i());
            }
            default -> throw new IllegalStateException("Unknown item kind");
        }
    }

    private static void writeTimestamp(final Cursor c, final Timestamp.Writer w) {
        w.seconds(c.l()).nanos(c.i());
    }

    private static void writeTransactionResult(final Cursor c, final TransactionResult.Writer w) {
        w.statusProtoOrdinal(c.i());
        writeTimestamp(c, w.consensusTimestamp());
        w.transactionFeeCharged(c.l());
        final int transfers = c.i();
        if (transfers > 0) {
            final TransferList.Writer list = w.transferList();
            for (int i = 0; i < transfers; i++) {
                final AccountAmount.Writer amount = list.addAccountAmounts();
                amount.accountID().accountNum(c.l());
                amount.amount(c.l());
            }
        }
        final int lists = c.i();
        for (int i = 0; i < lists; i++) {
            final TokenTransferList.Writer list = w.addTokenTransferLists();
            list.token().tokenNum(c.l());
            final int count = c.i();
            for (int j = 0; j < count; j++) {
                final AccountAmount.Writer amount = list.addTransfers();
                amount.accountID().accountNum(c.l());
                amount.amount(c.l());
            }
        }
    }

    private static void writeStateChanges(final Cursor c, final StateChanges.Writer w) {
        writeTimestamp(c, w.consensusTimestamp());
        final int count = c.i();
        for (int i = 0; i < count; i++) {
            final StateChange.Writer change = w.addStateChanges().stateId(c.i());
            final int op = c.i();
            switch (op) {
                case OP_TOKEN_REL -> {
                    final long account = c.l();
                    final long token = c.l();
                    final MapUpdateChange.Writer update = change.mapUpdate();
                    final AccountTokenAssociation.Writer key = update.key().tokenRelationshipKey();
                    key.accountId().accountNum(account);
                    key.tokenId().tokenNum(token);
                    final TokenRelation.Writer relation = update.value().tokenRelationValue();
                    relation.tokenId().tokenNum(token);
                    relation.accountId().accountNum(account);
                    relation.balance(c.l()).automaticAssociation(c.l() != 0);
                }
                case OP_ACCOUNT -> {
                    final MapUpdateChange.Writer update = change.mapUpdate();
                    update.key().accountIdKey().accountNum(c.l());
                    update.value().tokenRelationValue().balance(c.l());
                }
                case OP_NFT, OP_NFT_PRIVATE -> {
                    final long token = c.l();
                    final long serial = c.l();
                    final MapUpdateChange.Writer update = change.mapUpdate();
                    final NftID.Writer key = update.key().nftIdKey();
                    key.tokenId().tokenNum(token);
                    key.serialNumber(serial);
                    final Nft.Writer nft = update.value().nftValue();
                    if (op == OP_NFT) {
                        final NftID.Writer id = nft.nftId();
                        id.tokenId().tokenNum(token);
                        id.serialNumber(serial);
                    }
                    nft.ownerId().accountNum(c.l());
                    if (op == OP_NFT) {
                        nft.spenderId().accountNum(c.l());
                    }
                    writeTimestamp(c, nft.mintTime());
                }
                case OP_NFT_DELETE -> {
                    final NftID.Writer key = change.mapDelete().key().nftIdKey();
                    key.tokenId().tokenNum(c.l());
                    key.serialNumber(c.l());
                }
                case OP_SINGLETON -> change.singletonUpdate().entityNumberValue(c.l());
                case OP_RECEIPT_PUSH -> {
                    final TransactionReceiptEntry.Writer entry = change.queuePush()
                            .transactionReceiptEntriesElement()
                            .addEntries();
                    entry.nodeId(c.l());
                    final TransactionID.Writer id = entry.transactionId();
                    writeTimestamp(c, id.transactionValidStart());
                    id.accountID().accountNum(c.l());
                    entry.statusProtoOrdinal(c.i());
                }
                default -> change.queuePop();
            }
        }
    }

    // --------------------------------------------------------------------------------------------- transactions

    /**
     * {@value #TRANSACTIONS} encoded {@code SignedTransaction}s, for transaction decode: one signature pair each (a
     * 3-byte public key prefix, then a 65-byte ECDSA secp256k1 signature 80% of the time or a 64-byte ed25519 one),
     * and bodies of a token transfer between two accounts, or for about 12% of them an atomic batch of 2 to 20 inner
     * transactions.
     */
    public static List<Bytes> transactions() {
        return transactions(TRANSACTIONS, 20_26_10_31L);
    }

    /**
     * Encoded {@code SignedTransaction}s.
     *
     * @param count how many
     * @param seed the random seed; the same seed gives the same transactions
     */
    public static List<Bytes> transactions(final int count, final long seed) {
        final Generator g = new Generator(new Random(seed));
        final List<Bytes> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final TransactionBody.Builder body = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .transactionValidStart(Timestamp.newBuilder()
                                    .seconds(g.seconds + i / 4)
                                    .nanos(g.random.nextInt(1_000_000_000))
                                    .build())
                            .accountID(account(g.account()))
                            .build())
                    .nodeAccountID(account(3 + g.random.nextInt(28)))
                    .transactionFee(g.fee());
            if (g.random.nextDouble() < BATCH_SHARE) {
                final int inner = 2 + g.random.nextInt(19);
                final List<Bytes> transactions = new ArrayList<>(inner);
                for (int j = 0; j < inner; j++) {
                    transactions.add(signed(g, transfer(g, body).build()));
                }
                body.atomicBatch(AtomicBatchTransactionBody.newBuilder()
                        .transactions(transactions)
                        .build());
            } else {
                transfer(g, body);
            }
            list.add(signed(g, body.build()));
        }
        return list;
    }

    private static TransactionBody.Builder transfer(final Generator g, final TransactionBody.Builder body) {
        final long quantity = g.quantity();
        return body.cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(List.of(TokenTransferList.newBuilder()
                        .token(token(g.token()))
                        .transfers(List.of(
                                AccountAmount.newBuilder()
                                        .accountID(account(g.account()))
                                        .amount(-quantity)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(account(g.account()))
                                        .amount(quantity)
                                        .build()))
                        .build()))
                .build());
    }

    private static Bytes signed(final Generator g, final TransactionBody body) {
        final SignaturePair.Builder pair = SignaturePair.newBuilder().pubKeyPrefix(random(g, 3));
        if (g.random.nextDouble() < 0.8) {
            pair.ecdsaSecp256k1(random(g, 65));
        } else {
            pair.ed25519(random(g, 64));
        }
        return SignedTransaction.PROTOBUF.toBytes(SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .sigMap(SignatureMap.newBuilder().sigPair(List.of(pair.build())).build())
                .build());
    }

    private static Bytes random(final Generator g, final int length) {
        final byte[] bytes = new byte[length];
        g.random.nextBytes(bytes);
        return Bytes.wrap(bytes);
    }
}
