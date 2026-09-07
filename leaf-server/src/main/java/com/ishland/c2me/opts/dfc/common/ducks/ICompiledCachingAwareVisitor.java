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

package com.ishland.c2me.opts.dfc.common.ducks;

import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.ArgumentVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;

public interface ICompiledCachingAwareVisitor {

    CompiledEntry c2me$visitIfAbsent(CompiledEntry entry, ArgumentVisitor visitor);

    public static ArgumentVisitor c2me$getArgumentVisitor(DensityFunction.Visitor visitor) {
        return next -> {
            if (next instanceof DensityFunction df) {
                return df.mapAll(visitor);
            }
            if (next instanceof DensityFunction.NoiseHolder noise) {
                return visitor.visitNoise(noise);
            }
            return next;
        };
    }

}
