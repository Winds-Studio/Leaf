package org.dreeam.leaf.util;

import net.minecraft.world.entity.Entity;

public final class FastBitRadixSort {

    private static final int SMALL_ARRAY_THRESHOLD = 11;
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
        if (size - 1 <= SMALL_ARRAY_THRESHOLD) {
            for (int i = 0; i < size; i++) {
                this.bitsBuffer[i] = Double.doubleToRawLongBits(((Entity) entities[i]).distanceToSqr(tx, ty, tz));
            }
            insertionSort(entities, this.bitsBuffer, 0, size - 1);
            return;
        }

        long orBits = 0L;
        long andBits = -1L;
        for (int i = 0; i < size; i++) {
            long key = Double.doubleToRawLongBits(((Entity) entities[i]).distanceToSqr(tx, ty, tz));
            this.bitsBuffer[i] = key;
            orBits |= key;
            andBits &= key;
        }

        fastRadixSort(entities, this.bitsBuffer, 0, size - 1, highestDifferingBit(orBits, andBits));
    }

    private static int highestDifferingBit(long orBits, long andBits) {
        long differingBits = (orBits ^ andBits) & Long.MAX_VALUE;
        return 63 - Long.numberOfLeadingZeros(differingBits);
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
        long leftOrBits = 0L;
        long leftAndBits = -1L;
        long rightOrBits = 0L;
        long rightAndBits = -1L;

        while (i <= j) {
            while (i <= j && (bits[i] & mask) == 0) {
                leftOrBits |= bits[i];
                leftAndBits &= bits[i];
                i++;
            }
            while (i <= j && (bits[j] & mask) != 0) {
                rightOrBits |= bits[j];
                rightAndBits &= bits[j];
                j--;
            }
            if (i < j) {
                leftOrBits |= bits[j];
                leftAndBits &= bits[j];
                rightOrBits |= bits[i];
                rightAndBits &= bits[i];
                swap(ents, bits, i++, j--);
            }
        }

        if (low < j) {
            fastRadixSort(ents, bits, low, j, highestDifferingBit(leftOrBits, leftAndBits));
        }
        if (i < high) {
            fastRadixSort(ents, bits, i, high, highestDifferingBit(rightOrBits, rightAndBits));
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
