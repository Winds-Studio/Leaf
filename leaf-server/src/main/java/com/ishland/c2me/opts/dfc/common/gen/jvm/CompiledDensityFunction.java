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
import com.ishland.c2me.opts.dfc.common.ducks.IBlendingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.ducks.ICompiledCachingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.IMultiMethod;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.ISingleMethod;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.DensityFunction;

public class CompiledDensityFunction extends AbstractCompiledDensityFunction {

    private final int compiledIndex;
    private CompiledEntry compiledEntry = null;
    private ISingleMethod singleMethod = null;
    private IMultiMethod multiMethod = null;

    CompiledDensityFunction(int compiledIndex, DensityFunction blendingFallback) {
        this(compiledIndex, unwrapFallback(blendingFallback));
    }

    private CompiledDensityFunction(int compiledIndex, Supplier<DensityFunction> blendingFallback) {
        super(blendingFallback);
        this.compiledIndex = compiledIndex;
    }

    @Override
    public ISingleMethod getSingleMethod() {
        return this.singleMethod;
    }

    @Override
    public IMultiMethod getMultiMethod() {
        return this.multiMethod;
    }

    public synchronized void initFrom(CompiledEntry entry) {
        if (this.compiledEntry != null) {
            throw new IllegalStateException("Already initialized");
        }

        Objects.requireNonNull(entry);
        SubCompiledDensityFunction method = entry.getRootsUnsafe()[this.compiledIndex];
        this.compiledEntry = entry;
        this.singleMethod = Objects.requireNonNull(method.singleMethod);
        this.multiMethod = Objects.requireNonNull(method.multiMethod);
    }

    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        if (visitor instanceof IBlendingAwareVisitor blendingAwareVisitor && blendingAwareVisitor.c2me$isBlendingEnabled()) {
            DensityFunction fallback1 = this.getFallback();
            if (fallback1 == null) {
                throw new IllegalStateException("blendingFallback is no more");
            }
            return visitor.apply(fallback1);
        }
        CompiledEntry compiledEntry = this.compiledEntry;
        ISingleMethod singleMethod = this.singleMethod;
        IMultiMethod multiMethod = this.multiMethod;
        if (compiledEntry == null || singleMethod == null || multiMethod == null) {
            throw new IllegalStateException("Attempted to apply a incomplete compiled df");
        }

        Supplier<DensityFunction> fallback = this.blendingFallback != null ? Suppliers.memoize(() -> {
            DensityFunction densityFunction = this.blendingFallback.get();
            return densityFunction != null ? visitor.apply(densityFunction) : null;
        }) : null;
        CompiledDensityFunction function = new CompiledDensityFunction(this.compiledIndex, fallback);
        CompiledEntry initializedEntry;
        if (visitor instanceof ICompiledCachingAwareVisitor compiledCachingAwareVisitor) {
            initializedEntry = compiledCachingAwareVisitor.c2me$visitIfAbsent(compiledEntry, ICompiledCachingAwareVisitor.c2me$getArgumentVisitor(visitor));
        } else {
            initializedEntry = this.compiledEntry.newInstance(compiledEntry.getArgs(), ICompiledCachingAwareVisitor.c2me$getArgumentVisitor(visitor));
        }
        function.initFrom(initializedEntry);
        return function;
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        if (visitor instanceof IBlendingAwareVisitor blendingAwareVisitor && blendingAwareVisitor.c2me$isBlendingEnabled()) {
            DensityFunction fallback1 = this.getFallback();
            if (fallback1 == null) {
                throw new IllegalStateException("blendingFallback is no more");
            }
            return fallback1.mapAll(visitor);
        }
        CompiledEntry compiledEntry = this.compiledEntry;
        ISingleMethod singleMethod = this.singleMethod;
        IMultiMethod multiMethod = this.multiMethod;
        if (compiledEntry == null || singleMethod == null || multiMethod == null) {
            throw new IllegalStateException("Attempted to apply a incomplete compiled df");
        }
        Supplier<DensityFunction> fallback = this.blendingFallback != null ? Suppliers.memoize(() -> {
            DensityFunction densityFunction = this.blendingFallback.get();
            return densityFunction != null ? visitor.apply(densityFunction) : null;
        }) : null;
        CompiledDensityFunction function = new CompiledDensityFunction(this.compiledIndex, fallback);
        CompiledEntry initializedEntry;
        if (visitor instanceof ICompiledCachingAwareVisitor compiledCachingAwareVisitor) {
            initializedEntry = compiledCachingAwareVisitor.c2me$visitIfAbsent(compiledEntry, ICompiledCachingAwareVisitor.c2me$getArgumentVisitor(visitor));
        } else {
            initializedEntry = this.compiledEntry.newInstance(compiledEntry.getArgs(), ICompiledCachingAwareVisitor.c2me$getArgumentVisitor(visitor));
        }
        function.initFrom(initializedEntry);
        return function;
    }

}
