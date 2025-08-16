package org.dreeam.leaf.util.map;

import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public final class BinaryGoalSet extends AbstractSet<WrappedGoal> {
    private static final WrappedGoal[] EMPTY_ARRAY = {};
    private WrappedGoal[] a;
    private int size;
    private static final int DEFAULT_CAPACITY = 4;

    public BinaryGoalSet() {
        this.a = EMPTY_ARRAY;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        Arrays.fill(a, 0, size, null);
        size = 0;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean add(final WrappedGoal goal) {
        Objects.requireNonNull(goal);

        if (size == a.length) {
            grow();
        }

        final int priority = goal.getPriority();
        int left = 0;
        int right = size;

        while (left < right) {
            final int mid = (left + right) >>> 1;
            if (a[mid].getPriority() < priority) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        int gap = left;
        while (gap < size && a[gap].getPriority() == priority) {
            if (a[gap] == goal) {
                return false;
            }
            gap++;
        }

        if (left < size) {
            System.arraycopy(a, left, a, left + 1, size - left);
        }
        a[left] = goal;
        size++;
        return true;
    }

    @Override
    public boolean remove(final Object obj) {
        if (!(obj instanceof final WrappedGoal goal)) {
            return false;
        }

        final int priority = goal.getPriority();

        int left = 0;
        int right = size;

        while (left < right) {
            final int mid = (left + right) >>> 1;
            if (a[mid].getPriority() < priority) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        int gap = -1;
        for (int i = left; i < size && a[i].getPriority() == priority; i++) {
            if (a[i] == goal) {
                gap = i;
                break;
            }
        }

        if (gap == -1) {
            return false;
        }

        if (gap < size - 1) {
            System.arraycopy(a, gap + 1, a, gap, size - gap - 1);
        }

        size--;
        a[size] = null;
        return true;
    }

    @Override
    public boolean removeIf(@NotNull final Predicate<? super WrappedGoal> filter) {
        Objects.requireNonNull(filter);

        boolean removed = false;
        for (int i = size - 1; i >= 0; i--) {
            final WrappedGoal e = a[i];
            if (!filter.test(e)) {
                continue;
            }
            if (i < size - 1) {
                System.arraycopy(a, i + 1, a, i, size - i - 1);
            }
            size--;
            a[size] = null;
            removed = true;
        }
        return removed;
    }

    @Override
    public boolean contains(final Object obj) {
        if (!(obj instanceof final WrappedGoal goal)) {
            return false;
        }

        final int priority = goal.getPriority();
        int left = 0;
        int right = size;

        while (left < right) {
            final int mid = (left + right) >>> 1;
            if (a[mid].getPriority() < priority) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        for (int i = left; i < size && a[i].getPriority() == priority; i++) {
            if (a[i] == goal) {
                return true;
            }
        }

        return false;
    }

    private void grow() {
        final int capacity = (a.length == 0) ? DEFAULT_CAPACITY : a.length + (a.length >> 1);
        final WrappedGoal[] newArray = new WrappedGoal[capacity];
        System.arraycopy(a, 0, newArray, 0, size);
        a = newArray;
    }

    @Override
    @NotNull
    public Iterator<WrappedGoal> iterator() {
        return new Iterator<>() {
            private int cursor = 0;
            private int last = -1;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public WrappedGoal next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                last = cursor;
                return a[cursor++];
            }

            @Override
            public void remove() {
                if (last == -1) {
                    throw new IllegalStateException();
                }
                System.arraycopy(a, last + 1, a, last, size - last - 1);
                size--;
                a[size] = null;
                cursor = last;
                last = -1;
            }
        };
    }

    @Override
    public int hashCode() {
        int result = 0;
        for (int i = 0, j = size; i < j; i++) {
            final WrappedGoal e = a[i];
            result += e == null ? 0 : e.hashCode();
        }
        return result;
    }

    public WrappedGoal[] elements() {
        return a;
    }
}
