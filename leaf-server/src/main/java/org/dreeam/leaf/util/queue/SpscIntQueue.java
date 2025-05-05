package org.dreeam.leaf.util.queue;

import java.util.OptionalInt;

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


    public final OptionalInt recv() {
        final int idx = consumerIdx.getOpaque();
        int cachedIdx = producerCachedIdx.getPlain();
        if (idx == cachedIdx) {
            cachedIdx = producerIdx.getAcquire();
            producerCachedIdx.setPlain(cachedIdx);
            if (idx == cachedIdx) {
                return OptionalInt.empty();
            }
        }
        int e = data[idx];
        int nextIdx = idx + 1;
        if (nextIdx == data.length) {
            nextIdx = 0;
        }
        consumerIdx.setRelease(nextIdx);
        return OptionalInt.of(e);
    }

    public final int size() {
        return this.data.length;
    }

    static class PaddedAtomicInteger extends java.util.concurrent.atomic.AtomicInteger {
        // @formatter:off
        @SuppressWarnings("unused")
        private byte
            i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14, i15,
            j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15,
            k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15,
            l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15,

            m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15,
            n0, n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11, n12, n13, n14, n15,
            o0, o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15,
            p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15,

            q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15,
            r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15,
            s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15,
            t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,

            u0, u1, u2, u3, u4, u5, u6, u7, u8, u9, u10, u11, u12, u13, u14, u15,
            v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15,
            w0, w1, w2, w3, w4, w5, w6, w7, w8, w9, w10, w11, w12, w13, w14, w15,
            x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11;
        // @formatter:on
    }
}
