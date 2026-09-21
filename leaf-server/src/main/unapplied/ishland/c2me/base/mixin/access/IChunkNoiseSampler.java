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

package com.ishland.c2me.base.mixin.access;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.blending.Blender;

public interface IChunkNoiseSampler {

    int getStartBlockX();

    int getStartBlockY();

    int getStartBlockZ();

    int getHorizontalCellBlockCount();

    int getVerticalCellBlockCount();

    boolean getIsInInterpolationLoop();

    boolean getIsSamplingForCaches();

    int getStartBiomeX();

    int getStartBiomeZ();

    int getHorizontalCellCount();

    int getVerticalCellCount();

    int getMinimumCellY();

    int getCellBlockX();

    int getCellBlockY();

    int getCellBlockZ();

    BlockState invokeSampleBlockState();

    int getHorizontalBiomeEnd();

    DensityFunctions.BeardifierOrMarker getBeardifying();

    int getStartCellX();

    int getStartCellZ();

    Blender getBlender();

}
