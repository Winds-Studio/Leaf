/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.ishland.c2me.opts.dfc.common.gen.jvm.util;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.objectweb.asm.Type;

import java.util.Arrays;

public interface DfcObjectCache {

    public static final String GET_NOISE_POS_VANILLA_INTERFACE_DESC = Type.getMethodDescriptor(
            Type.getType(NoisePosVanillaInterface.class), Type.getType(int.class), Type.getType(int.class), Type.getType(int.class), Type.getType(EvalType.class), Type.getType(DfcObjectCache.class)
    );

    double[] getDoubleArray(int size, boolean zero);

    float[] getFloatArray(int size, boolean zero);

    int[] getIntArray(int size, boolean zero);

    NoisePosVanillaInterface getNoisePosVanillaInterface(int x, int y, int z, EvalType type, DfcObjectCache cache);

    void recycle(double[] array);

    void recycle(float[] array);

    void recycle(int[] array);

    void recycle(NoisePosVanillaInterface noisePosVanillaInterface);

    class Impl implements DfcObjectCache {

        private final Int2ReferenceArrayMap<ReferenceArrayList<double[]>> doubleArrayCache = new Int2ReferenceArrayMap<>();
        private final Int2ReferenceArrayMap<ReferenceArrayList<float[]>> floatArrayCache = new Int2ReferenceArrayMap<>();
        private final Int2ReferenceArrayMap<ReferenceArrayList<int[]>> intArrayCache = new Int2ReferenceArrayMap<>();
//        private final ReferenceArrayList<NoisePosVanillaInterface> noisePosVanillaInterfacesCache = new ReferenceArrayList<>();
        private final NoisePosVanillaInterface noisePosVanillaInterfaceSingleton = new NoisePosVanillaInterface();

        public double[] getDoubleArray(int size, boolean zero) {
            ReferenceArrayList<double[]> list = this.doubleArrayCache.computeIfAbsent(size, k -> new ReferenceArrayList<>());
            if (list.isEmpty()) {
                return new double[size];
            } else {
                double[] popped = list.pop();
                if (zero) {
                    Arrays.fill(popped, 0.0);
                }
                return popped;
            }
        }

        @Override
        public float[] getFloatArray(int size, boolean zero) {
            ReferenceArrayList<float[]> list = this.floatArrayCache.computeIfAbsent(size, k -> new ReferenceArrayList<>());
            if (list.isEmpty()) {
                return new float[size];
            } else {
                float[] popped = list.pop();
                if (zero) {
                    Arrays.fill(popped, 0.0F);
                }
                return popped;
            }
        }

        public int[] getIntArray(int size, boolean zero) {
            ReferenceArrayList<int[]> list = this.intArrayCache.computeIfAbsent(size, k -> new ReferenceArrayList<>());
            if (list.isEmpty()) {
                return new int[size];
            } else {
                int[] popped = list.pop();
                if (zero) {
                    Arrays.fill(popped, 0);
                }
                return popped;
            }
        }

        @Override
        public NoisePosVanillaInterface getNoisePosVanillaInterface(int x, int y, int z, EvalType type, DfcObjectCache cache) {
//            ReferenceArrayList<NoisePosVanillaInterface> list = this.noisePosVanillaInterfacesCache;
//            if (list.isEmpty()) {
//                return new NoisePosVanillaInterface().at(x, y, z, type, cache);
//            } else {
//                return list.pop().at(x, y, z, type, cache);
//            }
            this.noisePosVanillaInterfaceSingleton.ensureUninitialized();
            return this.noisePosVanillaInterfaceSingleton.at(x, y, z, type, cache);
        }

        public void recycle(double[] array) {
            this.doubleArrayCache.computeIfAbsent(array.length, k -> new ReferenceArrayList<>()).add(array);
        }

        @Override
        public void recycle(float[] array) {
            this.floatArrayCache.computeIfAbsent(array.length, k -> new ReferenceArrayList<>()).add(array);
        }

        public void recycle(int[] array) {
            this.intArrayCache.computeIfAbsent(array.length, k -> new ReferenceArrayList<>()).add(array);
        }

        @Override
        public void recycle(NoisePosVanillaInterface noisePosVanillaInterface) {
            noisePosVanillaInterface.deInit();
//            this.noisePosVanillaInterfacesCache.add(noisePosVanillaInterface);
        }

    }

    class Noop implements DfcObjectCache {

        public static final Noop INSTANCE = new Noop();

        private Noop() {
        }

        @Override
        public double[] getDoubleArray(int size, boolean zero) {
            return new double[size];
        }

        @Override
        public float[] getFloatArray(int size, boolean zero) {
            return new float[size];
        }

        @Override
        public int[] getIntArray(int size, boolean zero) {
            return new int[size];
        }

        @Override
        public NoisePosVanillaInterface getNoisePosVanillaInterface(int x, int y, int z, EvalType type, DfcObjectCache cache) {
            return new NoisePosVanillaInterface().at(x, y, z, type, cache);
        }

        @Override
        public void recycle(double[] array) {
        }

        @Override
        public void recycle(float[] array) {
        }

        @Override
        public void recycle(int[] array) {
        }

        @Override
        public void recycle(NoisePosVanillaInterface noisePosVanillaInterface) {
            noisePosVanillaInterface.deInit();
        }

    }

}
