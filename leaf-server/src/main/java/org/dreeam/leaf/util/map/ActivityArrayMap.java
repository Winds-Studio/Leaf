package org.dreeam.leaf.util.map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.schedule.Activity;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

public final class ActivityArrayMap<V> implements Map<Activity, V> {
    public static final int BITS = 25;

    public int bitset = 0;
    public final Object[] a = new Object[BITS + 1];
    private int size = 0;

    private transient KeySet keySet;
    private transient Values valuesCollection;
    private transient EntrySet entrySet;

    public ActivityArrayMap() {
        if (BuiltInRegistries.ACTIVITY.size() != BITS + 1) {
            throw new IllegalStateException("Unexpected registry minecraft:activity size");
        }
    }

    @Override
    public V put(Activity key, V value) {
        int index = key.id;
        int mask = 1 << index;
        @SuppressWarnings("unchecked")
        V oldValue = (V) a[index];
        boolean hadValue = oldValue != null;
        a[index] = value;
        if (!hadValue && value != null) {
            size++;
            bitset |= mask;
        }
        if (hadValue && value == null) {
            size--;
            bitset &= ~mask;
        }
        return oldValue;
    }

    @Override
    public V get(Object key) {
        if (key instanceof Activity activity) {
            @SuppressWarnings("unchecked")
            V value = (V) a[activity.id];
            return value;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof Activity activity && a[activity.id] != null;
    }

    @Override
    public V remove(Object key) {
        if (key instanceof Activity activity) {
            int index = activity.id;
            @SuppressWarnings("unchecked")
            V oldValue = (V) a[index];
            if (oldValue != null) {
                a[index] = null;
                bitset &= ~(1 << index);
                size--;
            }
            return oldValue;
        }
        return null;
    }

    @Override
    public void clear() {
        Arrays.fill(a, null);
        size = 0;
        bitset = 0;
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
    public boolean containsValue(Object value) {
        for (Object v : a) {
            if (Objects.equals(v, value)) return true;
        }
        return false;
    }

    @Override
    public void putAll(Map<? extends Activity, ? extends V> m) {
        for (Entry<? extends Activity, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public Set<Activity> keySet() {
        if (keySet == null) {
            keySet = new KeySet();
        }
        return keySet;
    }

    @Override
    public Collection<V> values() {
        if (valuesCollection == null) {
            valuesCollection = new Values();
        }
        return valuesCollection;
    }

    @Override
    public Set<Entry<Activity, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new EntrySet();
        }
        return entrySet;
    }

    private final class KeySet extends AbstractSet<Activity> {
        @Override
        public Iterator<Activity> iterator() {
            return new Iterator<>() {
                private int index = -1;

                private void advance() {
                    do index++;
                    while (index <= BITS && (bitset & (1 << index)) == 0);
                }

                {
                    advance();
                }

                @Override
                public boolean hasNext() {
                    return index <= BITS;
                }

                @Override
                public Activity next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    Activity activity = BuiltInRegistries.ACTIVITY.byIdOrThrow(index);
                    advance();
                    return activity;
                }
            };
        }

        @Override
        public int size() {
            return ActivityArrayMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            return ActivityArrayMap.this.containsKey(o);
        }

        @Override
        public void clear() {
            ActivityArrayMap.this.clear();
        }
    }

    private final class Values extends AbstractCollection<V> {
        @Override
        public Iterator<V> iterator() {
            return new Iterator<>() {
                private int index = -1;

                private void advance() {
                    do index++;
                    while (index <= BITS && (bitset & (1 << index)) == 0);
                }

                {
                    advance();
                }

                @Override
                public boolean hasNext() {
                    return index <= BITS;
                }

                @Override
                public V next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    @SuppressWarnings("unchecked")
                    V value = (V) a[index];
                    advance();
                    return value;
                }
            };
        }

        @Override
        public int size() {
            return ActivityArrayMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            return ActivityArrayMap.this.containsValue(o);
        }

        @Override
        public void clear() {
            ActivityArrayMap.this.clear();
        }
    }

    private final class EntrySet extends AbstractSet<Entry<Activity, V>> {
        @Override
        public Iterator<Entry<Activity, V>> iterator() {
            return new Iterator<>() {
                private int index = -1;
                private int last = -1;

                private void advance() {
                    do index++;
                    while (index <= BITS && (bitset & (1 << index)) == 0);
                }

                {
                    advance();
                }

                @Override
                public boolean hasNext() {
                    return index <= BITS;
                }

                @Override
                public Entry<Activity, V> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    last = index;
                    Activity activity = BuiltInRegistries.ACTIVITY.byIdOrThrow(index);
                    @SuppressWarnings("unchecked")
                    V value = (V) a[index];
                    advance();
                    return new SimpleEntry<>(activity, value);
                }

                @Override
                public void remove() {
                    if (last == -1) throw new IllegalStateException();
                    if (a[last] != null) {
                        a[last] = null;
                        bitset &= ~(1 << last);
                        size--;
                    }
                    last = -1;
                }
            };
        }

        @Override
        public int size() {
            return ActivityArrayMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Entry<?, ?> entry && entry.getKey() instanceof Activity activity) {
                @SuppressWarnings("unchecked")
                V value = (V) a[activity.id];
                return Objects.equals(value, entry.getValue());
            }
            return false;
        }

        @Override
        public void clear() {
            ActivityArrayMap.this.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        if (this.size() != other.size()) return false;

        for (Entry<Activity, V> entry : this.entrySet()) {
            Activity key = entry.getKey();
            V value = entry.getValue();
            if (!Objects.equals(value, other.get(key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Entry<Activity, V> entry : entrySet()) {
            hash += entry.hashCode();
        }
        return hash;
    }
}
