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

package com.ishland.c2me.opts.dfc.common.ast.spline;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.McToAst;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;

public class SplineAstNode implements AstNode {

    public final CubicSpline<DensityFunctions.Spline.Coordinate> spline;
    public final Reference2ReferenceOpenHashMap<DensityFunctions.Spline.Coordinate, AstNode> children = new Reference2ReferenceOpenHashMap<>();

    public SplineAstNode(CubicSpline<DensityFunctions.Spline.Coordinate> spline) {
        this.spline = spline;
        this.populateChildrenMap(this.spline);
    }

    private SplineAstNode(CubicSpline<DensityFunctions.Spline.Coordinate> spline, Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> children) {
        this.spline = spline;
        this.children.putAll(children);
    }

    @Override
    public AstNode[] getChildren() {
        return this.children.values().toArray(AstNode[]::new);
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        boolean isModified = false;
        Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> modified = new Reference2ReferenceOpenHashMap<>(this.children);
        for (Map.Entry<DensityFunctions.Spline.Coordinate, AstNode> entry : modified.entrySet()) {
            AstNode node = entry.getValue();
            AstNode transformed = node.transform(transformer);
            if (node != transformed) {
                isModified = true;
                entry.setValue(transformed);
            }
        }
        if (isModified) {
            return transformer.transform(new SplineAstNode(this.spline, modified));
        } else {
            return transformer.transform(this);
        }
    }

    public static AstNode needOptimizeSameLocationFunction(SplineAstNode node, CubicSpline<DensityFunctions.Spline.Coordinate>... splines) {
        if (splines.length == 0) return null;

        AstNode a1Ast;
        if (splines[0] instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> spline0) {
            a1Ast = node.children.get(spline0.coordinate());
            if (a1Ast instanceof ConstantNode) {
                return null;
            }
        } else {
            return null;
        }

        for (int i = 1, splinesLength = splines.length; i < splinesLength; i++) {
            CubicSpline<DensityFunctions.Spline.Coordinate> b = splines[i];
            if (b instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> b1) {
                AstNode b1Ast = node.children.get(b1.coordinate());
                if (!a1Ast.equals(b1Ast)) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return a1Ast;
    }

    private void populateChildrenMap(CubicSpline<DensityFunctions.Spline.Coordinate> a) {
        if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> a1) {
            for (CubicSpline<DensityFunctions.Spline.Coordinate> spline : a1.values()) {
                populateChildrenMap(spline);
            }
            DensityFunctions.Spline.Coordinate locationFunction = a1.coordinate();
            this.children.put(locationFunction, McToAst.toAst(locationFunction.function()));
        }
    }

    private static boolean deepEquals(CubicSpline<DensityFunctions.Spline.Coordinate> a,
                                      Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenA,
                                      CubicSpline<DensityFunctions.Spline.Coordinate> b,
                                      Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenB) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> a1 &&
                b instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> b1) {
            return a1.value() == b1.value();
        } else if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> a1 &&
                b instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> b1) {
            boolean equals1 = Arrays.equals(a1.derivatives(), b1.derivatives()) &&
                    Arrays.equals(a1.locations(), b1.locations()) &&
                    a1.values().size() == b1.values().size() &&
                    childrenA.get(a1.coordinate()).equals(childrenB.get(b1.coordinate()));
            if (!equals1) return false;
            int size = a1.values().size();
            for (int i = 0; i < size; i++) {
                if (!deepEquals(a1.values().get(i), childrenA, b1.values().get(i), childrenB)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean deepRelaxedEquals(CubicSpline<DensityFunctions.Spline.Coordinate> a,
                                             Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenA,
                                             CubicSpline<DensityFunctions.Spline.Coordinate> b,
                                             Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenB) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> a1 &&
                b instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> b1) {
            return a1.value() == b1.value();
        } else if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> a1 &&
                b instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> b1) {
            boolean equals1 = Arrays.equals(a1.derivatives(), b1.derivatives()) &&
                    Arrays.equals(a1.locations(), b1.locations()) &&
                    a1.values().size() == b1.values().size() &&
                    childrenA.get(a1.coordinate()).relaxedEquals(childrenB.get(b1.coordinate()));
            if (!equals1) return false;
            int size = a1.values().size();
            for (int i = 0; i < size; i++) {
                if (!deepRelaxedEquals(a1.values().get(i), childrenA, b1.values().get(i), childrenB)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static int deepHashcode(CubicSpline<DensityFunctions.Spline.Coordinate> a, Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenA) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> a1) {
            return Float.hashCode(a1.value());
        } else if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> a1) {
            int result = 1;

            result = 31 * result + Arrays.hashCode(a1.derivatives());
            result = 31 * result + Arrays.hashCode(a1.locations());
            for (CubicSpline<DensityFunctions.Spline.Coordinate> spline : a1.values()) {
                result = 31 * result + deepHashcode(spline, childrenA);
            }
            result = 31 * result + childrenA.get(a1.coordinate()).hashCode();

            return result;
        } else {
            return a.hashCode();
        }
    }

    private static int deepRelaxedHashcode(CubicSpline<DensityFunctions.Spline.Coordinate> a, Reference2ReferenceMap<DensityFunctions.Spline.Coordinate, AstNode> childrenA) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Coordinate> a1) {
            return Float.hashCode(a1.value());
        } else if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> a1) {
            int result = 1;

            result = 31 * result + Arrays.hashCode(a1.derivatives());
            result = 31 * result + Arrays.hashCode(a1.locations());
            for (CubicSpline<DensityFunctions.Spline.Coordinate> spline : a1.values()) {
                result = 31 * result + deepRelaxedHashcode(spline, childrenA);
            }
            result = 31 * result + childrenA.get(a1.coordinate()).relaxedHashCode();

            return result;
        } else {
            return a.hashCode();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplineAstNode that = (SplineAstNode) o;
        return deepEquals(this.spline, this.children, that.spline, that.children);
    }

    @Override
    public int hashCode() {
        return deepHashcode(this.spline, this.children);
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplineAstNode that = (SplineAstNode) o;
        return deepRelaxedEquals(this.spline, this.children, that.spline, that.children);
    }

    @Override
    public int relaxedHashCode() {
        return deepRelaxedHashcode(this.spline, this.children);
    }
}
