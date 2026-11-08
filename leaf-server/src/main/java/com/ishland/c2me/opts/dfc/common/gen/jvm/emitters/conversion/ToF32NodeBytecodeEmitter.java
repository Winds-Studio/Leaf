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

package com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.conversion;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.conversion.ToF32Node;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDef;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class ToF32NodeBytecodeEmitter implements BytecodeEmitter<ToF32Node> {
    public static final ToF32NodeBytecodeEmitter INSTANCE = new ToF32NodeBytecodeEmitter();

    private ToF32NodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(ToF32Node node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef nextMethod = context.newSingleMethod(node.next);
        context.callDelegateSingle(m, nextMethod, nextMethod.returnType());
        switch (nextMethod.returnType()) {
            case F32 -> {
            }
            case F64 -> m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
        }
        m.areturn(Type.FLOAT_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(ToF32Node node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef nextMethod = context.newMultiMethod(node.next);

        switch (nextMethod.returnType()) {
            case F32 -> {
                context.callDelegateMulti(m, nextMethod, AstNode.ReturnType.F32);
            }
            case F64 -> {
                int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.arraylength();
                m.iconst(0);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
                m.store(res1, InstructionAdapter.OBJECT_TYPE);

                context.callDelegateMulti(m, nextMethod, res1, nextMethod.returnType());

                context.doCountedLoop(m, localVarConsumer, idx -> {
                    m.load(1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);

                    m.load(res1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.DOUBLE_TYPE);

                    m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
                    m.astore(Type.FLOAT_TYPE);
                });

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(res1, InstructionAdapter.OBJECT_TYPE);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class)));
            }
        }

        m.areturn(Type.VOID_TYPE);
    }
}
