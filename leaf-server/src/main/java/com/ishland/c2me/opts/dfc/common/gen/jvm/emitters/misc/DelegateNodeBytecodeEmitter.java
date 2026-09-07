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

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ast.misc.DelegateNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.EachApplierVanillaInterface;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class DelegateNodeBytecodeEmitter<E extends DelegateNode> implements BytecodeEmitter<E> {
    private static final DelegateNodeBytecodeEmitter<DelegateNode> INSTANCE = new DelegateNodeBytecodeEmitter<>();

    private DelegateNodeBytecodeEmitter() {
    }

    public static <E1 extends DelegateNode> DelegateNodeBytecodeEmitter<E1> instance() {
        return (DelegateNodeBytecodeEmitter<E1>) INSTANCE;
    }

    @Override
    public void doBytecodeGenSingle(DelegateNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String newField = context.newField(DensityFunction.class, node.getDelegate());

        int borrowedNoisePos = localVarConsumer.createLocalVariable("noisePos", Type.getDescriptor(NoisePosVanillaInterface.class));

        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getNoisePosVanillaInterface", DfcObjectCache.GET_NOISE_POS_VANILLA_INTERFACE_DESC);
        m.store(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);

        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.load(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokestatic(
                Type.getInternalName(InvocationShim.class),
                "invokeDensityFunctionSample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.class), Type.getType(DensityFunction.FunctionContext.class)),
                false
        );

        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.getType(void.class), Type.getType(NoisePosVanillaInterface.class)));

        m.areturn(Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(DelegateNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String newField = context.newField(DensityFunction.class, node.getDelegate());

        int borrowedNoisePos = localVarConsumer.createLocalVariable("noisePos", Type.getDescriptor(NoisePosVanillaInterface.class));

        Label moreThanTwoLabel = new Label();

        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(1);
        m.ificmpgt(moreThanTwoLabel);

        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);

        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getNoisePosVanillaInterface", DfcObjectCache.GET_NOISE_POS_VANILLA_INTERFACE_DESC);
        m.store(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);

        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.load(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokestatic(
                Type.getInternalName(InvocationShim.class),
                "invokeDensityFunctionSample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.class), Type.getType(DensityFunction.FunctionContext.class)),
                false
        );

        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(borrowedNoisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.getType(void.class), Type.getType(NoisePosVanillaInterface.class)));

        m.astore(Type.DOUBLE_TYPE);
        m.areturn(Type.VOID_TYPE);

        m.visitLabel(moreThanTwoLabel);

        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.anew(Type.getType(EachApplierVanillaInterface.class));
        m.dup();
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(Type.getInternalName(EachApplierVanillaInterface.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(int[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(EvalType.class), Type.getType(DfcObjectCache.class)), false);
        m.invokestatic(
                Type.getInternalName(InvocationShim.class),
                "invokeDensityFunctionFill",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(DensityFunction.class), Type.getType(double[].class), Type.getType(DensityFunction.ContextProvider.class)),
                false
        );
        m.areturn(Type.VOID_TYPE);
    }
}
