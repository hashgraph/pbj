// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The base of every generated message writer ({@code Xxx.Writer}, from {@code pbj.writers}): a reusable flyweight that
 * encodes one message at a time into a {@link ProtoWriteBuffer}, as its field methods are called. It keeps the state
 * that makes misuse throw instead of producing bytes that differ from the model's encoding:
 * <ul>
 *     <li>Fields must be written in field-number order, each singular field at most once. The elements of a repeated
 *     field are written by consecutive calls, with nothing else in between.</li>
 *     <li>At most one alternative of each oneof may be written.</li>
 *     <li>A writer can only be written to between its start (or its parent opening it) and its {@link #end()}.</li>
 * </ul>
 *
 * <p><b>Nesting and auto-close.</b> A method on a parent writer that opens a nested message returns the child's writer,
 * which is open until it ends. Calling any method on a writer first ends every message still open inside it, so a
 * child's {@code end()} may be left out: {@code order.price().amount(5); order.memo("x");} ends the price before writing
 * the memo. Ending a parent ends its open children the same way. Child writers are created once and reused: the writer
 * a method returns is only valid until the next call on its parent, after which it has ended, or is reused for the next
 * element of a repeated field.
 *
 * <p><b>Encoded bytes.</b> After a message ends, {@link #offset()} and {@link #length()} give its encoded bytes, without
 * its tag and length prefix, in {@link ProtoWriteBuffer#array()}: for example to hash a block item, or send it, before
 * the enclosing block is finished. They stay valid until the buffer is reset, with one exception: when a nested message
 * ends, it may need a larger or smaller length prefix than was reserved, and then its bytes move, including those of the
 * messages that ended inside it. The direct children of a top-level message never move, since a top-level message has
 * no length prefix. Read a deeper message's range when it ends, or once its top-level ancestor's child has ended.
 *
 * <p><b>Cost.</b> Checking a field costs one compare against {@link #guard}, which is the last field number written
 * while nothing is open inside this message, and {@link Integer#MAX_VALUE} while a child is open or this writer is not:
 * then the check takes a slow path that ends the child, or throws. The methods generated writers call are
 * {@code protected}. Not thread safe.
 */
public abstract class MessageWriter {
    /** {@link #guard} while the field check must take the slow path: a child is open, or this writer is not. */
    private static final int SLOW = Integer.MAX_VALUE;

    /** Not started yet. */
    private static final byte NEW = 0;
    /** Started, or opened by a parent, and not ended. */
    private static final byte OPEN = 1;
    /** Open, with every field written by {@code writeFrom}: only {@link #end()} may follow. */
    private static final byte WRITTEN = 2;
    /** Ended, or abandoned by a buffer reset. */
    private static final byte ENDED = 3;

    /** The buffer being written to, while open; generated field methods write to it directly. */
    protected ProtoWriteBuffer out;
    /** The last field number written, or {@link #SLOW}: every field check compares with it, and nothing else. */
    private int guard = SLOW;
    /** The last field number written, kept while a child is open and {@link #guard} is {@link #SLOW}. */
    private int lastField;
    /** One bit per oneof of the message, set once one of its alternatives has been written. */
    private long oneofs;

    private byte state = NEW;
    /** The writer of the nested message opened last, which may still be open, or null. */
    private MessageWriter child;
    /** The writer of the message this one is nested in, while open, or null for a top-level message. */
    private MessageWriter parent;
    /** Where this message's bytes start, after the bytes reserved for its length prefix. */
    private int start;
    /** The number of bytes reserved for the length prefix, 0 for a top-level message. */
    private int reserved;
    /** Where the field's tag starts, for a packed field, which is dropped when it ends empty; otherwise -1. */
    private int tagStart = -1;
    /** The size of the last length prefix written for this writer, the adaptive guess for the next one; 0 if none. */
    private int lastLengthSize;

    private int offset = -1;
    private int length = -1;

    /** Create a writer, which must be started, or opened by its parent, before use. */
    protected MessageWriter() {}

    // ------------------------------------------------------------------------------------------------- lifecycle

    /**
     * Start writing a top-level message at the buffer's current position. The buffer must have no message open: write
     * one top-level message after another, or {@link ProtoWriteBuffer#reset()} first to write one on its own.
     *
     * @param out the buffer to write to
     * @throws IllegalStateException if a message is still open in the buffer
     */
    protected final void startTopLevel(@NonNull final ProtoWriteBuffer out) {
        if (out.top != null && out.top.isOpen()) {
            throw new IllegalStateException("A message is still being written to this buffer: end it, or reset the"
                    + " buffer, before starting another top-level message");
        }
        out.top = this;
        this.out = out;
        parent = null;
        reserved = 0;
        start = out.pos;
        guard = 0;
        oneofs = 0;
        state = OPEN;
    }

    /**
     * Open a nested message: write the field's tag, reserve bytes for its length, and start the child writer after
     * them. Called by a parent writer once it has checked the field.
     *
     * @param child the nested message's writer
     * @param perTypeReserve the per-type guess at the size of the length prefix, 1 or 2
     * @param tag the field's tag, wire type 2
     */
    protected final void openChild(@NonNull final MessageWriter child, final int perTypeReserve, final int tag) {
        final ProtoWriteBuffer out = this.out;
        open(
                child,
                out.adaptive && child.lastLengthSize != 0 ? child.lastLengthSize : out.oneByte ? 1 : perTypeReserve,
                tag);
    }

    /**
     * Open a nested message whose encoded length is known, such as a model object's {@code protobufSize()}, so the
     * length prefix is reserved exactly and the message's bytes never move.
     *
     * @param child the nested message's writer
     * @param length the message's encoded length
     * @param tag the field's tag, wire type 2
     */
    protected final void openChildSized(@NonNull final MessageWriter child, final int length, final int tag) {
        open(child, ProtoWriteBuffer.varintSize(length), tag);
    }

    private void open(final MessageWriter child, final int reserve, final int tag) {
        // the parent's next field check takes the slow path, which ends the child
        lastField = guard;
        guard = SLOW;
        if (this.child != child) {
            this.child = child;
        }
        final ProtoWriteBuffer out = this.out;
        if (out.limit - out.pos < 5 + reserve) {
            out.grow(5 + reserve);
        }
        final byte[] b = out.buf;
        int p = out.pos;
        if ((tag & ~0x7F) == 0) {
            b[p++] = (byte) tag;
        } else {
            p = ProtoWriteBuffer.tagWide(b, p, tag);
        }
        p += reserve;
        out.pos = p;
        if (child.out != out) {
            child.out = out;
        }
        if (child.parent != this) {
            child.parent = this;
        }
        child.reserved = reserve;
        child.start = p;
        child.guard = 0;
        child.oneofs = 0;
        child.state = OPEN;
    }

    /**
     * Open a packed repeated field, writing its tag: called by a parent writer once it has checked the field.
     *
     * @param packed the packed field's writer
     * @param tag the field's tag, wire type 2
     */
    protected final void openPacked(@NonNull final MessageWriter packed, final int tag) {
        final int at = out.pos;
        openChild(packed, 1, tag);
        packed.tagStart = at;
    }

    /**
     * Finish this message: end every message still open inside it, then write its length prefix if it is nested.
     *
     * @return the message's encoded length, without its tag and length prefix
     * @throws IllegalStateException if the writer is not open
     */
    public final int end() {
        if (guard == SLOW && state != WRITTEN) {
            if (state != OPEN) {
                throw new IllegalStateException(notOpen());
            }
            closeInside();
        }
        close();
        return length;
    }

    /** End the message open inside this one, if there is one, and any open inside it. */
    private void closeInside() {
        final MessageWriter c = child;
        if (c != null && (c.state == OPEN || c.state == WRITTEN)) {
            if (c.guard == SLOW) {
                c.closeInside();
            }
            c.close();
        }
    }

    /** Close this message, once nothing is open inside it. */
    private void close() {
        final int size = out.pos - start;
        if (size == 0 && tagStart >= 0) {
            // an empty packed field is not written at all, as the model writes it
            out.pos = tagStart;
            offset = tagStart;
        } else if (reserved == 1 && size < 0x80) {
            // the common case: a one-byte length, guessed right
            out.buf[start - 1] = (byte) size;
            offset = start;
            lastLengthSize = 1;
        } else if (reserved == 0) {
            offset = start;
        } else {
            closeLength(size);
        }
        length = size;
        guard = SLOW;
        state = ENDED;
        // the parent can write its next field without checking for an open child
        final MessageWriter p = parent;
        if (p != null) {
            p.guard = p.lastField;
        }
    }

    /** Write a length prefix that is not a correctly guessed single byte. */
    private void closeLength(final int size) {
        if (reserved == 2 && size >= 0x80 && size < 0x4000) {
            out.buf[start - 2] = (byte) (size | 0x80);
            out.buf[start - 1] = (byte) (size >>> 7);
            offset = start;
            lastLengthSize = 2;
        } else {
            offset = out.moveAndWriteLength(start, reserved, size);
            lastLengthSize = ProtoWriteBuffer.varintSize(size);
        }
    }

    /** Drop this writer's message, and any open inside it, which the buffer has discarded. */
    final void abandon() {
        if (state == OPEN || state == WRITTEN) {
            if (child != null) {
                child.abandon();
            }
            guard = SLOW;
            state = ENDED;
            offset = -1;
            length = -1;
        }
    }

    /** @return true if this writer has been started or opened and has not ended */
    public final boolean isOpen() {
        return state == OPEN || state == WRITTEN;
    }

    /**
     * @return where this message's encoded bytes, without tag and length prefix, start in the buffer's array
     * @throws IllegalStateException if the message has not ended
     */
    public final int offset() {
        if (state != ENDED || length < 0) {
            throw new IllegalStateException("A message's offset is known once it has ended");
        }
        return offset;
    }

    /**
     * @return the length of this message's encoded bytes, without tag and length prefix
     * @throws IllegalStateException if the message has not ended
     */
    public final int length() {
        if (state != ENDED || length < 0) {
            throw new IllegalStateException("A message's length is known once it has ended");
        }
        return length;
    }

    // ------------------------------------------------------------------------------------------- field checks

    /**
     * Check that a singular field may be written now, ending any message still open inside this one.
     *
     * @param number the field number
     * @throws IllegalStateException if this writer is not open, or a field with this or a higher number was written
     */
    protected final void checkField(final int number) {
        if (number <= guard) {
            checkSlow(number, false);
        }
        guard = number;
    }

    /**
     * Check that an element of a repeated field may be written now, ending any message still open inside this one.
     *
     * @param number the field number
     * @throws IllegalStateException if this writer is not open, or a field with a higher number was written
     */
    protected final void checkRepeatedField(final int number) {
        if (number < guard) {
            checkSlow(number, true);
        }
        guard = number;
    }

    /**
     * Whether the next element of a repeated field is its first: call before {@link #checkRepeatedField}.
     *
     * @param number the field number
     * @return true if the last field written was not this one
     */
    protected final boolean isFirstElement(final int number) {
        return (guard == SLOW ? lastField : guard) != number;
    }

    /**
     * Check that this writer is open, for writers whose elements have no field numbers, such as packed fields.
     *
     * @throws IllegalStateException if this writer is not open
     */
    protected final void checkOpen() {
        if (state != OPEN) {
            throw new IllegalStateException(notOpen());
        }
    }

    private void checkSlow(final int number, final boolean repeated) {
        int last = guard;
        if (last == SLOW && state == OPEN) {
            // a nested message was opened: end it, if it is still open, first. Usually it has nothing open inside
            // it, and ending it is all this call does.
            final MessageWriter c = child;
            if (c.state == OPEN && c.guard != SLOW) {
                c.close();
            } else {
                closeInside();
            }
            last = lastField;
            guard = last;
        }
        if (number < last || (number == last && !repeated)) {
            throw fieldError(number, repeated, last);
        }
    }

    private IllegalStateException fieldError(final int number, final boolean repeated, final int last) {
        if (state == WRITTEN) {
            return new IllegalStateException("No field can be written after writeFrom, which writes every field");
        }
        if (state != OPEN) {
            return new IllegalStateException(notOpen());
        }
        return new IllegalStateException(("Field %d written after field %d: fields must be written in field-number"
                        + " order, each singular field at most once, and the elements of a repeated field one after"
                        + " another")
                .formatted(number, last));
    }

    /**
     * Check that no alternative of a oneof has been written yet.
     *
     * @param bit the oneof's bit, {@code 1L << index}
     * @param name the oneof's name, for the error message
     * @throws IllegalStateException if an alternative was already written
     */
    protected final void checkOneof(final long bit, final String name) {
        if ((oneofs & bit) != 0) {
            throw new IllegalStateException("Only one alternative of oneof " + name + " can be written");
        }
        oneofs |= bit;
    }

    /**
     * Prepare to write every field from a model object, with the field methods: the writer must be open with nothing
     * written yet.
     *
     * @throws IllegalStateException if the writer is not open or a field has been written
     */
    protected final void beginWriteFrom() {
        if (guard != 0) {
            if (state != OPEN) {
                throw new IllegalStateException(notOpen());
            }
            throw new IllegalStateException("writeFrom writes every field, so it must be called before any other");
        }
    }

    /** Finish writing every field from a model object: end any open nested message, and allow no more fields. */
    protected final void finishWriteFrom() {
        if (guard == SLOW) {
            closeInside();
        }
        guard = SLOW;
        state = WRITTEN;
    }

    private String notOpen() {
        return state == NEW
                ? "This writer has not been started: call start(buffer), or get it from its parent writer"
                : "This writer's message has ended (or its buffer was reset): a child writer is valid until the next"
                        + " call on its parent";
    }
}
