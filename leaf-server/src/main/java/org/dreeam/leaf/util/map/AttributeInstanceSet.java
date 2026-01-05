package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;

public final class AttributeInstanceSet extends AbstractCollection<AttributeInstance> implements Set<AttributeInstance> {
    public final IntArraySet inner;
    public final AttributeInstanceArrayMap map;

    public AttributeInstanceSet(AttributeInstanceArrayMap map) {
        this.map = map;
        inner = new IntArraySet();
    }

    @Override
    public boolean add(AttributeInstance instance) {
        return inner.add(instance.getAttribute().value().id);
    }

    public boolean addAttribute(Attribute attribute) {
        return inner.add(attribute.id);
    }

    @Override
    public boolean remove(Object o) {
        return o instanceof AttributeInstance instance && inner.remove(instance.getAttribute().value().id);
    }

    @Override
    public @NotNull Iterator<AttributeInstance> iterator() {
        return new CloneIterator(inner.toIntArray(), map);
    }

    @Override
    public int size() {
        return inner.size();
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof AttributeInstance instance) {
            return inner.contains(instance.getAttribute().value().id);
        }
        return false;
    }

    @Override
    public AttributeInstance @NotNull [] toArray() {
        int[] innerClone = inner.toIntArray();
        AttributeInstance[] arr = new AttributeInstance[innerClone.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = map.getInstance(innerClone[i]);
        }
        return arr;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> T @NotNull [] toArray(T[] a) {
        if (a == null || (a.getClass() == AttributeInstance[].class && a.length == 0)) {
            return (T[]) toArray();
        }
        if (a.length < size()) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size());
        }
        System.arraycopy((T[]) toArray(), 0, a, 0, size());
        if (a.length > size()) {
            a[size()] = null;
        }
        return a;
    }

    private static final class CloneIterator implements Iterator<AttributeInstance> {
        private final int[] array;
        private int index = 0;
        private final AttributeInstanceArrayMap map;

        CloneIterator(int[] array, AttributeInstanceArrayMap map) {
            this.array = array;
            this.map = map;
        }

        @Override
        public boolean hasNext() {
            return index < array.length;
        }

        @Override
        public AttributeInstance next() {
            if (!hasNext()) throw new NoSuchElementException();
            return map.getInstance(array[index++]);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Set<?> s)) return false;
        if (s.size() != size()) return false;
        return containsAll(s);
    }
}
