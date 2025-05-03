package org.dreeam.leaf.util.queue;

/// Lock-free Single Producer Single Consumer Queue
public class SpscIntQueue {

    private final int[] data;
    private final PaddedAtomicInteger producerIdx = new PaddedAtomicInteger();
    private final PaddedAtomicInteger producerCachedIdx = new PaddedAtomicInteger();
    private final PaddedAtomicInteger consumerIdx = new PaddedAtomicInteger();
    private final PaddedAtomicInteger consumerCachedIdx = new PaddedAtomicInteger();

    public SpscIntQueue(int size) {
        this.data = new int[size + 1];
    }

    public final boolean send(int e) {
        final int idx = producerIdx.getOpaque();
        int nextIdx = idx + 1;
        if (nextIdx == data.length) {
            nextIdx = 0;
        }
        int cachedIdx = consumerCachedIdx.getPlain();
        if (nextIdx == cachedIdx) {
            cachedIdx = consumerIdx.getAcquire();
            consumerCachedIdx.setPlain(cachedIdx);
            if (nextIdx == cachedIdx) {
                return false;
            }
        }
        data[idx] = e;
        producerIdx.setRelease(nextIdx);
        return true;
    }


    public final int recv() {
        final int idx = consumerIdx.getOpaque();
        int cachedIdx = producerCachedIdx.getPlain();
        if (idx == cachedIdx) {
            cachedIdx = producerIdx.getAcquire();
            producerCachedIdx.setPlain(cachedIdx);
            if (idx == cachedIdx) {
                return Integer.MAX_VALUE;
            }
        }
        int e = data[idx];
        int nextIdx = idx + 1;
        if (nextIdx == data.length) {
            nextIdx = 0;
        }
        consumerIdx.setRelease(nextIdx);
        return e;
    }

    public final int size() {
        return this.data.length;
    }

    static class PaddedAtomicInteger extends java.util.concurrent.atomic.AtomicInteger {
        @SuppressWarnings("unused")
        private int i1, i2, i3, i4, i5, i6, i7, i8,
            i9, i10, i11, i12, i13, i14, i15;
    }
}
