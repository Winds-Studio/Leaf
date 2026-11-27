package org.dreeam.leaf.async.path;

import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;

public enum NodeEvaluatorType {
    WALK,
    SWIM,
    AMPHIBIOUS,
    FLY;

    public static NodeEvaluatorType fromNodeEvaluator(NodeEvaluator nodeEvaluator) {
        return switch (nodeEvaluator) {
            case SwimNodeEvaluator ignored -> SWIM;
            case FlyNodeEvaluator ignored -> FLY;
            case AmphibiousNodeEvaluator ignored -> AMPHIBIOUS;
            default -> WALK;
        };
    }
}
