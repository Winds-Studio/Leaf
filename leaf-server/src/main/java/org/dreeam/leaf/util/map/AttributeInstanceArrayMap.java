package org.dreeam.leaf.util.map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.dreeam.leaf.util.RegistryTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

// fast array backend map with O(1) get & put & remove
public final class AttributeInstanceArrayMap implements Map<Holder<Attribute>, AttributeInstance>, Cloneable {

    private int size = 0;
    private transient AttributeInstance[] a = new AttributeInstance[RegistryTypeManager.ATTRIBUTE_SIZE];
    private transient KeySet keys;
    private transient Values values;
    private transient EntrySet entries;

    public AttributeInstanceArrayMap() {
        // The check for a fixed vanilla size is no longer necessary
    }

    public AttributeInstanceArrayMap(final @NotNull Map<Holder<Attribute>, AttributeInstance> m) {
        this();
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
            int uid = attribute.uid;
            return uid >= 0 && uid < a.length && a[uid] != null;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return value instanceof AttributeInstance val && Objects.equals(getInstance(val.getAttribute().value().uid), val);
    }

    @Override
    public AttributeInstance get(Object key) {
        return key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute ? a[attribute.uid] : null;
    }

    @Nullable
    public AttributeInstance getInstance(int key) {
        return a[key];
    }

    @Override
    public AttributeInstance put(@NotNull Holder<Attribute> key, AttributeInstance value) {
        int uid = key.value().uid;
        AttributeInstance prev = a[uid];
        setByIndex(uid, value);
        return prev;
    }

    @Override
    public AttributeInstance remove(Object key) {
        if (!(key instanceof Holder<?> holder) || !(holder.value() instanceof Attribute attribute)) return null;
        int uid = attribute.uid;
        AttributeInstance prev = a[uid];
        setByIndex(uid, null);
        return prev;
    }

    @Override
    public void putAll(@NotNull Map<? extends Holder<Attribute>, ? extends AttributeInstance> m) {
        for (AttributeInstance e : m.values()) {
            if (e != null) {
                setByIndex(e.getAttribute().value().uid, e);
            }
        }
    }

    @Override
    public void clear() {
        Arrays.fill(a, null);
        size = 0;
    }

    @Override
    public @NotNull Set<Holder<Attribute>> keySet() {
        if (keys == null) {
            keys = new KeySet();
        }
        return keys;
    }

    @Override
    public @NotNull Collection<AttributeInstance> values() {
        if (values == null) {
            values = new Values();
        }
        return values;
    }

    @Override
    public @NotNull Set<Entry<Holder<Attribute>, AttributeInstance>> entrySet() {
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
        public @NotNull Iterator<Holder<Attribute>> iterator() {
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
            return BuiltInRegistries.ATTRIBUTE.get(currentIndex).orElseThrow();
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
        public @NotNull Iterator<AttributeInstance> iterator() {
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
        public AttributeInstance next() {
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
        public @NotNull Iterator<Entry<Holder<Attribute>, AttributeInstance>> iterator() {
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
            Holder<Attribute> key = BuiltInRegistries.ATTRIBUTE.get(nextIndex).orElseThrow();
            AttributeInstance value = a[nextIndex];
            nextIndex = findNextOccupied(nextIndex + 1);
            return new SimpleEntry<>(key, value) {
                @Override
                public AttributeInstance setValue(AttributeInstance newValue) {
                    AttributeInstance old = put(key, newValue);
                    super.setValue(newValue);
                    return old;
                }
            };
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

    private int findNextOccupied(int start) {
        for (int i = start; i < a.length; i++) {
            if (a[i] != null) {
                return i;
            }
        }
        return -1;
    }
}
