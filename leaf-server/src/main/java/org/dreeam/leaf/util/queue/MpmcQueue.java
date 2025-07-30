/*
 * Copyright (c) 2018 Aron Wieck Crown Communications GmbH
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package org.dreeam.leaf.util.queue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class MpmcQueue<T> {
    private static final long DONE_MASK = 0x0000_0000_0000_FF00L;
    private static final long PENDING_MASK = 0x0000_0000_0000_00FFL;
    private static final long DONE_PENDING_MASK = DONE_MASK | PENDING_MASK;
    private static final int INDEX_SHIFT = 16;
    private static final int DONE_SHIFT = 8;
    private static final long MAX_IN_PROGRESS = 16;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final int PARALLELISM = Runtime.getRuntime().availableProcessors();

    private static final VarHandle READ;
    private static final VarHandle WRITE;

    private final long mask;
    private final long capacity;
    @Nullable
    private final T[] buffer;

    private final PaddedReads reads = new PaddedReads();
    private final PaddedWrites writes = new PaddedWrites();

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(PaddedReads.class, "reads", long.class);
            WRITE = l.findVarHandle(PaddedWrites.class, "writes", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(Class<T> clazz, int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException();
        }

        this.capacity = Math.max(2, (1L << (Integer.SIZE - Integer.numberOfLeadingZeros(capacity - 1))));
        this.mask = this.capacity - 1L;
        //noinspection unchecked
        this.buffer = (clazz == Object.class)
            ? (T[]) new Object[(int) this.capacity]
            : (T[]) java.lang.reflect.Array.newInstance(clazz, (int) this.capacity);
    }

    private void spinWait(final int attempts) {
        //noinspection StatementWithEmptyBody
        if (attempts == 0) {
        } else if (PARALLELISM != 1 && (attempts & 31) != 31) {
            Thread.onSpinWait();
        } else {
            Thread.yield();
        }
    }

    public boolean send(@NotNull final T item) {
        long write = (long) WRITE.getAcquire(this.writes);
        boolean success;
        long newWrite = 0L;
        long index = 0L;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final long inProgressCnt = (write & PENDING_MASK);
            if ((((write >>> INDEX_SHIFT) + 1L) & mask) == ((long) READ.getVolatile(this.reads) >>> INDEX_SHIFT)) {
                success = false;
                break;
            }

            if (inProgressCnt == MAX_IN_PROGRESS) {
                write = (long) WRITE.getAcquire(this.writes);
                continue;
            }
            index = ((write >>> INDEX_SHIFT) + inProgressCnt) & mask;
            if (((index + 1L) & mask) == ((long) READ.getVolatile(this.reads) >>> INDEX_SHIFT)) {
                success = false;
                break;
            }
            newWrite = write + 1L;
            if (WRITE.weakCompareAndSetAcquire(this.writes, write, newWrite)) {
                success = true;
                break;
            }
            write = (long) WRITE.getVolatile(this.writes);
        }
        if (!success) {
            return false;
        }
        buffer[(int) index] = item;
        write = newWrite;
        while (true) {
            final long n = ((write & DONE_MASK) >>> DONE_SHIFT) + 1L == (write & PENDING_MASK)
                ? ((write >>> INDEX_SHIFT) + (write & PENDING_MASK) & mask) << INDEX_SHIFT
                : write >>> INDEX_SHIFT == index
                ? write + (1L << INDEX_SHIFT) - 1L & (mask << INDEX_SHIFT | DONE_PENDING_MASK)
                : write + (1L << DONE_SHIFT);
            if (WRITE.weakCompareAndSetRelease(this.writes, write, n)) {
                break;
            }
            write = (long) WRITE.getVolatile(this.writes);
            spinWait(attempts++);
        }
        return true;
    }

    public @Nullable T recv() {
        long read = (long) READ.getAcquire(this.reads);
        boolean success;
        long index = 0;
        long newRead = 0L;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final long inProgressCnt = (read & PENDING_MASK);
            if ((read >>> INDEX_SHIFT) == ((long) WRITE.getVolatile(this.writes) >>> INDEX_SHIFT)) {
                success = false;
                break;
            }
            if (inProgressCnt == MAX_IN_PROGRESS) {
                read = (long) READ.getAcquire(this.reads);
                continue;
            }
            index = ((read >>> INDEX_SHIFT) + inProgressCnt) & mask;
            if ((index & mask) == ((long) WRITE.getVolatile(this.writes) >>> INDEX_SHIFT)) {
                success = false;
                break;
            }
            newRead = read + 1L;
            if (READ.weakCompareAndSetAcquire(this.reads, read, newRead)) {
                success = true;
                break;
            }
            read = (long) READ.getVolatile(this.reads);
        }
        if (!success) {
            return null;
        }
        final T result = buffer[(int) index];
        buffer[(int) index] = null;
        read = newRead;
        while (true) {
            final long n = ((read & DONE_MASK) >>> DONE_SHIFT) + 1L == (read & PENDING_MASK)
                ? ((read >>> INDEX_SHIFT) + (read & PENDING_MASK) & mask) << INDEX_SHIFT
                : read >>> INDEX_SHIFT == index
                ? read + (1L << INDEX_SHIFT) - 1L & (mask << INDEX_SHIFT | DONE_PENDING_MASK)
                : read + (1L << DONE_SHIFT);
            if (READ.weakCompareAndSetRelease(this.reads, read, n)) {
                break;
            }
            read = (long) READ.getVolatile(this.reads);
            spinWait(attempts++);
        }
        return result;
    }

    public int length() {
        final long reads = (long) READ.getVolatile(this.reads);
        final long writes = (long) WRITE.getVolatile(this.writes);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        return (int) (readIndex <= writeIndex ? writeIndex - readIndex : writeIndex + capacity - readIndex);
        // (readIndex <= writeIndex ? writeIndex - readIndex : writeIndex + capacity - readIndex) - (reads & PENDING_MASK)
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public int remaining() {
        final long reads = (long) READ.getVolatile(this.reads);
        final long writes = (long) WRITE.getVolatile(this.writes);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = readIndex <= writeIndex ?
            writeIndex - readIndex :
            writeIndex + capacity - readIndex;
        return (int) (mask - len - (writes & PENDING_MASK));
    }

    @SuppressWarnings("unused")
    private final static class PaddedReads {
        private byte i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15;
        private byte j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
        private byte k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15;
        private byte l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
        private byte m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15;
        private byte n0, n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15;
        private byte o0, o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15;
        private byte p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;

        private volatile long reads;
    }
    @SuppressWarnings("unused")
    private final static class PaddedWrites {
        private byte i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15;
        private byte j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
        private byte k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15;
        private byte l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
        private byte m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15;
        private byte n0, n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15;
        private byte o0, o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15;
        private byte p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;

        private volatile long writes;
    }
}
