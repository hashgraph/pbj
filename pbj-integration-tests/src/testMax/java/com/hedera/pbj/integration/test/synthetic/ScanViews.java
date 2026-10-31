// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.output.BlockFooter;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
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
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Read every field of the synthetic workload's messages through views, and sum them exactly as {@link ScanObjects}
 * does. Reusable: one instance holds the views, so a pass allocates nothing. Bytes fields are read in place, their
 * first and last byte with {@code xxxByteAt}, and the transaction body through its view with {@code bodyBytesAs}.
 */
public final class ScanViews {
    private final Block.View block = new Block.View();
    private final BlockItem.View item = new BlockItem.View();
    private final SignedTransaction.View signed = new SignedTransaction.View();
    private final TransactionBody.View body = new TransactionBody.View();

    /** Sum every field of every item of an encoded block. */
    public long block(final Bytes encoded) throws ParseException {
        long sum = 0;
        for (var items = block.wrap(encoded).items(); items.next(); ) {
            sum += item(items.value());
        }
        return sum;
    }

    /** Sum every field of an encoded block item. */
    public long item(final Bytes encoded) throws ParseException {
        return item(item.wrap(encoded));
    }

    /** Sum every field of a block item. */
    public long item(final BlockItem.View item) throws ParseException {
        long sum = item.itemKind().protoOrdinal();
        switch (item.itemKind()) {
            case BLOCK_HEADER -> sum += blockHeader(item.blockHeader());
            case SIGNED_TRANSACTION -> {
                final int n = item.signedTransactionLength();
                sum += n == 0 ? 0 : n + item.signedTransactionByteAt(0) + item.signedTransactionByteAt(n - 1);
            }
            case TRANSACTION_RESULT -> sum += transactionResult(item.transactionResult());
            case STATE_CHANGES -> sum += stateChanges(item.stateChanges());
            case BLOCK_FOOTER -> sum += blockFooter(item.blockFooter());
            case BLOCK_PROOF -> sum += blockProof(item.blockProof());
            default -> throw new IllegalStateException("Not in the synthetic workload: " + item.itemKind());
        }
        return sum;
    }

    // Bytes fields add their length and first and last byte, 0 if empty, read in place with xxxByteAt.

    private static long bool(final boolean value) {
        return value ? 1 : 0;
    }

    private static long timestamp(final Timestamp.View t) {
        return t.seconds() + t.nanos();
    }

    private long accountId(final AccountID.View a) {
        long sum = a.shardNum() + a.realmNum() + a.accountKind().protoOrdinal();
        switch (a.accountKind()) {
            case ACCOUNT_NUM -> sum += a.accountNum();
            case ALIAS -> {
                final int n = a.aliasLength();
                sum += n == 0 ? 0 : n + a.aliasByteAt(0) + a.aliasByteAt(n - 1);
            }
            default -> {}
        }
        return sum;
    }

    private static long tokenId(final TokenID.View t) {
        return t.shardNum() + t.realmNum() + t.tokenNum();
    }

    private static long nftId(final NftID.View n) throws ParseException {
        return (n.hasTokenId() ? tokenId(n.tokenId()) : 0) + n.serialNumber();
    }

    private static long semanticVersion(final SemanticVersion.View v) {
        return v.major() + v.minor() + v.patch() + v.preLength() + v.buildLength();
    }

    private static long blockHeader(final BlockHeader.View h) throws ParseException {
        return (h.hasHapiProtoVersion() ? semanticVersion(h.hapiProtoVersion()) : 0)
                + (h.hasSoftwareVersion() ? semanticVersion(h.softwareVersion()) : 0)
                + h.number()
                + (h.hasBlockTimestamp() ? timestamp(h.blockTimestamp()) : 0)
                + h.hashAlgorithmProtoOrdinal();
    }

    private static long blockFooter(final BlockFooter.View f) {
        final int a = f.previousBlockRootHashLength();
        final int b = f.rootHashOfAllBlockHashesTreeLength();
        final int c = f.startOfBlockStateRootHashLength();
        return (a == 0 ? 0 : a + f.previousBlockRootHashByteAt(0) + f.previousBlockRootHashByteAt(a - 1))
                + (b == 0
                        ? 0
                        : b + f.rootHashOfAllBlockHashesTreeByteAt(0) + f.rootHashOfAllBlockHashesTreeByteAt(b - 1))
                + (c == 0 ? 0 : c + f.startOfBlockStateRootHashByteAt(0) + f.startOfBlockStateRootHashByteAt(c - 1));
    }

    private long blockProof(final BlockProof.View p) throws ParseException {
        long sum = p.block() + p.proofKind().protoOrdinal();
        if (p.hasSignedBlockProof()) {
            final var signed = p.signedBlockProof();
            final int n = signed.blockSignatureLength();
            sum += n == 0 ? 0 : n + signed.blockSignatureByteAt(0) + signed.blockSignatureByteAt(n - 1);
        }
        return sum;
    }

    private long accountAmount(final AccountAmount.View a) throws ParseException {
        return (a.hasAccountID() ? accountId(a.accountID()) : 0) + a.amount() + bool(a.isApproval());
    }

    private long tokenTransferList(final TokenTransferList.View t) throws ParseException {
        long sum = t.hasToken() ? tokenId(t.token()) : 0;
        for (var transfers = t.transfers(); transfers.next(); ) {
            sum += accountAmount(transfers.value());
        }
        return sum;
    }

    private long transactionResult(final TransactionResult.View r) throws ParseException {
        long sum = r.statusProtoOrdinal()
                + (r.hasConsensusTimestamp() ? timestamp(r.consensusTimestamp()) : 0)
                + (r.hasParentConsensusTimestamp() ? timestamp(r.parentConsensusTimestamp()) : 0)
                + r.transactionFeeCharged();
        if (r.hasTransferList()) {
            for (var amounts = r.transferList().accountAmounts(); amounts.next(); ) {
                sum += accountAmount(amounts.value());
            }
        }
        for (var lists = r.tokenTransferLists(); lists.next(); ) {
            sum += tokenTransferList(lists.value());
        }
        return sum;
    }

    private long mapChangeKey(final MapChangeKey.View k) throws ParseException {
        long sum = k.keyChoiceKind().protoOrdinal();
        switch (k.keyChoiceKind()) {
            case ACCOUNT_ID_KEY -> sum += accountId(k.accountIdKey());
            case NFT_ID_KEY -> sum += nftId(k.nftIdKey());
            case TOKEN_RELATIONSHIP_KEY -> {
                final AccountTokenAssociation.View a = k.tokenRelationshipKey();
                sum += (a.hasAccountId() ? accountId(a.accountId()) : 0) + (a.hasTokenId() ? tokenId(a.tokenId()) : 0);
            }
            default -> throw new IllegalStateException("Not in the synthetic workload: " + k.keyChoiceKind());
        }
        return sum;
    }

    private long tokenRelation(final TokenRelation.View t) throws ParseException {
        return (t.hasTokenId() ? tokenId(t.tokenId()) : 0)
                + (t.hasAccountId() ? accountId(t.accountId()) : 0)
                + t.balance()
                + bool(t.frozen())
                + bool(t.kycGranted())
                + bool(t.automaticAssociation())
                + (t.hasPreviousToken() ? tokenId(t.previousToken()) : 0)
                + (t.hasNextToken() ? tokenId(t.nextToken()) : 0);
    }

    private long nft(final Nft.View n) throws ParseException {
        final int m = n.metadataLength();
        return (n.hasNftId() ? nftId(n.nftId()) : 0)
                + (n.hasOwnerId() ? accountId(n.ownerId()) : 0)
                + (n.hasSpenderId() ? accountId(n.spenderId()) : 0)
                + (n.hasMintTime() ? timestamp(n.mintTime()) : 0)
                + (m == 0 ? 0 : m + n.metadataByteAt(0) + n.metadataByteAt(m - 1))
                + (n.hasOwnerPreviousNftId() ? nftId(n.ownerPreviousNftId()) : 0)
                + (n.hasOwnerNextNftId() ? nftId(n.ownerNextNftId()) : 0);
    }

    private long mapChangeValue(final MapChangeValue.View v) throws ParseException {
        long sum = v.valueChoiceKind().protoOrdinal();
        switch (v.valueChoiceKind()) {
            case TOKEN_RELATION_VALUE -> sum += tokenRelation(v.tokenRelationValue());
            case NFT_VALUE -> sum += nft(v.nftValue());
            default -> throw new IllegalStateException("Not in the synthetic workload: " + v.valueChoiceKind());
        }
        return sum;
    }

    private long transactionId(final TransactionID.View t) throws ParseException {
        return (t.hasTransactionValidStart() ? timestamp(t.transactionValidStart()) : 0)
                + (t.hasAccountID() ? accountId(t.accountID()) : 0)
                + bool(t.scheduled())
                + t.nonce();
    }

    private long receiptEntry(final TransactionReceiptEntry.View e) throws ParseException {
        return e.nodeId() + (e.hasTransactionId() ? transactionId(e.transactionId()) : 0) + e.statusProtoOrdinal();
    }

    private long stateChange(final StateChange.View c) throws ParseException {
        long sum = c.stateId() + c.changeOperationKind().protoOrdinal();
        switch (c.changeOperationKind()) {
            case MAP_UPDATE -> {
                final MapUpdateChange.View update = c.mapUpdate();
                sum += (update.hasKey() ? mapChangeKey(update.key()) : 0)
                        + (update.hasValue() ? mapChangeValue(update.value()) : 0)
                        + bool(update.identical());
            }
            case MAP_DELETE -> {
                final var delete = c.mapDelete();
                sum += delete.hasKey() ? mapChangeKey(delete.key()) : 0;
            }
            case SINGLETON_UPDATE -> {
                final var singleton = c.singletonUpdate();
                // the wrapper's value is only available boxed, which allocates for values over 127
                sum += singleton.newValueKind().protoOrdinal() + singleton.entityNumberValueOrElse(0);
            }
            case QUEUE_PUSH -> {
                final var push = c.queuePush();
                sum += push.valueKind().protoOrdinal();
                for (var entries = push.transactionReceiptEntriesElement().entries(); entries.next(); ) {
                    sum += receiptEntry(entries.value());
                }
            }
            case QUEUE_POP -> {}
            default -> throw new IllegalStateException("Not in the synthetic workload: " + c.changeOperationKind());
        }
        return sum;
    }

    private long stateChanges(final StateChanges.View s) throws ParseException {
        long sum = s.hasConsensusTimestamp() ? timestamp(s.consensusTimestamp()) : 0;
        for (var changes = s.stateChanges(); changes.next(); ) {
            sum += stateChange(changes.value());
        }
        return sum;
    }

    // ----------------------------------------------------------------------------------------------- transactions

    /** Read a signed transaction and its body, and sum every field of both. */
    public long transaction(final Bytes signedTransaction) throws ParseException {
        final SignedTransaction.View s = signed.wrap(signedTransaction);
        long sum = bool(s.useSerializedTxMessageHashAlgorithm());
        if (s.hasSigMap()) {
            for (var pairs = s.sigMap().sigPair(); pairs.next(); ) {
                final SignaturePair.View pair = pairs.value();
                final int prefix = pair.pubKeyPrefixLength();
                sum += (prefix == 0 ? 0 : prefix + pair.pubKeyPrefixByteAt(0) + pair.pubKeyPrefixByteAt(prefix - 1))
                        + pair.signatureKind().protoOrdinal();
                switch (pair.signatureKind()) {
                    case ED25519 -> {
                        final int n = pair.ed25519Length();
                        sum += n == 0 ? 0 : n + pair.ed25519ByteAt(0) + pair.ed25519ByteAt(n - 1);
                    }
                    case ECDSA_SECP256K1 -> {
                        final int n = pair.ecdsaSecp256k1Length();
                        sum += n == 0 ? 0 : n + pair.ecdsaSecp256k1ByteAt(0) + pair.ecdsaSecp256k1ByteAt(n - 1);
                    }
                    default -> throw new IllegalStateException("Not in the synthetic workload");
                }
            }
        }
        // the body is read in place, through its view wrapped over the bytes field
        final int n = s.bodyBytesLength();
        sum += n == 0 ? 0 : n + s.bodyBytesByteAt(0) + s.bodyBytesByteAt(n - 1);
        return sum + transactionBody(s.bodyBytesAs(body));
    }

    private long transactionBody(final TransactionBody.View b) throws ParseException {
        long sum = (b.hasTransactionID() ? transactionId(b.transactionID()) : 0)
                + (b.hasNodeAccountID() ? accountId(b.nodeAccountID()) : 0)
                + b.transactionFee()
                + b.dataKind().protoOrdinal();
        switch (b.dataKind()) {
            case CRYPTO_TRANSFER -> {
                final CryptoTransferTransactionBody.View transfer = b.cryptoTransfer();
                if (transfer.hasTransfers()) {
                    for (var amounts = transfer.transfers().accountAmounts(); amounts.next(); ) {
                        sum += accountAmount(amounts.value());
                    }
                }
                for (var lists = transfer.tokenTransfers(); lists.next(); ) {
                    sum += tokenTransferList(lists.value());
                }
            }
            case ATOMIC_BATCH -> {
                final AtomicBatchTransactionBody.View batch = b.atomicBatch();
                for (var transactions = batch.transactions(); transactions.next(); ) {
                    final int n = transactions.valueLength();
                    sum += n == 0 ? 0 : n + transactions.valueByteAt(0) + transactions.valueByteAt(n - 1);
                }
            }
            default -> throw new IllegalStateException("Not in the synthetic workload: " + b.dataKind());
        }
        return sum;
    }
}
