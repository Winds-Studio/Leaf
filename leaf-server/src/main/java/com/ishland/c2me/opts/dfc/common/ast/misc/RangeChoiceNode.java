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

public class RangeChoiceNode implements AstNode {

    public final AstNode input;
    public final double minInclusive;
    public final double maxExclusive;
    public final AstNode whenInRange;
    public final AstNode whenOutOfRange;

    public RangeChoiceNode(AstNode input, double minInclusive, double maxExclusive, AstNode whenInRange, AstNode whenOutOfRange) {
        this.input = Objects.requireNonNull(input);
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = Objects.requireNonNull(whenInRange);
        this.whenOutOfRange = Objects.requireNonNull(whenOutOfRange);
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{this.input, this.whenInRange, this.whenOutOfRange};
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode input = this.input.transform(transformer);
        AstNode whenInRange = this.whenInRange.transform(transformer);
        AstNode whenOutOfRange = this.whenOutOfRange.transform(transformer);
        if (this.input == input && this.whenInRange == whenInRange && this.whenOutOfRange == whenOutOfRange) {
            return transformer.transform(this);
        } else {
            return transformer.transform(new RangeChoiceNode(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangeChoiceNode that = (RangeChoiceNode) o;
        return Double.compare(minInclusive, that.minInclusive) == 0 && Double.compare(maxExclusive, that.maxExclusive) == 0 && Objects.equals(input, that.input) && Objects.equals(whenInRange, that.whenInRange) && Objects.equals(whenOutOfRange, that.whenOutOfRange);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.hashCode();
        result = 31 * result + Double.hashCode(this.minInclusive);
        result = 31 * result + Double.hashCode(this.maxExclusive);
        result = 31 * result + this.whenInRange.hashCode();
        result = 31 * result + this.whenOutOfRange.hashCode();

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangeChoiceNode that = (RangeChoiceNode) o;
        return Double.compare(minInclusive, that.minInclusive) == 0 && Double.compare(maxExclusive, that.maxExclusive) == 0 && input.relaxedEquals(that.input) && whenInRange.relaxedEquals(that.whenInRange) && whenOutOfRange.relaxedEquals(that.whenOutOfRange);
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.relaxedHashCode();
        result = 31 * result + Double.hashCode(this.minInclusive);
        result = 31 * result + Double.hashCode(this.maxExclusive);
        result = 31 * result + this.whenInRange.relaxedHashCode();
        result = 31 * result + this.whenOutOfRange.relaxedHashCode();

        return result;
    }
}
