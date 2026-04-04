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
 * when new elements are added and the list is at full capacity. This list is ideal for
 * scenarios requiring a fixed-size "rolling buffer" or "history log" where elements
 * are primarily added and iterated, and the oldest elements are implicitly removed
 * to make space for new ones.
 *
 * <p>This implementation provides constant-time {@code add}, {@code get}, and {@code set} operations.
 * When the list reaches its specified capacity, adding a new element will automatically
 * evict the element at the head of the list, ensuring the list's size never exceeds its capacity.
 *
 * <p>The {@code remove(int index)}, {@code indexOf(Object o)}, and {@code lastIndexOf(Object o)}
 * operations run in linear time (O(n)). Iteration over the elements also takes time proportional
 * to the number of elements in the list.
 *
 * <p>The capacity of the list is always rounded up to the next power of two to optimize
 * index calculations using bitwise operations.
 */
@SuppressWarnings({"unchecked", "unused"})
public final class EvictingRingList<E> extends AbstractList<E> implements RandomAccess {

    private final Object[] elements;
    private final int capacity;

    private int head = 0;
    private int size = 0;
    private int tail = 0;

    private final int mask;

    public EvictingRingList(int requestedCapacity) {
        if (requestedCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = tableSizeFor(requestedCapacity);
        this.mask = this.capacity - 1;
        this.elements = new Object[this.capacity];
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : n + 1;
    }

    public EvictingRingList(Collection<? extends E> c) {
        this(Math.max(1, c.size()));
        addAll(c);
    }

    @Override
    public boolean add(E e) {
        modCount++;
        elements[tail] = e;
        tail = (tail + 1) & mask;
        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) & mask;
        }
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
    public E remove(int index) {
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
            Arrays.fill(elements, head, capacity, null);
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
        final int size = this.size;
        int i = head;

        for (int count = 0; count < size; count++) {
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
                int firstPart = capacity - head;
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
                int firstPart = capacity - head;
                System.arraycopy(elements, head, a, 0, firstPart);
                System.arraycopy(elements, 0, a, firstPart, tail);
            }
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    private void rangeCheck(int index) {
        Objects.checkIndex(index, size);
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
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
