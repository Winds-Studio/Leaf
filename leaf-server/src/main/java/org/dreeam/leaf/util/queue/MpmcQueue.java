/*
 * Copyright (c) 2018 Aron Wieck Crown Communications GmbH
 * SPDX-License-Identifier: MIT OR Apache-2.0
 */

package org.dreeam.leaf.util.queue;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@NullMarked
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
    private final @Nullable T[] buffer;

    private final ReadCounter reads = new ReadCounter();
    private final WriteCounter writes = new WriteCounter();

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(ReadCounter.class, "reads", long.class);
            WRITE = l.findVarHandle(WriteCounter.class, "writes", long.class);
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

    public boolean send(final T item) {
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
    public abstract static sealed class CachePadded permits ReadCounter, WriteCounter {
        public final byte i0 = 0, i1 = 0, i2 = 0, i3 = 0, i4 = 0, i5 = 0, i6 = 0, i7 = 0, i8 = 0, i9 = 0, i10 = 0, i11 = 0, i12 = 0, i13 = 0, i14 = 0, i15 = 0;
        public final byte j0 = 0, j1 = 0, j2 = 0, j3 = 0, j4 = 0, j5 = 0, j6 = 0, j7 = 0, j8 = 0, j9 = 0, j10 = 0, j11 = 0, j12 = 0, j13 = 0, j14 = 0, j15 = 0;
        public final byte k0 = 0, k1 = 0, k2 = 0, k3 = 0, k4 = 0, k5 = 0, k6 = 0, k7 = 0, k8 = 0, k9 = 0, k10 = 0, k11 = 0, k12 = 0, k13 = 0, k14 = 0, k15 = 0;
        public final byte l0 = 0, l1 = 0, l2 = 0, l3 = 0, l4 = 0, l5 = 0, l6 = 0, l7 = 0, l8 = 0, l9 = 0, l10 = 0, l11 = 0, l12 = 0, l13 = 0, l14 = 0, l15 = 0;
        public final byte m0 = 0, m1 = 0, m2 = 0, m3 = 0, m4 = 0, m5 = 0, m6 = 0, m7 = 0, m8 = 0, m9 = 0, m10 = 0, m11 = 0, m12 = 0, m13 = 0, m14 = 0, m15 = 0;
        public final byte n0 = 0, n1 = 0, n2 = 0, n3 = 0, n4 = 0, n5 = 0, n6 = 0, n7 = 0, n8 = 0, n9 = 0, n10 = 0, n11 = 0, n12 = 0, n13 = 0, n14 = 0, n15 = 0;
        public final byte o0 = 0, o1 = 0, o2 = 0, o3 = 0, o4 = 0, o5 = 0, o6 = 0, o7 = 0, o8 = 0, o9 = 0, o10 = 0, o11 = 0, o12 = 0, o13 = 0, o14 = 0, o15 = 0;
        public final byte p0 = 0, p1 = 0, p2 = 0, p3 = 0, p4 = 0, p5 = 0, p6 = 0, p7 = 0, p8 = 0, p9 = 0, p10 = 0, p11 = 0, p12 = 0, p13 = 0, p14 = 0, p15 = 0;
    }

    private static final class ReadCounter extends CachePadded {
        @SuppressWarnings("unused")
        private volatile long reads;
    }

    private static final class WriteCounter extends CachePadded {
        @SuppressWarnings("unused")
        private volatile long writes;
    }
}
