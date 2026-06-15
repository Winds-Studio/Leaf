package org.dreeam.leaf.async.path;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;

import java.util.ArrayDeque;

public final class NodeEvaluatorCache {

    private static final Int2ObjectOpenHashMap<ArrayDeque<NodeEvaluator>> NODE_EVALUATORS = new Int2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<NodeEvaluator, NodeEvaluatorGenerator> NODE_EVALUATOR_TO_GENERATOR = new Object2ObjectOpenHashMap<>();

    public static final ThreadLocal<BinaryHeap> HEAP_LOCAL = ThreadLocal.withInitial(BinaryHeap::new);
    public static final ThreadLocal<Node[]> NEIGHBORS_LOCAL = ThreadLocal.withInitial(() -> new Node[32]);

    private NodeEvaluatorCache() {
    }

    public static synchronized NodeEvaluator takeNodeEvaluator(NodeEvaluatorGenerator generator, NodeEvaluator localNodeEvaluator) {
        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        NodeEvaluator nodeEvaluator = NODE_EVALUATORS.computeIfAbsent(nodeEvaluatorFeatures, key -> new ArrayDeque<>()).poll();

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(NodeEvaluatorFeatures.unpack(nodeEvaluatorFeatures));
        }

        NODE_EVALUATOR_TO_GENERATOR.put(nodeEvaluator, generator);

        return nodeEvaluator;
    }

    public static synchronized void returnNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        final NodeEvaluatorGenerator generator = NODE_EVALUATOR_TO_GENERATOR.remove(nodeEvaluator);
        Validate.notNull(generator, "NodeEvaluator already returned");

        final int feature = NodeEvaluatorFeatures.fromNodeEvaluator(nodeEvaluator);
        NODE_EVALUATORS.computeIfAbsent(feature, key -> new ArrayDeque<>()).offer(nodeEvaluator);
    }

    public static synchronized void removeNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        NODE_EVALUATOR_TO_GENERATOR.remove(nodeEvaluator);
    }
}
