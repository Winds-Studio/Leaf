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
import com.ishland.c2me.opts.dfc.common.ast.conversion.ToF64Node;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDef;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class ToF64NodeBytecodeEmitter implements BytecodeEmitter<ToF64Node> {
    public static final ToF64NodeBytecodeEmitter INSTANCE = new ToF64NodeBytecodeEmitter();

    private ToF64NodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(ToF64Node node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef nextMethod = context.newSingleMethod(node.next);
        context.callDelegateSingle(m, nextMethod, nextMethod.returnType());
        switch (nextMethod.returnType()) {
            case F64 -> {
            }
            case F32 -> m.cast(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
        }
        m.areturn(Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(ToF64Node node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef nextMethod = context.newMultiMethod(node.next);

        switch (nextMethod.returnType()) {
            case F64 -> {
                context.callDelegateMulti(m, nextMethod, AstNode.ReturnType.F64);
            }
            case F32 -> {
                int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(float[].class));

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.arraylength();
                m.iconst(0);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getFloatArray", Type.getMethodDescriptor(Type.getType(float[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
                m.store(res1, InstructionAdapter.OBJECT_TYPE);

                context.callDelegateMulti(m, nextMethod, res1, nextMethod.returnType());

                context.doCountedLoop(m, localVarConsumer, idx -> {
                    m.load(1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);

                    m.load(res1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.FLOAT_TYPE);

                    m.cast(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
                    m.astore(Type.DOUBLE_TYPE);
                });

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(res1, InstructionAdapter.OBJECT_TYPE);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(float[].class)));
            }
        }

        m.areturn(Type.VOID_TYPE);
    }
}
