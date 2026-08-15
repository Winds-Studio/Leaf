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

import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDefF64;
import net.minecraft.world.level.levelgen.DensityFunction;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class GenericShiftedNoiseNodeBytecodeEmitter implements BytecodeEmitter<GenericShiftedNoiseNode> {
    public static final GenericShiftedNoiseNodeBytecodeEmitter INSTANCE = new GenericShiftedNoiseNodeBytecodeEmitter();

    private GenericShiftedNoiseNodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(GenericShiftedNoiseNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, node.noise);

        ValuesMethodDefF64 inputXMethod = context.newSingleMethodF64(node.inputX);
        ValuesMethodDefF64 inputYMethod = context.newSingleMethodF64(node.inputY);
        ValuesMethodDefF64 inputZMethod = context.newSingleMethodF64(node.inputZ);

        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));

        context.callDelegateSingle(m, inputXMethod);
        context.callDelegateSingle(m, inputYMethod);
        context.callDelegateSingle(m, inputZMethod);

        m.invokestatic(
                Type.getInternalName(InvocationShim.class),
                "invokeDensityFunctionNoiseSample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.NoiseHolder.class), Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                false
        );
        m.areturn(Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(GenericShiftedNoiseNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, node.noise);

        ValuesMethodDefF64 inputXMethod = context.newMultiMethodF64(node.inputX);
        ValuesMethodDefF64 inputYMethod = context.newMultiMethodF64(node.inputY);
        ValuesMethodDefF64 inputZMethod = context.newMultiMethodF64(node.inputZ);
        boolean eliminatedX = inputXMethod.isConst();
        boolean eliminatedY = inputYMethod.isConst();
        boolean eliminatedZ = inputZMethod.isConst();
        int arraysNeeded = (!eliminatedX ? 1 : 0) + (!eliminatedY ? 1 : 0) + (!eliminatedZ ? 1 : 0);

        int[] arrays = new int[arraysNeeded];
        if (arraysNeeded >= 1) {
            arrays[0] = 1;
        }
        if (arraysNeeded >= 2) {
            arrays[1] = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));
            m.load(6, InstructionAdapter.OBJECT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.arraylength();
            m.iconst(0);
            m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
            m.store(arrays[1], InstructionAdapter.OBJECT_TYPE);
        }
        if (arraysNeeded >= 3) {
            arrays[2] = localVarConsumer.createLocalVariable("res2", Type.getDescriptor(double[].class));
            m.load(6, InstructionAdapter.OBJECT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.arraylength();
            m.iconst(0);
            m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
            m.store(arrays[2], InstructionAdapter.OBJECT_TYPE);
        }

        {
            int arrIdx = 0;
            if (!eliminatedX) {
                context.callDelegateMulti(m, inputXMethod, arrays[arrIdx ++]);
            }
            if (!eliminatedY) {
                context.callDelegateMulti(m, inputYMethod, arrays[arrIdx ++]);
            }
            if (!eliminatedZ) {
                context.callDelegateMulti(m, inputZMethod, arrays[arrIdx ++]);
            }
        }

        context.doCountedLoop(m, localVarConsumer, idx -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);

            {
                m.load(0, InstructionAdapter.OBJECT_TYPE);
                m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));

                int arrIdx = 0;
                if (!eliminatedX) {
                    m.load(arrays[arrIdx ++], InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.DOUBLE_TYPE);
                } else {
                    m.dconst(inputXMethod.constValue());
                }

                if (!eliminatedY) {
                    m.load(arrays[arrIdx ++], InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.DOUBLE_TYPE);
                } else {
                    m.dconst(inputYMethod.constValue());
                }

                if (!eliminatedZ) {
                    m.load(arrays[arrIdx ++], InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.DOUBLE_TYPE);
                } else {
                    m.dconst(inputZMethod.constValue());
                }

                m.invokestatic(
                        Type.getInternalName(InvocationShim.class),
                        "invokeDensityFunctionNoiseSample",
                        Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.NoiseHolder.class), Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                        false
                );
            }

            m.astore(Type.DOUBLE_TYPE);

        });

        for (int i = 1; i < arrays.length; i ++) {
            m.load(6, InstructionAdapter.OBJECT_TYPE);
            m.load(arrays[i], InstructionAdapter.OBJECT_TYPE);
            m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class)));
        }

        m.areturn(Type.VOID_TYPE);
    }
}
