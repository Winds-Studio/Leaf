package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.objects.AbstractObject2DoubleMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import java.util.NoSuchElementException;

public final class FluidHeightMap extends AbstractObject2DoubleMap<TagKey<Fluid>> {
    private double water = 0.0;
    private double lava = 0.0;

    @Override
    public int size() {
        return 2;
    }

    @Override
    public ObjectSet<Entry<TagKey<Fluid>>> object2DoubleEntrySet() {
        return new EntrySet();
    }

    @Override
    public double getDouble(Object k) {
        return k == FluidTags.WATER ? water : k == FluidTags.LAVA ? lava : 0.0;
    }

    @Override
    public double put(TagKey<Fluid> k, double v) {
        if (k == FluidTags.WATER) {
            double prev = this.water;
            this.water = v;
            return prev;
        } else if (k == FluidTags.LAVA) {
            double prev = this.lava;
            this.lava = v;
            return prev;
        }
        return 0.0;
    }

    @Override
    public void clear() {
        this.water = 0.0;
        this.lava = 0.0;
    }

    private final class EntrySet extends AbstractObjectSet<Entry<TagKey<Fluid>>> {
        @Override
        public ObjectIterator<Entry<TagKey<Fluid>>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?> entry)) {
                return false;
            }
            Object key = entry.getKey();
            if (key == FluidTags.WATER) {
                return entry.getDoubleValue() == water;
            } else if (key == FluidTags.LAVA) {
                return entry.getDoubleValue() == lava;
            }
            return false;
        }

        @Override
        public boolean remove(final Object o) {
            if (!(o instanceof Entry<?> entry)) {
                return false;
            }
            Object key = entry.getKey();
            if (key == FluidTags.WATER) {
                water = 0.0;
                return true;
            } else if (key == FluidTags.LAVA) {
                lava = 0.0;
                return true;
            }
            return false;
        }
    }

    private final class EntryIterator implements ObjectIterator<Entry<TagKey<Fluid>>> {
        private int index = 0;
        private Entry<TagKey<Fluid>> entry = null;

        @Override
        public boolean hasNext() {
            return index < 2;
        }

        @Override
        public Entry<TagKey<Fluid>> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (index == 0) {
                index++;
                return entry = new DoubleEntry(FluidTags.WATER);
            } else {
                index++;
                return entry = new DoubleEntry(FluidTags.LAVA);
            }
        }

        @Override
        public void remove() {
            if (entry == null) {
                throw new IllegalStateException();
            }
            TagKey<Fluid> key = entry.getKey();
            if (key == FluidTags.WATER) {
                water = 0.0;
            } else if (key == FluidTags.LAVA) {
                lava = 0.0;
            }
            entry = null;
        }
    }

    private final class DoubleEntry implements Entry<TagKey<Fluid>> {
        private final TagKey<Fluid> key;

        public DoubleEntry(TagKey<Fluid> key) {
            this.key = key;
        }

        @Override
        public TagKey<Fluid> getKey() {
            return key;
        }

        @Override
        public double getDoubleValue() {
            return key == FluidTags.WATER ? water : lava;
        }

        @Override
        public double setValue(double value) {
            double prev;
            if (key == FluidTags.WATER) {
                prev = water;
                water = value;
            } else {
                prev = lava;
                lava = value;
            }
            return prev;
        }
    }
}
