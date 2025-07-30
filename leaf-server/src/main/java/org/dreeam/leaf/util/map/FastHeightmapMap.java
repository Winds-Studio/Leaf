package org.dreeam.leaf.util.map;

import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Fast map implementation optimized for Heightmap.Types enum.
 */
public class FastHeightmapMap implements Map<Heightmap.Types, Heightmap> {
    private final Heightmap[] values;
    private int size = 0;

    public FastHeightmapMap() {
        // Get max ID from Heightmap.Types enum constants
        int maxId = 0;
        for (Heightmap.Types type : Heightmap.Types.values()) {
            maxId = Math.max(maxId, type.id);
        }
        this.values = new Heightmap[maxId + 1];
    }

    @Override
    @Nullable
    public Heightmap get(Object key) {
        if (key instanceof Heightmap.Types) {
            return values[((Heightmap.Types) key).id];
        }
        return null;
    }

    @Override
    @Nullable
    public Heightmap put(Heightmap.Types key, Heightmap value) {
        int id = key.id;
        Heightmap old = values[id];
        values[id] = value;
        if (old == null && value != null) {
            size++;
        } else if (old != null && value == null) {
            size--;
        }
        return old;
    }

    @Override
    @Nullable
    public Heightmap remove(Object key) {
        if (key instanceof Heightmap.Types) {
            int id = ((Heightmap.Types) key).id;
            Heightmap old = values[id];
            values[id] = null;
            if (old != null) {
                size--;
            }
            return old;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof Heightmap.Types) {
            return values[((Heightmap.Types) key).id] != null;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Heightmap h : values) {
            if (h != null && h.equals(value)) {
                return true;
            }
        }
        return false;
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
    public void clear() {
        Arrays.fill(values, null);
        size = 0;
    }

    public Heightmap computeIfAbsent(Heightmap.Types key, java.util.function.Function<Heightmap.Types, Heightmap> mappingFunction) {
        int id = key.id;
        Heightmap existing = values[id];
        if (existing != null) {
            return existing;
        }
        Heightmap newValue = mappingFunction.apply(key);
        if (newValue != null) {
            values[id] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public Set<Heightmap.Types> keySet() {
        Set<Heightmap.Types> keys = java.util.EnumSet.noneOf(Heightmap.Types.class);
        for (Heightmap.Types type : Heightmap.Types.values()) {
            if (values[type.id] != null) {
                keys.add(type);
            }
        }
        return keys;
    }

    @Override
    public Collection<Heightmap> values() {
        java.util.List<Heightmap> list = new java.util.ArrayList<>(size);
        for (Heightmap h : values) {
            if (h != null) {
                list.add(h);
            }
        }
        return list;
    }

    @Override
    public Set<Entry<Heightmap.Types, Heightmap>> entrySet() {
        Set<Entry<Heightmap.Types, Heightmap>> entries = new java.util.HashSet<>();
        for (Heightmap.Types type : Heightmap.Types.values()) {
            Heightmap value = values[type.id];
            if (value != null) {
                entries.add(new java.util.AbstractMap.SimpleImmutableEntry<>(type, value));
            }
        }
        return entries;
    }

    @Override
    public void putAll(Map<? extends Heightmap.Types, ? extends Heightmap> m) {
        for (Entry<? extends Heightmap.Types, ? extends Heightmap> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
