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

package com.ishland.c2me.opts.dfc.common.ast.misc;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;

import java.util.Arrays;

public class IntervalSelectNode implements AstNode {

    public final AstNode input;
    public final double[] thresholds;
    public final AstNode[] functions;

    public IntervalSelectNode(AstNode input, double[] thresholds, AstNode[] functions) {
        this.input = input;
        this.thresholds = thresholds;
        this.functions = functions;
    }

    @Override
    public AstNode[] getChildren() {
        AstNode[] nodes = new AstNode[this.functions.length + 1];
        nodes[0] = this.input;
        System.arraycopy(this.functions, 0, nodes, 1, this.functions.length);
        return nodes;
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        boolean changed = false;

        AstNode transformedInput = this.input.transform(transformer);
        if (transformedInput != this.input) changed |= true;

        AstNode[] transformedFunctions = this.functions.clone();
        for (int i = 0, transformedFunctionsLength = transformedFunctions.length; i < transformedFunctionsLength; i++) {
            AstNode transformedFunction = transformedFunctions[i];
            transformedFunctions[i] = transformedFunction.transform(transformer);
            if (transformedFunctions[i] != transformedFunction) changed |= true;
        }

        if (!changed) {
            return transformer.transform(this);
        } else {
            return transformer.transform(new IntervalSelectNode(transformedInput, this.thresholds.clone(), transformedFunctions));
        }
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (o == null || getClass() != o.getClass()) return false;
        IntervalSelectNode that = (IntervalSelectNode) o;
        if (!this.input.relaxedEquals(that.input) || !Arrays.equals(this.thresholds, that.thresholds)) return false;
        if (this.functions == that.functions)
            return true;
        if (this.functions == null || that.functions == null)
            return false;
        int length = this.functions.length;
        if (that.functions.length != length)
            return false;

        for (int i = 0; i < length; i++) {
            AstNode e1 = this.functions[i];
            AstNode e2 = that.functions[i];
            if (!e1.relaxedEquals(e2))
                return false;
        }
        return true;
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + input.relaxedHashCode();
        result = 31 * result + Arrays.hashCode(thresholds);
        for (AstNode function : functions) {
            result = 31 * result + function.relaxedHashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        IntervalSelectNode that = (IntervalSelectNode) o;
        if (!this.input.equals(that.input) || !Arrays.equals(this.thresholds, that.thresholds)) return false;
        if (this.functions == that.functions)
            return true;
        if (this.functions == null || that.functions == null)
            return false;
        int length = this.functions.length;
        if (that.functions.length != length)
            return false;

        for (int i = 0; i < length; i++) {
            AstNode e1 = this.functions[i];
            AstNode e2 = that.functions[i];
            if (!e1.equals(e2))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + input.hashCode();
        result = 31 * result + Arrays.hashCode(thresholds);
        for (AstNode function : functions) {
            result = 31 * result + function.hashCode();
        }
        return result;
    }
}
