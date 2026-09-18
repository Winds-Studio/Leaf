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

package com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc;

import com.ishland.c2me.opts.dfc.common.ast.misc.IntervalSelectNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDefF64;
import com.ishland.flowsched.util.Assertions;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.Arrays;

public class IntervalSelectNodeBytecodeEmitter implements BytecodeEmitter<IntervalSelectNode> {
    public static final IntervalSelectNodeBytecodeEmitter INSTANCE = new IntervalSelectNodeBytecodeEmitter();

    private IntervalSelectNodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(IntervalSelectNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDefF64 inputMethod = context.newSingleMethodF64(node.input);

        Label endLabel = new Label();

        context.callDelegateSingle(m, inputMethod);
        ValuesMethodDefF64[] delegates = Arrays.stream(node.functions).map(context::newSingleMethodF64).toArray(ValuesMethodDefF64[]::new);
        genBinarySearch(
                node.thresholds, delegates,
                context, m, endLabel, 0, node.thresholds.length
        );

        for (ValuesMethodDefF64 delegate : delegates) {
            Assertions.assertTrue(delegate == null);
        }

        m.visitLabel(endLabel);
        m.areturn(Type.DOUBLE_TYPE);
    }

    private static void genBinarySearch(double[] thresholds, ValuesMethodDefF64[] delegates, BytecodeGen.Context context, InstructionAdapter m, Label endLabel, int fromIndex, int toIndex) {
        Assertions.assertTrue(fromIndex < toIndex);

        int mid = (fromIndex + toIndex - 1) >>> 1;
        double midVal = thresholds[mid];

        Label geLabel = new Label();
        m.dup2();
        m.dconst(midVal);
        m.cmpg(Type.DOUBLE_TYPE);
        m.ifge(geLabel);

        if (fromIndex == mid) {
            m.pop2();
            context.callDelegateSingle(m, delegates[fromIndex]);
            m.goTo(endLabel);
            delegates[fromIndex] = null;
        } else {
            genBinarySearch(thresholds, delegates, context, m, endLabel, fromIndex, mid);
        }

        m.visitLabel(geLabel);

        if (mid + 1 == toIndex) {
            m.pop2();
            context.callDelegateSingle(m, delegates[toIndex]);
            m.goTo(endLabel);
            delegates[toIndex] = null;
        } else {
            genBinarySearch(thresholds, delegates, context, m, endLabel, mid + 1, toIndex);
        }
    }

    @Override
    public void doBytecodeGenMulti(IntervalSelectNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateAllToSingle(m, localVarConsumer, node);
        m.areturn(Type.VOID_TYPE);
    }
}
