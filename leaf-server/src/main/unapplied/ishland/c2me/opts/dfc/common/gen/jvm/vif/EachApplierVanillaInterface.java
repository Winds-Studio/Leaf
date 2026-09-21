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

package com.ishland.c2me.opts.dfc.common.gen.jvm.vif;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IDfcObjectCacheCapable;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;

public class EachApplierVanillaInterface implements DensityFunction.ContextProvider, IDfcObjectCacheCapable {

    private final int[] x;
    private final int[] y;
    private final int[] z;
    private final EvalType type;
    private final DfcObjectCache cache;

    private final NoisePosVanillaInterface noisePosVanillaInterface = new NoisePosVanillaInterface();

    public EachApplierVanillaInterface(int[] x, int[] y, int[] z, EvalType type) {
        this(x, y, z, type, DfcObjectCache.Noop.INSTANCE);
    }

    public EachApplierVanillaInterface(int[] x, int[] y, int[] z, EvalType type, DfcObjectCache cache) {
        this.x = Objects.requireNonNull(x);
        this.y = Objects.requireNonNull(y);
        this.z = Objects.requireNonNull(z);
        this.type = Objects.requireNonNull(type);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public DensityFunction.FunctionContext forIndex(int index) {
        return this.noisePosVanillaInterface.at(x[index], y[index], z[index], type, cache);
    }

    @Override
    public void fillAllDirectly(double[] densities, DensityFunction densityFunction) {
        for (int i = 0; i < x.length; i++) {
            densities[i] = densityFunction.compute(this.forIndex(i));
        }
        this.noisePosVanillaInterface.deInit();
    }

    public int[] getX() {
        return x;
    }

    public int[] getY() {
        return y;
    }

    public int[] getZ() {
        return z;
    }

    public EvalType getType() {
        return type;
    }

    @Override
    public DfcObjectCache c2me$getDfcObjectCache() {
        return this.cache;
    }
}
