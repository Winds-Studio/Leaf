package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class BehaviorControlArraySet<E extends LivingEntity> extends AbstractObjectSet<BehaviorControl<? super E>> {

    private static final BehaviorControl[] EMPTY_ARRAY = {};
    private int running;
    private transient @Nullable BehaviorControl<? super E>[] a;
    private int size;

    public BehaviorControlArraySet() {
        this.a = EMPTY_ARRAY;
    }

    public @Nullable BehaviorControl<? super E>[] raw() {
        return a;
    }

    public void inc() {
        running++;
    }

    public void dec() {
        running--;
    }

    public boolean running() {
        return running != 0;
    }

    @Override
    public int size() {
        return size;
    }

    private int findKey(final Object o) {
        final @Nullable BehaviorControl<? super E>[] a = this.a;
        for (int i = size; i-- != 0; ) if (Objects.equals(a[i], o)) return i;
        return -1;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object k) {
        return findKey(k) != -1;
    }

    @Override
    public ObjectIterator<BehaviorControl<? super E>> iterator() {
        return new ObjectIterator<>() {
            int curr = -1, next = 0;

            @Override
            public boolean hasNext() {
                return next < size;
            }

            @Override
            public BehaviorControl<? super E> next() {
                if (!hasNext()) throw new NoSuchElementException();
                return Objects.requireNonNull(a[curr = next++]);
            }

            @Override
            public void remove() {
                if (curr == -1) throw new IllegalStateException();
                curr = -1;
                final int tail = size-- - next--;
                System.arraycopy(a, next + 1, a, next, tail);
                a[size] = null;
            }

            @Override
            public int skip(int n) {
                if (n < 0) throw new IllegalArgumentException("Argument must be nonnegative: " + n);
                n = Math.min(n, size - next);
                next += n;
                if (n != 0) curr = next - 1;
                return n;
            }

            @Override
            public void forEachRemaining(final Consumer<? super BehaviorControl<? super E>> action) {
                final @Nullable BehaviorControl<? super E>[] a = BehaviorControlArraySet.this.a;
                while (next < size) action.accept(a[next++]);
            }
        };
    }

    @Override
    public Object[] toArray() {
        final int size = size();
        if (size == 0) return it.unimi.dsi.fastutil.objects.ObjectArrays.EMPTY_ARRAY;
        return java.util.Arrays.copyOf(a, size, Object[].class);
    }

    @Override
    public <T> @Nullable T[] toArray(@Nullable T @Nullable[] a) {
        if (a == null) {
            a = (T[]) new Object[size];
        } else if (a.length < size) {
            a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        }
        System.arraycopy(this.a, 0, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @Override
    public boolean add(BehaviorControl<? super E> k) {
        final int pos = findKey(k);
        if (pos != -1) return false;
        if (size == a.length) {
            final @Nullable BehaviorControl<? super E>[] b = new BehaviorControl[size == 0 ? 2 : size * 2];
            for (int i = size; i-- != 0; ) b[i] = a[i];
            a = b;
        }
        a[size++] = k;
        return true;
    }

    @Override
    public boolean remove(Object k) {
        final int pos = findKey(k);
        if (pos == -1) return false;
        final int tail = size - pos - 1;
        for (int i = 0; i < tail; i++) a[pos + i] = a[pos + i + 1];
        size--;
        a[size] = null;
        return true;
    }

    @Override
    public void clear() {
        java.util.Arrays.fill(a, 0, size, null);
        size = 0;
    }
}
