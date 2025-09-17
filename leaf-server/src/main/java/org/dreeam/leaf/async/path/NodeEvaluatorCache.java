package org.dreeam.leaf.async.path;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;

public final class NodeEvaluatorCache {

    private static final Int2ObjectOpenHashMap<ArrayDeque<NodeEvaluator>> threadLocalNodeEvaluators = new Int2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<NodeEvaluator, NodeEvaluatorGenerator> nodeEvaluatorToGenerator = new Object2ObjectOpenHashMap<>();

    private NodeEvaluatorCache() {
    }

    public static synchronized @NotNull NodeEvaluator takeNodeEvaluator(@NotNull NodeEvaluatorGenerator generator, @NotNull NodeEvaluator localNodeEvaluator) {
        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        NodeEvaluator nodeEvaluator = threadLocalNodeEvaluators.computeIfAbsent(nodeEvaluatorFeatures, key -> new ArrayDeque<>()).poll();

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(NodeEvaluatorFeatures.unpack(nodeEvaluatorFeatures));
        }

        nodeEvaluatorToGenerator.put(nodeEvaluator, generator);

        return nodeEvaluator;
    }

    public static synchronized void returnNodeEvaluator(@NotNull final NodeEvaluator nodeEvaluator) {
        final NodeEvaluatorGenerator generator = nodeEvaluatorToGenerator.remove(nodeEvaluator);
        Validate.notNull(generator, "NodeEvaluator already returned");

        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(nodeEvaluator);
        threadLocalNodeEvaluators.computeIfAbsent(nodeEvaluatorFeatures, key -> new ArrayDeque<>()).offer(nodeEvaluator);
    }

    public static synchronized void removeNodeEvaluator(@NotNull final NodeEvaluator nodeEvaluator) {
        nodeEvaluatorToGenerator.remove(nodeEvaluator);
    }
}
