package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;

@NullMarked
public final class TrackerByFluidMap extends AbstractReference2ObjectMap<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator> {
    private EntityFluidInteraction.@Nullable CurrentAccumulator water = null;
    private EntityFluidInteraction.@Nullable CurrentAccumulator lava = null;

    @Override
    public int size() {
        return 2;
    }

    @Override
    public ObjectSet<Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator>> reference2ObjectEntrySet() {
        return new EntrySet();
    }

    @Override
    public EntityFluidInteraction.@Nullable CurrentAccumulator get(Object k) {
        return k == FluidTags.WATER ? water : k == FluidTags.LAVA ? lava : null;
    }

    @Override
    public EntityFluidInteraction.@Nullable CurrentAccumulator put(TagKey<Fluid> k, EntityFluidInteraction.CurrentAccumulator v) {
        if (k == FluidTags.WATER) {
            EntityFluidInteraction.CurrentAccumulator prev = this.water;
            this.water = v;
            return prev;
        } else if (k == FluidTags.LAVA) {
            EntityFluidInteraction.CurrentAccumulator prev = this.lava;
            this.lava = v;
            return prev;
        }
        return null;
    }

    @Override
    public void clear() {
        this.water = null;
        this.lava = null;
    }

    public void init() {
        this.water = new EntityFluidInteraction.CurrentAccumulator();
        this.lava = new EntityFluidInteraction.CurrentAccumulator();
    }

    public void reset() {
        if (this.water != null) {
            this.water.reset();
        }
        if (this.lava != null) {
            this.lava.reset();
        }
    }

    private final class EntrySet extends AbstractObjectSet<Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator>> {
        @Override
        public ObjectIterator<Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?> entry)) {
                return false;
            }
            Object key = entry.getKey();
            if (key == FluidTags.WATER) {
                return entry.getValue() == water;
            } else if (key == FluidTags.LAVA) {
                return entry.getValue() == lava;
            }
            return false;
        }

        @Override
        public boolean remove(final Object o) {
            if (!(o instanceof Entry<?, ?> entry)) {
                return false;
            }
            Object key = entry.getKey();
            if (key == FluidTags.WATER) {
                water = null;
                return true;
            } else if (key == FluidTags.LAVA) {
                lava = null;
                return true;
            }
            return false;
        }
    }

    private final class EntryIterator implements ObjectIterator<Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator>> {
        private int index = 0;
        @Nullable
        private Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator> entry = null;

        @Override
        public boolean hasNext() {
            if (index == 0 && water != null) {
                return true;
            } else {
                return (index == 0 || index == 1) && lava != null;
            }
        }

        @Override
        public Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator> next() {
            if (index == 0 && water != null) {
                index = 1;
                return entry = new TrackerEntry(FluidTags.WATER);
            } else if ((index == 0 || index == 1) && lava != null) {
                index = 2;
                return entry = new TrackerEntry(FluidTags.LAVA);
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            if (entry == null) {
                throw new IllegalStateException();
            }
            TagKey<Fluid> key = entry.getKey();
            if (key == FluidTags.WATER) {
                water = null;
            } else if (key == FluidTags.LAVA) {
                lava = null;
            }
            entry = null;
        }
    }

    private final class TrackerEntry implements Entry<TagKey<Fluid>, EntityFluidInteraction.CurrentAccumulator> {
        private final TagKey<Fluid> key;

        public TrackerEntry(TagKey<Fluid> key) {
            this.key = key;
        }

        @Override
        public TagKey<Fluid> getKey() {
            return key;
        }

        @Override
        public EntityFluidInteraction.CurrentAccumulator getValue() {
            return key == FluidTags.WATER ? water : lava;
        }

        @Override
        public EntityFluidInteraction.CurrentAccumulator setValue(EntityFluidInteraction.CurrentAccumulator value) {
            EntityFluidInteraction.CurrentAccumulator prev;
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
