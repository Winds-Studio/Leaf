package org.dreeam.leaf.async.path;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;

import java.util.ArrayDeque;

public final class NodeEvaluatorCache {

    // Different generators can implement different pathfinding rules with the same feature flags
    private static final Reference2ObjectOpenHashMap<NodeEvaluatorGenerator, Int2ObjectOpenHashMap<ArrayDeque<NodeEvaluator>>> NODE_EVALUATORS = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<NodeEvaluator, ArrayDeque<NodeEvaluator>> NODE_EVALUATOR_TO_POOL = new Reference2ObjectOpenHashMap<>();

    public static final ThreadLocal<BinaryHeap> HEAP_LOCAL = ThreadLocal.withInitial(BinaryHeap::new);
    public static final ThreadLocal<Node[]> NEIGHBORS_LOCAL = ThreadLocal.withInitial(() -> new Node[32]);

    private NodeEvaluatorCache() {
    }

    public static synchronized NodeEvaluator takeNodeEvaluator(NodeEvaluatorGenerator generator, NodeEvaluator localNodeEvaluator) {
        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        final Int2ObjectOpenHashMap<ArrayDeque<NodeEvaluator>> generatorEvaluators = NODE_EVALUATORS.computeIfAbsent(generator, key -> new Int2ObjectOpenHashMap<>());
        final ArrayDeque<NodeEvaluator> pool = generatorEvaluators.computeIfAbsent(nodeEvaluatorFeatures, key -> new ArrayDeque<>());
        NodeEvaluator nodeEvaluator = pool.poll();

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(NodeEvaluatorFeatures.unpack(nodeEvaluatorFeatures));
        }

        NODE_EVALUATOR_TO_POOL.put(nodeEvaluator, pool);

        return nodeEvaluator;
    }

    public static synchronized void returnNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        final ArrayDeque<NodeEvaluator> pool = NODE_EVALUATOR_TO_POOL.remove(nodeEvaluator);
        Validate.notNull(pool, "NodeEvaluator already returned");

        pool.offer(nodeEvaluator);
    }

    public static synchronized void removeNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        NODE_EVALUATOR_TO_POOL.remove(nodeEvaluator);
    }
}
