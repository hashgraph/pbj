package com.hedera.pbj.intergration.test;

import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.params.ParameterizedTest;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Unit Test for TimestampTest model object. Generate based on protobuf schema.
 */
public final class TestHashFunctions {
    public static int hash1(Hasheval hashEval) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    Hasheval.PROTOBUF.toBytes(hashEval).toByteArray());
            int res = hash[0] << 24 | hash[1] << 16 | hash[2] << 8 | hash[3];
            return processForBetterDistribution(res);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static int hash2(Hasheval hashEval) {
        if (hashEval == null) return 0;

        int result = 1;
        if (hashEval.int32Number() != Hasheval.DEFAULT.int32Number()) {
            result = 31 * result + Integer.hashCode(hashEval.int32Number());
        }
        if (hashEval.sint32Number() != Hasheval.DEFAULT.sint32Number()) {
            result = 31 * result + Integer.hashCode(hashEval.sint32Number());
        }
        if (hashEval.uint32Number() != Hasheval.DEFAULT.uint32Number()) {
            result = 31 * result + Integer.hashCode(hashEval.uint32Number());
        }
        if (hashEval.fixed32Number() != Hasheval.DEFAULT.fixed32Number()) {
            result = 31 * result + Integer.hashCode(hashEval.fixed32Number());
        }
        if (hashEval.sfixed32Number() != Hasheval.DEFAULT.sfixed32Number()) {
            result = 31 * result + Integer.hashCode(hashEval.sfixed32Number());
        }
        if (hashEval.floatNumber() != Hasheval.DEFAULT.floatNumber()) {
            result = 31 * result + Float.hashCode(hashEval.floatNumber());
        }
        if (hashEval.int64Number() != Hasheval.DEFAULT.int64Number()) {
            result = 31 * result + Long.hashCode(hashEval.int64Number());
        }
        if (hashEval.sint64Number() != Hasheval.DEFAULT.sint64Number()) {
            result = 31 * result + Long.hashCode(hashEval.sint64Number());
        }
        if (hashEval.uint64Number() != Hasheval.DEFAULT.uint64Number()) {
            result = 31 * result + Long.hashCode(hashEval.uint64Number());
        }
        if (hashEval.fixed64Number() != Hasheval.DEFAULT.fixed64Number()) {
            result = 31 * result + Long.hashCode(hashEval.fixed64Number());
        }
        if (hashEval.sfixed64Number() != Hasheval.DEFAULT.sfixed64Number()) {
            result = 31 * result + Long.hashCode(hashEval.sfixed64Number());
        }
        if (hashEval.doubleNumber() != Hasheval.DEFAULT.doubleNumber()) {
            result = 31 * result + Double.hashCode(hashEval.doubleNumber());
        }
        if (hashEval.booleanField() != Hasheval.DEFAULT.booleanField()) {
            result = 31 * result + Boolean.hashCode(hashEval.booleanField());
        }
        if (hashEval.enumSuit() != Hasheval.DEFAULT.enumSuit()) {
            result = 31 * result + hashEval.enumSuit().hashCode();
        }
        if (hashEval.subObject() != Hasheval.DEFAULT.subObject()) {
            TimestampTest sub = hashEval.subObject();
            if (sub.nanos() != sub.DEFAULT.nanos()) {
                result = 31 * result + Integer.hashCode(sub.nanos());
            }
            if (sub.seconds() != sub.DEFAULT.seconds()) {
                result = 31 * result + Long.hashCode(sub.seconds());
            }
        }
        if (hashEval.text() != Hasheval.DEFAULT.text()) {
            result = 31 * result + hashEval.text().hashCode();
        }
        if (hashEval.bytesField() != Hasheval.DEFAULT.bytesField()) {
            result = 31 * result + (hashEval.bytesField() == null ? 0 : hashEval.bytesField().hashCode());
        }

        return processForBetterDistribution(result);
    }

    private static int processForBetterDistribution(int val) {
        val += val << 30;
        val ^= val >>> 27;
        val += val << 16;
        val ^= val >>> 20;
        val += val << 5;
        val ^= val >>> 18;
        val += val << 10;
        val ^= val >>> 24;
        val += val << 30;
        return val;
    }
}