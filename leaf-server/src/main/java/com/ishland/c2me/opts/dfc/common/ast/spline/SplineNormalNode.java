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

import java.util.Arrays;
import java.util.Objects;

public class SplineNormalNode implements AstNode {

    public final AstNode locationFunction;
    public final float[] locations;
    public final AstNode[] values;
    public final float[] derivatives;

    public SplineNormalNode(AstNode locationFunction, float[] locations, AstNode[] values, float[] derivatives) {
        this.locationFunction = Objects.requireNonNull(locationFunction);
        this.locations = Objects.requireNonNull(locations);
        this.values = Objects.requireNonNull(values);
        this.derivatives = Objects.requireNonNull(derivatives);
    }

    @Override
    public AstNode[] getChildren() {
        AstNode[] nodes = new AstNode[this.values.length + 1];
        nodes[0] = this.locationFunction;
        System.arraycopy(this.values, 0, nodes, 1, this.values.length);
        return nodes;
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        boolean changed = false;

        AstNode transformedLocationFunction = this.locationFunction.transform(transformer);
        if (transformedLocationFunction != this.locationFunction) changed |= true;

        AstNode[] transformedValues = this.values.clone();
        for (int i = 0, transformedValuesLength = transformedValues.length; i < transformedValuesLength; i++) {
            AstNode transformedFunction = transformedValues[i];
            transformedValues[i] = transformedFunction.transform(transformer);
            if (transformedValues[i] != transformedFunction) changed |= true;
        }

        if (!changed) {
            return transformer.transform(this);
        } else {
            return transformer.transform(new SplineNormalNode(transformedLocationFunction, this.locations.clone(), transformedValues, this.derivatives.clone()));
        }
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (o == null || getClass() != o.getClass()) return false;
        SplineNormalNode that = (SplineNormalNode) o;
        if (!locationFunction.relaxedEquals(that.locationFunction)) return false;
        if (!Arrays.equals(locations, that.locations)) return false;

        int length = this.values.length;
        if (that.values.length != length)
            return false;
        for (int i = 0; i < length; i++) {
            AstNode e1 = this.values[i];
            AstNode e2 = that.values[i];
            if (!e1.relaxedEquals(e2))
                return false;
        }

        if (!Arrays.equals(derivatives, that.derivatives)) return false;

        return true;
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + locationFunction.relaxedHashCode();
        result = 31 * result + Arrays.hashCode(locations);
        for (AstNode value : values) {
            result = 31 * result + value.relaxedHashCode();
        }
        result = 31 * result + Arrays.hashCode(derivatives);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SplineNormalNode that = (SplineNormalNode) o;
        if (!locationFunction.equals(that.locationFunction)) return false;
        if (!Arrays.equals(locations, that.locations)) return false;

        int length = this.values.length;
        if (that.values.length != length)
            return false;
        for (int i = 0; i < length; i++) {
            AstNode e1 = this.values[i];
            AstNode e2 = that.values[i];
            if (!e1.equals(e2))
                return false;
        }

        if (!Arrays.equals(derivatives, that.derivatives)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + locationFunction.hashCode();
        result = 31 * result + Arrays.hashCode(locations);
        for (AstNode value : values) {
            result = 31 * result + value.hashCode();
        }
        result = 31 * result + Arrays.hashCode(derivatives);
        return result;
    }

    @Override
    public ReturnType getReturnType() {
        return ReturnType.F32;
    }
}
