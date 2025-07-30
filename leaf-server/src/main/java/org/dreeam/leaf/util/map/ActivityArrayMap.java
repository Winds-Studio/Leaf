package org.dreeam.leaf.util.map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.schedule.Activity;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

public final class ActivityArrayMap<V> implements Map<Activity, V> {

    public int[] k;
    public V[] v;
    private int size = 0;
    private int bitset = 0;
    private transient KeySet keySet;
    private transient Values values;
    private transient EntrySet entrySet;

    public ActivityArrayMap(V[] arr) {
        this.k = new int[arr.length];
        this.v = arr;
    }

    private int findIndex(int activity) {
        int mask = 1 << activity;
        if ((bitset & mask) == 0) {
            return -1;
        }
        for (int i = 0; i < size; i++) {
            if (k[i] == activity) {
                return i;
            }
        }
        return -1;
    }

    private void ensureCap() {
        if (size >= k.length) {
            int newCapacity = Math.max(2, k.length + k.length / 2);
            k = Arrays.copyOf(k, newCapacity);
            v = Arrays.copyOf(v, newCapacity);
        }
    }

    private void removeAtIndex(int index) {
        if (index < 0 || index >= size) {
            return;
        }
        bitset &= ~(1 << k[index]);
        System.arraycopy(k, index + 1, k, index, size - index - 1);
        System.arraycopy(v, index + 1, v, index, size - index - 1);

        size--;
        v[size] = null;
    }

    @Override
    public V put(Activity key, V value) {
        int index = findIndex(key.id);
        if (index >= 0) {
            final V oldValue = v[index];
            if (value == null) {
                removeAtIndex(index);
            } else {
                v[index] = value;
            }
            return oldValue;
        } else if (value != null) {
            ensureCap();
            k[size] = key.id;
            v[size] = value;
            bitset |= (1 << key.id);
            size++;
        }
        return null;
    }

    @Override
    public V get(Object key) {
        if (key instanceof Activity activity) {
            int index = findIndex(activity.id);
            if (index >= 0) {
                return v[index];
            }
        }
        return null;
    }

    public V getValue(int key) {
        int index = findIndex(key);
        if (index >= 0) {
            return v[index];
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof Activity activity)) {
            return false;
        }
        return (bitset & 1 << activity.id) != 0;
    }

    @Override
    public V remove(Object key) {
        if (key instanceof Activity activity) {
            int index = findIndex(activity.id);
            if (index >= 0) {
                V oldValue = v[index];
                removeAtIndex(index);
                return oldValue;
            }
        }
        return null;
    }

    @Override
    public void clear() {
        Arrays.fill(v, 0, size, null);
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
        for (int i = 0; i < size; i++) {
            if (Objects.equals(v[i], value)) {
                return true;
            }
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
        if (values == null) {
            values = new Values();
        }
        return values;
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
                private int index = 0;
                private int lastReturned = -1;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public Activity next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    lastReturned = index;
                    return BuiltInRegistries.ACTIVITY.byIdOrThrow(k[index++]);
                }

                @Override
                public void remove() {
                    if (lastReturned < 0) throw new IllegalStateException();
                    ActivityArrayMap.this.removeAtIndex(lastReturned);
                    index = lastReturned;
                    lastReturned = -1;
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
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public V next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return v[index++];
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
                private int index = 0;
                private int lastReturned = -1;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public Entry<Activity, V> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    lastReturned = index;
                    int key = k[index];
                    V value = v[index];
                    index++;
                    return new SimpleEntry<>(BuiltInRegistries.ACTIVITY.byIdOrThrow(key), value);
                }

                @Override
                public void remove() {
                    if (lastReturned < 0) throw new IllegalStateException();
                    ActivityArrayMap.this.removeAtIndex(lastReturned);
                    index = lastReturned;
                    lastReturned = -1;
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
                int index = findIndex(activity.id);
                if (index >= 0) {
                    return Objects.equals(v[index], entry.getValue());
                }
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
        for (int i = 0; i < size; i++) {
            hash += Objects.hashCode(k[i]) ^ Objects.hashCode(v[i]);
        }
        return hash;
    }
}
