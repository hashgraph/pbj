package protoparse;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.hapi.node.token.GrantedCryptoAllowance;
import com.hedera.hapi.node.token.GrantedNftAllowance;
import com.hedera.hapi.node.token.GrantedTokenAllowance;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

/**
 * Create a complex account details we can use as benchmark
 */
public class AccountDetailsPbj {
    private static final Random RANDOM = new Random(351343135153L);

    public static final AccountDetails ACCOUNT_DETAILS =
            new AccountDetails.Builder()
                    .accountId(new AccountID.Builder()
                            .shardNum(posLong())
                            .realmNum(posLong())
                            .accountNum(posLong())
                            .build())
                    .contractAccountId(randomHex(64))
                    .deleted(false)
                    .proxyAccountId(new AccountID.Builder()
                            .shardNum(posLong())
                            .realmNum(posLong())
                            .accountNum(posLong())
                            .alias(randomBytes(32))
                            .build())
                    .proxyReceived(RANDOM.nextLong())
                    .key(new Key.Builder()
                            .keyList(new KeyList.Builder()
                                    .keys(List.of(
                                            new Key.Builder()
                                                    .ed25519(randomBytes(32))
                                                    .build(),
                                            new Key.Builder()
                                                    .ecdsa384(randomBytes(48))
                                                    .build(),
                                            new Key.Builder()
                                                    .contractID(new ContractID.Builder()
                                                            .shardNum(posLong())
                                                            .realmNum(posLong())
                                                            .contractNum(posLong())
                                                            .build())
                                                    .build()
                                    ))
                                    .build())
                            .build())
                    .balance(RANDOM.nextLong())
                    .receiverSigRequired(true)
                    .expirationTime(new Timestamp.Builder()
                            .nanos(RANDOM.nextInt(0, Integer.MAX_VALUE))
                            .seconds(RANDOM.nextLong(0, Long.MAX_VALUE))
                            .build())
                    .autoRenewPeriod(new Duration.Builder()
                            .seconds(RANDOM.nextLong(0, Long.MAX_VALUE))
                            .build())
                    .tokenRelationships(List.of(
                            new TokenRelationship.Builder()
                                    .balance(RANDOM.nextLong(1, Long.MAX_VALUE))
                                    .decimals(RANDOM.nextInt(0, Integer.MAX_VALUE))
                                    .automaticAssociation(true)
                                    .symbol(randomHex(3))
                                    .tokenId(new TokenID(posLong(),posLong(),posLong()))
                                    .build(),
                            new TokenRelationship.Builder()
                                    .balance(RANDOM.nextLong(1, Long.MAX_VALUE))
                                    .decimals(RANDOM.nextInt(0, Integer.MAX_VALUE))
                                    .automaticAssociation(true)
                                    .symbol(randomHex(3))
                                    .tokenId(new TokenID(posLong(),posLong(),posLong()))
                                    .build()
                    ))
                    .memo(randomHex(80))
                    .ownedNfts(RANDOM.nextLong(10, Integer.MAX_VALUE))
                    .maxAutomaticTokenAssociations(RANDOM.nextInt(10, Integer.MAX_VALUE))
                    .alias(randomBytes(32))
                    .ledgerId(randomBytes(32))
                    .grantedCryptoAllowances(List.of(
                            new GrantedCryptoAllowance(new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build()
                                    ,posLong()),
                            new GrantedCryptoAllowance(new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build()
                                    ,posLong())
                    ))
                    .grantedNftAllowances(List.of(
                            new GrantedNftAllowance(new TokenID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).tokenNum(posLong())
                                    .build()
                                    ,new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build()),
                            new GrantedNftAllowance(new TokenID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).tokenNum(posLong())
                                    .build()
                                    ,new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build())
                    ))
                    .grantedTokenAllowances(List.of(
                            new GrantedTokenAllowance(new TokenID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).tokenNum(posLong())
                                    .build()
                                    ,new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build(),
                                    posLong()),
                            new GrantedTokenAllowance(new TokenID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).tokenNum(posLong())
                                    .build()
                                    ,new AccountID.Builder()
                                    .shardNum(posLong()).realmNum(posLong()).accountNum(posLong())
                                    .build(),
                                    posLong())
                    ))
                    .build();

    private static long posLong() {
        return RANDOM.nextLong(0, Integer.MAX_VALUE);
    }

    private static Bytes randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return Bytes.wrap(data);
    }

    private static String randomHex(int size) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(0,15)));
        }
        return sb.toString();
    }
}
