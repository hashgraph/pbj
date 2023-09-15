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
 * and functions to get the memory offsets of the {@link ByteBuffer} object, so a
 * direct and fast access to the data can be performed.
 * The implementation is inspired by the Google's Protobuf implementation.
 */
final class UnsafeUtils {

  /** A static {@link sun.misc.Unsafe} object */
  private static final sun.misc.Unsafe UNSAFE = getUnsafe();

  /** A static {@link MemoryAccessor} object*/
  private static final MemoryAccessor MEMORY_ACCESSOR = getMemoryAccessor();

  /** A static flag indicating if the VM supports unsafe {@link ByteBuffer} operations */
  private static final boolean HAS_UNSAFE_BYTEBUFFER_OPERATIONS =
      supportsUnsafeByteBufferOperations();

  /** A static flag indicating if the VM supports unsafe array operations */
  private static final boolean HAS_UNSAFE_ARRAY_OPERATIONS = supportsUnsafeArrayOperations();

  /** The unsafe native address of a byte array */
  static final long BYTE_ARRAY_BASE_OFFSET = arrayBaseOffset(byte[].class);

  /** The unsafe native address of a {@link ByteBuffer} object */
  private static final long BUFFER_ADDRESS_OFFSET = fieldOffset(bufferAddressField());

  /** Constructor for UnsafeUtils object */
  private UnsafeUtils() {}

  /**
   * Returns if the VM supports unsafe {@link ByteBuffer} operations
   *
   * @return true if there is support, otherwise false
   */
  static boolean hasUnsafeByteBufferOperations() {
    return HAS_UNSAFE_BYTEBUFFER_OPERATIONS;
  }

  /**
   * Returns the base address in native memory for an array object
   *
   * @param clazz The class for which we want the base address
   * @return The base address of the array object or null, if not supported
   */
  private static int arrayBaseOffset(Class<?> clazz) {
    return HAS_UNSAFE_ARRAY_OPERATIONS ? MEMORY_ACCESSOR.arrayBaseOffset(clazz) : -1;
  }

  /**
   * Copies direct memory segment to unsafe buffer.
   *
   * @param src The memory chunk to copy.
   * @param srcIndex The start offset for the copy operation.
   * @param targetOffset The start offset of the target memory to copy to.
   * @param length The amounts of bytes to copy.
   */
  static void copyMemory(byte[] src, long srcIndex, long targetOffset, long length) {
    MEMORY_ACCESSOR.copyMemory(src, srcIndex, targetOffset, length);
  }

  /**
   * Copies direct memory segment to unsafe buffer.
   *
   * @param srcOffset The start offset to copy from
   * @param target The target buffer where to copy the data
   * @param targetIndex The start offset of the target data where to copy to
   * @param length The length of the copy operation.
   */
  static void copyMemory(long srcOffset, byte[] target, long targetIndex, long length) {
    MEMORY_ACCESSOR.copyMemory(srcOffset, target, targetIndex, length);
  }

  /**
   * Get a byte value from a native buffer.
   *
   * @param address The address where to get the byte value from.
   * @return The value to at index address in the native memory.
   */
  static byte getByte(long address) {
    return MEMORY_ACCESSOR.getByte(address);
  }

  /**
   * Stores a byte
   *
   * @param address The address where to store the byte to
   * @param value The value to store
   */
  static void putByte(long address, byte value) {
    MEMORY_ACCESSOR.putByte(address, value);
  }

  /**
   * Gets the offset of the {@code address} field of the given direct {@link ByteBuffer}.
   *
   * @param buffer The {@link ByteBuffer} object for wich to get the native base pointer
   */
  static long addressOffset(ByteBuffer buffer) {
    return MEMORY_ACCESSOR.getLong(buffer, BUFFER_ADDRESS_OFFSET);
  }

  /**
   * Gets the {@code sun.misc.Unsafe} instance, or {@code null} if not available on this platform.
   *
   * @return A valid {@link sun.misc.Unsafe} object or null.
   */
  @SuppressWarnings("removal")
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
  /**
   * Get a {@link MemoryAccessor} appropriate for the platform, or null if not supported.
   *
   * @return A valid MemoryAccessor object for this VM or null
   */
  private static MemoryAccessor getMemoryAccessor() {
    if (UNSAFE == null) {
      return null;
    }
    return new JvmMemoryAccessor(UNSAFE);
  }

  /**
   * A method that returns if the VM supports unsafe array operations
   *
   * @return true if the VM supports unsafe array operations, otherwise false
   */
  private static boolean supportsUnsafeArrayOperations() {
    if (MEMORY_ACCESSOR == null) {
      return false;
    }
    return MEMORY_ACCESSOR.supportsUnsafeArrayOperations();
  }

  /**
   * A method that returns if the VM supports unsafe {@link ByteBuffer} operations
   *
   * @return true if the VM supports unsafe {@link ByteBuffer} operations otherwise false
   */
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
   *
   * @param field The {@link Field} on which we need to get the memory offset for
   */
  private static long fieldOffset(Field field) {
    return field == null || MEMORY_ACCESSOR == null ? -1 : MEMORY_ACCESSOR.objectFieldOffset(field);
  }

  /**
   * Gets the field with the given name within the class, or {@code null} if not found.
   *
   * @param clazz The class on which to get the field from
   * @param fieldName The name of the field that should be looked up.
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

  /** An abstract Unsafe Memory Accessor class */
  private abstract static class MemoryAccessor {
    sun.misc.Unsafe unsafe;

    /**
     * Constructor for unsafe memory accessor.
     * @param unsafe The unsafe memory access object.
     */
    MemoryAccessor(sun.misc.Unsafe unsafe) {
      this.unsafe = unsafe;
    }

    /**
     * Gets the object field offset in the native memory.
     *
     * @param field The field of which to get the offset of.
     * @return The offset
     */
    public final long objectFieldOffset(Field field) {
      return unsafe.objectFieldOffset(field);
    }

    /**
     * Gets the array object offset in the native memory.
     *
     * @param clazz The clazz of which to get the array offset of.
     * @return The offset in the base memory
     */
    public final int arrayBaseOffset(Class<?> clazz) {
      return unsafe.arrayBaseOffset(clazz);
    }

    /**
     * Indicates whether the unsafe array operations are supported
     * by this memory accessor (VM).
     *
     * @return true if supported, otherwise false
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

    /**
     * Gets a long value from native memory
     *
     * @param target The target object
     * @param offset The offset to get the long value from
     * @return The long value of target at offset
     */
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

    /**
     * Get a byte value from a native buffer.
     *
     * @param address The address where to get the byte value from.
     * @return The value to at index address in the native memory.
     */
    public abstract byte getByte(long address);

    /**
     * Stores a byte
     *
     * @param address The address where to store the byte to
     * @param value The value to store
     */
    public abstract void putByte(long address, byte value);

    /**
     * * Copies direct memory segment to unsafe buffer.
     *
     * @param srcOffset The start offset to copy from
     * @param target The target buffer where to copy the data
     * @param targetIndex The start offset of the target data where to copy to
     * @param length The length of the copy operation.
     */
    public abstract void copyMemory(long srcOffset, byte[] target, long targetIndex, long length);

    /**
     * Copies direct memory segment to unsafe buffer.
     *
     * @param src The memory chunk to copy.
     * @param srcIndex The start offset for the copy operation.
     * @param targetOffset The start offset of the target memory to copy to.
     * @param length The amounts of bytes to copy.
     */
    public abstract void copyMemory(byte[] src, long srcIndex, long targetOffset, long length);
  }

  /**
   * A JavaMemory unsafe accessor. It provides access to the Java VM Memory,
   * if the VM supports unsafe operations.
   */
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

    /** {@inheritDoc} */
    @Override
    public byte getByte(long address) {
      return unsafe.getByte(address);
    }

    /** {@inheritDoc} */
    @Override
    public void putByte(long address, byte value) {
      unsafe.putByte(address, value);
    }

    /** {@inheritDoc} */
    @Override
    public void copyMemory(long srcOffset, byte[] target, long targetIndex, long length) {
      unsafe.copyMemory(null, srcOffset, target, BYTE_ARRAY_BASE_OFFSET + targetIndex, length);
    }

    /** {@inheritDoc} */
    @Override
    public void copyMemory(byte[] src, long srcIndex, long targetOffset, long length) {
      unsafe.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcIndex, null, targetOffset, length);
    }
  }

}