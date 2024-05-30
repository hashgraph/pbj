package com.hedera.pbj.intergration.test;

import static java.util.Collections.shuffle;
import static java.util.Collections.sort;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.ComparableOneOf;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.BoolValueComparableTest;
import com.hedera.pbj.test.proto.pbj.BytesValueComparableTest;
import com.hedera.pbj.test.proto.pbj.ComparableEnum;
import com.hedera.pbj.test.proto.pbj.ComparableOneOfTest;
import com.hedera.pbj.test.proto.pbj.ComparableSubObj;
import com.hedera.pbj.test.proto.pbj.ComparableTest;
import com.hedera.pbj.test.proto.pbj.DoubleValueComparableTest;
import com.hedera.pbj.test.proto.pbj.FloatValueComparableTest;
import com.hedera.pbj.test.proto.pbj.Int32ValueComparableTest;
import com.hedera.pbj.test.proto.pbj.Int64ValueComparableTest;
import com.hedera.pbj.test.proto.pbj.LimitedComparableTest;
import com.hedera.pbj.test.proto.pbj.LimitedComparableTestWithOneOf;
import com.hedera.pbj.test.proto.pbj.StringValueComparableTest;
import com.hedera.pbj.test.proto.pbj.UnsignedComparableTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import java.util.random.RandomGenerator;

/**
 * Unit test for {@link ComparableTest} and {@link LimitedComparableTest} objects.
 * The goal is to test the generated {@link Comparable} interface implementation.
 */
class CompareToTest {

    @Test
    void testCompareTo_int32() {
        assertComparables(
                new ComparableTest(1, 0.0, false, null, null, null, null),
                new ComparableTest(2, 0.0, false, null, null, null, null),
                new ComparableTest(3, 0.0, false, null, null, null, null));
    }
    @Test
    void testCompareTo_double() {
        assertComparables(
                new ComparableTest(1, 0.0, false, null, null, null, null),
                new ComparableTest(2, 1.5, false, null, null, null, null),
                new ComparableTest(3, 2.66, false, null, null, null, null)
        );
    }
    @Test
    void testCompareTo_bool() {
        assertComparables(
                new ComparableTest(0, 0.0, false, null, null, null, null),
                new ComparableTest(0, 0.0, true, null, null, null, null)
        );
    }
    @Test
    void testCompareTo_string() {
        assertComparables(
                new ComparableTest(0, 0.0, false, "a", null, null, null),
                new ComparableTest(0, 0.0, false, "b", null, null, null),
                new ComparableTest(0, 0.0, false, "c", null, null, null)
        );
    }
    @Test
    void testCompareTo_bytes() {
        assertComparables(
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("a")),
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("aa")),
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("aaa"))
        );
    }
    @Test
    void testCompareTo_bytes_same_lenth() {
         assertComparables(
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("aba")),
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("abb")),
                new ComparableTest(0, 0.0, false, null, null, null, Bytes.wrap("abc"))
        );
    }
    @Test
    void testCompareTo_enum(){
        assertComparables(
                new ComparableTest(0, 0.0, false, null, ComparableEnum.ONE, null, null),
                new ComparableTest(0, 0.0, false, null, ComparableEnum.TWO, null, null),
                new ComparableTest(0, 0.0, false, null, ComparableEnum.THREE, null, null)
        );
    }
    @Test
    void testCompareTo_subObject(){
        assertComparables(
                new ComparableTest(0, 0.0, false, null, null, new ComparableSubObj(1), null),
                new ComparableTest(0, 0.0, false, null, null,  new ComparableSubObj(2), null),
                new ComparableTest(0, 0.0, false, null, null,  new ComparableSubObj(3), null)
        );
    }
    @Test
     void compareTo_mixed() {
         assertComparables(
                 new ComparableTest(1, 0.0, false, null, null, new ComparableSubObj(1), null),
                 new ComparableTest(1, 0.0, false, null, null, new ComparableSubObj(2), null),
                 new ComparableTest(2, 0.0, false, null, null, new ComparableSubObj(1), null),
                 new ComparableTest(2, 0.0, false, null, null,  new ComparableSubObj(2), null)
         );
     }
     @Test
     void compareTo_StringValue() {
            assertComparables(
                    new StringValueComparableTest(null),
                    new StringValueComparableTest("a"),
                    new StringValueComparableTest("b"),
                    new StringValueComparableTest("c")
            );
     }
     @Test
     void compareTo_BoolValue() {
            assertComparables(
                    new BoolValueComparableTest(null),
                    new BoolValueComparableTest(false),
                    new BoolValueComparableTest(true)
            );
     }
     @Test
     void compareTo_Int32Value() {
            assertComparables(
                    new Int32ValueComparableTest(null, null),
                    new Int32ValueComparableTest(1, null),
                    new Int32ValueComparableTest(2, null),
                    new Int32ValueComparableTest(3, null)
            );
     }
     @Test
     void compareTo_Uint32Value() {
            assertComparables(
                    new Int32ValueComparableTest(null, null),
                    new Int32ValueComparableTest(null, 0),
                    new Int32ValueComparableTest(null, 3),
                    new Int32ValueComparableTest(null, -2),
                    new Int32ValueComparableTest(null, -1)
            );
     }
    @Test
    void compareTo_Int64Value() {
        assertComparables(
                new Int64ValueComparableTest(null, null),
                new Int64ValueComparableTest(1L, null),
                new Int64ValueComparableTest(2L, null),
                new Int64ValueComparableTest(3L, null)
        );
    }
    @Test
    void compareTo_UInt64Value() {
        assertComparables(
                new Int64ValueComparableTest(null, null),
                new Int64ValueComparableTest(null, 0L),
                new Int64ValueComparableTest(null, 3L),
                new Int64ValueComparableTest(null, -2L),
                new Int64ValueComparableTest(null, -1L)
        );
    }
    @Test
    void compareTo_FloatValue() {
        assertComparables(
                new FloatValueComparableTest(null),
                new FloatValueComparableTest(1.0f),
                new FloatValueComparableTest(2.5f),
                new FloatValueComparableTest(7.7f));
    }
    @Test
    void compareTo_DoubleValue() {
        assertComparables(
                new DoubleValueComparableTest(null),
                new DoubleValueComparableTest(1.0),
                new DoubleValueComparableTest(2.5),
                new DoubleValueComparableTest(7.7));
    }
    @Test
    void compareTo_ByteValue() {
        assertComparables(
                new BytesValueComparableTest(null),
                new BytesValueComparableTest(Bytes.wrap("a")),
                new BytesValueComparableTest(Bytes.wrap("b")),
                new BytesValueComparableTest(Bytes.wrap("c")));
    }
    @Test
    void comareTo_unsigned() {
        assertComparables(
                new UnsignedComparableTest(0, 0L),
                new UnsignedComparableTest(3, 0L),
                new UnsignedComparableTest(-2, 0L),
                new UnsignedComparableTest(-1, 0L));
        assertComparables(
                new UnsignedComparableTest(0, 0L),
                new UnsignedComparableTest(0, 3L),
                new UnsignedComparableTest(0, -2L),
                new UnsignedComparableTest(0, -1L)
        );
    }
    @Test
     void limitedCompareTo_nonComparableOneOf() {
        // This code is only here to be compiled. OneOf field is not listed in `pbj.comparable` option
         // and therefore it has `OneOf` type, not `ComparableOneOf`.
        new LimitedComparableTestWithOneOf(0, new OneOf<>(LimitedComparableTestWithOneOf.OneOfExampleOneOfType.ONE_OF_SUB_OBJECT, new ComparableSubObj(1)));
     }

     @Test
     void limitedCompareTo_int32() {
         assertComparables(
                 new LimitedComparableTest(1, 0L, false, null, null, null),
                 new LimitedComparableTest(2, 0L, false, null, null, null),
                 new LimitedComparableTest(3, 0L, false, null, null, null));
     }

     @Test
     void limitedCompareTo_text() {
         assertComparables(
                 new LimitedComparableTest(0, 0L, false, "1", null, null),
                 new LimitedComparableTest(0, 0L, false, "2", null, null),
                 new LimitedComparableTest(0, 0L, false, "3", null, null));
     }

     @Test
     void limitedCompareTo_subObj() {
         assertComparables(
                 new LimitedComparableTest(0, 0L, false, null, null,  new ComparableSubObj(1)),
                 new LimitedComparableTest(0, 0L, false, null, null,  new ComparableSubObj(2)),
                 new LimitedComparableTest(0, 0L, false, null, null,  new ComparableSubObj(3)));
     }

     @Test
     void limitedCompareTo_mixed() {
        // note that only field 1, 4 and 6 are comparable, others are ignored
         assertComparables(
                 new LimitedComparableTest(1, nextLong(), nextBoolean(), "1", nextEnum(),  new ComparableSubObj(1)),
                 new LimitedComparableTest(1, nextLong(), nextBoolean(), "1", nextEnum(),  new ComparableSubObj(2)),
                 new LimitedComparableTest(1, nextLong(), nextBoolean(), "2", nextEnum(),  new ComparableSubObj(1)),
                 new LimitedComparableTest(1, nextLong(), nextBoolean(), "2", nextEnum(),  new ComparableSubObj(2)),
                 new LimitedComparableTest(2, nextLong(), nextBoolean(), "1", nextEnum(),  new ComparableSubObj(1)),
                 new LimitedComparableTest(2, nextLong(), nextBoolean(), "1", nextEnum(),  new ComparableSubObj(2)),
                 new LimitedComparableTest(2, nextLong(), nextBoolean(), "2", nextEnum(),  new ComparableSubObj(1)),
                 new LimitedComparableTest(2, nextLong(), nextBoolean(), "2", nextEnum(),  new ComparableSubObj(2))
         );
     }

     @Test
     void oneOfCompareTo() {
         assertComparables(
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.TEXT1_ONE_OF, "a"),
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.TEXT1_ONE_OF, "b"),
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.TEXT2_ONE_OF, "a"),
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.TEXT2_ONE_OF, "b"),
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.SUB_OBJECT, new ComparableSubObj(1)),
                 createOneOf(ComparableOneOfTest.OneofExampleOneOfType.SUB_OBJECT, new ComparableSubObj(2))
         );
     }

     private ComparableOneOfTest createOneOf(ComparableOneOfTest.OneofExampleOneOfType type, Comparable value) {
         return new ComparableOneOfTest(new ComparableOneOf<>(type, value));
     }

    private static long nextLong() {
        return RandomGenerator.getDefault().nextLong();
    }

    private static boolean nextBoolean() {
        return RandomGenerator.getDefault().nextBoolean();
    }

    private static ComparableEnum nextEnum() {
        return ComparableEnum.fromProtobufOrdinal(RandomGenerator.getDefault().nextInt(ComparableEnum.values().length));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertComparables(final Comparable... objs) {
        final var list = new ArrayList<Comparable>() {
            {
                for (Comparable<?> obj : objs) {
                    add(obj);
                }
            }
        };
        // randomize list first before sort it
        shuffle(list);
        sort(list);
        for (int i = 0; i < objs.length; i++) {
            assertEquals(objs[i], list.get(i), "obj[" + i + "] expected");
        }
    }
}
