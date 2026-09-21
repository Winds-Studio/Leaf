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

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineNormalNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.jvm.SplineSupport;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDefF64;
import com.ishland.flowsched.util.Assertions;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class SplineNormalNodeBytecodeEmitter implements BytecodeEmitter<SplineNormalNode> {
    public static final SplineNormalNodeBytecodeEmitter INSTANCE = new SplineNormalNodeBytecodeEmitter();

    private SplineNormalNodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(SplineNormalNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String locations = context.newField(float[].class, node.locations);
        String derivatives = context.newField(float[].class, node.derivatives);

        int point = localVarConsumer.createLocalVariable("point", Type.FLOAT_TYPE.getDescriptor());
        int rangeForLocation = localVarConsumer.createLocalVariable("rangeForLocation", Type.INT_TYPE.getDescriptor());

        int lastConst = node.locations.length - 1;

        ValuesMethodDefF64 locationFunction = context.newSingleMethodF64(node.locationFunction);
        context.callDelegateSingle(m, locationFunction);
        m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
        m.store(point, Type.FLOAT_TYPE);

        int valuesMethodsLength = node.values.length;
        if (valuesMethodsLength == 1) {
            m.load(point, Type.FLOAT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            context.callDelegateSingle(m, context.newSingleMethodF32(node.values[0]));
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    derivatives,
                    Type.getDescriptor(float[].class)
            );
            m.iconst(0);
            m.invokestatic(
                    Type.getInternalName(SplineSupport.class),
                    "sampleOutsideRange",
                    Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                    false
            );
            m.areturn(Type.FLOAT_TYPE);
        } else {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            m.load(point, Type.FLOAT_TYPE);
            m.invokestatic(
                    Type.getInternalName(SplineSupport.class),
                    "findRangeForLocation",
                    Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE),
                    false
            );
            m.store(rangeForLocation, Type.INT_TYPE);

            Label label1 = new Label();
            Label label2 = new Label();

            m.load(rangeForLocation, Type.INT_TYPE);
            m.ifge(label1);
            // rangeForLocation < 0
            m.load(point, Type.FLOAT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            context.callDelegateSingle(m, context.newSingleMethodF32(node.values[0]));
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    derivatives,
                    Type.getDescriptor(float[].class)
            );
            m.iconst(0);
            m.invokestatic(
                    Type.getInternalName(SplineSupport.class),
                    "sampleOutsideRange",
                    Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                    false
            );
            m.areturn(Type.FLOAT_TYPE);

            m.visitLabel(label1);
            m.load(rangeForLocation, Type.INT_TYPE);
            m.iconst(lastConst);
            m.ificmpne(label2);
            // rangeForLocation == last
            m.load(point, Type.FLOAT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            context.callDelegateSingle(m, context.newSingleMethodF32(node.values[lastConst]));
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    derivatives,
                    Type.getDescriptor(float[].class)
            );
            m.iconst(lastConst);
            m.invokestatic(
                    Type.getInternalName(SplineSupport.class),
                    "sampleOutsideRange",
                    Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                    false
            );
            m.areturn(Type.FLOAT_TYPE);

            m.visitLabel(label2);

            int loc0 = localVarConsumer.createLocalVariable("loc0", Type.FLOAT_TYPE.getDescriptor());
            int loc1 = localVarConsumer.createLocalVariable("loc1", Type.FLOAT_TYPE.getDescriptor());
            int locDist = localVarConsumer.createLocalVariable("locDist", Type.FLOAT_TYPE.getDescriptor());
            int k = localVarConsumer.createLocalVariable("k", Type.FLOAT_TYPE.getDescriptor());
            int n = localVarConsumer.createLocalVariable("n", Type.FLOAT_TYPE.getDescriptor());
            int o = localVarConsumer.createLocalVariable("o", Type.FLOAT_TYPE.getDescriptor());
            int onDist = localVarConsumer.createLocalVariable("onDist", Type.FLOAT_TYPE.getDescriptor());
            int p = localVarConsumer.createLocalVariable("p", Type.FLOAT_TYPE.getDescriptor());
            int q = localVarConsumer.createLocalVariable("q", Type.FLOAT_TYPE.getDescriptor());

            int cache1Local = localVarConsumer.createLocalVariable("cache1Local", Type.FLOAT_TYPE.getDescriptor());

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            m.load(rangeForLocation, Type.INT_TYPE);
            m.aload(Type.FLOAT_TYPE);
            m.store(loc0, Type.FLOAT_TYPE);

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    locations,
                    Type.getDescriptor(float[].class)
            );
            m.load(rangeForLocation, Type.INT_TYPE);
            m.iconst(1);
            m.add(Type.INT_TYPE);
            m.aload(Type.FLOAT_TYPE);
            m.store(loc1, Type.FLOAT_TYPE);

            m.load(loc1, Type.FLOAT_TYPE);
            m.load(loc0, Type.FLOAT_TYPE);
            m.sub(Type.FLOAT_TYPE);
            m.store(locDist, Type.FLOAT_TYPE);

            m.load(point, Type.FLOAT_TYPE);
            m.load(loc0, Type.FLOAT_TYPE);
            m.sub(Type.FLOAT_TYPE);
            m.load(locDist, Type.FLOAT_TYPE);
            m.div(Type.FLOAT_TYPE);
            m.store(k, Type.FLOAT_TYPE);

            Label[] jumpLabels = new Label[valuesMethodsLength - 1];
            boolean[] jumpGenerated = new boolean[valuesMethodsLength - 1];
            for (int i = 0; i < valuesMethodsLength - 1; i++) {
                jumpLabels[i] = new Label();
            }
            Label defaultLabel = new Label();
            Label label3 = new Label();

            m.load(rangeForLocation, Type.INT_TYPE);
            m.tableswitch(
                    0,
                    valuesMethodsLength - 2,
                    defaultLabel,
                    jumpLabels
            );

            for (int i = 0; i < valuesMethodsLength - 1; i++) {
                if (jumpGenerated[i]) continue;
                m.visitLabel(jumpLabels[i]);
                jumpGenerated[i] = true;
                for (int j = i + 1; j < valuesMethodsLength - 1; j++) { // deduplication
                    if (node.values[i].equals(node.values[j]) && node.values[i + 1].equals(node.values[j + 1])) {
                        m.visitLabel(jumpLabels[j]);
                        jumpGenerated[j] = true;
                    }
                }

                boolean optimizePure = node.values[i].equals(node.values[i + 1]);
                context.callDelegateSingle(m, context.newSingleMethodF32(node.values[i]));
                if (optimizePure) { // splines are pure
                    m.dup();
                    m.store(n, Type.FLOAT_TYPE);
                    m.store(o, Type.FLOAT_TYPE);
                } else {
                    m.store(n, Type.FLOAT_TYPE);
                    context.callDelegateSingle(m, context.newSingleMethodF32(node.values[i + 1]));
                    m.store(o, Type.FLOAT_TYPE);
                }
                m.goTo(label3);
            }

            m.visitLabel(defaultLabel);
            m.iconst(0);
            m.aconst("boom");
            m.invokestatic(
                    Type.getInternalName(Assertions.class),
                    "assertTrue",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE, Type.getType(String.class)),
                    false
            );
            m.fconst(Float.NaN); // unreachable code
            m.areturn(Type.FLOAT_TYPE);

            m.visitLabel(label3);

            m.load(o, Type.FLOAT_TYPE);
            m.load(n, Type.FLOAT_TYPE);
            m.sub(Type.FLOAT_TYPE);
            m.store(onDist, Type.FLOAT_TYPE);

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    derivatives,
                    Type.getDescriptor(float[].class)
            );
            m.load(rangeForLocation, Type.INT_TYPE);
            m.aload(Type.FLOAT_TYPE);
            m.load(locDist, Type.FLOAT_TYPE);
            m.mul(Type.FLOAT_TYPE);
            m.load(onDist, Type.FLOAT_TYPE);
            m.sub(Type.FLOAT_TYPE);
            m.store(p, Type.FLOAT_TYPE);

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(
                    context.className,
                    derivatives,
                    Type.getDescriptor(float[].class)
            );
            m.load(rangeForLocation, Type.INT_TYPE);
            m.iconst(1);
            m.add(Type.INT_TYPE);
            m.aload(Type.FLOAT_TYPE);
            m.neg(Type.FLOAT_TYPE);
            m.load(locDist, Type.FLOAT_TYPE);
            m.mul(Type.FLOAT_TYPE);
            m.load(onDist, Type.FLOAT_TYPE);
            m.add(Type.FLOAT_TYPE);
            m.store(q, Type.FLOAT_TYPE);

            m.load(k, Type.FLOAT_TYPE);
            m.load(n, Type.FLOAT_TYPE);
            m.load(o, Type.FLOAT_TYPE);
            m.invokestatic(
                    Type.getInternalName(InvocationShim.class),
                    "invokeMathHelperLerp",
                    "(FFF)F",
                    false
            );
            m.load(k, Type.FLOAT_TYPE);
            m.fconst(1.0F);
            m.load(k, Type.FLOAT_TYPE);
            m.sub(Type.FLOAT_TYPE);
            m.mul(Type.FLOAT_TYPE);
            m.load(k, Type.FLOAT_TYPE);
            m.load(p, Type.FLOAT_TYPE);
            m.load(q, Type.FLOAT_TYPE);
            m.invokestatic(
                    Type.getInternalName(InvocationShim.class),
                    "invokeMathHelperLerp",
                    "(FFF)F",
                    false
            );
            m.mul(Type.FLOAT_TYPE);
            m.add(Type.FLOAT_TYPE);
            m.areturn(Type.FLOAT_TYPE);
        }
    }

    @Override
    public void doBytecodeGenMulti(SplineNormalNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateAllToSingle(m, localVarConsumer, node);
        m.areturn(Type.VOID_TYPE);
    }
}
