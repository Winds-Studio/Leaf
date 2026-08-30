/*
 * Copyright (c) 2018 Aron Wieck Crown Communications GmbH
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package org.dreeam.leaf.util.queue;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@SuppressWarnings("unused")
abstract sealed class ReadCounter permits CachePad1 {
    protected volatile long r;
}

@SuppressWarnings("unused")
abstract sealed class CachePad1 extends ReadCounter permits WriteCounter {
    byte i0, i1, i2, i3, i4, i5, i6, i7,
        j0, j1, j2, j3, j4, j5, j6, j7,
        k0, k1, k2, k3, k4, k5, k6, k7,
        l0, l1, l2, l3, l4, l5, l6, l7,
        m0, m1, m2, m3, m4, m5, m6, m7,
        n0, n1, n2, n3, n4, n5, n6, n7,
        o0, o1, o2, o3, o4, o5, o6, o7;
}

@SuppressWarnings("unused")
abstract sealed class WriteCounter extends CachePad1 permits CachePad2 {
    protected volatile long w;
}

@SuppressWarnings("unused")
abstract sealed class CachePad2 extends WriteCounter permits MpmcQueue {
    byte i0, i1, i2, i3, i4, i5, i6, i7,
        j0, j1, j2, j3, j4, j5, j6, j7,
        k0, k1, k2, k3, k4, k5, k6, k7,
        l0, l1, l2, l3, l4, l5, l6, l7,
        m0, m1, m2, m3, m4, m5, m6, m7,
        n0, n1, n2, n3, n4, n5, n6, n7,
        o0, o1, o2, o3, o4, o5, o6, o7;
}

/// ```text
/// counter layout
/// +63------------------------------------------------16+15-----8+7------0+
/// |                        index                       |  done  |  pend  |
/// +----------------------------------------------------+--------+--------+
/// ```
///
/// - index (48bits): current read/write position in the ring buffer (head/tail)
/// - pend (8bits): number of pending concurrent read/writes
/// - done (8bits): number of completed read/writes
///
/// For reading reads_pend is incremented first, then the content of the ring buffer is read from memory.
/// After reading is done reads_done is incremented. reads_index is only incremented if reads_done is equal to reads_pend.
///
/// For writing first writes_pend is incremented, then the content of the ring buffer is updated.
/// After writing writes_done is incremented. If writes_done is equal to writes_pend then both are set to 0 and writes_index is incremented.
///
/// In rare cases this can result in a race where multiple threads increment reads_pend in turn and reads_done never quite reaches reads_pend.
/// If reads_pend == 16 or writes_pend == 16 a spin loop waits it to be <16 to continue.
@NullMarked
public final class MpmcQueue<T> extends CachePad2 {

    private static final long DONE_MASK = 0x0000_0000_0000_FF00L;
    private static final long PENDING_MASK = 0x0000_0000_0000_00FFL;
    private static final long DONE_PENDING_MASK = DONE_MASK | PENDING_MASK;
    private static final int INDEX_SHIFT = 16;
    private static final int DONE_SHIFT = 8;
    private static final long MAX_IN_PROGRESS = 16;
    private static final int MAX_CAPACITY = 1 << 30;

    private static final VarHandle READ;
    private static final VarHandle WRITE;
    private static final VarHandle A;

    private final long mask;
    private final T[] a;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(MpmcQueue.class, "r", long.class);
            WRITE = l.findVarHandle(MpmcQueue.class, "w", long.class);
            A = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(final int capacity) {
        super();
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException();
        }

        final long size = Math.max(2, (1L << (Integer.SIZE - Integer.numberOfLeadingZeros(capacity))));
        this.mask = size - 1L;
        this.a = (T[]) new Object[(int) size];
    }

    public MpmcQueue(final Class<T> ignore, final int capacity) {
        this(capacity);
    }

    public boolean send(final T item) {
        long write = (long) WRITE.getAcquire(this);
        long index;
        while (true) {
            final long inProgressCnt = (write & PENDING_MASK);
            if (writeFull(write >>> INDEX_SHIFT)) {
                return false;
            }
            if (inProgressCnt == MAX_IN_PROGRESS) {
                spinWait();
                write = (long) WRITE.getAcquire(this);
                continue;
            }
            index = nextIndex(write, inProgressCnt);
            if (writeFull(index)) {
                return false;
            }
            final long newWrite = write + 1L;
            final long prev = (long) WRITE.compareAndExchangeAcquire(this, write, newWrite);
            if (prev == write) {
                write = newWrite;
                break;
            }
            write = prev;
        }
        A.setRelease(this.a, (int) index, item);
        long expected = write;
        while (true) {
            final long n = nextState(expected, index);
            final long cmp = (long) WRITE.compareAndExchangeRelease(this, expected, n);
            if (cmp == expected) {
                return true;
            } else {
                expected = cmp;
            }
        }
    }

    public @Nullable T recv() {
        long read = (long) READ.getAcquire(this);
        long index;
        while (true) {
            final long inProgressCnt = (read & PENDING_MASK);
            if (readEmpty(read >>> INDEX_SHIFT)) {
                return null;
            }
            if (inProgressCnt == MAX_IN_PROGRESS) {
                spinWait();
                read = (long) READ.getAcquire(this);
                continue;
            }
            index = nextIndex(read, inProgressCnt);
            if (readEmpty(index)) {
                return null;
            }
            final long newRead = read + 1L;
            final long prev = (long) READ.compareAndExchangeAcquire(this, read, newRead);
            if (prev == read) {
                read = newRead;
                break;
            }
            read = prev;
        }
        // noinspection unchecked
        final T result = (T) A.getAndSetAcquire(this.a, (int) index, null);
        long expected = read;
        while (true) {
            final long n = nextState(expected, index);
            final long cmp = (long) READ.compareAndExchangeRelease(this, expected, n);
            if (cmp == expected) {
                return result;
            } else {
                expected = cmp;
            }
        }
    }

    private long nextIndex(final long read, final long pending) {
        return ((read >>> INDEX_SHIFT) + pending) & this.mask;
    }

    private static void spinWait() {
        Thread.onSpinWait();
    }

    /// incrementing the done count and potentially advancing the index
    ///
    /// if done + 1 == pending (all operations complete)
    /// increment index by pending, zero pending and done
    ///
    /// if index == idx (completing in order)
    /// increment index, decrement pending, wrapping and preserve done
    ///
    /// else (skip index increment)
    /// increment done
    private long nextState(final long c, final long idx) {
        return (((c & DONE_MASK) >>> DONE_SHIFT) + 1L) == (c & PENDING_MASK)
            ? (((c >>> INDEX_SHIFT) + (c & PENDING_MASK)) & this.mask) << INDEX_SHIFT
            : (c >>> INDEX_SHIFT) == idx
            ? (c + DONE_PENDING_MASK) & ((this.mask << INDEX_SHIFT) | DONE_PENDING_MASK)
            : c + (1L << DONE_SHIFT);
    }

    /// write would cause the queue to become full
    private boolean writeFull(final long wIdx) {
        return ((wIdx + 1L) & this.mask) == ((long) READ.getVolatile(this) >>> INDEX_SHIFT);
    }

    /// read would read an empty position
    private boolean readEmpty(final long rIdx) {
        return (rIdx & this.mask) == ((long) WRITE.getVolatile(this) >>> INDEX_SHIFT);
    }

    public int length() {
        final long reads = (long) READ.getVolatile(this);
        final long writes = (long) WRITE.getVolatile(this);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = (readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + this.mask + 1L - readIndex);
        return (int) (len - (reads & PENDING_MASK));
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public int remaining() {
        final long reads = (long) READ.getVolatile(this);
        final long writes = (long) WRITE.getVolatile(this);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + this.mask + 1L - readIndex;
        return (int) (this.mask - len - (writes & PENDING_MASK));
    }
}
