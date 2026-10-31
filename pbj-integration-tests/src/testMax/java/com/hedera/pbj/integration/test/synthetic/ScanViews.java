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
 * does. Reusable: one instance holds the views and a scratch array, so a pass allocates nothing (except where a view
 * API itself does, noted below).
 *
 * <p>Views have no allocation-free way to look at a few bytes of a bytes field, so the first and last byte are read by
 * copying the field into the scratch array, and the transaction body is parsed from such a copy.
 */
public final class ScanViews {
    private final Block.View block = new Block.View();
    private final BlockItem.View item = new BlockItem.View();
    private final SignedTransaction.View signed = new SignedTransaction.View();
    private final TransactionBody.View body = new TransactionBody.View();
    private final byte[] scratch = new byte[1 << 20];
    /** The copy of a transaction body that its view reads, apart from the scratch array its fields are copied to. */
    private final byte[] bodyCopy = new byte[1 << 20];

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
                final int length = item.signedTransactionCopyTo(scratch, 0);
                sum += bytes(length);
            }
            case TRANSACTION_RESULT -> sum += transactionResult(item.transactionResult());
            case STATE_CHANGES -> sum += stateChanges(item.stateChanges());
            case BLOCK_FOOTER -> sum += blockFooter(item.blockFooter());
            case BLOCK_PROOF -> sum += blockProof(item.blockProof());
            default -> throw new IllegalStateException("Not in the synthetic workload: " + item.itemKind());
        }
        return sum;
    }

    /** The length and first and last byte of the {@code length} bytes copied into the scratch array. */
    private long bytes(final int length) {
        return length == 0 ? 0 : length + scratch[0] + scratch[length - 1];
    }

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
            case ALIAS -> sum += bytes(a.aliasCopyTo(scratch, 0));
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

    private long blockFooter(final BlockFooter.View f) {
        return bytes(f.previousBlockRootHashCopyTo(scratch, 0))
                + bytes(f.rootHashOfAllBlockHashesTreeCopyTo(scratch, 0))
                + bytes(f.startOfBlockStateRootHashCopyTo(scratch, 0));
    }

    private long blockProof(final BlockProof.View p) throws ParseException {
        long sum = p.block() + p.proofKind().protoOrdinal();
        if (p.hasSignedBlockProof()) {
            sum += bytes(p.signedBlockProof().blockSignatureCopyTo(scratch, 0));
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
        return (n.hasNftId() ? nftId(n.nftId()) : 0)
                + (n.hasOwnerId() ? accountId(n.ownerId()) : 0)
                + (n.hasSpenderId() ? accountId(n.spenderId()) : 0)
                + (n.hasMintTime() ? timestamp(n.mintTime()) : 0)
                + bytes(n.metadataCopyTo(scratch, 0))
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
                sum += singleton.newValueKind().protoOrdinal() + singleton.entityNumberValue();
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
                sum += bytes(pair.pubKeyPrefixCopyTo(scratch, 0))
                        + pair.signatureKind().protoOrdinal();
                switch (pair.signatureKind()) {
                    case ED25519 -> sum += bytes(pair.ed25519CopyTo(scratch, 0));
                    case ECDSA_SECP256K1 -> sum += bytes(pair.ecdsaSecp256k1CopyTo(scratch, 0));
                    default -> throw new IllegalStateException("Not in the synthetic workload");
                }
            }
        }
        // the body is parsed from a copy, as a view can't be wrapped around another view's bytes field
        final int bodyLength = s.bodyBytesCopyTo(bodyCopy, 0);
        sum += bodyLength == 0 ? 0 : bodyLength + bodyCopy[0] + bodyCopy[bodyLength - 1];
        return sum + transactionBody(body.wrap(bodyCopy, 0, bodyLength));
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
                    final int length = transactions.valueLength();
                    if (length > 0) {
                        // only the first and last byte are needed, but the whole value has to be copied
                        transactions.valueCopyTo(scratch, 0);
                        sum += length + scratch[0] + scratch[length - 1];
                    }
                }
            }
            default -> throw new IllegalStateException("Not in the synthetic workload: " + b.dataKind());
        }
        return sum;
    }
}
