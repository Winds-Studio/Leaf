package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.entity.schedule.Activity;
import org.dreeam.leaf.util.RegistryTypeManager;

import java.util.*;

public final class ActivityBitSet extends AbstractObjectSet<Activity> {

    private int bitset = 0;
    private boolean dirty = true;

    public boolean unsetDirty() {
        if (dirty) {
            dirty = false;
            return true;
        } else {
            return false;
        }
    }

    public int bitSet() {
        return bitset;
    }

    @Override
    public boolean add(Activity activity) {
        int mask = 1 << activity.id;
        if ((bitset & mask) != 0) return false;
        bitset |= mask;
        dirty = true;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Activity activity) {
            int mask = 1 << activity.id;
            if ((bitset & mask) != 0) {
                bitset &= ~mask;
                dirty = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return (o instanceof Activity activity) && ((bitset & (1 << activity.id)) != 0);
    }

    @Override
    public ObjectIterator<Activity> iterator() {
        return new ObjectIterator<>() {
            private int index = 0;
            {
                while (index < RegistryTypeManager.ACTIVITY_SIZE && (bitset & (1 << index)) == 0) {
                    index++;
                }
            }

            @Override
            public boolean hasNext() {
                return index < RegistryTypeManager.ACTIVITY_SIZE;
            }

            @Override
            public Activity next() {
                if (!hasNext()) throw new NoSuchElementException();
                Activity act = RegistryTypeManager.ACTIVITY_DIRECT[index++];
                while (index < RegistryTypeManager.ACTIVITY_SIZE && (bitset & (1 << index)) == 0) {
                    index++;
                }
                return act;
            }
        };
    }

    @Override
    public int size() {
        return Integer.bitCount(bitset);
    }

    @Override
    public void clear() {
        bitset = 0;
        dirty = true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Set<?> s)) return false;
        if (s.size() != size()) return false;
        return containsAll(s);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < RegistryTypeManager.ACTIVITY_SIZE; i++) {
            if ((bitset & (1 << i)) != 0) {
                hash += RegistryTypeManager.ACTIVITY_DIRECT[i].hashCode();
            }
        }
        return hash;
    }
}
