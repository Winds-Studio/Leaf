package org.dreeam.leaf.util.queue;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MpmcIntQueue {
    static final class Slot {
        public volatile long sequence;
        public volatile int value;

        Slot(long sequence) {
            this.sequence = sequence;
        }
    }

    private final int capacity;
    private final Padded padded1 = new Padded();
    private final AtomicReferenceArray<Slot> buffer;
    private final Padded padded2 = new Padded();
    private final AtomicLong head = new AtomicLong();
    private final Padded padded3 = new Padded();
    private final AtomicLong tail = new AtomicLong();

    public MpmcIntQueue(int capacity) {
        this.capacity = capacity;
        buffer = new AtomicReferenceArray<>(capacity);
        for (int i = 0; i < capacity; i++) {
            buffer.set(i, new Slot(i));
        }
    }

    public boolean send(int value) {
        while (true) {
            long currentTail = tail.get();
            int index = (int) (currentTail % capacity);
            Slot slot = buffer.get(index);
            long seq = slot.sequence;

            long dif = seq - currentTail;

            if (dif == 0) {
                if (tail.compareAndSet(currentTail, currentTail + 1)) {
                    slot.value = value;
                    slot.sequence = currentTail + 1;
                    return true;
                }
            } else if (dif < 0) {
                return false;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    public OptionalInt recv() {
        while (true) {
            long currentHead = head.get();
            int index = (int) (currentHead % capacity);
            Slot slot = buffer.get(index);
            long seq = slot.sequence;
            long dif = seq - (currentHead + 1);

            if (dif == 0) {
                if (head.compareAndSet(currentHead, currentHead + 1)) {
                    int value = slot.value;
                    slot.sequence = currentHead + capacity;
                    return OptionalInt.of(value);
                }
            } else if (dif < 0) {
                return OptionalInt.empty();
            } else {
                Thread.onSpinWait();
            }
        }
    }

    public int size() {
        return (int) (tail.get() - head.get());
    }

    static class Padded {
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
