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

package com.ishland.c2me.opts.dfc.common.gen;

import com.ishland.c2me.opts.dfc.common.ducks.IBlendingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.ducks.ICompiledCachingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.ArgumentVisitor;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;

public class DelegatingBlendingCachingAwareVisitor implements IBlendingAwareVisitor, ICompiledCachingAwareVisitor, DensityFunction.Visitor {

    private final DensityFunction.Visitor delegate;
    private final Map<CompiledEntry, CompiledEntry> backingCache;
    private final boolean blendingEnabled;

    public DelegatingBlendingCachingAwareVisitor(DensityFunction.Visitor delegate, Map<CompiledEntry, CompiledEntry> backingCache, boolean blendingEnabled) {
        this.delegate = Objects.requireNonNull(delegate);
        this.backingCache = Objects.requireNonNull(backingCache);
        this.blendingEnabled = blendingEnabled;
    }

    @Override
    public DensityFunction apply(DensityFunction densityFunction) {
        return this.delegate.apply(densityFunction);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseDensityFunction) {
        return this.delegate.visitNoise(noiseDensityFunction);
    }

    @Override
    public boolean c2me$isBlendingEnabled() {
        return this.blendingEnabled;
    }

    @Override
    public CompiledEntry c2me$visitIfAbsent(CompiledEntry entry, ArgumentVisitor visitor) {
        return this.backingCache.computeIfAbsent(entry, compiledEntry -> compiledEntry.newInstance(compiledEntry.getArgs(), visitor));
    }
}
