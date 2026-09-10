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

package com.ishland.c2me.opts.dfc.common.gen.jvm;

import com.google.common.base.Suppliers;
import com.ishland.c2me.base.mixin.access.IChunkNoiseSampler;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IBlendingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.ducks.ICoordinatesFilling;
import com.ishland.c2me.opts.dfc.common.ducks.IDfcObjectCacheCapable;
import com.ishland.c2me.opts.dfc.common.ducks.IPreloadedCoordinates;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.IMultiMethod;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.ISingleMethod;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.EachApplierVanillaInterface;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

public abstract class AbstractCompiledDensityFunction implements DensityFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompiledDensityFunction.class);

    protected final Supplier<DensityFunction> blendingFallback;

    public AbstractCompiledDensityFunction(DensityFunction blendingFallback) {
        this(unwrapFallback(blendingFallback));
    }

    public AbstractCompiledDensityFunction(Supplier<DensityFunction> blendingFallback) {
        this.blendingFallback = blendingFallback;
    }

    protected static Supplier<DensityFunction> unwrapFallback(DensityFunction densityFunction) {
        if (densityFunction instanceof AbstractCompiledDensityFunction scdf) {
            return scdf.blendingFallback;
        } else {
            return densityFunction != null ? Suppliers.ofInstance(densityFunction) : null;
        }
    }

    public abstract ISingleMethod getSingleMethod();

    public abstract IMultiMethod getMultiMethod();

    @Override
    public double compute(FunctionContext pos) {
        ISingleMethod singleMethod1 = this.getSingleMethod();
        if (singleMethod1 == null || (pos instanceof NoiseChunk sampler && !((IChunkNoiseSampler) sampler).getBlender().isEmpty())) {
            DensityFunction fallback = this.getFallback();
            if (fallback == null) {
                throw new IllegalStateException("blendingFallback is no more");
            }
            return fallback.compute(pos);
        }
        DfcObjectCache cache = pos instanceof IDfcObjectCacheCapable cacheCapable ? cacheCapable.c2me$getDfcObjectCache() : DfcObjectCache.Noop.INSTANCE;
        return singleMethod1.evalSingle(pos.blockX(), pos.blockY(), pos.blockZ(), EvalType.from(pos), cache);
    }

    @Override
    public void fillArray(double[] densities, ContextProvider applier) {
        IMultiMethod multiMethod1 = this.getMultiMethod();
        if (multiMethod1 == null || (applier instanceof NoiseChunk sampler && !((IChunkNoiseSampler) sampler).getBlender().isEmpty())) {
            DensityFunction fallback = this.getFallback();
            if (fallback == null) {
                throw new IllegalStateException("blendingFallback is no more");
            }
            fallback.fillArray(densities, applier);
            return;
        }
        if (applier instanceof EachApplierVanillaInterface vanillaInterface) {
            multiMethod1.evalMulti(densities, vanillaInterface.getX(), vanillaInterface.getY(), vanillaInterface.getZ(), EvalType.from(applier), vanillaInterface.c2me$getDfcObjectCache());
            return;
        }

        DfcObjectCache cache = applier instanceof IDfcObjectCacheCapable cacheCapable ? cacheCapable.c2me$getDfcObjectCache() : DfcObjectCache.Noop.INSTANCE;
        int[] x;
        int[] y;
        int[] z;
        boolean allocatedOnDemand;
        if (applier instanceof IPreloadedCoordinates preloadedCoordinates) {
            x = preloadedCoordinates.c2me$getXArray();
            y = preloadedCoordinates.c2me$getYArray();
            z = preloadedCoordinates.c2me$getZArray();
            allocatedOnDemand = false;
        } else {
            x = cache.getIntArray(densities.length, false);
            y = cache.getIntArray(densities.length, false);
            z = cache.getIntArray(densities.length, false);
            if (applier instanceof ICoordinatesFilling coordinatesFilling) {
                coordinatesFilling.c2me$fillCoordinates(x, y, z);
            } else {
                for (int i = 0; i < densities.length; i ++) {
                    FunctionContext pos = applier.forIndex(i);
                    x[i] = pos.blockX();
                    y[i] = pos.blockY();
                    z[i] = pos.blockZ();
                }
            }
            allocatedOnDemand = true;
        }
        try {
            multiMethod1.evalMulti(densities, x, y, z, EvalType.from(applier), cache);
        } finally {
            if (allocatedOnDemand) {
                cache.recycle(x);
                cache.recycle(y);
                cache.recycle(z);
            }
        }
    }

    @Override
    public abstract DensityFunction mapChildren(Visitor visitor);

    @Override
    public abstract DensityFunction mapAll(Visitor visitor);

    @Override
    public double minValue() {
        DensityFunction fallback = this.getFallback();
        return fallback != null ? fallback.minValue() : Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        DensityFunction fallback = this.getFallback();
        return fallback != null ? fallback.maxValue() : Double.POSITIVE_INFINITY;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException();
    }

    protected DensityFunction getFallback() {
        return this.blendingFallback != null ? this.blendingFallback.get() : null;
    }
}
