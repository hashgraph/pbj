package com.hedera.pbj.intergration.test;

import static java.util.Collections.shuffle;
import static java.util.Collections.sort;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.ComparableEnum;
import com.hedera.pbj.test.proto.pbj.ComparableSubObj;
import com.hedera.pbj.test.proto.pbj.ComparableTest;
import com.hedera.pbj.test.proto.pbj.LimitedComparableTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Unit test for {@link ComparableTest} and {@link LimitedComparableTest} objects.
 * The goal is to test the generated {@link Comparable} interface implementation.
 */
public class CompareToTest {

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
                new ComparableTest(0, 0.0, false, null, Bytes.wrap("1"), null, null),
                new ComparableTest(0, 0.0, false, null, Bytes.wrap("12"), null, null),
                new ComparableTest(0, 0.0, false, null, Bytes.wrap("123"), null, null)
        );
    }

    @Test
    void testCompareTo_bytes_same_lenth() {
        final var test1 = new ComparableTest(0, 0.0, false, null, Bytes.wrap("1"), null, null);
        final var test2 = new ComparableTest(0, 0.0, false, null, Bytes.wrap("2"), null, null);
        final var test3 = new ComparableTest(0, 0.0, false, null, Bytes.wrap("3"), null, null);

        final var list = new ComparableTest[] {test2, test3, test1};
        Arrays.sort(list);
        // if the length is the same, then the element are not reordered
        assertEquals(test1, list[2], "test1 expected");
        assertEquals(test2, list[0], "test2 expected");
        assertEquals(test3, list[1], "test3 expected");
    }

    @Test
    void testCompareTo_enum(){
        assertComparables(
                new ComparableTest(0, 0.0, false, null, null, ComparableEnum.ONE, null),
                new ComparableTest(0, 0.0, false, null, null, ComparableEnum.TWO, null),
                new ComparableTest(0, 0.0, false, null, null, ComparableEnum.THREE, null)
        );
    }

    @Test
    void testCompareTo_subObject(){
        assertComparables(
                new ComparableTest(0, 0.0, false, null, null, null, new ComparableSubObj(1)),
                new ComparableTest(0, 0.0, false, null, null, null,  new ComparableSubObj(2)),
                new ComparableTest(0, 0.0, false, null, null, null,  new ComparableSubObj(3))
        );
    }

     @Test
     void compareTo_mixed() {
         assertComparables(
                 new ComparableTest(1, 0.0, false, null, null, null, new ComparableSubObj(1)),
                 new ComparableTest(1, 0.0, false, null, null, null, new ComparableSubObj(2)),
                 new ComparableTest(2, 0.0, false, null, null, null, new ComparableSubObj(1)),
                 new ComparableTest(2, 0.0, false, null, null, null,  new ComparableSubObj(2))
         );
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

    private static long nextLong() {
        return  RandomGenerator.getDefault().nextLong();
    }

    private static boolean nextBoolean() {
        return  RandomGenerator.getDefault().nextBoolean();
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
