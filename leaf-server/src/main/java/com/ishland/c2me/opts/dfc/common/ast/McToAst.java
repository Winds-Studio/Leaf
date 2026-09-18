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

package com.ishland.c2me.opts.dfc.common.ast;

import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.DivNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
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
import com.ishland.c2me.opts.dfc.common.ast.misc.YClampedGradientNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineNormalNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.AbsNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CubeNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegMulNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SquareNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SqueezeNode;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

public class McToAst {

    private static final Logger LOGGER = LoggerFactory.getLogger(McToAst.class);
    public static final FrontendRegistry<AstEmitter<? extends DensityFunction>> REGISTRY = new FrontendRegistry<>();
    private static final ConcurrentHashMap<Class<?>, AtomicLong> delegateStatistics = new ConcurrentHashMap<>();

    static {
        REGISTRY.registerExactMatch(NoiseChunk.BlendAlpha.class, f -> new ConstantNode(1.0));
        REGISTRY.registerExactMatch(NoiseChunk.BlendOffset.class, f -> new ConstantNode(0.0));
        REGISTRY.registerExactMatch(DensityFunctions.BlendAlpha.class, f -> new ConstantNode(1.0));
        REGISTRY.registerExactMatch(DensityFunctions.BlendOffset.class, f -> new ConstantNode(0.0));

        {
            AstEmitter<? extends DensityFunctions.TwoArgumentSimpleFunction> emitter = f -> {
                return switch (f.type()) {
                    case ADD -> new AddNode(toAst(f.argument1()), toAst(f.argument2()));
                    case MUL -> new MulNode(toAst(f.argument1()), toAst(f.argument2()));
                    case MIN -> {
                        double rightMin = f.argument2().minValue();
                        if (f.argument1().minValue() < rightMin) {
                            yield new MinShortNode(toAst(f.argument1()), toAst(f.argument2()), rightMin);
                        } else {
                            yield new MinNode(toAst(f.argument1()), toAst(f.argument2()));
                        }
                    }
                    case MAX -> {
                        double rightMax = f.argument2().maxValue();
                        if (f.argument1().maxValue() > rightMax) {
                            yield new MaxShortNode(toAst(f.argument1()), toAst(f.argument2()), rightMax);
                        } else {
                            yield new MaxNode(toAst(f.argument1()), toAst(f.argument2()));
                        }
                    }
                };
            };
            REGISTRY.registerExactMatch(DensityFunctions.Ap2.class, (AstEmitter<DensityFunctions.Ap2>) emitter);
            REGISTRY.registerExactMatch(DensityFunctions.MulOrAdd.class, (AstEmitter<DensityFunctions.MulOrAdd>) emitter);
        }

        REGISTRY.registerExactMatch(DensityFunctions.Clamp.class, f -> new MaxNode(new ConstantNode(f.minValue()), new MinNode(new ConstantNode(f.maxValue()), toAst(f.input()))));
        REGISTRY.registerExactMatch(DensityFunctions.Constant.class, f -> new ConstantNode(f.value()));
        REGISTRY.registerExactMatch(DensityFunctions.HolderHolder.class, f -> toAst(f.function().value()));
        REGISTRY.registerExactMatch(DensityFunctions.Mapped.class, f -> {
            return switch (f.type()) {
                case ABS -> new AbsNode(toAst(f.input()));
                case SQUARE -> new SquareNode(toAst(f.input()));
                case CUBE -> new CubeNode(toAst(f.input()));
                case HALF_NEGATIVE -> new NegMulNode(toAst(f.input()), 0.5);
                case QUARTER_NEGATIVE -> new NegMulNode(toAst(f.input()), 0.25);
                case INVERT -> new DivNode(new ConstantNode(1.0), toAst(f.input()));
                case SQUEEZE -> new SqueezeNode(toAst(f.input()));
            };
        });
        REGISTRY.registerExactMatch(DensityFunctions.RangeChoice.class, f -> new RangeChoiceNode(toAst(f.input()), f.minInclusive(), f.maxExclusive(), toAst(f.whenInRange()), toAst(f.whenOutOfRange())));

        {
            AstEmitter<? extends IFastCacheLike> emitter = f -> {
                if ((Object) f instanceof DensityFunctions.Marker wrapping && wrapping.type() == DensityFunctions.Marker.Type.BlendDensity) {
                    return toAst(f.c2me$getDelegate());
                }
                return new CacheLikeNode(f, toAst(f.c2me$getDelegate()));
            };
            REGISTRY.registerExactMatch(DensityFunctions.Marker.class, (AstEmitter<DensityFunctions.Marker>) (Object) emitter);
            REGISTRY.registerExactMatch(NoiseChunk.Cache2D.class, (AstEmitter<NoiseChunk.Cache2D>) (Object) emitter);
            REGISTRY.registerExactMatch(NoiseChunk.CacheOnce.class, (AstEmitter<NoiseChunk.CacheOnce>) (Object) emitter);
            REGISTRY.registerExactMatch(NoiseChunk.NoiseInterpolator.class, (AstEmitter<NoiseChunk.NoiseInterpolator>) (Object) emitter);
            REGISTRY.registerExactMatch(NoiseChunk.FlatCache.class, (AstEmitter<NoiseChunk.FlatCache>) (Object) emitter);
        }

        REGISTRY.registerExactMatch(DensityFunctions.ShiftedNoise.class, f -> {
            return new GenericShiftedNoiseNode(
                    new AddNode(new MulNode(CoordinateNode.AXIS_X, new ConstantNode(f.xzScale())), toAst(f.shiftX())),
                    new AddNode(new MulNode(CoordinateNode.AXIS_Y, new ConstantNode(f.yScale())), toAst(f.shiftY())),
                    new AddNode(new MulNode(CoordinateNode.AXIS_Z, new ConstantNode(f.xzScale())), toAst(f.shiftZ())),
                    f.noise()
            );
        });
        REGISTRY.registerExactMatch(DensityFunctions.Noise.class, f -> {
            return new GenericShiftedNoiseNode(
                    new MulNode(CoordinateNode.AXIS_X, new ConstantNode(f.xzScale())),
                    new MulNode(CoordinateNode.AXIS_Y, new ConstantNode(f.yScale())),
                    new MulNode(CoordinateNode.AXIS_Z, new ConstantNode(f.xzScale())),
                    f.noise()
            );
        });
        REGISTRY.registerExactMatch(DensityFunctions.Shift.class, f -> {
            return new MulNode(
                    new GenericShiftedNoiseNode(
                            new MulNode(CoordinateNode.AXIS_X, new ConstantNode(0.25)),
                            new MulNode(CoordinateNode.AXIS_Y, new ConstantNode(0.25)),
                            new MulNode(CoordinateNode.AXIS_Z, new ConstantNode(0.25)),
                            f.offsetNoise()
                    ),
                    new ConstantNode(4.0)
            );
        });
        REGISTRY.registerExactMatch(DensityFunctions.ShiftA.class, f -> {
            return new MulNode(
                    new GenericShiftedNoiseNode(
                            new MulNode(CoordinateNode.AXIS_X, new ConstantNode(0.25)),
                            new ConstantNode(0.0),
                            new MulNode(CoordinateNode.AXIS_Z, new ConstantNode(0.25)),
                            f.offsetNoise()
                    ),
                    new ConstantNode(4.0)
            );
        });
        REGISTRY.registerExactMatch(DensityFunctions.ShiftB.class, f -> {
            return new MulNode(
                    new GenericShiftedNoiseNode(
                            new MulNode(CoordinateNode.AXIS_Z, new ConstantNode(0.25)),
                            new MulNode(CoordinateNode.AXIS_X, new ConstantNode(0.25)),
                            new ConstantNode(0.0),
                            f.offsetNoise()
                    ),
                    new ConstantNode(4.0)
            );
        });
        REGISTRY.registerExactMatch(DensityFunctions.YClampedGradient.class, f -> new YClampedGradientNode(f.fromY(), f.toY(), f.fromValue(), f.toValue()));
        REGISTRY.registerExactMatch(DensityFunctions.IntervalSelect.class, f -> new IntervalSelectNode(toAst(f.input()), f.thresholds().toDoubleArray(), f.functions().stream().map(McToAst::toAst).toArray(AstNode[]::new)));
        REGISTRY.registerExactMatch(DensityFunctions.Spline.class, f -> new Multi2SingleNode(new ToF64Node(toAst(f.spline()))));
        REGISTRY.registerExactMatch(DensityFunctions.FindTopSurface.class, f -> new FindTopSurfaceNode(toAst(f.density()), toAst(f.upperBound()), new ConstantNode(f.lowerBound()), f.cellHeight()));

        // Nodes intentionally handled by the delegate emitter on the JVM backend.
        REGISTRY.registerExactMatch(DensityFunctions.EndIslandDensityFunction.class, EndIslandsNode::new);
        REGISTRY.registerExactMatch(BlendedNoise.class, InterpolatedNoiseSamplerNode::new);
        REGISTRY.registerExactMatch(DensityFunctions.BeardifierMarker.class, BeardifierNode::new);

    }

    public static AstNode toAst(CubicSpline<DensityFunctions.Spline.Coordinate> spline) {
        return switch (spline) {
            case CubicSpline.Constant<DensityFunctions.Spline.Coordinate> f -> new ConstantF32Node(f.value());
            case CubicSpline.Multipoint<DensityFunctions.Spline.Coordinate> f -> new SplineNormalNode(
                    toAst(f.coordinate().function()),
                    f.locations().clone(),
                    f.values().stream().map(McToAst::toAst).toArray(AstNode[]::new),
                    f.derivatives().clone()
            );
        };
    }

    public static <T extends DensityFunction> AstNode toAst(T df) {
        AstEmitter<T> emitter = (AstEmitter<T>) REGISTRY.getOptional(df.getClass());
        if (emitter != null) {
            return emitter.toAst(df);
        } else {
            long known = delegateStatistics.computeIfAbsent(df.getClass(), unused -> new AtomicLong(0L)).getAndIncrement();
            if (known == 0) {
                LOGGER.warn("warn_once: Generating DelegateNode for type: {}", df.getClass().toString());
            }
            return new DelegateNode(df);
        }
    }

}
