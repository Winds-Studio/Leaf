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
public final class MpmcQueue<T> extends WriteCounter {
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
    private final long capacity;
    private final @Nullable T[] buffer;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            READ = l.findVarHandle(ReadCounter.class, "reads", long.class);
            WRITE = l.findVarHandle(WriteCounter.class, "writes", long.class);
            A = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public MpmcQueue(final Class<T> clazz, final int capacity) {
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
        if ((attempts & 31) != 31) {
            Thread.onSpinWait();
        } else {
            Thread.yield();
        }
    }

    public boolean send(final T item) {
        long write = (long) WRITE.getAcquire(this);
        long newWrite;
        long index;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final long inProgressCnt = (write & PENDING_MASK);
            if ((((write >>> INDEX_SHIFT) + 1L) & mask) == ((long) READ.getVolatile(this) >>> INDEX_SHIFT)) {
                return false;
            }

            if (inProgressCnt == MAX_IN_PROGRESS) {
                write = (long) WRITE.getAcquire(this);
                continue;
            }
            index = ((write >>> INDEX_SHIFT) + inProgressCnt) & mask;
            if (((index + 1L) & mask) == ((long) READ.getVolatile(this) >>> INDEX_SHIFT)) {
                return false;
            }
            newWrite = write + 1L;
            final long curr = (long) WRITE.compareAndExchangeAcquire(this, write, newWrite);
            if (curr == write) {
                write = newWrite;
                break;
            }
            write = curr;
        }
        A.setRelease(this.buffer, (int) index, item);
        while (true) {
            spinWait(attempts++);
            final long n = ((write & DONE_MASK) >>> DONE_SHIFT) + 1L == (write & PENDING_MASK)
                ? ((write >>> INDEX_SHIFT) + (write & PENDING_MASK) & mask) << INDEX_SHIFT
                : write >>> INDEX_SHIFT == index
                ? write + (1L << INDEX_SHIFT) - 1L & (mask << INDEX_SHIFT | DONE_PENDING_MASK)
                : write + (1L << DONE_SHIFT);
            final long curr = (long) WRITE.compareAndExchangeRelease(this, write, n);
            if (curr == write) {
                return true;
            } else {
                write = curr;
            }
        }
    }

    public @Nullable T recv() {
        long read = (long) READ.getAcquire(this);
        long index;
        long newRead;
        int attempts = 0;
        while (true) {
            spinWait(attempts++);
            final long inProgressCnt = (read & PENDING_MASK);
            if ((read >>> INDEX_SHIFT) == ((long) WRITE.getVolatile(this) >>> INDEX_SHIFT)) {
                return null;
            }
            if (inProgressCnt == MAX_IN_PROGRESS) {
                read = (long) READ.getAcquire(this);
                continue;
            }
            index = ((read >>> INDEX_SHIFT) + inProgressCnt) & mask;
            if ((index & mask) == ((long) WRITE.getVolatile(this) >>> INDEX_SHIFT)) {
                return null;
            }
            newRead = read + 1L;
            final long prev = (long) READ.compareAndExchangeAcquire(this, read, newRead);
            if (prev == read) {
                read = newRead;
                break;
            }
            read = prev;
        }
        // noinspection unchecked
        final T result = (T) A.getAndSetAcquire(this.buffer, (int) index, null);
        while (true) {
            spinWait(attempts++);
            final long n = ((read & DONE_MASK) >>> DONE_SHIFT) + 1L == (read & PENDING_MASK)
                ? ((read >>> INDEX_SHIFT) + (read & PENDING_MASK) & mask) << INDEX_SHIFT
                : read >>> INDEX_SHIFT == index
                ? read + (1L << INDEX_SHIFT) - 1L & (mask << INDEX_SHIFT | DONE_PENDING_MASK)
                : read + (1L << DONE_SHIFT);
            final long curr = (long) READ.compareAndExchangeRelease(this, read, n);
            if (curr == read) {
                return result;
            } else {
                read = curr;
            }
        }
    }

    public int length() {
        final long reads = (long) READ.getVolatile(this);
        final long writes = (long) WRITE.getVolatile(this);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        return (int) (readIndex <= writeIndex ? writeIndex - readIndex : writeIndex + capacity - readIndex);
        // (readIndex <= writeIndex ? writeIndex - readIndex : writeIndex + capacity - readIndex) - (reads & PENDING_MASK)
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public int remaining() {
        final long reads = (long) READ.getVolatile(this);
        final long writes = (long) WRITE.getVolatile(this);
        final long readIndex = (reads >>> INDEX_SHIFT);
        final long writeIndex = (writes >>> INDEX_SHIFT);
        final long len = readIndex <= writeIndex ?
            writeIndex - readIndex :
            writeIndex + capacity - readIndex;
        return (int) (mask - len - (writes & PENDING_MASK));
    }
}
