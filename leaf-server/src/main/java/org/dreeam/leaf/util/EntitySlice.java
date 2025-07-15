package org.dreeam.leaf.util;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;

@NullMarked
public record EntitySlice(Entity[] array, int start, int end) implements Iterable<Entity> {
    public EntitySlice(final Entity[] entities) {
        this(entities, 0, entities.length);
    }

    public int size() {
        return end - start;
    }

    public boolean isEmpty() {
        return start >= end;
    }

    public Entity get(final int index) {
        return array[start + index];
    }

    @Override
    public Iterator<Entity> iterator() {
        return new SliceIterator(this);
    }

    public EntitySlice[] splitEvenly(int parts) {
        if (parts > size()) {
            parts = size();
        }
        if (parts <= 1) {
            return new EntitySlice[]{this};
        }

        final EntitySlice[] result = new EntitySlice[parts];
        final int sliceSize = size();
        final int base = sliceSize / parts;
        final int remainder = sliceSize % parts;

        int curr = start;
        for (int i = 0; i < parts; i++) {
            int endIdx = curr + base + (i < remainder ? 1 : 0);
            result[i] = new EntitySlice(array, curr, endIdx);
            curr = endIdx;
        }
        return result;
    }

    public EntitySlice[] splitAt(final int index) {
        final int m = start + index;
        return new EntitySlice[]{
            new EntitySlice(array, start, m),
            new EntitySlice(array, m, end)
        };
    }

    public EntitySlice subSlice(final int startIndex, final int endIndex) {
        return new EntitySlice(array, start + startIndex, start + endIndex);
    }

    public EntitySlice subSlice(final int startIndex) {
        return subSlice(startIndex, size());
    }

    public EntitySlice[] chunks(final int chunkSize) {
        if (isEmpty() || chunkSize <= 0) {
            return new EntitySlice[0];
        }

        final int len = (size() + chunkSize - 1) / chunkSize;
        EntitySlice[] result = new EntitySlice[len];

        int curr = start;
        for (int i = 0; i < len; i++) {
            final int endIdx = Math.min(curr + chunkSize, end);
            result[i] = new EntitySlice(array, curr, endIdx);
            curr = endIdx;
        }
        return result;
    }

    private static final class SliceIterator implements Iterator<Entity> {
        private final EntitySlice slice;
        private int current;

        public SliceIterator(EntitySlice slice) {
            this.slice = slice;
            this.current = slice.start;
        }

        @Override
        public boolean hasNext() {
            return current < slice.end;
        }

        @Override
        public Entity next() {
            if (!hasNext()) {
                throw new IndexOutOfBoundsException();
            }
            return slice.array[current++];
        }
    }
}
