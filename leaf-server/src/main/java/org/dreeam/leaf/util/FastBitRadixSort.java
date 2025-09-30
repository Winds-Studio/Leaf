package org.dreeam.leaf.util;

import net.minecraft.world.entity.Entity;

public final class FastBitRadixSort {

    private static final int SMALL_ARRAY_THRESHOLD = 6;
    private static final long[] LONGS = new long[0];
    private long[] bitsBuffer = LONGS;

    public void sort(Object[] entities, int size, net.minecraft.core.Position target) {
        if (size <= 1) {
            return;
        }

        if (this.bitsBuffer.length < size) {
            this.bitsBuffer = new long[size];
        }
        double tx = target.x();
        double ty = target.y();
        double tz = target.z();
        for (int i = 0; i < size; i++) {
            this.bitsBuffer[i] = Double.doubleToRawLongBits(((Entity) entities[i]).distanceToSqr(tx, ty, tz));
        }

        fastRadixSort(entities, this.bitsBuffer, 0, size - 1, 62);
    }

    private static void fastRadixSort(
        Object[] ents,
        long[] bits,
        int low,
        int high,
        int bit
    ) {
        if (bit < 0 || low >= high) {
            return;
        }

        if (high - low <= SMALL_ARRAY_THRESHOLD) {
            insertionSort(ents, bits, low, high);
            return;
        }

        int i = low;
        int j = high;
        final long mask = 1L << bit;

        while (i <= j) {
            while (i <= j && (bits[i] & mask) == 0) {
                i++;
            }
            while (i <= j && (bits[j] & mask) != 0) {
                j--;
            }
            if (i < j) {
                swap(ents, bits, i++, j--);
            }
        }

        if (low < j) {
            fastRadixSort(ents, bits, low, j, bit - 1);
        }
        if (i < high) {
            fastRadixSort(ents, bits, i, high, bit - 1);
        }
    }

    private static void insertionSort(
        Object[] ents,
        long[] bits,
        int low,
        int high
    ) {
        for (int i = low + 1; i <= high; i++) {
            int j = i;
            Object currentEntity = ents[j];
            long currentBits = bits[j];

            while (j > low && bits[j - 1] > currentBits) {
                ents[j] = ents[j - 1];
                bits[j] = bits[j - 1];
                j--;
            }
            ents[j] = currentEntity;
            bits[j] = currentBits;
        }
    }

    private static void swap(Object[] ents, long[] bits, int a, int b) {
        Object tempEntity = ents[a];
        ents[a] = ents[b];
        ents[b] = tempEntity;

        long tempBits = bits[a];
        bits[a] = bits[b];
        bits[b] = tempBits;
    }
}
