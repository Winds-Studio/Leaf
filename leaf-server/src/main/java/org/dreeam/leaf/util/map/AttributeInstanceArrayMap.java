package org.dreeam.leaf.util.map;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.dreeam.leaf.util.RegistryTypeManager;
import org.jspecify.annotations.Nullable;

import java.util.*;

// fast array backend map with O(1) get & put & remove
public final class AttributeInstanceArrayMap implements Map<Holder<Attribute>, AttributeInstance>, Cloneable {

    private int size = 0;
    private transient @Nullable AttributeInstance[] a = new AttributeInstance[RegistryTypeManager.ATTRIBUTE_SIZE];
    private transient @Nullable KeySet keys;
    private transient @Nullable Values values;
    private transient @Nullable EntrySet entries;

    public AttributeInstanceArrayMap() {
    }

    public AttributeInstanceArrayMap(final Map<Holder<Attribute>, AttributeInstance> m) {
        putAll(m);
    }

    private void setByIndex(int index, @Nullable AttributeInstance instance) {
        boolean empty = a[index] == null;
        if (instance == null) {
            if (!empty) {
                size--;
                a[index] = null;
            }
        } else {
            if (empty) {
                size++;
            }
            a[index] = instance;
        }
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
    public boolean containsKey(Object key) {
        if (key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute) {
            int id = attribute.id;
            return id >= 0 && id < a.length && a[id] != null;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return value instanceof AttributeInstance val && Objects.equals(getInstance(val.getAttribute().value().id), val);
    }

    @Override
    public @Nullable AttributeInstance get(Object key) {
        return key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute ? a[attribute.id] : null;
    }

    @Nullable
    public AttributeInstance getInstance(int key) {
        return a[key];
    }

    @Override
    public @Nullable AttributeInstance put(Holder<Attribute> key, @Nullable AttributeInstance value) {
        int id = key.value().id;
        AttributeInstance prev = a[id];
        setByIndex(id, value);
        return prev;
    }

    @Override
    public @Nullable AttributeInstance remove(Object key) {
        if (!(key instanceof Holder<?> holder) || !(holder.value() instanceof Attribute attribute)) return null;
        int id = attribute.id;
        AttributeInstance prev = a[id];
        setByIndex(id, null);
        return prev;
    }

    @Override
    public void putAll(Map<? extends Holder<Attribute>, ? extends AttributeInstance> m) {
        if (!m.isEmpty()) {
            for (AttributeInstance e : m.values()) {
                setByIndex(e.getAttribute().value().id, e);
            }
        }
    }

    @Override
    public void clear() {
        Arrays.fill(a, null);
        size = 0;
    }

    @Override
    public Set<Holder<Attribute>> keySet() {
        if (keys == null) {
            keys = new KeySet();
        }
        return keys;
    }

    @Override
    public Collection<AttributeInstance> values() {
        if (values == null) {
            values = new Values();
        }
        return values;
    }

    @Override
    public Set<Entry<Holder<Attribute>, AttributeInstance>> entrySet() {
        if (entries == null) {
            entries = new EntrySet();
        }
        return entries;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> s)) return false;
        if (s.size() != size()) return false;
        if (o instanceof AttributeInstanceArrayMap that) {
            return Arrays.equals(a, that.a);
        }
        for (Entry<?, ?> e : s.entrySet()) {
            if (!Objects.equals(get(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(a);
    }

    @Override
    public AttributeInstanceArrayMap clone() {
        AttributeInstanceArrayMap c;
        try {
            c = (AttributeInstanceArrayMap) super.clone();
        } catch (CloneNotSupportedException cantHappen) {
            throw new InternalError();
        }
        c.a = a.clone();
        c.entries = null;
        c.keys = null;
        c.values = null;
        return c;
    }

    private final class KeySet extends AbstractSet<Holder<Attribute>> {
        @Override
        public Iterator<Holder<Attribute>> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            return AttributeInstanceArrayMap.this.containsKey(o);
        }
    }

    private final class KeyIterator implements Iterator<Holder<Attribute>> {
        private int currentIndex = -1;
        private int nextIndex = findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return nextIndex != -1;
        }

        @Override
        public Holder<Attribute> next() {
            if (!hasNext()) throw new NoSuchElementException();
            currentIndex = nextIndex;
            nextIndex = findNextOccupied(nextIndex + 1);
            return RegistryTypeManager.ATTRIBUTE[currentIndex];
        }

        @Override
        public void remove() {
            if (currentIndex == -1) throw new IllegalStateException();
            setByIndex(currentIndex, null);
            currentIndex = -1;
        }
    }

    private final class Values extends AbstractCollection<AttributeInstance> {
        @Override
        public Iterator<AttributeInstance> iterator() {
            return new ValueIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            return containsValue(o);
        }
    }

    private final class ValueIterator implements Iterator<AttributeInstance> {
        private int currentIndex = -1;
        private int nextIndex = findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return nextIndex != -1;
        }

        @Override
        public @Nullable AttributeInstance next() {
            if (!hasNext()) throw new NoSuchElementException();
            currentIndex = nextIndex;
            AttributeInstance value = a[nextIndex];
            nextIndex = findNextOccupied(nextIndex + 1);
            return value;
        }

        @Override
        public void remove() {
            if (currentIndex == -1) throw new IllegalStateException();
            setByIndex(currentIndex, null);
            currentIndex = -1;
        }
    }

    private final class EntrySet extends AbstractSet<Entry<Holder<Attribute>, AttributeInstance>> {
        @Override
        public Iterator<Entry<Holder<Attribute>, AttributeInstance>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }
            return Objects.equals(get(e.getKey()), e.getValue());
        }
    }

    private final class EntryIterator implements Iterator<Entry<Holder<Attribute>, AttributeInstance>> {
        private int currentIndex = -1;
        private int nextIndex = findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return nextIndex != -1;
        }

        @Override
        public Entry<Holder<Attribute>, AttributeInstance> next() {
            if (!hasNext()) throw new NoSuchElementException();
            currentIndex = nextIndex;
            AttributeInstance value = a[nextIndex];
            nextIndex = findNextOccupied(nextIndex + 1);
            return new MapEntry(currentIndex, Objects.requireNonNull(value));
        }

        @Override
        public void remove() {
            if (currentIndex == -1) {
                throw new IllegalStateException();
            }
            setByIndex(currentIndex, null);
            currentIndex = -1;
        }
    }

    private final class MapEntry implements Entry<Holder<Attribute>, AttributeInstance> {
        private final int key;
        private AttributeInstance value;

        MapEntry(int key, AttributeInstance value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Holder<Attribute> getKey() {
            return RegistryTypeManager.ATTRIBUTE[this.key];
        }

        @Override
        public AttributeInstance getValue() {
            return value;
        }

        @Override
        public AttributeInstance setValue(AttributeInstance newValue) {
            AttributeInstance oldValue = this.value;
            this.value = newValue;
            AttributeInstanceArrayMap.this.setByIndex(this.key, newValue);
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry<?, ?> entry)) return false;
            return Objects.equals(RegistryTypeManager.ATTRIBUTE[key], entry.getKey()) && Objects.equals(value, entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    private int findNextOccupied(int start) {
        for (int i = start; i < a.length; i++) {
            if (a[i] != null) {
                return i;
            }
        }
        return -1;
    }

    public @Nullable AttributeInstance[] elements() {
        return a;
    }
}
