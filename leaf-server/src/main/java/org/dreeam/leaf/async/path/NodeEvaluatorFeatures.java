package org.dreeam.leaf.async.path;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record NodeEvaluatorFeatures(
    NodeEvaluatorType type,
    boolean canPassDoors,
    boolean canFloat,
    boolean canWalkOverFences,
    boolean canOpenDoors,
    boolean allowBreaching
) {
    public static NodeEvaluatorFeatures fromNodeEvaluator(NodeEvaluator nodeEvaluator) {
        NodeEvaluatorType type = NodeEvaluatorType.fromNodeEvaluator(nodeEvaluator);
        boolean canPassDoors = nodeEvaluator.canPassDoors();
        boolean canFloat = nodeEvaluator.canFloat();
        boolean canWalkOverFences = nodeEvaluator.canWalkOverFences();
        boolean canOpenDoors = nodeEvaluator.canOpenDoors();
        boolean allowBreaching = nodeEvaluator instanceof SwimNodeEvaluator swimNodeEvaluator && swimNodeEvaluator.allowBreaching;
        return new NodeEvaluatorFeatures(type, canPassDoors, canFloat, canWalkOverFences, canOpenDoors, allowBreaching);
    }

    public byte pack() {
        int packed = type.ordinal();
        packed |= canPassDoors ? 0b100 : 0;
        packed |= canFloat ? 0b1000 : 0;
        packed |= canWalkOverFences ? 0b1_0000 : 0;
        packed |= canOpenDoors ? 0b10_0000 : 0;
        packed |= allowBreaching ? 0b100_0000 : 0;
        return (byte) packed;
    }

    @Contract("_ -> new")
    public static @NotNull NodeEvaluatorFeatures unpack(byte packed) {
        int n = packed & 0xFF;
        return new NodeEvaluatorFeatures(
            NodeEvaluatorType.ALL[n & 0b11],
            (n & 0b100) != 0,
            (n & 0b1000) != 0,
            (n & 0b1_0000) != 0,
            (n & 0b10_0000) != 0,
            (n & 0b100_0000) != 0
        );
    }
}
