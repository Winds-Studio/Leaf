/*
 * Copyright (c) 2018 Aron Wieck Crown Communications GmbH
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package org.dreeam.leaf.util.queue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

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
public final class MpmcQueue<T> {
    private static final long DONE_MASK = 0x0000_0000_0000_FF00L;
    private static final long PENDING_MASK = 0x0000_0000_0000_00FFL;
    private static final long INDEX_MASK = 0x00FF_FFFF_FFFF_0000L;
    private static final long DONE_PENDING_MASK = DONE_MASK | PENDING_MASK;
    private static final long FAST_PATH_MASK = INDEX_MASK | DONE_MASK;
    private static final int INDEX_SHIFT = 16;
    private static final int DONE_SHIFT = 8;
    private static final long MAX_IN_PROGRESS = 16;
    private static final int MAX_CAPACITY = 1 << 30;

    private static final VarHandle V;
    private static final VarHandle A;

    private final long mask;
    private final T[] a;

    private final Counter reads = new Counter();
    private final Counter writes = new Counter();

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            V = l.findVarHandle(Counter.class, "v", long.class);
            A = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException();
        }

        this.mask = Math.max(2, (1L << (Integer.SIZE - Integer.numberOfLeadingZeros(capacity - 1)))) - 1L;
        //noinspection unchecked
        this.a = (T[]) new Object[(int) (this.mask + 1L)];
    }

    public boolean send(final T item) {
        java.util.Objects.requireNonNull(item);
        long write = (long) V.getAcquire(this.writes);
        long idx;
        while (true) {
            final long wPend = (write & PENDING_MASK);
            if (writeErr(write >>> INDEX_SHIFT, (long) V.getVolatile(this.reads), mask)) {
                return false;
            }
            if (wPend == MAX_IN_PROGRESS) {
                Thread.onSpinWait();
                write = (long) V.getAcquire(this.writes);
                continue;
            }
            idx = ((write >>> INDEX_SHIFT) + wPend) & mask;
            if (writeErr(idx, (long) V.getVolatile(this.reads), mask)) {
                return false;
            }
            final long n = write + 1L;
            final long prev = (long) V.compareAndExchangeAcquire(this.writes, write, n);
            if (prev == write) {
                write = n;
                break;
            }
            write = prev;
        }
        A.setRelease(this.a, (int) idx, item);
        if (incDoneFast(mask, write, idx)) {
            V.getAndAddRelease(this.writes, DONE_PENDING_MASK);
        } else while (true) {
            final long prev = (long) V.compareAndExchangeRelease(this.writes, write, incDone(mask, write, idx));
            if (prev == write) {
                break;
            }
            write = prev;
        }
        return true;
    }

    public T recv() {
        long read = (long) V.getAcquire(this.reads);
        long idx;
        while (true) {
            final long rPend = (read & PENDING_MASK);
            if (readErr((read >>> INDEX_SHIFT), (long) V.getVolatile(this.writes), mask)) {
                return null;
            }
            if (rPend == MAX_IN_PROGRESS) {
                Thread.onSpinWait();
                read = (long) V.getAcquire(this.reads);
                continue;
            }
            idx = ((read >>> INDEX_SHIFT) + rPend) & mask;
            if (readErr(idx, (long) V.getVolatile(this.writes), mask)) {
                return null;
            }
            final long n = read + 1L;
            final long prev = (long) V.compareAndExchangeAcquire(this.reads, read, n);
            if (prev == read) {
                read = n;
                break;
            }
            read = prev;
        }
        @SuppressWarnings("unchecked")
        final T result = (T) A.getAndSetAcquire(this.a, (int) idx, null);
        if (incDoneFast(mask, read, idx)) {
            V.getAndAddRelease(this.reads, DONE_PENDING_MASK);
        } else while (true) {
            final long prev = (long) V.compareAndExchangeRelease(this.reads, read, incDone(mask, read, idx));
            if (prev == read) {
                break;
            }
            read = prev;
        }
        return result;
    }

    /// directly increment the index and zero pending and done when:
    ///
    /// - first pending operation (index == idx)
    /// - no operations have completed yet (done == 0)
    /// - don't need to wrap around the buffer (index < mask)
    private static boolean incDoneFast(final long m, final long c, final long idx) {
        return (c & FAST_PATH_MASK) == (idx << INDEX_SHIFT) && idx < m;
    }

    /// incrementing the done count and potentially advancing the index
    ///
    /// if done + 1 == pending (all operations complete)
    /// increment index by pending, zero pending and done
    ///
    /// if index == idx (completing in order)
    /// increment index by 1, decrement pending, preserve done
    ///
    /// else (skip index increment)
    /// increment done
    private static long incDone(final long m, final long c, final long idx) {
        return (((c & DONE_MASK) >>> DONE_SHIFT) + 1L) == (c & PENDING_MASK)
            ? (((c >>> INDEX_SHIFT) + (c & PENDING_MASK)) & m) << INDEX_SHIFT
            : (c >>> INDEX_SHIFT) == idx
            ? (c + DONE_PENDING_MASK) & ((m << INDEX_SHIFT) | DONE_PENDING_MASK)
            : c + (1L << DONE_SHIFT);
    }

    /// write would cause the queue to become full
    private static boolean writeErr(long wIdx, long r, long mask) {
        return ((wIdx + 1L) & mask) == r >>> INDEX_SHIFT;
    }

    /// read would read an empty position
    private static boolean readErr(long rIdx, long w, long mask) {
        return (rIdx & mask) == (w >>> INDEX_SHIFT);
    }

    public int length() {
        final long reads = (long) V.getVolatile(this.reads);
        final long writes = (long) V.getVolatile(this.writes);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = (readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + this.mask + 1 - readIndex);
        return (int) (len - (reads & PENDING_MASK));
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public int remaining() {
        final long reads = (long) V.getVolatile(this.reads);
        final long writes = (long) V.getVolatile(this.writes);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = readIndex <= writeIndex
            ? writeIndex - readIndex
            : writeIndex + this.mask + 1 - readIndex;
        return (int) (mask - len - (writes & PENDING_MASK));
    }

    @SuppressWarnings("unused")
    private abstract static sealed class CachePadded permits Counter {
        byte i0, i1, i2, i3, i4, i5, i6, i7, j0, j1, j2, j3, j4, j5, j6, j7;
        byte k0, k1, k2, k3, k4, k5, k6, k7, l0, l1, l2, l3, l4, l5, l6, l7;
        byte m0, m1, m2, m3, m4, m5, m6, m7, n0, n1, n2, n3, n4, n5, n6, n7;
        byte o0, o1, o2, o3, o4, o5, o6, o7, p0, p1, p2, p3, p4, p5, p6, p7;
        byte q0, q1, q2, q3, q4, q5, q6, q7, r0, r1, r2, r3, r4, r5, r6, r7;
        byte s0, s1, s2, s3, s4, s5, s6, s7, t0, t1, t2, t3, t4, t5, t6, t7;
        byte u0, u1, u2, u3, u4, u5, u6, u7, v0, v1, v2, v3, v4, v5, v6, v7;
        byte w0, w1, w2, w3, w4, w5, w6, w7;
    }

    private static final class Counter extends CachePadded {
        @SuppressWarnings("unused")
        private volatile long v;
    }
}
