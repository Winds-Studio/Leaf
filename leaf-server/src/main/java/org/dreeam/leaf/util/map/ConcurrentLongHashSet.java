package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcurrentLongHashSet extends LongOpenHashSet implements LongSet {

    private static final int DEFAULT_SEGMENTS = 16; // Should be power-of-two
    private final Segment[] segments;
    private final int segmentMask;

    public ConcurrentLongHashSet() {
        this(DEFAULT_SEGMENTS);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        for (Object obj : c) {
            if (obj instanceof Long) {
                modified |= remove(obj);
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        LongIterator iterator = iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if (!c.contains(key)) {
                modified |= remove(key);
            }
        }
        return modified;
    }

    public ConcurrentLongHashSet(int concurrencyLevel) {
        int numSegments = Integer.highestOneBit(concurrencyLevel) << 1;
        this.segmentMask = numSegments - 1;
        this.segments = new Segment[numSegments];
        for (int i = 0; i < numSegments; i++) {
            segments[i] = new Segment();
        }
    }

    // ------------------- Core Methods -------------------
    @Override
    public boolean add(long key) {
        Segment segment = getSegment(key);
        segment.lock();
        try {
            return segment.set.add(key);
        } finally {
            segment.unlock();
        }
    }

    @Override
    public boolean contains(long key) {
        Segment segment = getSegment(key);
        segment.lock();
        try {
            return segment.set.contains(key);
        } finally {
            segment.unlock();
        }
    }

    @Override
    public boolean remove(long key) {
        Segment segment = getSegment(key);
        segment.lock();
        try {
            return segment.set.remove(key);
        } finally {
            segment.unlock();
        }
    }

    // ------------------- Bulk Operations -------------------
    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        for (Object obj : c) {
            if (obj == null || !(obj instanceof Long)) return false;
            if (!contains(obj)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Long> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        for (Long value : c) {
            modified |= add(value);
        }
        return modified;
    }

    // ------------------- Locking Helpers -------------------
    private Segment getSegment(long key) {
        int hash = spreadHash(Long.hashCode(key));
        return segments[hash & segmentMask];
    }

    private static int spreadHash(int h) {
        return (h ^ (h >>> 16)) & 0x7fffffff; // Avoid negative indices
    }

    // ------------------- Size Stuff -------------------
    @Override
    public int size() {
        int count = 0;
        for (Segment segment : segments) {
            segment.lock();
            count += segment.set.size();
            segment.unlock();
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        for (Segment segment : segments) {
            segment.lock();
            boolean empty = segment.set.isEmpty();
            segment.unlock();
            if (!empty) return false;
        }
        return true;
    }

    // ------------------- Cleanup -------------------
    @Override
    public void clear() {
        for (Segment segment : segments) {
            segment.lock();
            segment.set.clear();
            segment.unlock();
        }
    }

    // ------------------- Iteration -------------------
    @Override
    public LongIterator iterator() {
        return new CompositeLongIterator();
    }

    private class CompositeLongIterator implements LongIterator {
        private int currentSegment = 0;
        private LongIterator currentIterator;

        CompositeLongIterator() {
            advanceSegment();
        }

        private void advanceSegment() {
            while (currentSegment < segments.length) {
                segments[currentSegment].lock();
                currentIterator = segments[currentSegment].set.iterator();
                if (currentIterator.hasNext()) break;
                segments[currentSegment].unlock();
                currentSegment++;
            }
        }

        @Override
        public boolean hasNext() {
            if (currentIterator == null) return false;
            if (currentIterator.hasNext()) return true;
            segments[currentSegment].unlock();
            currentSegment++;
            advanceSegment();
            return currentIterator != null && currentIterator.hasNext();
        }

        @Override
        public long nextLong() {
            if (!hasNext()) throw new NoSuchElementException();
            return currentIterator.nextLong();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // ------------------- Segment (these nuts) -------------------
    private static class Segment {
        final LongOpenHashSet set = new LongOpenHashSet();
        final ReentrantLock lock = new ReentrantLock();

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }
    }

    // ignore
    @Override
    public long[] toLongArray() {
        long[] result = new long[size()];
        int i = 0;
        LongIterator it = iterator();
        while (it.hasNext()) {
            result[i++] = it.nextLong();
        }
        return result;
    }

    @Override
    public long[] toArray(long[] a) {
        long[] result = toLongArray();
        if (a.length < result.length) return result;
        System.arraycopy(result, 0, a, 0, result.length);
        return a;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongSet that)) return false;
        return size() == that.size() && containsAll(that);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        LongIterator it = iterator();
        while (it.hasNext()) {
            hash += Long.hashCode(it.nextLong());
        }
        return hash;
    }

    @Override
    @NotNull
    public Object[] toArray() {
        return Collections.unmodifiableSet(this).toArray();
    }

    @Override
    @NotNull
    public <T> T[] toArray(@NotNull T[] a) {
        return Collections.unmodifiableSet(this).toArray(a);
    }
}
