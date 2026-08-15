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

package com.ishland.c2me.opts.dfc.common.ast;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.lang.invoke.VarHandle;
import net.minecraft.world.level.levelgen.DensityFunction;

public class FrontendRegistry<E extends AstEmitter<? extends DensityFunction>> {

    private final Reference2ReferenceOpenHashMap<Class<? extends DensityFunction>, E> exactMatches = new Reference2ReferenceOpenHashMap<>();
    private volatile boolean frozen = false;

    public <N extends DensityFunction, E1 extends AstEmitter<N>> void registerExactMatch(Class<N> clazz, E1 emitter) {
        if (!this.frozen) {
            synchronized (this) {
                if (!this.frozen) {
                    VarHandle.fullFence();
                    if (this.exactMatches.containsKey(clazz)) {
                        throw new IllegalArgumentException("Already registered");
                    }
                    this.exactMatches.put(clazz, (E) emitter);
                    VarHandle.fullFence();
                    return;
                }
            }
        }

        throw new IllegalStateException("Already frozen");
    }

    public <N extends DensityFunction, E1 extends AstEmitter<N>> E1 getOptional(Class<N> clazz) {
        if (!this.frozen) {
            synchronized (this) {
                if (!this.frozen) {
                    this.frozen = true;
                }
            }
            VarHandle.fullFence();
        }

        E exactMatch = this.exactMatches.get(clazz);
        if (exactMatch != null) {
            return (E1) exactMatch;
        }

        return null;
    }

}
