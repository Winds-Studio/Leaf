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

import com.ishland.c2me.opts.dfc.common.ast.binary.AbstractBinaryNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.DivNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
import com.ishland.c2me.opts.dfc.common.gen.CodeGenRegistry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.meta.ValuesMethodDefF64;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class BinaryNodeBytecodeEmitters {
    public abstract static class AbstractGenericBinaryNodeBytecodeEmitter<T extends AbstractBinaryNode> implements BytecodeEmitter<T> {
        @Override
        public final void doBytecodeGenSingle(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newSingleMethodF64(node.left);
            ValuesMethodDefF64 rightMethod = context.newSingleMethodF64(node.right);

            context.callDelegateSingle(m, leftMethod);
            context.callDelegateSingle(m, rightMethod);

            this.bytecodeGenSingleBody(node, m);
        }

        @Override
        public final void doBytecodeGenMulti(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newMultiMethodF64(node.left);
            ValuesMethodDefF64 rightMethod = context.newMultiMethodF64(node.right);

            if (leftMethod.isConst()) {
                context.callDelegateMulti(m, rightMethod);
                context.doCountedLoop(m, localVarConsumer, idx -> bytecodeGenConstMultiBody(node, m, idx, leftMethod.constValue()));
            } else {
                int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.arraylength();
                m.iconst(0);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
                m.store(res1, InstructionAdapter.OBJECT_TYPE);
                context.callDelegateMulti(m, leftMethod);
                context.callDelegateMulti(m, rightMethod, res1);

                context.doCountedLoop(m, localVarConsumer, idx -> bytecodeGenMultiBody(node, m, idx, res1));

                m.load(6, InstructionAdapter.OBJECT_TYPE);
                m.load(res1, InstructionAdapter.OBJECT_TYPE);
                m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class)));
            }

            m.areturn(Type.VOID_TYPE);
        }

        protected abstract void bytecodeGenInstruction(InstructionAdapter m);

        protected void bytecodeGenSingleBody(T node, InstructionAdapter m) {
            this.bytecodeGenInstruction(m);
            m.areturn(Type.DOUBLE_TYPE);
        }

        protected void bytecodeGenMultiBody(T node, InstructionAdapter m, int idx, int res1) {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.dup2();
            m.aload(Type.DOUBLE_TYPE);
            m.load(res1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            this.bytecodeGenInstruction(m);
            m.astore(Type.DOUBLE_TYPE);
        }

        protected void bytecodeGenConstMultiBody(T node, InstructionAdapter m, int idx, double constLeft) {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.dconst(constLeft);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            this.bytecodeGenInstruction(m);
            m.astore(Type.DOUBLE_TYPE);
        }

    }

    public static class AddNodeEmitter extends AbstractGenericBinaryNodeBytecodeEmitter<AddNode> {
        public static final AddNodeEmitter INSTANCE = new AddNodeEmitter();

        private AddNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(InstructionAdapter m) {
            m.add(Type.DOUBLE_TYPE);
        }
    }

    public static class DivNodeEmitter extends AbstractGenericBinaryNodeBytecodeEmitter<DivNode> {
        public static final DivNodeEmitter INSTANCE = new DivNodeEmitter();

        private DivNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(InstructionAdapter m) {
            m.div(Type.DOUBLE_TYPE);
        }
    }

    public static class MaxNodeEmitter extends AbstractGenericBinaryNodeBytecodeEmitter<MaxNode> {
        public static final MaxNodeEmitter INSTANCE = new MaxNodeEmitter();

        private MaxNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(InstructionAdapter m) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "max",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class MinNodeEmitter extends AbstractGenericBinaryNodeBytecodeEmitter<MinNode> {
        public static final MinNodeEmitter INSTANCE = new MinNodeEmitter();

        private MinNodeEmitter() {
        }

        @Override
        protected void bytecodeGenInstruction(InstructionAdapter m) {
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "min",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
        }
    }

    public static class MaxShortNodeEmitter implements BytecodeEmitter<MaxShortNode> {
        public static final MaxShortNodeEmitter INSTANCE = new MaxShortNodeEmitter();

        private MaxShortNodeEmitter() {
        }

        @Override
        public void doBytecodeGenSingle(MaxShortNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newSingleMethodF64(node.left);
            ValuesMethodDefF64 rightMethod = context.newSingleMethodF64(node.right);

            Label minLabel = new Label();

            context.callDelegateSingle(m, leftMethod);
            m.dup2();
            m.dconst(node.rightMax);
            m.cmpl(Type.DOUBLE_TYPE);
            m.ifle(minLabel);
            m.areturn(Type.DOUBLE_TYPE);

            m.visitLabel(minLabel);
            context.callDelegateSingle(m, rightMethod);
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "max",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
            m.areturn(Type.DOUBLE_TYPE);
        }

        @Override
        public void doBytecodeGenMulti(MaxShortNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newMultiMethodF64(node.left);
            ValuesMethodDefF64 rightMethodSingle = context.newSingleMethodF64(node.right);
            context.callDelegateMulti(m, leftMethod);

            context.doCountedLoop(m, localVarConsumer, idx -> {
                Label minLabel = new Label();
                Label end = new Label();

                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);

                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.DOUBLE_TYPE);

                m.dup2();
                m.dconst(node.rightMax);
                m.cmpl(Type.DOUBLE_TYPE);
                m.ifle(minLabel);
                m.goTo(end);

                m.visitLabel(minLabel);
                context.callDelegateSingleFromMulti(m, rightMethodSingle, idx);
                m.invokestatic(
                        Type.getInternalName(Math.class),
                        "max",
                        Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                        false
                );

                m.visitLabel(end);
                m.astore(Type.DOUBLE_TYPE);
            });

            m.areturn(Type.VOID_TYPE);
        }
    }

    public static class MinShortNodeEmitter implements BytecodeEmitter<MinShortNode> {
        public static final MinShortNodeEmitter INSTANCE = new MinShortNodeEmitter();

        private MinShortNodeEmitter() {
        }

        @Override
        public void doBytecodeGenSingle(MinShortNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newSingleMethodF64(node.left);
            ValuesMethodDefF64 rightMethod = context.newSingleMethodF64(node.right);

            Label minLabel = new Label();

            context.callDelegateSingle(m, leftMethod);
            m.dup2();
            m.dconst(node.rightMin);
            m.cmpg(Type.DOUBLE_TYPE);
            m.ifge(minLabel);
            m.areturn(Type.DOUBLE_TYPE);

            m.visitLabel(minLabel);
            context.callDelegateSingle(m, rightMethod);
            m.invokestatic(
                    Type.getInternalName(Math.class),
                    "min",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                    false
            );
            m.areturn(Type.DOUBLE_TYPE);
        }

        @Override
        public void doBytecodeGenMulti(MinShortNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newMultiMethodF64(node.left);
            ValuesMethodDefF64 rightMethodSingle = context.newSingleMethodF64(node.right);
            context.callDelegateMulti(m, leftMethod);

            context.doCountedLoop(m, localVarConsumer, idx -> {
                Label minLabel = new Label();
                Label end = new Label();

                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);

                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.DOUBLE_TYPE);

                m.dup2();
                m.dconst(node.rightMin);
                m.cmpg(Type.DOUBLE_TYPE);
                m.ifge(minLabel);
                m.goTo(end);

                m.visitLabel(minLabel);
                context.callDelegateSingleFromMulti(m, rightMethodSingle, idx);
                m.invokestatic(
                        Type.getInternalName(Math.class),
                        "min",
                        Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE),
                        false
                );

                m.visitLabel(end);
                m.astore(Type.DOUBLE_TYPE);
            });

            m.areturn(Type.VOID_TYPE);
        }
    }

    public static class MulNodeEmitter implements BytecodeEmitter<MulNode> {
        public static final MulNodeEmitter INSTANCE = new MulNodeEmitter();

        private MulNodeEmitter() {
        }

        @Override
        public void doBytecodeGenSingle(MulNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newSingleMethodF64(node.left);
            ValuesMethodDefF64 rightMethod = context.newSingleMethodF64(node.right);

            if (leftMethod.isConst()) {
                if (leftMethod.constValue() == 0.0) {
                    m.dconst(0.0);
                } else {
                    m.dconst(leftMethod.constValue());
                    context.callDelegateSingle(m, rightMethod);
                    m.mul(Type.DOUBLE_TYPE);
                }
            } else {
                Label notZero = new Label();

                context.callDelegateSingle(m, leftMethod);
                m.dup2();
                m.dconst(0.0);
                m.cmpl(Type.DOUBLE_TYPE);
                m.ifne(notZero);
                m.dconst(0.0);
                m.areturn(Type.DOUBLE_TYPE);

                m.visitLabel(notZero);
                context.callDelegateSingle(m, rightMethod);
                m.mul(Type.DOUBLE_TYPE);
            }

            m.areturn(Type.DOUBLE_TYPE);
        }

        @Override
        public void doBytecodeGenMulti(MulNode node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
            ValuesMethodDefF64 leftMethod = context.newMultiMethodF64(node.left);
            if (leftMethod.isConst()) {
                if (leftMethod.constValue() == 0.0) {
                    context.callDelegateMulti(m, leftMethod);
                } else {
                    ValuesMethodDefF64 rightMethod = context.newMultiMethodF64(node.right);

                    context.callDelegateMulti(m, rightMethod);

                    context.doCountedLoop(m, localVarConsumer, idx -> {
                        m.load(1, InstructionAdapter.OBJECT_TYPE);
                        m.load(idx, Type.INT_TYPE);

                        m.dconst(leftMethod.constValue());
                        m.load(1, InstructionAdapter.OBJECT_TYPE);
                        m.load(idx, Type.INT_TYPE);
                        m.aload(Type.DOUBLE_TYPE);
                        m.mul(Type.DOUBLE_TYPE);

                        m.astore(Type.DOUBLE_TYPE);
                    });
                }
            } else {
                ValuesMethodDefF64 rightMethodSingle = context.newSingleMethodF64(node.right);
                context.callDelegateMulti(m, leftMethod);

                context.doCountedLoop(m, localVarConsumer, idx -> {
                    Label minLabel = new Label();
                    Label end = new Label();

                    m.load(1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);

                    m.load(1, InstructionAdapter.OBJECT_TYPE);
                    m.load(idx, Type.INT_TYPE);
                    m.aload(Type.DOUBLE_TYPE);

                    m.dup2();
                    m.dconst(0.0);
                    m.cmpl(Type.DOUBLE_TYPE);
                    m.ifne(minLabel);
                    m.pop2();
                    m.dconst(0.0);
                    m.goTo(end);

                    m.visitLabel(minLabel);
                    context.callDelegateSingleFromMulti(m, rightMethodSingle, idx);
                    m.mul(Type.DOUBLE_TYPE);

                    m.visitLabel(end);
                    m.astore(Type.DOUBLE_TYPE);
                });
            }

            m.areturn(Type.VOID_TYPE);
        }
    }

    public static void register(CodeGenRegistry<BytecodeEmitter<?>> registry) {
        registry.registerExactMatch(AddNode.class, AddNodeEmitter.INSTANCE);
        registry.registerExactMatch(DivNode.class, DivNodeEmitter.INSTANCE);
        registry.registerExactMatch(MaxNode.class, MaxNodeEmitter.INSTANCE);
        registry.registerExactMatch(MaxShortNode.class, MaxShortNodeEmitter.INSTANCE);
        registry.registerExactMatch(MinNode.class, MinNodeEmitter.INSTANCE);
        registry.registerExactMatch(MinShortNode.class, MinShortNodeEmitter.INSTANCE);
        registry.registerExactMatch(MulNode.class, MulNodeEmitter.INSTANCE);
    }

}
