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

package com.ishland.c2me.opts.dfc.common.gen.jvm;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.conversion.ToF32Node;
import com.ishland.c2me.opts.dfc.common.ast.conversion.ToF64Node;
import com.ishland.c2me.opts.dfc.common.ast.misc.BeardifierNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantF32Node;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CoordinateNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.DelegateNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.EndIslandsNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.FindTopSurfaceNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.InterpolatedNoiseSamplerNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.IntervalSelectNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.Multi2SingleNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RangeChoiceNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RootNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.YClampedGradientNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineNormalNode;
import com.ishland.c2me.opts.dfc.common.gen.CodeGenRegistry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.BinaryNodeBytecodeEmitters;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.UnaryNodeBytecodeEmitters;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.conversion.ToF32NodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.conversion.ToF64NodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.CacheLikeNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.ConstantF32NodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.ConstantNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.CoordinateNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.DelegateNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.FindTopSurfaceNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.GenericShiftedNoiseNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.IntervalSelectNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.Multi2SingleNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.RangeChoiceNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.RootNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.SplineNormalNodeBytecodeEmitter;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.YClampedGradientNodeBytecodeEmitter;
import org.objectweb.asm.commons.InstructionAdapter;

public class BytecodeGenRegistry {

    public static final CodeGenRegistry<BytecodeEmitter<? extends AstNode>> REGISTRY = new CodeGenRegistry<>();

    static {
        BinaryNodeBytecodeEmitters.register(REGISTRY);
        UnaryNodeBytecodeEmitters.register(REGISTRY);

        REGISTRY.registerExactMatch(ToF32Node.class, ToF32NodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(ToF64Node.class, ToF64NodeBytecodeEmitter.INSTANCE);

        REGISTRY.registerExactMatch(CacheLikeNode.class, CacheLikeNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(ConstantNode.class, ConstantNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(ConstantF32Node.class, ConstantF32NodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(CoordinateNode.class, CoordinateNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(FindTopSurfaceNode.class, FindTopSurfaceNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(GenericShiftedNoiseNode.class, GenericShiftedNoiseNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(RangeChoiceNode.class, RangeChoiceNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(RootNode.class, RootNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(YClampedGradientNode.class, YClampedGradientNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(IntervalSelectNode.class, IntervalSelectNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(Multi2SingleNode.class, Multi2SingleNodeBytecodeEmitter.INSTANCE);
        REGISTRY.registerExactMatch(SplineNormalNode.class, SplineNormalNodeBytecodeEmitter.INSTANCE);
//        REGISTRY.registerExactMatch(SplineAstNode.class, SplineAstNodeBytecodeEmitter.INSTANCE);

        REGISTRY.registerExactMatch(DelegateNode.class, DelegateNodeBytecodeEmitter.instance());
        REGISTRY.registerExactMatch(BeardifierNode.class, DelegateNodeBytecodeEmitter.instance());
        REGISTRY.registerExactMatch(EndIslandsNode.class, DelegateNodeBytecodeEmitter.instance());
        REGISTRY.registerExactMatch(InterpolatedNoiseSamplerNode.class, DelegateNodeBytecodeEmitter.instance());
    }

    public static <T extends AstNode> void doBytecodeGenSingle(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        BytecodeEmitter<T> emitter = (BytecodeEmitter<T>) REGISTRY.get(node.getClass());
        emitter.doBytecodeGenSingle(node, context, m, localVarConsumer);
    }

    public static <T extends AstNode> void doBytecodeGenMulti(T node, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        BytecodeEmitter<T> emitter = (BytecodeEmitter<T>) REGISTRY.get(node.getClass());
        emitter.doBytecodeGenMulti(node, context, m, localVarConsumer);
    }

}
