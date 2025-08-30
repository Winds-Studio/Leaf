package org.dreeam.leaf.util.list;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * A List implementation that maintains a hash-based counter for O(1) element lookup.
 * Combines an array-based list for order with a hash map for fast containment checks.
 */
public class HashedReferenceList<T> implements List<T> {

    // The actual ordered storage of elements
    private final ReferenceArrayList<T> list = new ReferenceArrayList<>();
    // Tracks occurrence count of each element for O(1) contains checks
    private final Reference2IntOpenHashMap<T> counter;

    /**
     * Creates a new HashedReferenceList containing all elements from the provided list
     * while building a counter map for fast lookups.
     */
    public HashedReferenceList(List<T> list) {
        this.list.addAll(list);
        this.counter = new Reference2IntOpenHashMap<>();
        this.counter.defaultReturnValue(0);
        for (T obj : this.list) {
            this.counter.addTo(obj, 1);
        }
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    /**
     * Checks if an element exists in the list in O(1) time using the counter map.
     */
    @Override
    public boolean contains(Object o) {
        return this.counter.containsKey(o);
    }

    @Override
    public Iterator<T> iterator() {
        return this.listIterator();
    }

    @Override
    public Object[] toArray() {
        return this.list.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1 @NotNull [] a) {
        return this.list.toArray(a);
    }

    /**
     * Adds an element and updates the counter map.
     */
    @Override
    public boolean add(T t) {
        this.trackReferenceAdded(t);
        return this.list.add(t);
    }

    /**
     * Removes an element and updates the counter map.
     */
    @Override
    public boolean remove(Object o) {
        this.trackReferenceRemoved(o);
        return this.list.remove(o);
    }

    /**
     * Checks if all elements of the collection exist in this list.
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object obj : c) {
            if (this.counter.containsKey(obj)) continue;
            return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T obj : c) {
            this.trackReferenceAdded(obj);
        }
        return this.list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        for (T obj : c) {
            this.trackReferenceAdded(obj);
        }
        return this.list.addAll(index, c);
    }

    /**
     * Optimizes removal by converting to a hash set for large operations.
     */
    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        if (this.size() >= 2 && c.size() > 4 && c instanceof List) {
            c = new ReferenceOpenHashSet<>(c);
        }
        this.counter.keySet().removeAll(c);
        return this.list.removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        this.counter.keySet().retainAll(c);
        return this.list.retainAll(c);
    }

    @Override
    public void clear() {
        this.counter.clear();
        this.list.clear();
    }

    @Override
    public T get(int index) {
        return this.list.get(index);
    }

    /**
     * Sets an element at specific index while maintaining accurate counts.
     */
    @Override
    public T set(int index, T element) {
        T prev = this.list.set(index, element);
        if (prev != element) {
            if (prev != null) {
                this.trackReferenceRemoved(prev);
            }
            this.trackReferenceAdded(element);
        }
        return prev;
    }

    @Override
    public void add(int index, T element) {
        this.trackReferenceAdded(element);
        this.list.add(index, element);
    }

    @Override
    public T remove(int index) {
        T prev = this.list.remove(index);
        if (prev != null) {
            this.trackReferenceRemoved(prev);
        }
        return prev;
    }

    @Override
    public int indexOf(Object o) {
        return this.list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.list.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return this.listIterator(0);
    }

    /**
     * Custom ListIterator implementation that maintains counter consistency.
     */
    @Override
    public ListIterator<T> listIterator(final int index) {
        return new ListIterator<>() {
            private final ListIterator<T> inner;

            {
                this.inner = HashedReferenceList.this.list.listIterator(index);
            }

            @Override
            public boolean hasNext() {
                return this.inner.hasNext();
            }

            @Override
            public T next() {
                return this.inner.next();
            }

            @Override
            public boolean hasPrevious() {
                return this.inner.hasPrevious();
            }

            @Override
            public T previous() {
                return this.inner.previous();
            }

            @Override
            public int nextIndex() {
                return this.inner.nextIndex();
            }

            @Override
            public int previousIndex() {
                return this.inner.previousIndex();
            }

            /**
             * Removes the current element and updates counter.
             */
            @Override
            public void remove() {
                int last = this.previousIndex();
                if (last == -1) {
                    throw new NoSuchElementException();
                }
                Object prev = HashedReferenceList.this.get(last);
                if (prev != null) {
                    HashedReferenceList.this.trackReferenceRemoved(prev);
                }
                this.inner.remove();
            }

            /**
             * Sets the current element and updates counter.
             */
            @Override
            public void set(T t) {
                int last = this.previousIndex();
                if (last == -1) {
                    throw new NoSuchElementException();
                }
                Object prev = HashedReferenceList.this.get(last);
                if (prev != t) {
                    if (prev != null) {
                        HashedReferenceList.this.trackReferenceRemoved(prev);
                    }
                    HashedReferenceList.this.trackReferenceAdded(t);
                }
                this.inner.remove();
            }

            @Override
            public void add(T t) {
                HashedReferenceList.this.trackReferenceAdded(t);
                this.inner.add(t);
            }
        };
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return this.list.subList(fromIndex, toIndex);
    }

    /**
     * Increments the reference counter for an added element.
     */
    private void trackReferenceAdded(T t) {
        this.counter.addTo(t, 1);
    }

    /**
     * Decrements the reference counter and removes if count reaches 0.
     */
    private void trackReferenceRemoved(Object o) {
        if (this.counter.addTo((T) o, -1) <= 1) {
            this.counter.removeInt(o);
        }
    }

    /**
     * Factory method to create a HashedReferenceList from an existing list.
     */
    public static <T> HashedReferenceList<T> wrapper(List<T> list) {
        return new HashedReferenceList<>(list);
    }
}
