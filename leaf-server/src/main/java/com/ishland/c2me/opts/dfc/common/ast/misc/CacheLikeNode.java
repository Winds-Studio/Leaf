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
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunctions;

public class CacheLikeNode implements AstNode {

    private final IFastCacheLike cacheLike;
    private final AstNode delegate;

    public CacheLikeNode(IFastCacheLike cacheLike, AstNode delegate) {
        this.cacheLike = cacheLike;
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{this.delegate};
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode delegate = this.delegate.transform(transformer);
        if (this.delegate == delegate) {
            return transformer.transform(this);
        } else {
            return transformer.transform(new CacheLikeNode(this.cacheLike, delegate));
        }
    }

    public IFastCacheLike getCacheLike() {
        return cacheLike;
    }

    public AstNode getDelegate() {
        return delegate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheLikeNode that = (CacheLikeNode) o;
        return equals(cacheLike, that.cacheLike) && Objects.equals(delegate, that.delegate);
    }

    private static boolean equals(IFastCacheLike a, IFastCacheLike b) {
        if ((Object) a instanceof DensityFunctions.Marker wrappingA && (Object) b instanceof DensityFunctions.Marker wrappingB) {
            return wrappingA.type() == wrappingB.type();
        } else {
            return a.equals(b);
        }
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + hashCode(cacheLike);
        result = 31 * result + delegate.hashCode();

        return result;
    }

    private static int hashCode(IFastCacheLike o) {
        if ((Object) o instanceof DensityFunctions.Marker wrapping) {
            return wrapping.type().hashCode();
        } else {
            return o.hashCode();
        }
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheLikeNode that = (CacheLikeNode) o;
        return relaxedEquals(cacheLike, that.cacheLike) && delegate.relaxedEquals(that.delegate);
    }

    private static boolean relaxedEquals(IFastCacheLike a, IFastCacheLike b) {
        if ((Object) a instanceof DensityFunctions.Marker wrappingA && (Object) b instanceof DensityFunctions.Marker wrappingB) {
            return wrappingA.type() == wrappingB.type();
        } else {
            return a.getClass() == b.getClass();
        }
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + relaxedHashCode(this.cacheLike);
        result = 31 * result + delegate.relaxedHashCode();

        return result;
    }

    private static int relaxedHashCode(IFastCacheLike o) {
        if ((Object) o instanceof DensityFunctions.Marker wrapping) {
            return wrapping.type().hashCode();
        } else {
            return o.getClass().hashCode();
        }
    }
}
