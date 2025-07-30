package org.dreeam.leaf.util.list;

import it.unimi.dsi.fastutil.objects.AbstractObjectList;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * optimized version of ObjectArrayList for NonNullList
 * Licensed under: LGPL-3.0 (https://www.gnu.org/licenses/lgpl-3.0.html)
 * By: @taiyouh at discord
 */
public class OptimizedNonNullListArrayList<E> extends AbstractObjectList<E>
    implements RandomAccess, Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_INITIAL_CAPACITY = 10;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8; // JVM limit
    private static final int GROWTH_SHIFT = 1; // equivalent to /2 but faster
    private static final int CAPACITY_THRESHOLD = 1 << 30;

    private static final int HASH_VALID_FLAG = 1;
    private static final int HAS_DEFAULT_FLAG = 2;

    /** Packed flags for various boolean states */
    private int flags;

    /** The backing array */
    protected transient E[] a;

    /** The current actual size - kept as power of 2 when possible for faster operations */
    protected transient int size;

    /** The default value */
    @Nullable
    private final E defaultValue;

    /** Cached hash code for performance (invalidated on modifications) */
    private transient int cachedHashCode = 0;

    // === CONSTRUCTORS ===

    public OptimizedNonNullListArrayList(int capacity, @Nullable E defaultValue) {
        if (capacity < 0) throw new IllegalArgumentException("Initial capacity (" + capacity + ") is negative");

        // round up to next power of 2 for better cache perf
        capacity = nextPowerOfTwo(capacity);

        if (capacity == 0) {
            this.a = (E[])ObjectArrays.EMPTY_ARRAY;
        } else {
            this.a = (E[])new Object[capacity];
        }

        this.defaultValue = defaultValue;
        this.flags = defaultValue != null ? HAS_DEFAULT_FLAG : 0;
    }

    public OptimizedNonNullListArrayList(@Nullable E defaultValue) {
        this.a = (E[])ObjectArrays.DEFAULT_EMPTY_ARRAY;
        this.defaultValue = defaultValue;
        this.flags = defaultValue != null ? HAS_DEFAULT_FLAG : 0;
    }

    public OptimizedNonNullListArrayList(final E[] sourceArray, @Nullable E defaultValue) {
        this(sourceArray.length, defaultValue);
        System.arraycopy(sourceArray, 0, this.a, 0, sourceArray.length);
        this.size = sourceArray.length;
    }

    // === BIT MANIPULATION UTILITIES ===

    /**
     * Fast power-of-2 calculation
     */
    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        if ((n & (n - 1)) == 0) return n; // Already power of 2

        n--;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return ++n;
    }

    /**
     * Check if number is power of 2
     */
    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    // === FLAG UTILITIES ===

    private boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    private void setFlag(int flag) {
        flags |= flag;
    }

    private void clearFlag(int flag) {
        flags &= ~flag;
    }

    // === GROWTH LOGIC ===

    private void grow(int minCapacity) {
        final int oldCapacity = a.length;
        if (minCapacity <= oldCapacity) return;

        int newCapacity;

        if (a == ObjectArrays.DEFAULT_EMPTY_ARRAY) {
            newCapacity = Math.max(DEFAULT_INITIAL_CAPACITY, minCapacity);
        } else {
            // use shifts instead of division/multiplication
            if (oldCapacity < CAPACITY_THRESHOLD) {
                // For smaller arrays, use 1.5x growth
                newCapacity = oldCapacity + (oldCapacity >>> GROWTH_SHIFT);
            } else {
                // For larger arrays, use more conservative growth to avoid OOM
                newCapacity = oldCapacity + (oldCapacity >>> 2); // 1.25x growth
            }

            newCapacity = Math.max(newCapacity, minCapacity);

            if (newCapacity > MAX_ARRAY_SIZE) {
                newCapacity = hugeCapacity(minCapacity);
            }
            if (newCapacity < 1024) {
                newCapacity = nextPowerOfTwo(newCapacity);
            }
        }

        final Object[] newArray = new Object[newCapacity];
        System.arraycopy(a, 0, newArray, 0, size);
        a = (E[])newArray;
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
    }

    // === CORE OPERATIONS ===

    @Override
    public void add(final int index, final E k) {
        Objects.requireNonNull(k, "Cannot add null element to this list");

        if ((index >>> 31) != 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }

        grow(size + 1);

        if (index != size) {
            System.arraycopy(a, index, a, index + 1, size - index);
        }

        a[index] = k;
        size++;
        invalidateHash();
    }

    @Override
    public boolean add(final E k) {
        Objects.requireNonNull(k, "Cannot add null element to this list");
        grow(size + 1);
        a[size++] = k;
        invalidateHash();
        return true;
    }

    @Override
    public E get(final int index) {
        if ((index | (size - 1 - index)) < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return a[index];
    }

    @Override
    public E remove(final int index) {
        if ((index | (size - 1 - index)) < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }

        final E old = a[index];
        final int numMoved = size - index - 1;

        if (numMoved > 0) {
            System.arraycopy(a, index + 1, a, index, numMoved);
        }

        a[--size] = null; // Clear reference for GC
        invalidateHash();
        return old;
    }

    @Override
    public E set(final int index, final E k) {
        Objects.requireNonNull(k, "Cannot set null element in this list");

        if ((index | (size - 1 - index)) < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }

        final E old = a[index];
        a[index] = k;
        invalidateHash();
        return old;
    }

    @Override
    public void clear() {
        if (defaultValue != null && size > 0) {
            Arrays.fill(a, 0, size, defaultValue);
        }
        size = 0;
        invalidateHash();
    }

    /**
     * fast fill with default value
     */
    public void fillWithDefault() {
        if (hasFlag(HAS_DEFAULT_FLAG) && size > 0) {
            Arrays.fill(a, 0, size, defaultValue);
            invalidateHash();
        }
    }

    // === SEARCH OPERATIONS ===

    @Override
    public int indexOf(final Object k) {
        if (k == null) return -1;

        final Object[] array = this.a;
        final int length = this.size;

        int i = 0;
        for (; i < length - 3; i += 4) {
            if (k.equals(array[i])) return i;
            if (k.equals(array[i + 1])) return i + 1;
            if (k.equals(array[i + 2])) return i + 2;
            if (k.equals(array[i + 3])) return i + 3;
        }

        for (; i < length; i++) {
            if (k.equals(array[i])) return i;
        }

        return -1;
    }

    @Override
    public int lastIndexOf(final Object k) {
        if (k == null) return -1;

        final Object[] array = this.a;

        for (int i = size - 1; i >= 3; i -= 4) {
            if (k.equals(array[i])) return i;
            if (k.equals(array[i - 1])) return i - 1;
            if (k.equals(array[i - 2])) return i - 2;
            if (k.equals(array[i - 3])) return i - 3;
        }

        for (int i = Math.min(size - 1, 3); i >= 0; i--) {
            if (k.equals(array[i])) return i;
        }

        return -1;
    }

    @Override
    public int hashCode() {
        if (hasFlag(HASH_VALID_FLAG)) return cachedHashCode;

        final Object[] array = this.a;
        final int length = this.size;
        int h;
        int i = 0;
        if (length >=4) {
            int h1 = 1;
            int h2 = 3;
            int h3 = 7;
            int h4 = 11;
            for (; i < length - 3; i += 4) {
                h1 = 31 * h1 + array[i].hashCode();
                h2 = 31 * h2 + array[i + 1].hashCode();
                h3 = 31 * h3 + array[i + 2].hashCode();
                h4 = 31 * h4 + array[i + 3].hashCode();
            }
            h = h1 + h2 + h3 + h4;
        } else {
            h = 1;
        }
        for (; i < length; i++) {
            h = 31 * h + array[i].hashCode();
        }
        cachedHashCode = h;
        setFlag(HASH_VALID_FLAG);
        return h;
    }

    private void invalidateHash() {
        clearFlag(HASH_VALID_FLAG);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof List)) return false;

        if (o instanceof OptimizedNonNullListArrayList) {
            final OptimizedNonNullListArrayList<E> other = (OptimizedNonNullListArrayList<E>)o;
            if (size != other.size) return false;
            if (hasFlag(HASH_VALID_FLAG) && other.hasFlag(HASH_VALID_FLAG) && cachedHashCode != other.cachedHashCode) {
                return false;
            }
            return Arrays.equals(a, 0, size, other.a, 0, size);
        }

        return super.equals(o);
    }

    @Override public int size() { return size; }
    @Override public boolean isEmpty() { return size == 0; }

    // === SPLITERATOR WITH PREFETCH HINTS ===

    @Override
    public ObjectSpliterator<E> spliterator() {
        return new OptimizedSpliterator();
    }

    private final class OptimizedSpliterator implements ObjectSpliterator<E> {
        private int pos, max;
        private boolean hasSplit = false;

        public OptimizedSpliterator() {
            this(0, OptimizedNonNullListArrayList.this.size, false);
        }

        private OptimizedSpliterator(int pos, int max, boolean hasSplit) {
            this.pos = pos;
            this.max = max;
            this.hasSplit = hasSplit;
        }

        @Override
        public int characteristics() {
            return ObjectSpliterators.LIST_SPLITERATOR_CHARACTERISTICS;
        }

        @Override
        public long estimateSize() {
            return getWorkingMax() - pos;
        }

        private int getWorkingMax() {
            return hasSplit ? max : OptimizedNonNullListArrayList.this.size;
        }

        @Override
        public boolean tryAdvance(final Consumer<? super E> action) {
            if (pos >= getWorkingMax()) return false;
            action.accept(a[pos++]);
            return true;
        }

        @Override
        public ObjectSpliterator<E> trySplit() {
            final int max = getWorkingMax();
            final int remaining = max - pos;

            int retLen = remaining >>> 1;
            if (retLen <= 1) return null;

            this.max = max;
            int myNewPos = pos + retLen;
            int oldPos = pos;
            this.pos = myNewPos;
            this.hasSplit = true;

            return new OptimizedSpliterator(oldPos, myNewPos, true);
        }
    }

    @Override
    public OptimizedNonNullListArrayList<E> clone() {
        OptimizedNonNullListArrayList<E> cloned;
        try {
            cloned = (OptimizedNonNullListArrayList<E>)super.clone();
        } catch (CloneNotSupportedException err) {
            throw new InternalError(err);
        }

        cloned.a = Arrays.copyOf(this.a, this.size);
        cloned.clearFlag(HASH_VALID_FLAG);
        return cloned;
    }

    @Serial
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        s.writeInt(size);
        for (int i = 0; i < size; i++) {
            s.writeObject(a[i]);
        }
    }

    @Serial
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        this.size = s.readInt();
        this.a = (E[])new Object[this.size];
        for (int i = 0; i < size; i++) {
            a[i] = (E)s.readObject();
        }
        this.flags = (defaultValue != null) ? HAS_DEFAULT_FLAG : 0;
        // Hash is not valid after deserialization (HASH_VALID_FLAG remains cleared)
    }
}
