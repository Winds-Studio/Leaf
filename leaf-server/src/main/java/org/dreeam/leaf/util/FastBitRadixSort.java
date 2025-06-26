package org.dreeam.leaf.util;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.world.entity.Entity;
import java.util.Arrays;
import java.util.List;

public class FastBitRadixSort {

    private static final int THRESHOLD = 32;
    private static final ThreadLocal<ReferenceArrayList<Entity>> ENTITY_BUFFER = ThreadLocal.withInitial(() -> ReferenceArrayList.wrap(new Entity[0]));
    private static final ThreadLocal<long[]> BITS_BUFFER = ThreadLocal.withInitial(() -> new long[0]);
    private static final ThreadLocal<long[]> TEMP_BITS_BUF = ThreadLocal.withInitial(() -> new long[0]);
    private static final ThreadLocal<Entity[]> TEMP_ENTITY_BUF = ThreadLocal.withInitial(() -> new Entity[0]);

    @SuppressWarnings("unchecked")
    public static <T extends Entity> List<T> unsafeBuffer() {
        return (ReferenceArrayList<T>) ENTITY_BUFFER.get();
    }

    public static <T extends Entity> T[] unsafeSort(T[] n, Entity referenceEntity, Class<T> entityClass) {
        var entities = ENTITY_BUFFER.get();
        int size = entities.size();
        if (size <= 1) {
            var result = entities.toArray(n);
            entities.clear();
            return result;
        }

        if (BITS_BUFFER.get().length < size) {
            BITS_BUFFER.set(new long[size]);
        }
        var bitsBuf = BITS_BUFFER.get();
        double x = referenceEntity.getX();
        double y = referenceEntity.getY();
        double z = referenceEntity.getZ();
        Entity[] ele = entities.elements();
        for (int i = 0; i < size; i++) {
            bitsBuf[i] = Double.doubleToRawLongBits(ele[i].distanceToSqr(x, y, z));
        }

        if (size <= THRESHOLD) {
            insertion(ele, bitsBuf, size - 1);
        } else {
            if (TEMP_ENTITY_BUF.get().length < size) {
                TEMP_ENTITY_BUF.set(new Entity[size]);
            }
            if (TEMP_BITS_BUF.get().length < size) {
                TEMP_BITS_BUF.set(new long[size]);
            }
            Entity[] tempEnt = TEMP_ENTITY_BUF.get();
            long[] tempBits = TEMP_BITS_BUF.get();
            lsdRadix(ele, bitsBuf, tempEnt, tempBits, size);
            Arrays.fill(tempEnt, 0, size, null);
        }

        var resultArray = entities.toArray(n);
        entities.clear();
        return resultArray;
    }

    private static void lsdRadix(
        Entity[] ent,
        long[] bits,
        Entity[] tempEnt,
        long[] tempBits,
        int size
    ) {
        Entity[] entSrc = ent;
        long[] bitsSrc = bits;
        Entity[] entDst = tempEnt;
        long[] bitsDst = tempBits;
        int[] count = new int[256];

        for (int shift = 0; shift < 64; shift += 8) {
            Arrays.fill(count, 0);

            for (int i = 0; i < size; i++) {
                final int b = (int)((bitsSrc[i] >>> shift) & 0xFF);
                count[b]++;
            }

            int total = 0;
            for (int i = 0; i < 256; i++) {
                int c = count[i];
                count[i] = total;
                total += c;
            }

            int i = 0;
            for (; i + 3 < size; i += 4) {
                final long v0 = bitsSrc[i];
                final int b0 = (int)((v0 >>> shift) & 0xFF);
                entDst[count[b0]] = entSrc[i];
                bitsDst[count[b0]++] = v0;

                final long v1 = bitsSrc[i + 1];
                final int b1 = (int)((v1 >>> shift) & 0xFF);
                entDst[count[b1]] = entSrc[i + 1];
                bitsDst[count[b1]++] = v1;

                final long v2 = bitsSrc[i + 2];
                final int b2 = (int)((v2 >>> shift) & 0xFF);
                entDst[count[b2]] = entSrc[i + 2];
                bitsDst[count[b2]++] = v2;

                final long v3 = bitsSrc[i + 3];
                final int b3 = (int)((v3 >>> shift) & 0xFF);
                entDst[count[b3]] = entSrc[i + 3];
                bitsDst[count[b3]++] = v3;
            }

            for (; i < size; i++) {
                final long v = bitsSrc[i];
                final int b = (int)((v >>> shift) & 0xFF);
                entDst[count[b]] = entSrc[i];
                bitsDst[count[b]++] = v;
            }

            final Entity[] tempE = entSrc;
            entSrc = entDst;
            entDst = tempE;

            final long[] tempB = bitsSrc;
            bitsSrc = bitsDst;
            bitsDst = tempB;
        }

        if (entSrc != ent) {
            System.arraycopy(entSrc, 0, ent, 0, size);
            System.arraycopy(bitsSrc, 0, bits, 0, size);
        }
    }

    private static void insertion(
        Entity[] ents,
        long[] bits,
        int high
    ) {
        for (int i = 1; i <= high; i++) {
            int j = i;
            Entity currentEntity = ents[j];
            long currentBits = bits[j];

            while (j > 0 && bits[j - 1] > currentBits) {
                ents[j] = ents[j - 1];
                bits[j] = bits[j - 1];
                j--;
            }
            ents[j] = currentEntity;
            bits[j] = currentBits;
        }
    }
}
