package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSpliterator;

import java.io.Serial;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class SyncLongOpenHashSet extends LongOpenHashSet {
    public SyncLongOpenHashSet() {
    }

    @Override
    public boolean remove(final long k) {
        synchronized (this) {
            return super.remove(k);
        }
    }

    @Override
    public boolean add(final long k) {
        synchronized (this) {
            return super.add(k);
        }
    }

    @Override
    public boolean contains(final long k) {
        synchronized (this) {
            return super.contains(k);
        }
    }

    @Override
    @Deprecated
    public boolean rem(final long k) {
        synchronized (this) {
            return super.remove(k);
        }
    }

    @Override
    public int size() {
        synchronized (this) {
            return super.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (this) {
            return super.isEmpty();
        }
    }

    @Override
    public long[] toLongArray() {
        synchronized (this) {
            return super.toLongArray();
        }
    }

    @Override
    public Object[] toArray() {
        synchronized (this) {
            return super.toArray();
        }
    }

    @Deprecated
    @Override
    public long[] toLongArray(final long[] a) {
        return toArray(a);
    }

    @Override
    public long[] toArray(final long[] a) {
        synchronized (this) {
            return super.toArray(a);
        }
    }

    @Override
    public boolean addAll(final LongCollection c) {
        synchronized (this) {
            return super.addAll(c);
        }
    }

    @Override
    public boolean containsAll(final LongCollection c) {
        synchronized (this) {
            return super.containsAll(c);
        }
    }

    @Override
    public boolean removeAll(final LongCollection c) {
        synchronized (this) {
            return super.removeAll(c);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super Long> filter) {
        return super.removeIf(filter);
    }

    @Override
    public boolean retainAll(final LongCollection c) {
        synchronized (this) {
            return super.retainAll(c);
        }
    }

    @Override
    @Deprecated
    public boolean add(final Long k) {
        synchronized (this) {
            return super.add(k.longValue());
        }
    }

    @Override
    @Deprecated
    public boolean contains(final Object k) {
        synchronized (this) {
            return super.contains((((Long) k).longValue()));
        }
    }

    @Override
    @Deprecated
    public boolean remove(final Object k) {
        synchronized (this) {
            return super.remove((((Long) k).longValue()));
        }
    }

    @Override
    public LongIterator longIterator() {
        return super.longIterator();
    }

    @Override
    public LongSpliterator longSpliterator() {
        return super.longSpliterator();
    }

    @Override
    public java.util.stream.LongStream longStream() {
        return super.longStream();
    }

    @Override
    public java.util.stream.LongStream longParallelStream() {
        return super.longParallelStream();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        synchronized (this) {
            return super.toArray(a);
        }
    }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator) {
        synchronized (this) {
            return super.toArray(generator);
        }
    }

    @Override
    public LongIterator iterator() {
        return super.iterator();
    }

    @Override
    public LongSpliterator spliterator() {
        return super.spliterator();
    }

    @Deprecated
    @Override
    public java.util.stream.Stream<Long> stream() {
        return super.stream();
    }

    @Deprecated
    @Override
    public java.util.stream.Stream<Long> parallelStream() {
        return super.parallelStream();
    }

    @Override
    public void forEach(final java.util.function.LongConsumer action) {
        synchronized (this) {
            super.forEach(action);
        }
    }

    @Deprecated
    @Override
    public void forEach(Consumer<? super Long> action) {
        synchronized (this) {
            super.forEach(action);
        }
    }

    @Override
    public boolean addAll(final Collection<? extends Long> c) {
        synchronized (this) {
            return super.addAll(c);
        }
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        synchronized (this) {
            return super.containsAll(c);
        }
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        synchronized (this) {
            return super.removeAll(c);
        }
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        synchronized (this) {
            return super.retainAll(c);
        }
    }

    @Override
    public boolean removeIf(final java.util.function.LongPredicate filter) {
        synchronized (this) {
            return super.removeIf(filter);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            super.clear();
        }
    }

    @Override
    public String toString() {
        synchronized (this) {
            return super.toString();
        }
    }

    @Override
    public int hashCode() {
        synchronized (this) {
            return super.hashCode();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        synchronized (this) {
            return super.equals(o);
        }
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        synchronized (this) {
            s.defaultWriteObject();
        }
    }

    @Override
    public LongOpenHashSet clone() {
        synchronized (this) {
            return super.clone();
        }
    }
}
