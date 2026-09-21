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

import com.ishland.c2me.opts.dfc.common.ast.misc.Multi2SingleNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDef;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class Multi2SingleNodeBytecodeEmitter implements BytecodeEmitter<Multi2SingleNode> {
    public static final Multi2SingleNodeBytecodeEmitter INSTANCE = new Multi2SingleNodeBytecodeEmitter();

    private Multi2SingleNodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(Multi2SingleNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef nextMethod = context.newSingleMethod(node.next);

        context.callDelegateSingle(m, nextMethod, nextMethod.returnType());
        switch (nextMethod.returnType()) {
            case F64 -> m.areturn(Type.DOUBLE_TYPE);
            case F32 -> m.areturn(Type.FLOAT_TYPE);
        }
    }

    @Override
    public void doBytecodeGenMulti(Multi2SingleNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateAllToSingle(m, localVarConsumer, node);
        m.areturn(Type.VOID_TYPE);
    }
}
