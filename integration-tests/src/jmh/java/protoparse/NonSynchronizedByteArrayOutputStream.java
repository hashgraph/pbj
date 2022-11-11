package protoparse;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Faster non-synchronized ByteArrayOutputStream
 */
public final class NonSynchronizedByteArrayOutputStream extends OutputStream {
    private byte[] buf;
    private int count;

    public NonSynchronizedByteArrayOutputStream() {
        this(32);
    }

    public NonSynchronizedByteArrayOutputStream(int size) {
        if (size < 0) throw new IllegalArgumentException();
        buf = new byte[size];
    }

    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            buf = Arrays.copyOf(buf, newLength(oldCapacity,
                    minGrowth, oldCapacity /* preferred growth */));
        }
    }

    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        return oldLength + Math.max(minGrowth, prefGrowth);
    }

    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    public void write(byte b[], int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void reset() {
        count = 0;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public void close() {
    }
}
