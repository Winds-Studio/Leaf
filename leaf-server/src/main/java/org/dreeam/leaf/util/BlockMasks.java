package org.dreeam.leaf.util;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class BlockMasks {
    public static final int WALL_TAG = 0x01;
    public static final int FENCE_TAG = 0x02;
    public static final int CLIMBABLE_TAG = 0x04;
    public static final int POWDER_SNOW_CL = 0x08;
    public static final int FENCE_GATE_CL = 0x10;
    public static final int TRAP_DOOR_CL = 0x20;
    public static final int WATER = 0x40;
    public static final int LAVA = 0x80;
    public static final int CAN_HOLD_ANY_FLUID = 0x100;

    public static final int FLUID = WATER | LAVA;
    public static final int IS_STATE_CLIMBABLE = org.dreeam.leaf.util.BlockMasks.CLIMBABLE_TAG | org.dreeam.leaf.util.BlockMasks.POWDER_SNOW_CL;

    public static int init(final BlockState state) {
        int i = 0;
        i |= state.is(BlockTags.WALLS) ? WALL_TAG : 0;
        i |= state.is(BlockTags.FENCES) ? FENCE_TAG : 0;
        i |= state.is(BlockTags.CLIMBABLE) ? CLIMBABLE_TAG : 0;
        i |= state.getBlock() instanceof PowderSnowBlock ? POWDER_SNOW_CL : 0;
        i |= state.getBlock() instanceof FenceGateBlock ? FENCE_GATE_CL : 0;
        i |= state.getBlock() instanceof TrapDoorBlock ? TRAP_DOOR_CL : 0;
        i |= state.getFluidState().is(FluidTags.WATER) ? WATER : 0;
        i |= state.getFluidState().is(FluidTags.LAVA) ? LAVA : 0;
        i |= FlowingFluid.canHoldAnyFluid(state) ? CAN_HOLD_ANY_FLUID : 0;
        return i;
    }
}
