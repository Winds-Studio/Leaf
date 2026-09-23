package org.dreeam.leaf.util;

import net.minecraft.server.level.ChunkMap;

public record TrackerSlice(ChunkMap.TrackedEntity[] array, int start, int end) {

    public TrackerSlice(final ChunkMap.TrackedEntity[] trackers) {
        this(trackers, 0, trackers.length);
    }

    public int size() {
        return end - start;
    }

    public TrackerSlice[] splitEvenly(final int parts) {
        if (parts == 1) {
            return new TrackerSlice[]{this};
        }

        final TrackerSlice[] result = new TrackerSlice[parts];
        final int sliceSize = size();
        final int base = sliceSize / parts;
        final int remainder = sliceSize % parts;

        int curr = start;
        for (int i = 0; i < parts; i++) {
            final int endIdx = curr + base + (i < remainder ? 1 : 0);
            result[i] = new TrackerSlice(array, curr, endIdx);
            curr = endIdx;
        }
        return result;
    }

    public TrackerSlice[] chunks(final int chunkSize) {
        final int len = (size() + chunkSize - 1) / chunkSize;
        final TrackerSlice[] result = new TrackerSlice[len];

        int curr = start;
        for (int i = 0; i < len; i++) {
            final int endIdx = Math.min(curr + chunkSize, end);
            result[i] = new TrackerSlice(array, curr, endIdx);
            curr = endIdx;
        }
        return result;
    }
}
