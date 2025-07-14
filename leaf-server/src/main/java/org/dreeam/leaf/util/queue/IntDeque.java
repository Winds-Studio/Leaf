package org.dreeam.leaf.util.queue;

/// @see it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
public final class IntDeque {
    private int[] array;
    private int length;
    private int start;
    private int end;

    public IntDeque(final int cap) {
        array = new int[cap];
        length = array.length;
    }

    public void clear() {
        start = 0;
        end = 0;
    }

    public boolean isEmpty() {
        return end == start;
    }

    public int poll() {
        final int t = array[start];
        if (++start == length) start = 0;
        // no resize
        return t;
    }

    public void push(final int node) {
        array[end++] = node;
        if (end == length) end = 0;
        if (end == start) resize(length, 2 * length);
    }

    private void resize(final int size, final int newLength) {
        final int[] newArray = new int[newLength];
        assert end == start;
        if (size != 0) {
            System.arraycopy(array, start, newArray, 0, length - start);
            System.arraycopy(array, 0, newArray, length - start, end);
        }
        start = 0;
        end = size;
        array = newArray;
        length = newLength;
    }
}
