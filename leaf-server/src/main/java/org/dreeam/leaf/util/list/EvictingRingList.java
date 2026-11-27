package org.dreeam.leaf.util.list;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.RandomAccess;
import java.util.Objects;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * A size-limited, circular array-backed list that automatically evicts the oldest elements
 * when new elements are added and the list is at full capacity. This list dynamically
 * grows up to its maximum capacity, making it ideal for scenarios requiring a fixed-size
 * "rolling buffer" or "history log" where elements are primarily added and iterated.
 *
 * <p>This implementation provides constant-time {@code add}, {@code get}, and {@code set} operations.
 * When the list reaches its specified max capacity, adding a new element will automatically
 * evict the element at the head of the list.
 *
 * <p>The capacity of the list is always rounded up to the next power of two to optimize
 * index calculations using bitwise operations. It starts with an initial capacity (default 16)
 * and dynamically doubles its size until it hits the max capacity.
 */
@SuppressWarnings({"unchecked", "unused"})
public final class EvictingRingList<E> extends AbstractList<E> implements RandomAccess {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private Object[] elements;
    private final int maxCapacity;

    private int head = 0;
    private int size = 0;
    private int tail = 0;

    private int mask;

    public EvictingRingList(int requestedMaxCapacity) {
        if (requestedMaxCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.maxCapacity = tableSizeFor(requestedMaxCapacity);

        int initialCapacity = Math.min(DEFAULT_INITIAL_CAPACITY, this.maxCapacity);
        this.elements = new Object[initialCapacity];
        this.mask = initialCapacity - 1;
    }

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public EvictingRingList(Collection<? extends E> c) {
        this(Math.max(1, c.size()));
        addAll(c);
    }

    public EvictingRingList(int requestedMaxCapacity, Collection<? extends E> c) {
        this(requestedMaxCapacity);
        addAll(c);
    }

    private void grow() {
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity << 1;

        Object[] newElements = new Object[newCapacity];

        int firstPart = oldCapacity - head;
        System.arraycopy(elements, head, newElements, 0, firstPart);
        System.arraycopy(elements, 0, newElements, firstPart, head);

        this.elements = newElements;
        this.mask = newCapacity - 1;
        this.head = 0;
        this.tail = oldCapacity;
    }

    @Override
    public boolean add(E e) {
        modCount++;

        if (size < elements.length) {
            size++;
        } else if (elements.length < maxCapacity) {
            grow();
            size++;
        } else {
            head = (head + 1) & mask;
        }

        elements[tail] = e;
        tail = (tail + 1) & mask;

        return true;
    }
    @Override
    public E get(int index) {
        Objects.checkIndex(index, size);
        return (E) elements[(head + index) & mask];
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, size);
        int realIndex = (head + index) & mask;
        E oldValue = (E) elements[realIndex];
        elements[realIndex] = element;
        return oldValue;
    }

    @Override
    public E remove(int index) { // TODO Can be further optimized, but do we really need it?
        Objects.checkIndex(index, size);
        modCount++;
        int realIndex = (head + index) & mask;
        E oldValue = (E) elements[realIndex];

        for (int i = index; i < size - 1; i++) {
            int current = (head + i) & mask;
            int next = (head + i + 1) & mask;
            elements[current] = elements[next];
        }

        int lastIndex = (head + size - 1) & mask;
        elements[lastIndex] = null;

        tail = (tail - 1) & mask;
        size--;

        return oldValue;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(o, get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int i = size - 1; i >= 0; i--) {
            if (Objects.equals(o, get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        modCount++;
        if (size == 0) {
            return;
        }

        if (head < tail) {
            Arrays.fill(elements, head, tail, null);
        } else {
            Arrays.fill(elements, head, elements.length, null);
            if (tail > 0) {
                Arrays.fill(elements, 0, tail, null);
            }
        }
        head = tail = size = 0;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final int expectedModCount = modCount;
        final int currentSize = this.size;
        int i = head;

        for (int count = 0; count < currentSize; count++) {
            action.accept((E) elements[i]);
            i = (i + 1) & mask;
        }

        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public Object @NotNull [] toArray() {
        Object[] result = new Object[size];
        if (size > 0) {
            if (head < tail) {
                System.arraycopy(elements, head, result, 0, size);
            } else {
                int firstPart = elements.length - head;
                System.arraycopy(elements, head, result, 0, firstPart);
                System.arraycopy(elements, 0, result, firstPart, tail);
            }
        }
        return result;
    }

    @Override
    public <T> T @NotNull [] toArray(T[] a) {
        if (a.length < size) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }
        if (size > 0) {
            if (head < tail) {
                System.arraycopy(elements, head, a, 0, size);
            } else {
                int firstPart = elements.length - head;
                System.arraycopy(elements, head, a, 0, firstPart);
                System.arraycopy(elements, 0, a, firstPart, tail);
            }
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        return new RingIterator();
    }

    private class RingIterator implements Iterator<E> {
        private int cursor;
        private int remaining;
        private int lastRet = -1;
        private int expectedModCount;

        public RingIterator() {
            this.cursor = head;
            this.remaining = size;
            this.expectedModCount = modCount;
        }

        @Override
        public boolean hasNext() {
            return remaining > 0;
        }

        @Override
        public E next() {
            checkForComodification();
            if (remaining <= 0) {
                throw new NoSuchElementException();
            }

            lastRet = cursor;

            E e = (E) elements[cursor];

            cursor = (cursor + 1) & mask;
            remaining--;

            return e;
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            checkForComodification();

            try {
                int logicalIndex = (lastRet - head) & mask;

                EvictingRingList.this.remove(logicalIndex);

                cursor = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }

        final void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
