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
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;

public class NoisePosVanillaInterface implements DensityFunction.FunctionContext {

    private int x;
    private int y;
    private int z;
    private EvalType type;
    private DfcObjectCache cache = DfcObjectCache.Noop.INSTANCE;

    public NoisePosVanillaInterface() {
    }

    public void ensureUninitialized() {
        if (type != null) throw new IllegalStateException("double use");
    }

    private void ensureInitialized() {
        if (type == null) throw new IllegalStateException("uninitialized use");
    }

    public NoisePosVanillaInterface at(int x, int y, int z, EvalType type, DfcObjectCache cache) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = Objects.requireNonNull(type);
        this.cache = Objects.requireNonNull(cache);
        return this;
    }

    @Override
    public int blockX() {
        ensureInitialized();
        return x;
    }

    @Override
    public int blockY() {
        ensureInitialized();
        return y;
    }

    @Override
    public int blockZ() {
        ensureInitialized();
        return z;
    }

    public EvalType getType() {
        ensureInitialized();
        return type;
    }

    public void deInit() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.type = null;
        this.cache = DfcObjectCache.Noop.INSTANCE;
    }

}
