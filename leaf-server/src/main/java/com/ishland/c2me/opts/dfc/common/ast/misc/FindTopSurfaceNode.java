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

import java.util.Objects;

public class FindTopSurfaceNode implements AstNode {

    public final AstNode density;
    public final AstNode upperBound;
    public final AstNode lowerBound;
    public final int cellHeight;

    public FindTopSurfaceNode(AstNode density, AstNode upperBound, AstNode lowerBound, int cellHeight) {
        this.density = Objects.requireNonNull(density);
        this.upperBound = Objects.requireNonNull(upperBound);
        this.lowerBound = Objects.requireNonNull(lowerBound);
        this.cellHeight = cellHeight;
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[] {this.density, this.upperBound, this.lowerBound};
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode density = this.density.transform(transformer);
        AstNode upperBound = this.upperBound.transform(transformer);
        AstNode lowerBound = this.lowerBound.transform(transformer);
        if (density == this.density && upperBound == this.upperBound && lowerBound == this.lowerBound) {
            return transformer.transform(this);
        } else {
            return transformer.transform(new FindTopSurfaceNode(density, upperBound, lowerBound, this.cellHeight));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FindTopSurfaceNode that = (FindTopSurfaceNode) o;
        return cellHeight == that.cellHeight && Objects.equals(density, that.density) && Objects.equals(upperBound, that.upperBound) && Objects.equals(lowerBound, that.lowerBound);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.density.hashCode();
        result = 31 * result + this.upperBound.hashCode();
        result = 31 * result + this.lowerBound.hashCode();
        result = 31 * result + Integer.hashCode(cellHeight);

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (o == null || getClass() != o.getClass()) return false;
        FindTopSurfaceNode that = (FindTopSurfaceNode) o;
        return cellHeight == that.cellHeight && this.density.relaxedEquals(that.density) && this.upperBound.relaxedEquals(that.upperBound) && this.lowerBound.relaxedEquals(that.lowerBound);
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.density.relaxedHashCode();
        result = 31 * result + this.upperBound.relaxedHashCode();
        result = 31 * result + this.lowerBound.relaxedHashCode();
        result = 31 * result + Integer.hashCode(cellHeight);

        return result;
    }
}
