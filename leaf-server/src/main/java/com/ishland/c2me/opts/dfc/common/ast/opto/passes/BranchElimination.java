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

package com.ishland.c2me.opts.dfc.common.ast.opto.passes;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RangeChoiceNode;

public class BranchElimination implements AstTransformer {

    public static final BranchElimination INSTANCE = new BranchElimination();

    private BranchElimination() {
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case RangeChoiceNode rangeChoiceNode -> {
                if (rangeChoiceNode.input instanceof ConstantNode c) {
                    if (c.getValue() >= rangeChoiceNode.minInclusive && c.getValue() < rangeChoiceNode.maxExclusive) {
                        yield rangeChoiceNode.whenInRange;
                    } else {
                        yield rangeChoiceNode.whenOutOfRange;
                    }
                }

                if (rangeChoiceNode.whenInRange.equals(rangeChoiceNode.whenOutOfRange)) {
                    yield rangeChoiceNode.whenInRange;
                }

                yield rangeChoiceNode;
            }
            default -> astNode;
        };
    }

}
