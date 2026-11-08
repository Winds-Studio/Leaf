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

package com.ishland.c2me.opts.dfc.common.ast.binary;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;

import java.util.Objects;

public abstract class AbstractBinaryNode implements AstNode {

    public final AstNode left;
    public final AstNode right;

    public AbstractBinaryNode(AstNode left, AstNode right) {
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{left, right};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractBinaryNode that = (AbstractBinaryNode) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + left.hashCode();
        result = 31 * result + right.hashCode();

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractBinaryNode that = (AbstractBinaryNode) o;
        return left.relaxedEquals(that.left) && right.relaxedEquals(that.right);
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + left.relaxedHashCode();
        result = 31 * result + right.relaxedHashCode();

        return result;
    }

    protected abstract AstNode newInstance(AstNode left, AstNode right);

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode left = this.left.transform(transformer);
        AstNode right = this.right.transform(transformer);
        if (left == this.left && right == this.right) {
            return transformer.transform(this);
        } else {
            return transformer.transform(newInstance(left, right));
        }
    }

    public AstNode swapOperands() {
        return newInstance(this.right, this.left);
    }

    public boolean canSwapOperandsSafely() {
        return true;
    }

}
