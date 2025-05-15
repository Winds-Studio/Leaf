package org.dreeam.leaf.util.map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/// fast array backend map with O(1) get & put & remove
public class AttributeInstanceArrayMap implements Map<Holder<Attribute>, AttributeInstance>, Cloneable {
    private int size = 0;
    private transient AttributeInstance[] a = new AttributeInstance[32];
    private transient KeySet keys;
    private transient Values values;
    private transient EntrySet entries;

    public AttributeInstanceArrayMap() {
        if (BuiltInRegistries.ATTRIBUTE.size() != 32) {
            throw new IllegalStateException("Registered custom attribute");
        }
    }

    public AttributeInstanceArrayMap(final @NotNull Map<Holder<Attribute>, AttributeInstance> m) {
        this();
        for (AttributeInstance e : m.values()) {
            setByIndex(e.getAttribute().value().uid, e);
        }
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
    public final int size() {
        return size;
    }

    @Override
    public final boolean isEmpty() {
        return size == 0;
    }

    @Override
    public final boolean containsKey(Object key) {
        if (key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute) {
            int uid = attribute.uid;
            return uid >= 0 && uid < a.length && a[uid] != null;
        }
        return false;
    }

    @Override
    public final boolean containsValue(Object value) {
        for (final AttributeInstance instance : a) {
            if (Objects.equals(value, instance)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final AttributeInstance get(Object key) {
        return key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute ? a[attribute.uid] : null;
    }

    @Override
    public final AttributeInstance put(@NotNull Holder<Attribute> key, AttributeInstance value) {
        int uid = key.value().uid;
        AttributeInstance prev = a[uid];
        setByIndex(uid, value);
        return prev;
    }

    @Override
    public final AttributeInstance remove(Object key) {
        if (!(key instanceof Holder<?> holder) || !(holder.value() instanceof Attribute attribute)) return null;
        int uid = attribute.uid;
        AttributeInstance prev = a[uid];
        setByIndex(uid, null);
        return prev;
    }

    @Override
    public final void putAll(@NotNull Map<? extends Holder<Attribute>, ? extends AttributeInstance> m) {
        m.forEach(this::put);
    }

    @Override
    public final void clear() {
        Arrays.fill(a, null);
        size = 0;
    }

    @Override
    public final @NotNull Set<Holder<Attribute>> keySet() {
        if (keys == null) {
            keys = new KeySet();
        }
        return keys;
    }

    @Override
    public final @NotNull Collection<AttributeInstance> values() {
        if (values == null) {
            values = new Values();
        }
        return values;
    }

    @Override
    public final @NotNull Set<Entry<Holder<Attribute>, AttributeInstance>> entrySet() {
        if (entries == null) {
            entries = new EntrySet();
        }
        return entries;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        return entrySet().equals(other.entrySet());
    }

    @Override
    public final int hashCode() {
        return entrySet().hashCode();
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
            return BuiltInRegistries.ATTRIBUTE.asHolderIdMap().byIdOrThrow(currentIndex);
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
            Holder<Attribute> key = BuiltInRegistries.ATTRIBUTE.asHolderIdMap().byIdOrThrow(nextIndex);
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
