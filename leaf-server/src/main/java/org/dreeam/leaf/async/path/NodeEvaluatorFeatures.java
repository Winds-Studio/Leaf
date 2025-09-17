package org.dreeam.leaf.async.path;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;

public record NodeEvaluatorFeatures(
    NodeEvaluatorType type,
    boolean canPassDoors,
    boolean canFloat,
    boolean canWalkOverFences,
    boolean canOpenDoors,
    boolean allowBreaching
) {

    private static final NodeEvaluatorType[] NODE_EVALUATOR_TYPES = NodeEvaluatorType.values();

    public static int fromNodeEvaluator(NodeEvaluator nodeEvaluator) {
        NodeEvaluatorType type = NodeEvaluatorType.fromNodeEvaluator(nodeEvaluator);
        boolean canPassDoors = nodeEvaluator.canPassDoors();
        boolean canFloat = nodeEvaluator.canFloat();
        boolean canWalkOverFences = nodeEvaluator.canWalkOverFences();
        boolean canOpenDoors = nodeEvaluator.canOpenDoors();
        boolean allowBreaching = nodeEvaluator instanceof SwimNodeEvaluator swimNodeEvaluator && swimNodeEvaluator.allowBreaching;
        return pack(type, canPassDoors, canFloat, canWalkOverFences, canOpenDoors, allowBreaching);
    }

    public static int pack(NodeEvaluatorType type,
                           boolean canPassDoors,
                           boolean canFloat,
                           boolean canWalkOverFences,
                           boolean canOpenDoors,
                           boolean allowBreaching) {
        int value = 0;
        value |= type.ordinal();
        if (canPassDoors) value |= 0b100;
        if (canFloat) value |= 0b1000;
        if (canWalkOverFences) value |= 0b10000;
        if (canOpenDoors) value |= 0b100000;
        if (allowBreaching) value |= 0b1000000;
        return value;
    }

    public static NodeEvaluatorFeatures unpack(int value) {
        NodeEvaluatorType type = NODE_EVALUATOR_TYPES[value & 0b11];
        boolean canPassDoors = (value & 0b100) != 0;
        boolean canFloat = (value & 0b1000) != 0;
        boolean canWalkOverFences = (value & 0b10000) != 0;
        boolean canOpenDoors = (value & 0b100000) != 0;
        boolean allowBreaching = (value & 0b1000000) != 0;
        return new NodeEvaluatorFeatures(type, canPassDoors, canFloat, canWalkOverFences, canOpenDoors, allowBreaching);
    }
}
