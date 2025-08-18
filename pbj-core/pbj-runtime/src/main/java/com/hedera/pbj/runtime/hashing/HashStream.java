// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

import java.util.*;

interface HashStream extends HashSink {

    @Override
    HashStream putByte(byte v);

    @Override
    HashStream putBytes(byte[] x);

    @Override
    HashStream putBytes(byte[] x, int off, int len);

    @Override
    HashStream putByteArray(byte[] x);

    @Override
    HashStream putBoolean(boolean v);

    @Override
    HashStream putBooleans(boolean[] x);

    @Override
    HashStream putBooleans(boolean[] x, int off, int len);

    @Override
    HashStream putBooleanArray(boolean[] x);

    @Override
    HashStream putShort(short v);

    @Override
    HashStream putShorts(short[] x);

    @Override
    HashStream putShorts(short[] x, int off, int len);

    @Override
    HashStream putShortArray(short[] x);

    @Override
    HashStream putChar(char v);

    @Override
    HashStream putChars(char[] x);

    @Override
    HashStream putChars(char[] x, int off, int len);

    @Override
    HashStream putChars(CharSequence c);

    @Override
    HashStream putCharArray(char[] x);

    @Override
    HashStream putString(String s);

    @Override
    HashStream putInt(int v);

    @Override
    HashStream putInts(int[] x);

    @Override
    HashStream putInts(int[] x, int off, int len);

    @Override
    HashStream putIntArray(int[] x);

    @Override
    HashStream putLong(long v);

    @Override
    HashStream putLongs(long[] x);

    @Override
    HashStream putLongs(long[] x, int off, int len);

    @Override
    HashStream putLongArray(long[] x);

    @Override
    HashStream putFloat(float v);

    @Override
    HashStream putFloats(float[] x);

    @Override
    HashStream putFloats(float[] x, int off, int len);

    @Override
    HashStream putFloatArray(float[] x);

    @Override
    HashStream putDouble(double v);

    @Override
    HashStream putDoubles(double[] x);

    @Override
    HashStream putDoubles(double[] x, int off, int len);

    @Override
    HashStream putDoubleArray(double[] x);

    @Override
    HashStream putOptionalInt(OptionalInt v);

    @Override
    HashStream putOptionalLong(OptionalLong v);

    @Override
    HashStream putOptionalDouble(OptionalDouble v);

    /**
     * Resets the hash stream.
     *
     * <p>This allows to reuse this instance for new hash computations.
     *
     * @return this
     */
    HashStream reset();
}
