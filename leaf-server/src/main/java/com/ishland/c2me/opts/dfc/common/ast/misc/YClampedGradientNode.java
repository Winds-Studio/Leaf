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

public class YClampedGradientNode implements AstNode {

    public final double fromY;
    public final double toY;
    public final double fromValue;
    public final double toValue;

    public YClampedGradientNode(double fromY, double toY, double fromValue, double toValue) {
        this.fromY = fromY;
        this.toY = toY;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YClampedGradientNode that = (YClampedGradientNode) o;
        return Double.compare(fromY, that.fromY) == 0 && Double.compare(toY, that.toY) == 0 && Double.compare(fromValue, that.fromValue) == 0 && Double.compare(toValue, that.toValue) == 0;
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + Double.hashCode(fromY);
        result = 31 * result + Double.hashCode(toY);
        result = 31 * result + Double.hashCode(fromValue);
        result = 31 * result + Double.hashCode(toValue);

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        return this.equals(o);
    }

    @Override
    public int relaxedHashCode() {
        return this.hashCode();
    }
}
