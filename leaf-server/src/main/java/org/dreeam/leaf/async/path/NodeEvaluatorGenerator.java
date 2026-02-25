package org.dreeam.leaf.async.path;

import net.minecraft.world.level.pathfinder.NodeEvaluator;

public interface NodeEvaluatorGenerator {
    NodeEvaluator generate(NodeEvaluatorFeatures nodeEvaluatorFeatures);
}
