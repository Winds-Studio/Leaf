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

package com.ishland.c2me.opts.dfc.common.ast.opto;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.opto.passes.BranchElimination;
import com.ishland.c2me.opts.dfc.common.ast.opto.passes.CacheElimination;
import com.ishland.c2me.opts.dfc.common.ast.opto.passes.FoldConstants;
import com.ishland.c2me.opts.dfc.common.ast.opto.passes.TreeNormalization;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class OptoPasses {

    private static final AstTransformer[] PASSES = new AstTransformer[] {
            TreeNormalization.INSTANCE,
            FoldConstants.INSTANCE,
            BranchElimination.INSTANCE,
    };

    private static final AstTransformer[] PASSES_OCL = new AstTransformer[] {
            CacheElimination.INSTANCE,
            TreeNormalization.INSTANCE,
            FoldConstants.INSTANCE,
            BranchElimination.INSTANCE,
    };

    public static AstPair optimize(AstNode astNode) {
        return optimize0(astNode, PASSES);
    }

    public static AstPair optimizeOCL(AstNode astNode) {
        return optimize0(astNode, PASSES_OCL);
    }

    private static @NonNull AstPair optimize0(AstNode astNode, AstTransformer[] passes) {
        AstNode unoptimized = astNode;
        AstNode res = astNode;
        do {
            astNode = res;
            for (AstTransformer pass : passes) {
                res = res.transform(pass);
            }
        } while (res != astNode);
        return new AstPair(unoptimized, res);
    }

    public record AstPair(@Nullable AstNode unoptimized, @NonNull AstNode optimized) {

        public static AstPair ofOptimizedOnly(AstNode optimized) {
            return new AstPair(null, optimized);
        }

        public AstNode tryUnoptimized() {
            return this.unoptimized != null ? this.unoptimized : this.optimized;
        }

    }

}
