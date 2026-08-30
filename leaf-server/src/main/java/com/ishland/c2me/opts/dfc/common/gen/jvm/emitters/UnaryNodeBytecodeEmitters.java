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

package com.ishland.c2me.opts.dfc.common.gen.jvm.emitters;

import com.ishland.c2me.opts.dfc.common.ast.unary.AbsNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.AbstractUnaryNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CeilNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CosNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CubeNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.FloorNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegMulNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SinNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SqrtNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SquareNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SqueezeNode;
import com.ishland.c2me.opts.dfc.common.gen.CodeGenRegistry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDefF64;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class UnaryNodeBytecodeEmitters {
    public abstract static class AbstractGenericUnaryNodeBytecodeEmitter<T extends AbstractUnaryNode> implements BytecodeEmitter<T> {
        @Override
        public final void doBytecodeGenSingle(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 operandMethod = context.newSingleMethodF64(node.operand);
            context.callDelegateSingle(m, operandMethod);
            this.bytecodeGenInstruction(node, m, localVarConsumer);
            m.areturn(Type.DOUBLE_TYPE);
        }

        @Override
        public final void doBytecodeGenMulti(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 operandMethod = context.newMultiMethodF64(node.operand);
            context.callDelegateMulti(m, operandMethod);
            context.doCountedLoop(m, localVarConsumer, idx -> {
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.dup2();
                m.aload(Type.DOUBLE_TYPE);
                this.bytecodeGenInstruction(node, m, localVarConsumer);
                m.astore(Type.DOUBLE_TYPE);
            });
            m.areturn(Type.VOID_TYPE);
        }

        protected abstract void bytecodeGenInstruction(T node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer);
    }

    public static class AbsNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<AbsNode> {
        public static final AbsNodeEmitter INSTANCE = new AbsNodeEmitter();

        private AbsNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(AbsNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "abs",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class CeilNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<CeilNode> {
        public static final CeilNodeEmitter INSTANCE = new CeilNodeEmitter();

        private CeilNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(CeilNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "ceil",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class CosNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<CosNode> {
        public static final CosNodeEmitter INSTANCE = new CosNodeEmitter();

        private CosNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(CosNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "cos",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class CubeNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<CubeNode> {
        public static final CubeNodeEmitter INSTANCE = new CubeNodeEmitter();

        private CubeNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(CubeNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.dup2();
            m.dup2();
            m.mul(Type.DOUBLE_TYPE);
            m.mul(Type.DOUBLE_TYPE);
        }
    }

    public static class FloorNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<FloorNode> {
        public static final FloorNodeEmitter INSTANCE = new FloorNodeEmitter();

        private FloorNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(FloorNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "floor",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class NegMulNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<NegMulNode> {
        public static final NegMulNodeEmitter INSTANCE = new NegMulNodeEmitter();

        private NegMulNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(NegMulNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            int v = localVarConsumer.createLocalVariable("v", Type.DOUBLE_TYPE.getDescriptor());
            m.store(v, Type.DOUBLE_TYPE);

            Label negMulLabel = new Label();
            Label end = new Label();

            m.load(v, Type.DOUBLE_TYPE);
            m.dconst(0.0);
            m.cmpl(Type.DOUBLE_TYPE);
            m.ifle(negMulLabel); // v <= 0.0
            m.load(v, Type.DOUBLE_TYPE);
            m.goTo(end);
            m.visitLabel(negMulLabel);
            m.load(v, Type.DOUBLE_TYPE);
            m.dconst(node.negMul);
            m.mul(Type.DOUBLE_TYPE);
            m.visitLabel(end);
        }
    }

    public static class SinNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<SinNode> {
        public static final SinNodeEmitter INSTANCE = new SinNodeEmitter();

        private SinNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(SinNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "sin",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class SqrtNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<SqrtNode> {
        public static final SqrtNodeEmitter INSTANCE = new SqrtNodeEmitter();

        private SqrtNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(SqrtNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "sqrt",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class SquareNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<SquareNode> {
        public static final SquareNodeEmitter INSTANCE = new SquareNodeEmitter();

        private SquareNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(SquareNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.dup2();
            m.mul(Type.DOUBLE_TYPE);
        }
    }

    public static class SqueezeNodeEmitter extends AbstractGenericUnaryNodeBytecodeEmitter<SqueezeNode> {
        public static final SqueezeNodeEmitter INSTANCE = new SqueezeNodeEmitter();

        private SqueezeNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(SqueezeNode node, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            m.dconst(-1.0); // min
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "max",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
            m.dconst(1.0); // max
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "min",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );

            int v = localVarConsumer.createLocalVariable("v", Type.DOUBLE_TYPE.getDescriptor());
            m.store(v, Type.DOUBLE_TYPE);

            m.load(v, Type.DOUBLE_TYPE);
            m.dconst(2.0);
            m.div(Type.DOUBLE_TYPE);

            m.load(v, Type.DOUBLE_TYPE);
            m.dup2();
            m.dup2();
            m.mul(Type.DOUBLE_TYPE);
            m.mul(Type.DOUBLE_TYPE);
            m.dconst(24.0);
            m.div(Type.DOUBLE_TYPE);

            m.sub(Type.DOUBLE_TYPE);
        }
    }

    public static void register(CodeGenRegistry<BytecodeEmitter<?>> registry) {
        registry.registerExactMatch(AbsNode.class, AbsNodeEmitter.INSTANCE);
        registry.registerExactMatch(CeilNode.class, CeilNodeEmitter.INSTANCE);
        registry.registerExactMatch(CosNode.class, CosNodeEmitter.INSTANCE);
        registry.registerExactMatch(CubeNode.class, CubeNodeEmitter.INSTANCE);
        registry.registerExactMatch(FloorNode.class, FloorNodeEmitter.INSTANCE);
        registry.registerExactMatch(NegMulNode.class, NegMulNodeEmitter.INSTANCE);
        registry.registerExactMatch(SinNode.class, SinNodeEmitter.INSTANCE);
        registry.registerExactMatch(SqrtNode.class, SqrtNodeEmitter.INSTANCE);
        registry.registerExactMatch(SquareNode.class, SquareNodeEmitter.INSTANCE);
        registry.registerExactMatch(SqueezeNode.class, SqueezeNodeEmitter.INSTANCE);
    }

}
