package com.hedera.pbj.runtime.io.buffer;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * This class is providing access to some of the unsafe functionality for
 * directly allocated {@link ByteBuffer} objects.
 * It provides functions to query the VM for supporting the general functionality
 * and functions to get the memory offsetts of the {@link ByteBuffer} object, so a
 * direct and fast access to the data can be performed.
 * The implementation is inspired by the Google's Prrotobuf implementation.
 */
final class UnsafeUtils {
  private static final sun.misc.Unsafe UNSAFE = getUnsafe();
  private static final MemoryAccessor MEMORY_ACCESSOR = getMemoryAccessor();
  private static final boolean HAS_UNSAFE_BYTEBUFFER_OPERATIONS =
      supportsUnsafeByteBufferOperations();
  private static final boolean HAS_UNSAFE_ARRAY_OPERATIONS = supportsUnsafeArrayOperations();
  static final long BYTE_ARRAY_BASE_OFFSET = arrayBaseOffset(byte[].class);
  // Micro-optimization: we can assume a scale of 1 and skip the multiply
  // private static final long BYTE_ARRAY_INDEX_SCALE = 1;
  private static final long BUFFER_ADDRESS_OFFSET = fieldOffset(bufferAddressField());

  private UnsafeUtils() {}
  static boolean hasUnsafeByteBufferOperations() {
    return HAS_UNSAFE_BYTEBUFFER_OPERATIONS;
  }

  private static int arrayBaseOffset(Class<?> clazz) {
    return HAS_UNSAFE_ARRAY_OPERATIONS ? MEMORY_ACCESSOR.arrayBaseOffset(clazz) : -1;
  }
  static void copyMemory(byte[] src, long srcIndex, long targetOffset, long length) {
    MEMORY_ACCESSOR.copyMemory(src, srcIndex, targetOffset, length);
  }
  static void copyMemory(long srcOffset, byte[] target, long targetIndex, long length) {
    MEMORY_ACCESSOR.copyMemory(srcOffset, target, targetIndex, length);
  }

  static byte getByte(long address) {
    return MEMORY_ACCESSOR.getByte(address);
  }
  static void putByte(long address, byte value) {
    MEMORY_ACCESSOR.putByte(address, value);
  }
  /** Gets the offset of the {@code address} field of the given direct {@link ByteBuffer}. */
  static long addressOffset(ByteBuffer buffer) {
    return MEMORY_ACCESSOR.getLong(buffer, BUFFER_ADDRESS_OFFSET);
  }

  /**
   * Gets the {@code sun.misc.Unsafe} instance, or {@code null} if not available on this platform.
   */
  static sun.misc.Unsafe getUnsafe() {
    sun.misc.Unsafe unsafe = null;
    try {
      unsafe =
          AccessController.doPrivileged(
              new PrivilegedExceptionAction<sun.misc.Unsafe>() {
                @Override
                public sun.misc.Unsafe run() throws Exception {
                  Class<sun.misc.Unsafe> k = sun.misc.Unsafe.class;
                  for (Field f : k.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object x = f.get(null);
                    if (k.isInstance(x)) {
                      return k.cast(x);
                    }
                  }
                  // The sun.misc.Unsafe field does not exist.
                  return null;
                }
              });
    } catch (Throwable e) {
      // Catching Throwable here due to the fact that Google AppEngine raises NoClassDefFoundError
      // for Unsafe.
    }
    return unsafe;
  }
  /** Get a {@link MemoryAccessor} appropriate for the platform, or null if not supported. */
  private static MemoryAccessor getMemoryAccessor() {
    if (UNSAFE == null) {
      return null;
    }
    return new JvmMemoryAccessor(UNSAFE);
  }
  private static boolean supportsUnsafeArrayOperations() {
    if (MEMORY_ACCESSOR == null) {
      return false;
    }
    return MEMORY_ACCESSOR.supportsUnsafeArrayOperations();
  }
  private static boolean supportsUnsafeByteBufferOperations() {
    if (MEMORY_ACCESSOR == null) {
      return false;
    }
    return MEMORY_ACCESSOR.supportsUnsafeByteBufferOperations();
  }
  /** Finds the address field within a direct {@link Buffer}. */
  private static Field bufferAddressField() {
    Field field = field(Buffer.class, "address");
    return field != null && field.getType() == long.class ? field : null;
  }

  /**
   * Returns the offset of the provided field, or {@code -1} if {@code sun.misc.Unsafe} is not
   * available.
   */
  private static long fieldOffset(Field field) {
    return field == null || MEMORY_ACCESSOR == null ? -1 : MEMORY_ACCESSOR.objectFieldOffset(field);
  }
  /**
   * Gets the field with the given name within the class, or {@code null} if not found.
   */
  private static Field field(Class<?> clazz, String fieldName) {
    Field field;
    try {
      field = clazz.getDeclaredField(fieldName);
    } catch (Throwable t) {
      // Failed to access the fields.
      field = null;
    }
    return field;
  }
  private abstract static class MemoryAccessor {
    sun.misc.Unsafe unsafe;
    MemoryAccessor(sun.misc.Unsafe unsafe) {
      this.unsafe = unsafe;
    }
    public final long objectFieldOffset(Field field) {
      return unsafe.objectFieldOffset(field);
    }
    public final int arrayBaseOffset(Class<?> clazz) {
      return unsafe.arrayBaseOffset(clazz);
    }

    /** Relative Address Operations ---------------------------------------------
     * Indicates whether the following relative address operations are supported
     * by this memory accessor.
     */
    public boolean supportsUnsafeArrayOperations() {
      if (unsafe == null) {
      return false;
      }
      try {
        Class<?> clazz = unsafe.getClass();
        clazz.getMethod("objectFieldOffset", Field.class);
        clazz.getMethod("arrayBaseOffset", Class.class);
        clazz.getMethod("arrayIndexScale", Class.class);
        clazz.getMethod("getInt", Object.class, long.class);
        clazz.getMethod("putInt", Object.class, long.class, int.class);
        clazz.getMethod("getLong", Object.class, long.class);
        clazz.getMethod("putLong", Object.class, long.class, long.class);
        clazz.getMethod("getObject", Object.class, long.class);
        clazz.getMethod("putObject", Object.class, long.class, Object.class);
        return true;
      } catch (Throwable e) {
        // TODO: Log
      }
      return false;
    }
    public final long getLong(Object target, long offset) {
      return unsafe.getLong(target, offset);
    }

   /** Absolute Address Operations --------------------------------------------
    * Indicates whether the following absolute address operations are
    * supported by this memory accessor.
    */
    public boolean supportsUnsafeByteBufferOperations() {
      if (unsafe == null) {
        return false;
      }
      try {
        Class<?> clazz = unsafe.getClass();
        // Methods for getting direct buffer address.
        clazz.getMethod("objectFieldOffset", Field.class);
        clazz.getMethod("getLong", Object.class, long.class);
        if (bufferAddressField() == null) {
          return false;
        }
        return true;
      } catch (Throwable e) {
        // TODO: Log
      }
      return false;
    }
    public abstract byte getByte(long address);
    public abstract void putByte(long address, byte value);
    public abstract void copyMemory(long srcOffset, byte[] target, long targetIndex, long length);
    public abstract void copyMemory(byte[] src, long srcIndex, long targetOffset, long length);
  }
  private static final class JvmMemoryAccessor extends MemoryAccessor {
    JvmMemoryAccessor(sun.misc.Unsafe unsafe) {
      super(unsafe);
    }
    @Override
    public boolean supportsUnsafeArrayOperations() {
      if (!super.supportsUnsafeArrayOperations()) {
        return false;
      }
      try {
        Class<?> clazz = unsafe.getClass();
        clazz.getMethod("getByte", Object.class, long.class);
        clazz.getMethod("putByte", Object.class, long.class, byte.class);
        clazz.getMethod("getBoolean", Object.class, long.class);
        clazz.getMethod("putBoolean", Object.class, long.class, boolean.class);
        clazz.getMethod("getFloat", Object.class, long.class);
        clazz.getMethod("putFloat", Object.class, long.class, float.class);
        clazz.getMethod("getDouble", Object.class, long.class);
        clazz.getMethod("putDouble", Object.class, long.class, double.class);
        return true;
      } catch (Throwable e) {
        // TODO: Log
      }
      return false;
    }
    @Override
    public boolean supportsUnsafeByteBufferOperations() {
      if (!super.supportsUnsafeByteBufferOperations()) {
        return false;
      }
      try {
        Class<?> clazz = unsafe.getClass();
        clazz.getMethod("getByte", long.class);
        clazz.getMethod("putByte", long.class, byte.class);
        clazz.getMethod("getInt", long.class);
        clazz.getMethod("putInt", long.class, int.class);
        clazz.getMethod("getLong", long.class);
        clazz.getMethod("putLong", long.class, long.class);
        clazz.getMethod("copyMemory", long.class, long.class, long.class);
        clazz.getMethod(
            "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
        return true;
      } catch (Throwable e) {
        // TODO: Log
      }
      return false;
    }
    @Override
    public byte getByte(long address) {
      return unsafe.getByte(address);
    }
    @Override
    public void putByte(long address, byte value) {
      unsafe.putByte(address, value);
    }
    @Override
    public void copyMemory(long srcOffset, byte[] target, long targetIndex, long length) {
      unsafe.copyMemory(null, srcOffset, target, BYTE_ARRAY_BASE_OFFSET + targetIndex, length);
    }
    @Override
    public void copyMemory(byte[] src, long srcIndex, long targetOffset, long length) {
      unsafe.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcIndex, null, targetOffset, length);
    }
  }

}