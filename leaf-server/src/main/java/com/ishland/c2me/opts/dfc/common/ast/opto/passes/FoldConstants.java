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
import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.*;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.ZeroUtils;
import net.minecraft.util.Mth;

public class FoldConstants implements AstTransformer {

    public static final FoldConstants INSTANCE = new FoldConstants();

    private FoldConstants() {
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case AddNode addNode -> {
                if (addNode.left instanceof ConstantNode c1 && addNode.right instanceof ConstantNode c2) {
                    yield new ConstantNode(c1.getValue() + c2.getValue());
                }

                // TreeNormalization: const left
                if (addNode.left instanceof ConstantNode c && c.getValue() == 0.0 && !ZeroUtils.isPositiveZero(c.getValue())) {
                    yield addNode.right;
                }

                yield addNode;
            }
            case MulNode mulNode -> {
                if (mulNode.left instanceof ConstantNode c1 && mulNode.right instanceof ConstantNode c2) {
                    yield new ConstantNode(c1.getValue() * c2.getValue());
                }

                if (mulNode.left instanceof ConstantNode c && c.getValue() == 0.0) { // special case defined in vanilla
                    yield new ConstantNode(0.0);
                }

                // TreeNormalization: const left
                if (mulNode.left instanceof ConstantNode c && c.getValue() == 1.0) {
                    yield mulNode.right;
                }

                yield mulNode;
            }
            case MaxNode maxNode -> {
                if (maxNode.left instanceof ConstantNode c1 && maxNode.right instanceof ConstantNode c2) {
                    yield new ConstantNode(Math.max(c1.getValue(), c2.getValue()));
                }

                yield maxNode;
            }
            case MaxShortNode maxShortNode -> {
                if (maxShortNode.left instanceof ConstantNode c1 && c1.getValue() >= maxShortNode.rightMax) {
                    yield c1;
                }

                if (maxShortNode.left instanceof ConstantNode || maxShortNode.right instanceof ConstantNode) {
                    yield new MaxNode(maxShortNode.left, maxShortNode.right);
                }

                yield maxShortNode;
            }
            case MinNode minNode -> {
                if (minNode.left instanceof ConstantNode c1 && minNode.right instanceof ConstantNode c2) {
                    yield new ConstantNode(Math.min(c1.getValue(), c2.getValue()));
                }

                yield minNode;
            }
            case MinShortNode minShortNode -> {
                if (minShortNode.left instanceof ConstantNode c1 && c1.getValue() <= minShortNode.rightMin) {
                    yield c1;
                }

                if (minShortNode.left instanceof ConstantNode || minShortNode.right instanceof ConstantNode) {
                    yield new MinNode(minShortNode.left, minShortNode.right);
                }

                yield minShortNode;
            }
            case AbsNode absNode -> {
                if (absNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.abs(c.getValue()));
                }

                yield absNode;
            }
            case CeilNode ceilNode -> {
                if (ceilNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.ceil(c.getValue()));
                }

                yield ceilNode;
            }
            case CosNode cosNode -> {
                if (cosNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.cos(c.getValue()));
                }

                yield cosNode;
            }
            case CubeNode cubeNode -> {
                if (cubeNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(c.getValue() * c.getValue() * c.getValue());
                }

                yield cubeNode;
            }
            case FloorNode floorNode -> {
                if (floorNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.floor(c.getValue()));
                }

                yield floorNode;
            }
            case NegMulNode negMulNode -> {
                if (negMulNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(c.getValue() > 0.0 ? c.getValue() : c.getValue() * negMulNode.negMul);
                }

                yield negMulNode;
            }
            case SinNode sinNode -> {
                if (sinNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.sin(c.getValue()));
                }

                yield sinNode;
            }
            case SqrtNode sqrtNode -> {
                if (sqrtNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(Math.sqrt(c.getValue()));
                }

                yield sqrtNode;
            }
            case SquareNode squareNode -> {
                if (squareNode.operand instanceof ConstantNode c) {
                    yield new ConstantNode(c.getValue() * c.getValue());
                }

                yield squareNode;
            }
            case SqueezeNode squeezeNode -> {
                if (squeezeNode.operand instanceof ConstantNode c) {
                    double v = Mth.clamp(c.getValue(), -1.0, 1.0);
                    yield new ConstantNode(v / 2.0 - v * v * v / 24.0);
                }

                yield squeezeNode;
            }
            case CacheLikeNode cacheLikeNode -> {
                if (cacheLikeNode.getCacheLike().c2me$isActualCache() && cacheLikeNode.getDelegate() instanceof ConstantNode c) {
                    yield c;
                }

                yield cacheLikeNode;
            }
            case null -> throw new NullPointerException();
            default -> astNode;
        };
    }

}
