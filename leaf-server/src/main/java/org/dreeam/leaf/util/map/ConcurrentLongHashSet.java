package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Optimized thread-safe implementation of {@link LongSet} that uses striped locking
 * and primitive long arrays to minimize boxing/unboxing overhead.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ConcurrentLongHashSet extends LongOpenHashSet implements LongSet {

    // Number of lock stripes - higher number means more concurrency but more memory
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    // Load factor - when to resize the hash table
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // Initial capacity per stripe
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    // Array of segments (stripes)
    private final Segment[] segments;

    // Total size, cached for faster size() operation
    private final AtomicInteger size;

    /**
     * Creates a new empty concurrent long set with default parameters.
     */
    public ConcurrentLongHashSet() {
        this(DEFAULT_CONCURRENCY_LEVEL * DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new concurrent long set with the specified parameters.
     *
     * @param initialCapacity  the initial capacity
     * @param loadFactor       the load factor
     * @param concurrencyLevel the concurrency level
     */
    public ConcurrentLongHashSet(int initialCapacity, float loadFactor, int concurrencyLevel) {
        // Need to call super() even though we don't use its state
        super();

        // Validate parameters
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be positive");
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Load factor must be positive");
        }
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("Concurrency level must be positive");
        }

        // Calculate segment count (power of 2)
        int segmentCount = 1;
        while (segmentCount < concurrencyLevel) {
            segmentCount <<= 1;
        }

        // Calculate capacity per segment
        int segmentCapacity = Math.max(initialCapacity / segmentCount, DEFAULT_INITIAL_CAPACITY);

        // Create segments
        this.segments = new Segment[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            this.segments[i] = new Segment(segmentCapacity, loadFactor);
        }

        this.size = new AtomicInteger(0);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean isEmpty() {
        return size.get() == 0;
    }

    @Override
    public boolean add(long key) {
        Segment segment = segmentFor(key);
        int delta = segment.add(key) ? 1 : 0;
        if (delta > 0) {
            size.addAndGet(delta);
        }
        return delta > 0;
    }

    @Override
    public boolean contains(long key) {
        return segmentFor(key).contains(key);
    }

    @Override
    public boolean remove(long key) {
        Segment segment = segmentFor(key);
        int delta = segment.remove(key) ? -1 : 0;
        if (delta < 0) {
            size.addAndGet(delta);
        }
        return delta < 0;
    }

    @Override
    public void clear() {
        for (Segment segment : segments) {
            segment.clear();
        }
        size.set(0);
    }

    @Override
    public @NotNull LongIterator iterator() {
        return new ConcurrentLongIterator();
    }

    @Override
    public long[] toLongArray() {
        long[] result = new long[size()];
        int index = 0;
        for (Segment segment : segments) {
            index = segment.toLongArray(result, index);
        }
        return result;
    }

    @Override
    public long[] toArray(long[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        long[] result = toLongArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0;
        }
        return array;
    }

    @NotNull
    @Override
    public Object @NotNull [] toArray() {
        Long[] result = new Long[size()];
        int index = 0;
        for (Segment segment : segments) {
            index = segment.toObjectArray(result, index);
        }
        return result;
    }

    @NotNull
    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        Long[] result = new Long[size()];
        int index = 0;
        for (Segment segment : segments) {
            index = segment.toObjectArray(result, index);
        }

        if (array.length < result.length) {
            return (T[]) result;
        }

        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = null;
        }
        return array;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        for (Object o : collection) {
            if (o instanceof Long) {
                if (!contains(((Long) o).longValue())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Long> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        boolean modified = false;
        for (Long value : collection) {
            modified |= add(value);
        }
        return modified;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        boolean modified = false;
        for (Object o : collection) {
            if (o instanceof Long) {
                modified |= remove(((Long) o).longValue());
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");

        // Convert collection to a set of longs for faster lookups
        LongOpenHashSet toRetain = new LongOpenHashSet();
        for (Object o : collection) {
            if (o instanceof Long) {
                toRetain.add(((Long) o).longValue());
            }
        }

        boolean modified = false;
        for (Segment segment : segments) {
            modified |= segment.retainAll(toRetain);
        }

        if (modified) {
            // Recalculate size
            int newSize = 0;
            for (Segment segment : segments) {
                newSize += segment.size();
            }
            size.set(newSize);
        }

        return modified;
    }

    @Override
    public boolean addAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        LongIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= add(iterator.nextLong());
        }
        return modified;
    }

    @Override
    public boolean containsAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        LongIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            if (!contains(iterator.nextLong())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        LongIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= remove(iterator.nextLong());
        }
        return modified;
    }

    @Override
    public boolean retainAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");

        // For LongCollection we can directly use it
        boolean modified = false;
        for (Segment segment : segments) {
            modified |= segment.retainAll(c);
        }

        if (modified) {
            // Recalculate size
            int newSize = 0;
            for (Segment segment : segments) {
                newSize += segment.size();
            }
            size.set(newSize);
        }

        return modified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongSet that)) return false;
        if (size() != that.size()) return false;
        return containsAll(that);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Segment segment : segments) {
            hash += segment.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');

        LongIterator it = iterator();
        boolean hasNext = it.hasNext();
        while (hasNext) {
            sb.append(it.nextLong());
            hasNext = it.hasNext();
            if (hasNext) {
                sb.append(", ");
            }
        }

        sb.append(']');
        return sb.toString();
    }

    /**
     * Find the segment for a given key.
     */
    private Segment segmentFor(long key) {
        // Use high bits of hash to determine segment
        // This helps spread keys more evenly across segments
        return segments[(int) ((spread(key) >>> segmentShift()) & segmentMask())];
    }

    /**
     * Spread bits to reduce clustering for keys with similar hash codes.
     */
    private static long spread(long key) {
        long h = key;
        h ^= h >>> 32;
        h ^= h >>> 16;
        h ^= h >>> 8;
        return h;
    }

    private int segmentShift() {
        return Integer.numberOfLeadingZeros(segments.length);
    }

    private int segmentMask() {
        return segments.length - 1;
    }

    /**
     * A segment is a striped portion of the hash set with its own lock.
     */
    private static class Segment {
        private final ReentrantLock lock = new ReentrantLock();
        private long[] keys;
        private boolean[] used;
        private int size;
        private int threshold;
        private final float loadFactor;

        Segment(int initialCapacity, float loadFactor) {
            int capacity = MathUtil.nextPowerOfTwo(initialCapacity);
            this.keys = new long[capacity];
            this.used = new boolean[capacity];
            this.size = 0;
            this.loadFactor = loadFactor;
            this.threshold = (int) (capacity * loadFactor);
        }

        int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        boolean contains(long key) {
            lock.lock();
            try {
                int index = indexOf(key);
                return used[index] && keys[index] == key;
            } finally {
                lock.unlock();
            }
        }

        boolean add(long key) {
            lock.lock();
            try {
                int index = indexOf(key);

                // Key already exists
                if (used[index] && keys[index] == key) {
                    return false;
                }

                // Insert key
                keys[index] = key;
                if (!used[index]) {
                    used[index] = true;
                    size++;

                    // Check if rehash is needed
                    if (size > threshold) {
                        rehash();
                    }
                }

                return true;
            } finally {
                lock.unlock();
            }
        }

        boolean remove(long key) {
            lock.lock();
            try {
                int index = indexOf(key);

                // Key not found
                if (!used[index] || keys[index] != key) {
                    return false;
                }

                // Mark slot as unused
                used[index] = false;
                size--;

                // If the next slot is also used, we need to handle the removal properly
                // to maintain the open addressing property
                // This rehashing serves as a "cleanup" after removal
                if (size > 0) {
                    rehashFromIndex(index);
                }

                return true;
            } finally {
                lock.unlock();
            }
        }

        void clear() {
            lock.lock();
            try {
                Arrays.fill(used, false);
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        int toLongArray(long[] array, int offset) {
            lock.lock();
            try {
                for (int i = 0; i < keys.length; i++) {
                    if (used[i]) {
                        array[offset++] = keys[i];
                    }
                }
                return offset;
            } finally {
                lock.unlock();
            }
        }

        int toObjectArray(Long[] array, int offset) {
            lock.lock();
            try {
                for (int i = 0; i < keys.length; i++) {
                    if (used[i]) {
                        array[offset++] = keys[i];
                    }
                }
                return offset;
            } finally {
                lock.unlock();
            }
        }

        boolean retainAll(LongCollection toRetain) {
            lock.lock();
            try {
                boolean modified = false;
                for (int i = 0; i < keys.length; i++) {
                    if (used[i] && !toRetain.contains(keys[i])) {
                        used[i] = false;
                        size--;
                        modified = true;
                    }
                }

                // Rehash to clean up if needed
                if (modified && size > 0) {
                    rehash();
                }

                return modified;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Find the index where a key should be stored.
         * Uses linear probing for collision resolution.
         */
        private int indexOf(long key) {
            int mask = keys.length - 1;
            int index = (int) (spread(key) & mask);

            while (used[index] && keys[index] != key) {
                index = (index + 1) & mask;
            }

            return index;
        }

        /**
         * Rehash the segment with a larger capacity.
         */
        private void rehash() {
            int oldCapacity = keys.length;
            int newCapacity = oldCapacity * 2;

            long[] oldKeys = keys;
            boolean[] oldUsed = used;

            keys = new long[newCapacity];
            used = new boolean[newCapacity];
            size = 0;
            threshold = (int) (newCapacity * loadFactor);

            // Re-add all keys
            for (int i = 0; i < oldCapacity; i++) {
                if (oldUsed[i]) {
                    add(oldKeys[i]);
                }
            }
        }

        /**
         * Rehash from a specific index after removal to maintain proper open addressing.
         */
        private void rehashFromIndex(int startIndex) {
            int mask = keys.length - 1;
            int currentIndex = startIndex;
            int nextIndex = (currentIndex + 1) & mask;

            // For each cluster of used slots following the removal point
            while (used[nextIndex]) {
                long key = keys[nextIndex];
                int targetIndex = (int) (spread(key) & mask);

                // If the key's ideal position is between the removal point and the current position,
                // move it to the removal point
                if ((targetIndex <= currentIndex && currentIndex < nextIndex) ||
                    (nextIndex < targetIndex && targetIndex <= currentIndex) ||
                    (currentIndex < nextIndex && nextIndex < targetIndex)) {

                    keys[currentIndex] = keys[nextIndex];
                    used[currentIndex] = true;
                    used[nextIndex] = false;
                    currentIndex = nextIndex;
                }

                nextIndex = (nextIndex + 1) & mask;
            }
        }

        @Override
        public int hashCode() {
            lock.lock();
            try {
                int hash = 0;
                for (int i = 0; i < keys.length; i++) {
                    if (used[i]) {
                        hash += Long.hashCode(keys[i]);
                    }
                }
                return hash;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Concurrent iterator for the set.
     */
    private class ConcurrentLongIterator implements LongIterator {
        private int segmentIndex;
        private int keyIndex;
        private long lastReturned;
        private boolean lastReturnedValid;

        ConcurrentLongIterator() {
            segmentIndex = 0;
            keyIndex = 0;
            lastReturnedValid = false;
            advance();
        }

        @Override
        public boolean hasNext() {
            return segmentIndex < segments.length;
        }

        @Override
        public long nextLong() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }

            lastReturned = segments[segmentIndex].keys[keyIndex];
            lastReturnedValid = true;
            advance();
            return lastReturned;
        }

        @Override
        public Long next() {
            return nextLong();
        }

        @Override
        public void remove() {
            if (!lastReturnedValid) {
                throw new IllegalStateException();
            }

            ConcurrentLongHashSet.this.remove(lastReturned);
            lastReturnedValid = false;
        }

        private void advance() {
            while (segmentIndex < segments.length) {
                Segment segment = segments[segmentIndex];

                // Lock the segment to get a consistent view
                segment.lock.lock();
                try {
                    while (keyIndex < segment.keys.length) {
                        if (segment.used[keyIndex]) {
                            // Found next element
                            return;
                        }
                        keyIndex++;
                    }
                } finally {
                    segment.lock.unlock();
                }

                // Move to next segment
                segmentIndex++;
                keyIndex = 0;
            }
        }
    }

    /**
     * Utility class for math operations.
     */
    private static class MathUtil {
        /**
         * Returns the next power of two greater than or equal to the given value.
         */
        static int nextPowerOfTwo(int value) {
            int highestBit = Integer.highestOneBit(value);
            return value > highestBit ? highestBit << 1 : value;
        }
    }
}
