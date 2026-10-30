// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.input.EventHeader;
import com.hedera.hapi.block.stream.input.ParentEventReference;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The field values of the most common block items, extracted once from parsed items into flat arrays, as a node has
 * them while executing transactions: numbers in a {@code long[]}, bytes as ranges of one {@code byte[]}, strings as
 * references. Two consumers read the values in the same order: {@link #build} constructs model objects with their
 * builders, as a node does today before encoding them, and {@link #write} writes the same items through the generated
 * writers, with no objects.
 *
 * <p>The extraction is hand-written for the item kinds that make up almost all of a block: signed transactions,
 * transaction results, event headers, and state changes that are map updates of accounts, token relations, NFTs, topics
 * and contract storage slots, or map deletes. Items of other kinds, or with fields it doesn't cover, are left out; every
 * extracted item is checked to be rebuilt and rewritten byte for byte.
 */
public final class BlockItemValues {
    private long[] longs = new long[1 << 16];
    private int longCount;
    private Object[] strings = new Object[1024];
    private int stringCount;
    private byte[] heap = new byte[1 << 20];
    private int heapLength;
    private final List<BlockItem> items = new ArrayList<>();

    /** Thrown while extracting an item that has a field the extraction doesn't cover. */
    private static final class Unsupported extends RuntimeException {
        Unsupported(final String what) {
            super(what, null, false, false);
        }
    }

    /**
     * Extract the values of every supported item.
     *
     * @param all the items of a block
     * @return the values, of the items in {@link #items()}
     */
    public static BlockItemValues extract(final List<BlockItem> all) {
        final BlockItemValues values = new BlockItemValues();
        for (final BlockItem item : all) {
            final int longsBefore = values.longCount;
            final int stringsBefore = values.stringCount;
            final int heapBefore = values.heapLength;
            try {
                values.item(item);
                values.items.add(item);
            } catch (final Unsupported e) {
                values.longCount = longsBefore;
                values.stringCount = stringsBefore;
                values.heapLength = heapBefore;
            }
        }
        values.longs = Arrays.copyOf(values.longs, values.longCount);
        values.heap = Arrays.copyOf(values.heap, values.heapLength);
        return values;
    }

    /** @return the items whose values were extracted, in order */
    public List<BlockItem> items() {
        return items;
    }

    /** @return a new cursor at the first item's values */
    public Cursor cursor() {
        return new Cursor();
    }

    /** Reads the values in order. Reusable: {@link #reset()} it to read them again. Allocates nothing. */
    public final class Cursor {
        private int nextLong;
        private int nextString;
        /** The range of the bytes value read last by {@link #range()}. */
        int offset;

        int length;

        /** Go back to the first item's values. */
        public void reset() {
            nextLong = 0;
            nextString = 0;
        }

        long l() {
            return longs[nextLong++];
        }

        int i() {
            return (int) longs[nextLong++];
        }

        boolean z() {
            return longs[nextLong++] != 0;
        }

        String s() {
            return (String) strings[nextString++];
        }

        /** Read a bytes value into {@link #offset} and {@link #length}, ranges of {@link #heap()}. */
        void range() {
            offset = (int) longs[nextLong++];
            length = (int) longs[nextLong++];
        }

        /** Read a bytes value as a new {@link Bytes}, for a model object. */
        Bytes bytes() {
            range();
            return Bytes.wrap(heap, offset, length);
        }

        byte[] heap() {
            return heap;
        }
    }

    // ------------------------------------------------------------------------------------------- extraction

    private void l(final long value) {
        if (longCount == longs.length) {
            longs = Arrays.copyOf(longs, longs.length * 2);
        }
        longs[longCount++] = value;
    }

    private void z(final boolean value) {
        l(value ? 1 : 0);
    }

    private void s(final String value) {
        if (stringCount == strings.length) {
            strings = Arrays.copyOf(strings, strings.length * 2);
        }
        strings[stringCount++] = value;
    }

    private void b(final Bytes value) {
        final int length = (int) value.length();
        while (heapLength + length > heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
        value.writeTo(heap, heapLength);
        l(heapLength);
        l(length);
        heapLength += length;
    }

    private static void require(final boolean condition, final String what) {
        if (!condition) {
            throw new Unsupported(what);
        }
    }

    private void item(final BlockItem item) {
        switch (item.item().kind()) {
            case EVENT_HEADER -> {
                l(2);
                eventHeader(item.eventHeaderOrThrow());
            }
            case SIGNED_TRANSACTION -> {
                l(4);
                b(item.signedTransactionOrThrow());
            }
            case TRANSACTION_RESULT -> {
                l(5);
                transactionResult(item.transactionResultOrThrow());
            }
            case STATE_CHANGES -> {
                l(7);
                stateChanges(item.stateChangesOrThrow());
            }
            default -> throw new Unsupported(item.item().kind().name());
        }
    }

    private void timestamp(final Timestamp t) {
        l(t.seconds());
        l(t.nanos());
    }

    private void optionalTimestamp(final Timestamp t) {
        z(t != null);
        if (t != null) timestamp(t);
    }

    private void accountId(final AccountID id) {
        z(id != null);
        if (id == null) return;
        l(id.shardNum());
        l(id.realmNum());
        if (id.hasAccountNum()) {
            l(1);
            l(id.accountNumOrThrow());
        } else if (id.hasAlias()) {
            l(2);
            b(id.aliasOrThrow());
        } else {
            l(0);
        }
    }

    private void tokenId(final TokenID id) {
        z(id != null);
        if (id == null) return;
        l(id.shardNum());
        l(id.realmNum());
        l(id.tokenNum());
    }

    private void topicId(final TopicID id) {
        z(id != null);
        if (id == null) return;
        l(id.shardNum());
        l(id.realmNum());
        l(id.topicNum());
    }

    private void contractId(final ContractID id) {
        z(id != null);
        if (id == null) return;
        l(id.shardNum());
        l(id.realmNum());
        if (id.hasContractNum()) {
            l(1);
            l(id.contractNumOrThrow());
        } else if (id.hasEvmAddress()) {
            l(2);
            b(id.evmAddressOrThrow());
        } else {
            l(0);
        }
    }

    private void nftId(final NftID id) {
        z(id != null);
        if (id == null) return;
        tokenId(id.tokenId());
        l(id.serialNumber());
    }

    private void key(final Key key) {
        z(key != null);
        if (key == null) return;
        switch (key.key().kind()) {
            case UNSET -> l(0);
            case CONTRACT_ID -> {
                l(1);
                contractId(key.contractIDOrThrow());
            }
            case ED25519 -> {
                l(2);
                b(key.ed25519OrThrow());
            }
            case KEY_LIST -> {
                l(6);
                final List<Key> keys = key.keyListOrThrow().keys();
                l(keys.size());
                keys.forEach(this::key);
            }
            case ECDSA_SECP256K1 -> {
                l(7);
                b(key.ecdsaSecp256k1OrThrow());
            }
            default -> throw new Unsupported("key " + key.key().kind());
        }
    }

    private void account(final Account a) {
        require(a.cryptoAllowances().isEmpty(), "crypto allowances");
        require(a.approveForAllNftAllowances().isEmpty(), "NFT allowances");
        require(a.tokenAllowances().isEmpty(), "token allowances");
        require(a.headPendingAirdropId() == null, "pending airdrops");
        accountId(a.accountId());
        b(a.alias());
        key(a.key());
        l(a.expirationSecond());
        l(a.tinybarBalance());
        s(a.memo());
        z(a.deleted());
        l(a.stakedToMe());
        l(a.stakePeriodStart());
        if (a.hasStakedAccountId()) {
            l(10);
            accountId(a.stakedAccountIdOrThrow());
        } else if (a.hasStakedNodeId()) {
            l(11);
            l(a.stakedNodeIdOrThrow());
        } else {
            l(0);
        }
        z(a.declineReward());
        z(a.receiverSigRequired());
        tokenId(a.headTokenId());
        nftId(a.headNftId());
        l(a.headNftSerialNumber());
        l(a.numberOwnedNfts());
        l(a.maxAutoAssociations());
        l(a.usedAutoAssociations());
        l(a.numberAssociations());
        z(a.smartContract());
        l(a.numberPositiveBalances());
        l(a.ethereumNonce());
        l(a.stakeAtStartOfLastRewardedPeriod());
        accountId(a.autoRenewAccountId());
        l(a.autoRenewSeconds());
        l(a.contractKvPairsNumber());
        l(a.numberTreasuryTitles());
        z(a.expiredAndPendingRemoval());
        b(a.firstContractStorageKey());
        l(a.numberPendingAirdrops());
        l(a.numberHooksInUse());
        l(a.firstHookId());
        l(a.numberEvmHookStorageSlots());
        b(a.delegationAddress());
    }

    private void tokenRelation(final TokenRelation r) {
        tokenId(r.tokenId());
        accountId(r.accountId());
        l(r.balance());
        z(r.frozen());
        z(r.kycGranted());
        z(r.automaticAssociation());
        tokenId(r.previousToken());
        tokenId(r.nextToken());
    }

    private void nft(final Nft n) {
        nftId(n.nftId());
        accountId(n.ownerId());
        accountId(n.spenderId());
        optionalTimestamp(n.mintTime());
        b(n.metadata());
        nftId(n.ownerPreviousNftId());
        nftId(n.ownerNextNftId());
    }

    private void topic(final Topic t) {
        require(t.feeExemptKeyList().isEmpty(), "fee exempt keys");
        require(t.customFees().isEmpty(), "custom fees");
        topicId(t.topicId());
        l(t.sequenceNumber());
        l(t.expirationSecond());
        l(t.autoRenewPeriod());
        accountId(t.autoRenewAccountId());
        z(t.deleted());
        b(t.runningHash());
        s(t.memo());
        key(t.adminKey());
        key(t.submitKey());
        key(t.feeScheduleKey());
    }

    private void slotKey(final SlotKey k) {
        contractId(k.contractID());
        b(k.key());
    }

    private void mapChangeKey(final MapChangeKey k) {
        z(k != null);
        if (k == null) return;
        switch (k.keyChoice().kind()) {
            case UNSET -> l(0);
            case ACCOUNT_ID_KEY -> {
                l(1);
                accountId(k.accountIdKeyOrThrow());
            }
            case NFT_ID_KEY -> {
                l(5);
                nftId(k.nftIdKeyOrThrow());
            }
            case SLOT_KEY_KEY -> {
                l(10);
                slotKey(k.slotKeyKeyOrThrow());
            }
            case TOPIC_ID_KEY -> {
                l(12);
                topicId(k.topicIdKeyOrThrow());
            }
            default -> throw new Unsupported("map key " + k.keyChoice().kind());
        }
    }

    private void mapChangeValue(final MapChangeValue v) {
        z(v != null);
        if (v == null) return;
        switch (v.valueChoice().kind()) {
            case ACCOUNT_VALUE -> {
                l(1);
                account(v.accountValueOrThrow());
            }
            case NFT_VALUE -> {
                l(5);
                nft(v.nftValueOrThrow());
            }
            case SLOT_VALUE_VALUE -> {
                l(9);
                final SlotValue s = v.slotValueValueOrThrow();
                b(s.value());
                b(s.previousKey());
                b(s.nextKey());
            }
            case TOKEN_RELATION_VALUE -> {
                l(12);
                tokenRelation(v.tokenRelationValueOrThrow());
            }
            case TOPIC_VALUE -> {
                l(13);
                topic(v.topicValueOrThrow());
            }
            default -> throw new Unsupported("map value " + v.valueChoice().kind());
        }
    }

    private void stateChanges(final StateChanges changes) {
        optionalTimestamp(changes.consensusTimestamp());
        l(changes.stateChanges().size());
        for (final StateChange change : changes.stateChanges()) {
            l(change.stateId());
            if (change.hasMapUpdate()) {
                l(5);
                final MapUpdateChange update = change.mapUpdateOrThrow();
                mapChangeKey(update.key());
                mapChangeValue(update.value());
                z(update.identical());
            } else if (change.hasMapDelete()) {
                l(6);
                mapChangeKey(change.mapDeleteOrThrow().key());
            } else {
                throw new Unsupported("state change " + change.changeOperation().kind());
            }
        }
    }

    private void accountAmount(final AccountAmount a) {
        require(!a.hasPreTxAllowanceHook() && !a.hasPrePostTxAllowanceHook(), "allowance hooks");
        accountId(a.accountID());
        l(a.amount());
        z(a.isApproval());
    }

    private void transactionResult(final TransactionResult r) {
        require(r.scheduleRef() == null, "schedule");
        require(r.assessedCustomFees().isEmpty(), "custom fees");
        l(r.statusProtoOrdinal());
        optionalTimestamp(r.consensusTimestamp());
        optionalTimestamp(r.parentConsensusTimestamp());
        l(r.transactionFeeCharged());
        z(r.transferList() != null);
        if (r.transferList() != null) {
            l(r.transferList().accountAmounts().size());
            r.transferList().accountAmounts().forEach(this::accountAmount);
        }
        l(r.tokenTransferLists().size());
        for (final TokenTransferList list : r.tokenTransferLists()) {
            tokenId(list.token());
            l(list.transfers().size());
            list.transfers().forEach(this::accountAmount);
            l(list.nftTransfers().size());
            for (final NftTransfer nft : list.nftTransfers()) {
                require(
                        !nft.hasPreTxSenderAllowanceHook()
                                && !nft.hasPrePostTxSenderAllowanceHook()
                                && !nft.hasPreTxReceiverAllowanceHook()
                                && !nft.hasPrePostTxReceiverAllowanceHook(),
                        "allowance hooks");
                accountId(nft.senderAccountID());
                accountId(nft.receiverAccountID());
                l(nft.serialNumber());
                z(nft.isApproval());
            }
            z(list.expectedDecimals() != null);
            if (list.expectedDecimals() != null) l(list.expectedDecimals());
        }
        l(r.automaticTokenAssociations().size());
        for (final TokenAssociation association : r.automaticTokenAssociations()) {
            tokenId(association.tokenId());
            accountId(association.accountId());
        }
        l(r.paidStakingRewards().size());
        r.paidStakingRewards().forEach(this::accountAmount);
        l(r.congestionPricingMultiplier());
        l(r.highVolumePricingMultiplier());
    }

    private void eventHeader(final EventHeader h) {
        final EventCore core = h.eventCore();
        z(core != null);
        if (core != null) {
            l(core.creatorNodeId());
            l(core.birthRound());
            optionalTimestamp(core.timeCreated());
            l(core.coin());
        }
        l(h.parents().size());
        for (final ParentEventReference parent : h.parents()) {
            if (parent.hasEventDescriptor()) {
                l(1);
                final EventDescriptor d = parent.eventDescriptorOrThrow();
                b(d.hash());
                l(d.creatorNodeId());
                l(d.birthRound());
            } else if (parent.hasIndex()) {
                l(2);
                l(parent.indexOrThrow());
            } else {
                l(0);
            }
        }
    }

    // ------------------------------------------------------------------------------ building model objects

    /**
     * Build the next item's model objects from its values, with their builders.
     *
     * @param c the cursor, at the item's values
     * @return the item
     */
    public BlockItem build(final Cursor c) {
        return switch (c.i()) {
            case 2 -> BlockItem.newBuilder().eventHeader(buildEventHeader(c)).build();
            case 4 -> BlockItem.newBuilder().signedTransaction(c.bytes()).build();
            case 5 ->
                BlockItem.newBuilder()
                        .transactionResult(buildTransactionResult(c))
                        .build();
            default -> BlockItem.newBuilder().stateChanges(buildStateChanges(c)).build();
        };
    }

    private static Timestamp buildTimestamp(final Cursor c) {
        return c.z() ? Timestamp.newBuilder().seconds(c.l()).nanos(c.i()).build() : null;
    }

    private static AccountID buildAccountId(final Cursor c) {
        if (!c.z()) return null;
        final AccountID.Builder b = AccountID.newBuilder().shardNum(c.l()).realmNum(c.l());
        switch (c.i()) {
            case 1 -> b.accountNum(c.l());
            case 2 -> b.alias(c.bytes());
            default -> {}
        }
        return b.build();
    }

    private static TokenID buildTokenId(final Cursor c) {
        return c.z()
                ? TokenID.newBuilder()
                        .shardNum(c.l())
                        .realmNum(c.l())
                        .tokenNum(c.l())
                        .build()
                : null;
    }

    private static TopicID buildTopicId(final Cursor c) {
        return c.z()
                ? TopicID.newBuilder()
                        .shardNum(c.l())
                        .realmNum(c.l())
                        .topicNum(c.l())
                        .build()
                : null;
    }

    private static ContractID buildContractId(final Cursor c) {
        if (!c.z()) return null;
        final ContractID.Builder b = ContractID.newBuilder().shardNum(c.l()).realmNum(c.l());
        switch (c.i()) {
            case 1 -> b.contractNum(c.l());
            case 2 -> b.evmAddress(c.bytes());
            default -> {}
        }
        return b.build();
    }

    private static NftID buildNftId(final Cursor c) {
        return c.z()
                ? NftID.newBuilder()
                        .tokenId(buildTokenId(c))
                        .serialNumber(c.l())
                        .build()
                : null;
    }

    private static Key buildKey(final Cursor c) {
        if (!c.z()) return null;
        return switch (c.i()) {
            case 1 -> Key.newBuilder().contractID(buildContractId(c)).build();
            case 2 -> Key.newBuilder().ed25519(c.bytes()).build();
            case 6 -> {
                final int count = c.i();
                final List<Key> keys = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    keys.add(buildKey(c));
                }
                yield Key.newBuilder()
                        .keyList(KeyList.newBuilder().keys(keys).build())
                        .build();
            }
            case 7 -> Key.newBuilder().ecdsaSecp256k1(c.bytes()).build();
            default -> Key.DEFAULT;
        };
    }

    private static Account buildAccount(final Cursor c) {
        final Account.Builder b = Account.newBuilder()
                .accountId(buildAccountId(c))
                .alias(c.bytes())
                .key(buildKey(c))
                .expirationSecond(c.l())
                .tinybarBalance(c.l())
                .memo(c.s())
                .deleted(c.z())
                .stakedToMe(c.l())
                .stakePeriodStart(c.l());
        switch (c.i()) {
            case 10 -> b.stakedAccountId(buildAccountId(c));
            case 11 -> b.stakedNodeId(c.l());
            default -> {}
        }
        return b.declineReward(c.z())
                .receiverSigRequired(c.z())
                .headTokenId(buildTokenId(c))
                .headNftId(buildNftId(c))
                .headNftSerialNumber(c.l())
                .numberOwnedNfts(c.l())
                .maxAutoAssociations(c.i())
                .usedAutoAssociations(c.i())
                .numberAssociations(c.i())
                .smartContract(c.z())
                .numberPositiveBalances(c.i())
                .ethereumNonce(c.l())
                .stakeAtStartOfLastRewardedPeriod(c.l())
                .autoRenewAccountId(buildAccountId(c))
                .autoRenewSeconds(c.l())
                .contractKvPairsNumber(c.i())
                .numberTreasuryTitles(c.i())
                .expiredAndPendingRemoval(c.z())
                .firstContractStorageKey(c.bytes())
                .numberPendingAirdrops(c.l())
                .numberHooksInUse(c.l())
                .firstHookId(c.l())
                .numberEvmHookStorageSlots(c.l())
                .delegationAddress(c.bytes())
                .build();
    }

    private static TokenRelation buildTokenRelation(final Cursor c) {
        return TokenRelation.newBuilder()
                .tokenId(buildTokenId(c))
                .accountId(buildAccountId(c))
                .balance(c.l())
                .frozen(c.z())
                .kycGranted(c.z())
                .automaticAssociation(c.z())
                .previousToken(buildTokenId(c))
                .nextToken(buildTokenId(c))
                .build();
    }

    private static Nft buildNft(final Cursor c) {
        return Nft.newBuilder()
                .nftId(buildNftId(c))
                .ownerId(buildAccountId(c))
                .spenderId(buildAccountId(c))
                .mintTime(buildTimestamp(c))
                .metadata(c.bytes())
                .ownerPreviousNftId(buildNftId(c))
                .ownerNextNftId(buildNftId(c))
                .build();
    }

    private static Topic buildTopic(final Cursor c) {
        return Topic.newBuilder()
                .topicId(buildTopicId(c))
                .sequenceNumber(c.l())
                .expirationSecond(c.l())
                .autoRenewPeriod(c.l())
                .autoRenewAccountId(buildAccountId(c))
                .deleted(c.z())
                .runningHash(c.bytes())
                .memo(c.s())
                .adminKey(buildKey(c))
                .submitKey(buildKey(c))
                .feeScheduleKey(buildKey(c))
                .build();
    }

    private static MapChangeKey buildMapChangeKey(final Cursor c) {
        if (!c.z()) return null;
        return switch (c.i()) {
            case 1 -> MapChangeKey.newBuilder().accountIdKey(buildAccountId(c)).build();
            case 5 -> MapChangeKey.newBuilder().nftIdKey(buildNftId(c)).build();
            case 10 ->
                MapChangeKey.newBuilder()
                        .slotKeyKey(SlotKey.newBuilder()
                                .contractID(buildContractId(c))
                                .key(c.bytes())
                                .build())
                        .build();
            case 12 -> MapChangeKey.newBuilder().topicIdKey(buildTopicId(c)).build();
            default -> MapChangeKey.DEFAULT;
        };
    }

    private static MapChangeValue buildMapChangeValue(final Cursor c) {
        if (!c.z()) return null;
        return switch (c.i()) {
            case 1 -> MapChangeValue.newBuilder().accountValue(buildAccount(c)).build();
            case 5 -> MapChangeValue.newBuilder().nftValue(buildNft(c)).build();
            case 9 ->
                MapChangeValue.newBuilder()
                        .slotValueValue(SlotValue.newBuilder()
                                .value(c.bytes())
                                .previousKey(c.bytes())
                                .nextKey(c.bytes())
                                .build())
                        .build();
            case 12 ->
                MapChangeValue.newBuilder()
                        .tokenRelationValue(buildTokenRelation(c))
                        .build();
            default -> MapChangeValue.newBuilder().topicValue(buildTopic(c)).build();
        };
    }

    private static StateChanges buildStateChanges(final Cursor c) {
        final Timestamp timestamp = buildTimestamp(c);
        final int count = c.i();
        final List<StateChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final StateChange.Builder change = StateChange.newBuilder().stateId(c.i());
            if (c.i() == 5) {
                change.mapUpdate(MapUpdateChange.newBuilder()
                        .key(buildMapChangeKey(c))
                        .value(buildMapChangeValue(c))
                        .identical(c.z())
                        .build());
            } else {
                change.mapDelete(
                        MapDeleteChange.newBuilder().key(buildMapChangeKey(c)).build());
            }
            changes.add(change.build());
        }
        return StateChanges.newBuilder()
                .consensusTimestamp(timestamp)
                .stateChanges(changes)
                .build();
    }

    private static List<AccountAmount> buildAccountAmounts(final Cursor c) {
        final int count = c.i();
        final List<AccountAmount> amounts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            amounts.add(AccountAmount.newBuilder()
                    .accountID(buildAccountId(c))
                    .amount(c.l())
                    .isApproval(c.z())
                    .build());
        }
        return amounts;
    }

    private static TransactionResult buildTransactionResult(final Cursor c) {
        final TransactionResult.Builder b = TransactionResult.newBuilder()
                .status(ResponseCodeEnum.fromProtobufOrdinal(c.i()))
                .consensusTimestamp(buildTimestamp(c))
                .parentConsensusTimestamp(buildTimestamp(c))
                .transactionFeeCharged(c.l());
        if (c.z()) {
            b.transferList(TransferList.newBuilder()
                    .accountAmounts(buildAccountAmounts(c))
                    .build());
        }
        final int listCount = c.i();
        final List<TokenTransferList> lists = new ArrayList<>(listCount);
        for (int i = 0; i < listCount; i++) {
            final TokenTransferList.Builder list =
                    TokenTransferList.newBuilder().token(buildTokenId(c)).transfers(buildAccountAmounts(c));
            final int nftCount = c.i();
            final List<NftTransfer> nfts = new ArrayList<>(nftCount);
            for (int j = 0; j < nftCount; j++) {
                nfts.add(NftTransfer.newBuilder()
                        .senderAccountID(buildAccountId(c))
                        .receiverAccountID(buildAccountId(c))
                        .serialNumber(c.l())
                        .isApproval(c.z())
                        .build());
            }
            list.nftTransfers(nfts);
            if (c.z()) {
                list.expectedDecimals(c.i());
            }
            lists.add(list.build());
        }
        final int associationCount = c.i();
        final List<TokenAssociation> associations = new ArrayList<>(associationCount);
        for (int i = 0; i < associationCount; i++) {
            associations.add(TokenAssociation.newBuilder()
                    .tokenId(buildTokenId(c))
                    .accountId(buildAccountId(c))
                    .build());
        }
        return b.tokenTransferLists(lists)
                .automaticTokenAssociations(associations)
                .paidStakingRewards(buildAccountAmounts(c))
                .congestionPricingMultiplier(c.l())
                .highVolumePricingMultiplier(c.l())
                .build();
    }

    private static EventHeader buildEventHeader(final Cursor c) {
        EventCore core = null;
        if (c.z()) {
            core = EventCore.newBuilder()
                    .creatorNodeId(c.l())
                    .birthRound(c.l())
                    .timeCreated(buildTimestamp(c))
                    .coin(c.l())
                    .build();
        }
        final int count = c.i();
        final List<ParentEventReference> parents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parents.add(
                    switch (c.i()) {
                        case 1 ->
                            ParentEventReference.newBuilder()
                                    .eventDescriptor(EventDescriptor.newBuilder()
                                            .hash(c.bytes())
                                            .creatorNodeId(c.l())
                                            .birthRound(c.l())
                                            .build())
                                    .build();
                        case 2 -> ParentEventReference.newBuilder().index(c.i()).build();
                        default -> ParentEventReference.DEFAULT;
                    });
        }
        return EventHeader.newBuilder().eventCore(core).parents(parents).build();
    }

    // ------------------------------------------------------------------------------------- writing directly

    /**
     * Write the next item's fields from its values through a writer, with no model objects.
     *
     * @param c the cursor, at the item's values
     * @param w an open writer, with nothing written yet
     */
    public void write(final Cursor c, final BlockItem.Writer w) {
        switch (c.i()) {
            case 2 -> writeEventHeader(c, w.eventHeader());
            case 4 -> {
                c.range();
                w.signedTransaction(heap, c.offset, c.length);
            }
            case 5 -> writeTransactionResult(c, w.transactionResult());
            default -> writeStateChanges(c, w.stateChanges());
        }
    }

    private static void writeTimestamp(final Cursor c, final Timestamp.Writer w) {
        w.seconds(c.l()).nanos(c.i());
    }

    private static void writeAccountId(final Cursor c, final AccountID.Writer w) {
        w.shardNum(c.l()).realmNum(c.l());
        switch (c.i()) {
            case 1 -> w.accountNum(c.l());
            case 2 -> {
                c.range();
                w.alias(c.heap(), c.offset, c.length);
            }
            default -> {}
        }
    }

    private static void writeTokenId(final Cursor c, final TokenID.Writer w) {
        w.shardNum(c.l()).realmNum(c.l()).tokenNum(c.l());
    }

    private static void writeTopicId(final Cursor c, final TopicID.Writer w) {
        w.shardNum(c.l()).realmNum(c.l()).topicNum(c.l());
    }

    private static void writeContractId(final Cursor c, final ContractID.Writer w) {
        w.shardNum(c.l()).realmNum(c.l());
        switch (c.i()) {
            case 1 -> w.contractNum(c.l());
            case 2 -> {
                c.range();
                w.evmAddress(c.heap(), c.offset, c.length);
            }
            default -> {}
        }
    }

    private static void writeNftId(final Cursor c, final NftID.Writer w) {
        if (c.z()) writeTokenId(c, w.tokenId());
        w.serialNumber(c.l());
    }

    private static void writeKey(final Cursor c, final Key.Writer w) {
        switch (c.i()) {
            case 1 -> {
                if (c.z()) {
                    writeContractId(c, w.contractID());
                } else {
                    w.contractID((ContractID) null);
                }
            }
            case 2 -> {
                c.range();
                w.ed25519(c.heap(), c.offset, c.length);
            }
            case 6 -> {
                final KeyList.Writer list = w.keyList();
                final int count = c.i();
                for (int i = 0; i < count; i++) {
                    final Key.Writer key = list.addKeys();
                    if (c.z()) {
                        writeKey(c, key);
                    }
                }
            }
            case 7 -> {
                c.range();
                w.ecdsaSecp256k1(c.heap(), c.offset, c.length);
            }
            default -> {}
        }
    }

    private static void writeAccount(final Cursor c, final Account.Writer w) {
        if (c.z()) writeAccountId(c, w.accountId());
        c.range();
        w.alias(c.heap(), c.offset, c.length);
        if (c.z()) writeKey(c, w.key());
        w.expirationSecond(c.l())
                .tinybarBalance(c.l())
                .memo(c.s())
                .deleted(c.z())
                .stakedToMe(c.l())
                .stakePeriodStart(c.l());
        switch (c.i()) {
            case 10 -> {
                if (c.z()) writeAccountId(c, w.stakedAccountId());
            }
            case 11 -> w.stakedNodeId(c.l());
            default -> {}
        }
        w.declineReward(c.z()).receiverSigRequired(c.z());
        if (c.z()) writeTokenId(c, w.headTokenId());
        if (c.z()) writeNftId(c, w.headNftId());
        w.headNftSerialNumber(c.l())
                .numberOwnedNfts(c.l())
                .maxAutoAssociations(c.i())
                .usedAutoAssociations(c.i())
                .numberAssociations(c.i())
                .smartContract(c.z())
                .numberPositiveBalances(c.i())
                .ethereumNonce(c.l())
                .stakeAtStartOfLastRewardedPeriod(c.l());
        if (c.z()) writeAccountId(c, w.autoRenewAccountId());
        w.autoRenewSeconds(c.l())
                .contractKvPairsNumber(c.i())
                .numberTreasuryTitles(c.i())
                .expiredAndPendingRemoval(c.z());
        c.range();
        w.firstContractStorageKey(c.heap(), c.offset, c.length);
        w.numberPendingAirdrops(c.l())
                .numberHooksInUse(c.l())
                .firstHookId(c.l())
                .numberEvmHookStorageSlots(c.l());
        c.range();
        w.delegationAddress(c.heap(), c.offset, c.length);
    }

    private static void writeTokenRelation(final Cursor c, final TokenRelation.Writer w) {
        if (c.z()) writeTokenId(c, w.tokenId());
        if (c.z()) writeAccountId(c, w.accountId());
        w.balance(c.l()).frozen(c.z()).kycGranted(c.z()).automaticAssociation(c.z());
        if (c.z()) writeTokenId(c, w.previousToken());
        if (c.z()) writeTokenId(c, w.nextToken());
    }

    private static void writeNft(final Cursor c, final Nft.Writer w) {
        if (c.z()) writeNftId(c, w.nftId());
        if (c.z()) writeAccountId(c, w.ownerId());
        if (c.z()) writeAccountId(c, w.spenderId());
        if (c.z()) writeTimestamp(c, w.mintTime());
        c.range();
        w.metadata(c.heap(), c.offset, c.length);
        if (c.z()) writeNftId(c, w.ownerPreviousNftId());
        if (c.z()) writeNftId(c, w.ownerNextNftId());
    }

    private static void writeTopic(final Cursor c, final Topic.Writer w) {
        if (c.z()) writeTopicId(c, w.topicId());
        w.sequenceNumber(c.l()).expirationSecond(c.l()).autoRenewPeriod(c.l());
        if (c.z()) writeAccountId(c, w.autoRenewAccountId());
        w.deleted(c.z());
        c.range();
        w.runningHash(c.heap(), c.offset, c.length);
        w.memo(c.s());
        if (c.z()) writeKey(c, w.adminKey());
        if (c.z()) writeKey(c, w.submitKey());
        if (c.z()) writeKey(c, w.feeScheduleKey());
    }

    private static void writeMapChangeKey(final Cursor c, final MapChangeKey.Writer w) {
        switch (c.i()) {
            case 1 -> {
                if (c.z()) writeAccountId(c, w.accountIdKey());
            }
            case 5 -> {
                if (c.z()) writeNftId(c, w.nftIdKey());
            }
            case 10 -> {
                final SlotKey.Writer slot = w.slotKeyKey();
                if (c.z()) writeContractId(c, slot.contractID());
                c.range();
                slot.key(c.heap(), c.offset, c.length);
            }
            case 12 -> {
                if (c.z()) writeTopicId(c, w.topicIdKey());
            }
            default -> {}
        }
    }

    private static void writeMapChangeValue(final Cursor c, final MapChangeValue.Writer w) {
        switch (c.i()) {
            case 1 -> writeAccount(c, w.accountValue());
            case 5 -> writeNft(c, w.nftValue());
            case 9 -> {
                final SlotValue.Writer slot = w.slotValueValue();
                c.range();
                slot.value(c.heap(), c.offset, c.length);
                c.range();
                slot.previousKey(c.heap(), c.offset, c.length);
                c.range();
                slot.nextKey(c.heap(), c.offset, c.length);
            }
            case 12 -> writeTokenRelation(c, w.tokenRelationValue());
            default -> writeTopic(c, w.topicValue());
        }
    }

    private static void writeStateChanges(final Cursor c, final StateChanges.Writer w) {
        if (c.z()) writeTimestamp(c, w.consensusTimestamp());
        final int count = c.i();
        for (int i = 0; i < count; i++) {
            final StateChange.Writer change = w.addStateChanges().stateId(c.i());
            if (c.i() == 5) {
                final MapUpdateChange.Writer update = change.mapUpdate();
                if (c.z()) writeMapChangeKey(c, update.key());
                if (c.z()) writeMapChangeValue(c, update.value());
                update.identical(c.z());
            } else {
                final MapDeleteChange.Writer delete = change.mapDelete();
                if (c.z()) writeMapChangeKey(c, delete.key());
            }
        }
    }

    private static void writeAccountAmount(final Cursor c, final AccountAmount.Writer w) {
        if (c.z()) writeAccountId(c, w.accountID());
        w.amount(c.l()).isApproval(c.z());
    }

    private static void writeTransactionResult(final Cursor c, final TransactionResult.Writer w) {
        w.statusProtoOrdinal(c.i());
        if (c.z()) writeTimestamp(c, w.consensusTimestamp());
        if (c.z()) writeTimestamp(c, w.parentConsensusTimestamp());
        w.transactionFeeCharged(c.l());
        if (c.z()) {
            final TransferList.Writer list = w.transferList();
            final int count = c.i();
            for (int i = 0; i < count; i++) {
                writeAccountAmount(c, list.addAccountAmounts());
            }
        }
        final int listCount = c.i();
        for (int i = 0; i < listCount; i++) {
            final TokenTransferList.Writer list = w.addTokenTransferLists();
            if (c.z()) writeTokenId(c, list.token());
            final int transferCount = c.i();
            for (int j = 0; j < transferCount; j++) {
                writeAccountAmount(c, list.addTransfers());
            }
            final int nftCount = c.i();
            for (int j = 0; j < nftCount; j++) {
                final NftTransfer.Writer nft = list.addNftTransfers();
                if (c.z()) writeAccountId(c, nft.senderAccountID());
                if (c.z()) writeAccountId(c, nft.receiverAccountID());
                nft.serialNumber(c.l()).isApproval(c.z());
            }
            if (c.z()) list.expectedDecimals(c.i());
        }
        final int associationCount = c.i();
        for (int i = 0; i < associationCount; i++) {
            final TokenAssociation.Writer association = w.addAutomaticTokenAssociations();
            if (c.z()) writeTokenId(c, association.tokenId());
            if (c.z()) writeAccountId(c, association.accountId());
        }
        final int rewardCount = c.i();
        for (int i = 0; i < rewardCount; i++) {
            writeAccountAmount(c, w.addPaidStakingRewards());
        }
        w.congestionPricingMultiplier(c.l()).highVolumePricingMultiplier(c.l());
    }

    private static void writeEventHeader(final Cursor c, final EventHeader.Writer w) {
        if (c.z()) {
            final EventCore.Writer core = w.eventCore().creatorNodeId(c.l()).birthRound(c.l());
            if (c.z()) writeTimestamp(c, core.timeCreated());
            core.coin(c.l());
        }
        final int count = c.i();
        for (int i = 0; i < count; i++) {
            final ParentEventReference.Writer parent = w.addParents();
            switch (c.i()) {
                case 1 -> {
                    final EventDescriptor.Writer d = parent.eventDescriptor();
                    c.range();
                    d.hash(c.heap(), c.offset, c.length).creatorNodeId(c.l()).birthRound(c.l());
                }
                case 2 -> parent.index(c.i());
                default -> {}
            }
        }
    }
}
