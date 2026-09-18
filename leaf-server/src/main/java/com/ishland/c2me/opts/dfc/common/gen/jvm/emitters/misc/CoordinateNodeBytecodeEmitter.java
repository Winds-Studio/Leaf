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

import com.ishland.c2me.opts.dfc.common.ast.misc.CoordinateNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class CoordinateNodeBytecodeEmitter implements BytecodeEmitter<CoordinateNode> {
    public static final CoordinateNodeBytecodeEmitter INSTANCE = new CoordinateNodeBytecodeEmitter();

    private CoordinateNodeBytecodeEmitter() {
    }

    @Override
    public void doBytecodeGenSingle(CoordinateNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        switch (node.axis) {
            case X -> m.load(1, Type.INT_TYPE);
            case Y -> m.load(2, Type.INT_TYPE);
            case Z -> m.load(3, Type.INT_TYPE);
        }
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(CoordinateNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.doCountedLoop(m, localVarConsumer, idx -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);

            switch (node.axis) {
                case X -> m.load(2, InstructionAdapter.OBJECT_TYPE);
                case Y -> m.load(3, InstructionAdapter.OBJECT_TYPE);
                case Z -> m.load(4, InstructionAdapter.OBJECT_TYPE);
            }
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);

            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }
}
