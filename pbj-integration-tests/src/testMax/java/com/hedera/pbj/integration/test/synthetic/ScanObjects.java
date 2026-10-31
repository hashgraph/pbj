// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.output.BlockFooter;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountTokenAssociation;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntry;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;

/**
 * Read every field of the synthetic workload's messages through model objects, and sum them: every number, boolean (1
 * when true), enum ordinal and set oneof's field number, and for bytes their length and first and last byte. Every
 * field of each message type of the subset is read, set or not; {@link ScanViews} and the reference codec read the same
 * fields and must return the same sum.
 */
public final class ScanObjects {
    private ScanObjects() {}

    /** Sum every field of a block item. */
    public static long item(final BlockItem item) {
        long sum = item.item().kind().protoOrdinal();
        switch (item.item().kind()) {
            case BLOCK_HEADER -> sum += blockHeader(item.blockHeaderOrThrow());
            case SIGNED_TRANSACTION -> sum += bytes(item.signedTransactionOrThrow());
            case TRANSACTION_RESULT -> sum += transactionResult(item.transactionResultOrThrow());
            case STATE_CHANGES -> sum += stateChanges(item.stateChangesOrThrow());
            case BLOCK_FOOTER -> sum += blockFooter(item.blockFooterOrThrow());
            case BLOCK_PROOF -> sum += blockProof(item.blockProofOrThrow());
            default ->
                throw new IllegalStateException(
                        "Not in the synthetic workload: " + item.item().kind());
        }
        return sum;
    }

    /** Sum every field of every item. */
    public static long items(final List<BlockItem> items) {
        long sum = 0;
        for (int i = 0, n = items.size(); i < n; i++) {
            sum += item(items.get(i));
        }
        return sum;
    }

    static long bytes(final Bytes bytes) {
        final long length = bytes.length();
        return length == 0 ? 0 : length + bytes.getByte(0) + bytes.getByte(length - 1);
    }

    static long bool(final boolean value) {
        return value ? 1 : 0;
    }

    static long timestamp(final Timestamp t) {
        return t == null ? 0 : t.seconds() + t.nanos();
    }

    static long accountId(final AccountID a) {
        if (a == null) {
            return 0;
        }
        long sum = a.shardNum() + a.realmNum() + a.account().kind().protoOrdinal();
        switch (a.account().kind()) {
            case ACCOUNT_NUM -> sum += a.accountNumOrThrow();
            case ALIAS -> sum += bytes(a.aliasOrThrow());
            default -> {}
        }
        return sum;
    }

    static long tokenId(final TokenID t) {
        return t == null ? 0 : t.shardNum() + t.realmNum() + t.tokenNum();
    }

    static long nftId(final NftID n) {
        return n == null ? 0 : tokenId(n.tokenId()) + n.serialNumber();
    }

    static long semanticVersion(final SemanticVersion v) {
        return v == null
                ? 0
                : v.major()
                        + v.minor()
                        + v.patch()
                        + v.pre().length()
                        + v.build().length();
    }

    static long blockHeader(final BlockHeader h) {
        return semanticVersion(h.hapiProtoVersion())
                + semanticVersion(h.softwareVersion())
                + h.number()
                + timestamp(h.blockTimestamp())
                + h.hashAlgorithm().protoOrdinal();
    }

    static long blockFooter(final BlockFooter f) {
        return bytes(f.previousBlockRootHash())
                + bytes(f.rootHashOfAllBlockHashesTree())
                + bytes(f.startOfBlockStateRootHash());
    }

    static long blockProof(final BlockProof p) {
        long sum = p.block() + p.proof().kind().protoOrdinal();
        if (p.hasSignedBlockProof()) {
            sum += bytes(p.signedBlockProofOrThrow().blockSignature());
        }
        return sum;
    }

    static long accountAmount(final AccountAmount a) {
        return accountId(a.accountID()) + a.amount() + bool(a.isApproval());
    }

    static long accountAmounts(final List<AccountAmount> list) {
        long sum = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            sum += accountAmount(list.get(i));
        }
        return sum;
    }

    static long tokenTransferList(final TokenTransferList t) {
        return tokenId(t.token()) + accountAmounts(t.transfers());
    }

    static long transactionResult(final TransactionResult r) {
        long sum = r.status().protoOrdinal()
                + timestamp(r.consensusTimestamp())
                + timestamp(r.parentConsensusTimestamp())
                + r.transactionFeeCharged();
        if (r.transferList() != null) {
            sum += accountAmounts(r.transferList().accountAmounts());
        }
        final List<TokenTransferList> lists = r.tokenTransferLists();
        for (int i = 0, n = lists.size(); i < n; i++) {
            sum += tokenTransferList(lists.get(i));
        }
        return sum;
    }

    static long mapChangeKey(final MapChangeKey k) {
        if (k == null) {
            return 0;
        }
        long sum = k.keyChoice().kind().protoOrdinal();
        switch (k.keyChoice().kind()) {
            case ACCOUNT_ID_KEY -> sum += accountId(k.accountIdKeyOrThrow());
            case NFT_ID_KEY -> sum += nftId(k.nftIdKeyOrThrow());
            case TOKEN_RELATIONSHIP_KEY -> {
                final AccountTokenAssociation a = k.tokenRelationshipKeyOrThrow();
                sum += accountId(a.accountId()) + tokenId(a.tokenId());
            }
            default ->
                throw new IllegalStateException(
                        "Not in the synthetic workload: " + k.keyChoice().kind());
        }
        return sum;
    }

    static long tokenRelation(final TokenRelation t) {
        return tokenId(t.tokenId())
                + accountId(t.accountId())
                + t.balance()
                + bool(t.frozen())
                + bool(t.kycGranted())
                + bool(t.automaticAssociation())
                + tokenId(t.previousToken())
                + tokenId(t.nextToken());
    }

    static long nft(final Nft n) {
        return nftId(n.nftId())
                + accountId(n.ownerId())
                + accountId(n.spenderId())
                + timestamp(n.mintTime())
                + bytes(n.metadata())
                + nftId(n.ownerPreviousNftId())
                + nftId(n.ownerNextNftId());
    }

    static long mapChangeValue(final MapChangeValue v) {
        if (v == null) {
            return 0;
        }
        long sum = v.valueChoice().kind().protoOrdinal();
        switch (v.valueChoice().kind()) {
            case TOKEN_RELATION_VALUE -> sum += tokenRelation(v.tokenRelationValueOrThrow());
            case NFT_VALUE -> sum += nft(v.nftValueOrThrow());
            default ->
                throw new IllegalStateException(
                        "Not in the synthetic workload: " + v.valueChoice().kind());
        }
        return sum;
    }

    static long transactionId(final TransactionID t) {
        return t == null
                ? 0
                : timestamp(t.transactionValidStart()) + accountId(t.accountID()) + bool(t.scheduled()) + t.nonce();
    }

    static long receiptEntry(final TransactionReceiptEntry e) {
        return e.nodeId() + transactionId(e.transactionId()) + e.status().protoOrdinal();
    }

    static long stateChange(final StateChange c) {
        long sum = c.stateId() + c.changeOperation().kind().protoOrdinal();
        switch (c.changeOperation().kind()) {
            case MAP_UPDATE -> {
                final var update = c.mapUpdateOrThrow();
                sum += mapChangeKey(update.key()) + mapChangeValue(update.value()) + bool(update.identical());
            }
            case MAP_DELETE -> sum += mapChangeKey(c.mapDeleteOrThrow().key());
            case SINGLETON_UPDATE -> {
                final var singleton = c.singletonUpdateOrThrow();
                sum += singleton.newValue().kind().protoOrdinal();
                sum += singleton.entityNumberValueOrThrow();
            }
            case QUEUE_PUSH -> {
                final var push = c.queuePushOrThrow();
                sum += push.value().kind().protoOrdinal();
                final List<TransactionReceiptEntry> entries =
                        push.transactionReceiptEntriesElementOrThrow().entries();
                for (int i = 0, n = entries.size(); i < n; i++) {
                    sum += receiptEntry(entries.get(i));
                }
            }
            case QUEUE_POP -> {}
            default ->
                throw new IllegalStateException(
                        "Not in the synthetic workload: " + c.changeOperation().kind());
        }
        return sum;
    }

    static long stateChanges(final StateChanges s) {
        long sum = timestamp(s.consensusTimestamp());
        final List<StateChange> changes = s.stateChanges();
        for (int i = 0, n = changes.size(); i < n; i++) {
            sum += stateChange(changes.get(i));
        }
        return sum;
    }

    // ----------------------------------------------------------------------------------------------- transactions

    /** Parse a signed transaction and its body, and sum every field of both. */
    public static long transaction(final Bytes signedTransaction) throws ParseException {
        final SignedTransaction signed = SignedTransaction.PROTOBUF.parse(signedTransaction);
        long sum = bytes(signed.bodyBytes()) + bool(signed.useSerializedTxMessageHashAlgorithm());
        if (signed.sigMap() != null) {
            final List<SignaturePair> pairs = signed.sigMap().sigPair();
            for (int i = 0, n = pairs.size(); i < n; i++) {
                final SignaturePair pair = pairs.get(i);
                sum += bytes(pair.pubKeyPrefix()) + pair.signature().kind().protoOrdinal();
                switch (pair.signature().kind()) {
                    case ED25519 -> sum += bytes(pair.ed25519OrThrow());
                    case ECDSA_SECP256K1 -> sum += bytes(pair.ecdsaSecp256k1OrThrow());
                    default -> throw new IllegalStateException("Not in the synthetic workload");
                }
            }
        }
        return sum + transactionBody(TransactionBody.PROTOBUF.parse(signed.bodyBytes()));
    }

    static long transactionBody(final TransactionBody b) {
        long sum = transactionId(b.transactionID())
                + accountId(b.nodeAccountID())
                + b.transactionFee()
                + b.data().kind().protoOrdinal();
        switch (b.data().kind()) {
            case CRYPTO_TRANSFER -> {
                final var transfer = b.cryptoTransferOrThrow();
                if (transfer.transfers() != null) {
                    sum += accountAmounts(transfer.transfers().accountAmounts());
                }
                final List<TokenTransferList> lists = transfer.tokenTransfers();
                for (int i = 0, n = lists.size(); i < n; i++) {
                    sum += tokenTransferList(lists.get(i));
                }
            }
            case ATOMIC_BATCH -> {
                final List<Bytes> transactions = b.atomicBatchOrThrow().transactions();
                for (int i = 0, n = transactions.size(); i < n; i++) {
                    sum += bytes(transactions.get(i));
                }
            }
            default ->
                throw new IllegalStateException(
                        "Not in the synthetic workload: " + b.data().kind());
        }
        return sum;
    }
}
