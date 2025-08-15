// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * An array that counts occurrences of indices in the range [0, 4,294,967,295]. It uses 4 byte arrays to store counts
 * up to 250 and an overflow map for counts above 250.
 */
public final class CountingArray {
    /** Maximum value for the index, 2^32 */
    private static final long MAX_VALUE = 4_294_967_296L; // 2^32
    /** 4x 1 GB arrays to split the integer space into 4 parts */
    private final byte[][] counts = new byte[4][1_073_741_824];
    /** Overflow map for counts above 250 */
    private final Map<Long, Integer> overflowMap = new HashMap<>();

    /**
     * Clears all the counts
     */
    public void clear() {
        for (byte[] subArray : counts) {
            Arrays.fill(subArray, (byte) 0);
        }
        overflowMap.clear();
    }

    /**
     * Returns the number of counts greater than zero across all indices.
     * This includes counts in the overflow map.
     *
     * @return the number of counts greater than zero
     */
    public long numberOfGreaterThanZeroCounts() {
        long count = Arrays.stream(counts)
                .parallel()
                .mapToLong(subArray ->
                        // Count values > 0 and <= 250 in each subArray
                        IntStream.range(0, subArray.length)
                                .map(i -> Byte.toUnsignedInt(subArray[i]))
                                .filter(unsignedValue -> unsignedValue > 0 && unsignedValue <= 250)
                                .count())
                .sum();
        return count
                + overflowMap.values().stream().mapToLong(Integer::longValue).sum();
    }

    /**
     * Returns the number of counts greater than zero across all indices.
     * This includes counts in the overflow map.
     *
     * @return the number of counts greater than one
     */
    public long numberOfGreaterThanOneCounts() {
        long count = Arrays.stream(counts)
                .parallel()
                .mapToLong(subArray ->
                        // Count values > 1 and <= 250 in each subArray
                        IntStream.range(0, subArray.length)
                                .map(i -> Byte.toUnsignedInt(subArray[i]))
                                .filter(unsignedValue -> unsignedValue > 1 && unsignedValue <= 250)
                                .count())
                .sum();
        return count
                + overflowMap.values().stream().mapToLong(Integer::longValue).sum();
    }

    /**
     * Returns the number of 0 counts across all indices.
     *
     * @return the number of zero counts
     */
    public long numberOfZeroCounts() {
        long count = 0;
        for (byte[] subArray : counts) {
            for (byte b : subArray) {
                if (b == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Increments the count for the given index.
     *
     * @param index the index to increment, must be in the range [0, 4,294,967,295]
     */
    public void increment(long index) {
        if (index < 0 || index >= MAX_VALUE) {
            throw new IndexOutOfBoundsException("index: " + index);
        }
        int subArrayIndex = (int) (index >>> 30); // 2^30 = 1 GB
        int indexInSubArray = (int) (index & 0x3FFFFFFF); // 2^30 - 1
        byte[] subArray = counts[subArrayIndex];
        int currentValueUnsigned = Byte.toUnsignedInt(subArray[indexInSubArray]);
        if (currentValueUnsigned <= 250) {
            // Increment the count in the sub-array using value as unsigned byte
            final int newValueUnsigned = (currentValueUnsigned + 1) & 0xFF; // wrap at 255
            subArray[indexInSubArray] = (byte) newValueUnsigned;
        } else {
            // Handle overflow
            subArray[indexInSubArray] = Byte.MIN_VALUE; // marker for overflow
            overflowMap.compute(index, (key, value) -> value == null ? 250 : value + 1);
        }
    }

    /**
     * Prints the statistics of the counts, including the number of occurrences for each value from 0 to 250,
     * and the overflow counts.
     */
    public void printStats(final StringBuilder resultStr) {
        // count up number of bytes with each value 0 to 250
        long[] valueCounts = new long[251]; // 0 to 250
        for (byte[] subArray : counts) {
            for (byte b : subArray) {
                int unsignedValue = Byte.toUnsignedInt(b);
                if (unsignedValue <= 250) {
                    valueCounts[unsignedValue]++;
                }
            }
        }
        // print the counts
        resultStr.append("       Counts:");
        for (int i = 0; i <= 250; i++) {
            long count = valueCounts[i];
            if (count > 0) {
                resultStr.append(String.format("  %d=%,d", i, count));
            }
        }
        // print overflow map sorted by index
        resultStr.append("\n       Overflow counts: " + overflowMap.size());
        //        overflowMap.entrySet().stream()
        //                .sorted(Map.Entry.comparingByKey())
        //                .forEach(entry -> resultStr.append(String.format("  %d=%,d", entry.getKey(),
        // entry.getValue())));
        resultStr.append("\n");
    }
}
