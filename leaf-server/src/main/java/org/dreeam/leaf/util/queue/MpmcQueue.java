// Copyright (c) 2018 Aron Wieck Crown Communications GmbH, Karlsruhe, Germany
// Licensed under the terms of MIT license and the Apache License (Version 2.0).

package org.dreeam.leaf.util.queue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class MpmcQueue<T> {
    private static final int MAX_IN_PROGRESS = 16;
    private static final long DONE_MASK = 0x0000_0000_0000_FF00L;
    private static final long PENDING_MASK = 0x0000_0000_0000_00FFL;
    private static final long FAST_PATH_MASK = 0x00FF_FFFF_FFFF_FF00L;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final int PARALLELISM = Runtime.getRuntime().availableProcessors();

    private static final VarHandle READ;
    private static final VarHandle WRITE;

    private final int mask;
    private final T[] buffer;
    @SuppressWarnings("unused")
    private final Padded padded1 = new Padded();
    @SuppressWarnings("FieldMayBeFinal")
    private volatile long reads = 0L;
    @SuppressWarnings("unused")
    private final Padded padded2 = new Padded();
    @SuppressWarnings("FieldMayBeFinal")
    private volatile long writes = 0L;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(MpmcQueue.class, "reads",
                long.class);
            WRITE = l.findVarHandle(MpmcQueue.class, "writes",
                long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(Class<T> clazz, int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException();
        }

        this.mask = (1 << (Integer.SIZE - Integer.numberOfLeadingZeros(capacity - 1))) - 1;
        //noinspection unchecked
        this.buffer = (clazz == Object.class)
            ? (T[]) new Object[mask + 1]
            : (T[]) java.lang.reflect.Array.newInstance(clazz, mask + 1);
    }

    private void spinWait(final int attempts) {
        if (attempts == 0) {
        } else if (PARALLELISM != 1 && (attempts & 31) != 31) {
            Thread.onSpinWait();
        } else {
            Thread.yield();
        }
    }

    public boolean send(final T item) {
        long write = (long) WRITE.getAcquire(this);
        boolean success;
        long newWrite = 0L;
        int index = 0;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final int inProgressCnt = (int) (write & PENDING_MASK);
            if ((((int) (write >>> 16) + 1) & mask) == (int) ((long) READ.getAcquire(this) >>> 16)) {
                success = false;
                break;
            }

            if (inProgressCnt == MAX_IN_PROGRESS) {
                write = (long) WRITE.getAcquire(this);
                continue;
            }

            index = ((int) (write >>> 16) + inProgressCnt) & mask;

            if (((index + 1) & mask) == (int) ((long) READ.getAcquire(this) >>> 16)) {
                success = false;
                break;
            }

            newWrite = write + 1;
            if (WRITE.weakCompareAndSetAcquire(this, write, newWrite)) {
                success = true;
                break;
            }
            write = (long) WRITE.getVolatile(this);
        }
        if (!success) {
            return false;
        }
        buffer[index] = item;
        if ((newWrite & FAST_PATH_MASK) == ((long) index << 16) && index < mask) {
            WRITE.getAndAddRelease(this, (1L << 16) - 1);
        } else {
            write = newWrite;
            while (true) {
                final int inProcessCnt = (int) (write & PENDING_MASK);
                final long n;
                if (((int) ((write & DONE_MASK) >>> 8) + 1) == inProcessCnt) {
                    n = ((long) (((int) (write >>> 16) + inProcessCnt) & mask)) << 16;
                } else if ((int) (write >>> 16) == index) {
                    n = (write + (1L << 16) - 1) & (((long) mask << 16) | 0xFFFFL);
                } else {
                    n = write + (1L << 8);
                }

                if (WRITE.weakCompareAndSetRelease(this, write, n)) {
                    break;
                }
                write = (long) WRITE.getVolatile(this);
                spinWait(attempts++);
            }
        }

        return true;
    }

    public T recv() {
        long read = (long) READ.getAcquire(this);
        boolean success;
        int index = 0;
        long newRead = 0L;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final int inProgressCnt = (int) (read & PENDING_MASK);
            if ((int) (read >>> 16) == (int) ((long) WRITE.getAcquire(this) >>> 16)) {
                success = false;
                break;
            }

            if (inProgressCnt == MAX_IN_PROGRESS) {
                read = (long) READ.getAcquire(this);
                continue;
            }

            index = ((int) (read >>> 16) + inProgressCnt) & mask;

            if (index == (int) ((long) WRITE.getAcquire(this) >>> 16)) {
                success = false;
                break;
            }

            newRead = read + 1;
            if (READ.weakCompareAndSetAcquire(this, read, newRead)) {
                success = true;
                break;
            }
            read = (long) READ.getVolatile(this);
        }
        if (!success) {
            return null;
        }
        final T result = buffer[index];
        buffer[index] = null;
        if ((newRead & FAST_PATH_MASK) == ((long) index << 16) && index < mask) {
            READ.getAndAddRelease(this, (1L << 16) - 1);
        } else {
            read = newRead;
            while (true) {
                final int inProcessCnt = (int) (read & PENDING_MASK);
                final long n;
                if (((int) ((read & DONE_MASK) >>> 8) + 1) == inProcessCnt) {
                    n = ((long) (((int) (read >>> 16) + inProcessCnt) & mask)) << 16;
                } else if ((int) (read >>> 16) == index) {
                    n = (read + (1L << 16) - 1) & (((long) mask << 16) | 0xFFFFL);
                } else {
                    n = read + (1L << 8);
                }

                if (READ.weakCompareAndSetRelease(this, read, n)) {
                    break;
                }
                read = (long) READ.getVolatile(this);
                spinWait(attempts++);
            }
        }
        return result;
    }

    public int length() {
        final long readCounters = (long) READ.getVolatile(this);
        final long writeCounters = (long) WRITE.getVolatile(this);
        final int readIndex = (int) (readCounters >>> 16);
        final int writeIndex = (int) (writeCounters >>> 16);
        return (readIndex <= writeIndex ?
            writeIndex - readIndex :
            writeIndex + capacity() - readIndex) - (int) (readCounters & PENDING_MASK);
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public int capacity() {
        return buffer.length;
    }

    public int remaining() {
        final long readCounters = (long) READ.getVolatile(this);
        final long writeCounters = (long) WRITE.getVolatile(this);
        final int cap = capacity();
        final int readIndex = (int) (readCounters >>> 16);
        final int writeIndex = (int) (writeCounters >>> 16);
        final int len = readIndex <= writeIndex ?
            writeIndex - readIndex :
            writeIndex + cap - readIndex;
        return cap - 1 - len - (int) (writeCounters & PENDING_MASK);
    }

    @SuppressWarnings("unused")
    private final static class Padded {
        private byte i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15;
        private byte j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
        private byte k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15;
        private byte l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
        private byte m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15;
        private byte n0, n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15;
        private byte o0, o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15;
        private byte p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;
    }
}
