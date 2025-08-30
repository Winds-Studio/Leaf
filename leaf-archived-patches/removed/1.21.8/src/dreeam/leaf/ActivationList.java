package org.dreeam.leaf.util.list;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.AbstractList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A specialized list that allows for efficient hiding and showing of elements
 * without physically removing them from the backing store.
 * <p>
 * Iteration only processes "visible" elements, and visibility can be toggled in O(1) time.
 * This is useful for managing lists of tasks or objects where a large set exists,
 * but only a small subset is active at any given time.
 *
 * @param <E> The type of elements in this list.
 */
public class ActivationList<E> extends AbstractList<E> {

    private final ObjectArrayList<E> elements;
    private final BitSet visibilityMask;
    private final Object2IntOpenHashMap<E> elementToIndexMap;
    private final boolean isVisibleByDefault;
    private int removedSlotCount;

    /**
     * Constructs a new, empty MaskedList.
     *
     * @param isVisibleByDefault The default visibility for elements added to this list.
     */
    public ActivationList(boolean isVisibleByDefault) {
        this.elements = new ObjectArrayList<>();
        this.visibilityMask = new BitSet();
        this.elementToIndexMap = new Object2IntOpenHashMap<>();
        this.elementToIndexMap.defaultReturnValue(-1);
        this.isVisibleByDefault = isVisibleByDefault;
    }

    /**
     * Constructs a new, empty MaskedList with default visibility set to true.
     */
    public ActivationList() {
        this(true);
    }

    /**
     * Adds an element to the list or, if it already exists, updates its visibility.
     *
     * @param element The element to add or update.
     * @param visible The desired visibility of the element.
     */
    public void addOrUpdate(E element, boolean visible) {
        int index = this.elementToIndexMap.getInt(element);
        if (index == -1) {
            index = this.elements.size();
            this.elements.add(element);
            this.elementToIndexMap.put(element, index);
        }
        this.visibilityMask.set(index, visible);
    }

    /**
     * Sets the visibility of an existing element.
     *
     * @param element The element whose visibility to change.
     * @param visible True to make the element visible, false to hide it.
     */
    public void setVisibility(E element, boolean visible) {
        int index = this.elementToIndexMap.getInt(element);
        if (index != -1) {
            this.visibilityMask.set(index, visible);
        }
    }

    @Override
    public boolean add(E element) {
        if (this.elementToIndexMap.containsKey(element)) {
            throw new IllegalArgumentException("MaskedList cannot contain duplicate elements: " + element);
        }
        this.addOrUpdate(element, this.isVisibleByDefault);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        int index = this.elementToIndexMap.removeInt(o);
        if (index == -1) {
            return false;
        }

        this.visibilityMask.clear(index);
        this.elements.set(index, null);
        this.removedSlotCount++;

        if (this.removedSlotCount > 0 && this.removedSlotCount * 2 >= this.elements.size()) {
            compact();
        }
        return true;
    }

    /**
     * Rebuilds the internal list (wow)
     */
    private void compact() {
        int writeIndex = 0;
        for (int readIndex = 0; readIndex < this.elements.size(); readIndex++) {
            E element = this.elements.get(readIndex);

            if (element != null) {
                if (readIndex != writeIndex) {
                    this.elements.set(writeIndex, element);
                    this.elementToIndexMap.put(element, writeIndex);
                    this.visibilityMask.set(writeIndex, this.visibilityMask.get(readIndex));
                }
                writeIndex++;
            }
        }

        int oldSize = this.elements.size();
        if (writeIndex < oldSize) {
            this.elements.removeElements(writeIndex, oldSize);
            this.visibilityMask.clear(writeIndex, oldSize);
        }

        this.removedSlotCount = 0;
    }

    @Override
    public int size() {
        return this.visibilityMask.cardinality();
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= this.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Visible Size: " + this.size());
        }

        int setBitIndex = -1;
        for (int i = 0; i <= index; i++) {
            setBitIndex = this.visibilityMask.nextSetBit(setBitIndex + 1);
        }
        return this.elements.get(setBitIndex);
    }

    @Override
    public Iterator<E> iterator() {
        return new MaskedIterator();
    }

    private class MaskedIterator implements Iterator<E> {
        private int nextVisibleIndex;

        MaskedIterator() {
            this.nextVisibleIndex = ActivationList.this.visibilityMask.nextSetBit(0);
        }

        @Override
        public boolean hasNext() {
            return this.nextVisibleIndex != -1;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E element = ActivationList.this.elements.get(this.nextVisibleIndex);
            this.nextVisibleIndex = ActivationList.this.visibilityMask.nextSetBit(this.nextVisibleIndex + 1);
            return element;
        }
    }

    @Override
    public Spliterator<E> spliterator() {
        return new MaskedSpliterator();
    }

    private class MaskedSpliterator implements Spliterator<E> {
        private int currentIndex;

        MaskedSpliterator() {
            this.currentIndex = ActivationList.this.visibilityMask.nextSetBit(0);
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            if (this.currentIndex != -1) {
                action.accept(ActivationList.this.elements.get(this.currentIndex));
                this.currentIndex = ActivationList.this.visibilityMask.nextSetBit(this.currentIndex + 1);
                return true;
            }
            return false;
        }

        @Override
        public Spliterator<E> trySplit() {
            return null; // This spliterator does not support splitting.
        }

        @Override
        public long estimateSize() {
            return ActivationList.this.size();
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SIZED;
        }
    }
}
