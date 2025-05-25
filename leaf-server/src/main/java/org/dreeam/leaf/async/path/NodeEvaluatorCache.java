package org.dreeam.leaf.async.path;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.PriorityQueues;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMaps;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;

public class NodeEvaluatorCache {

    private static final Byte2ObjectMap<PriorityQueue<NodeEvaluator>> evaluators = Byte2ObjectMaps.synchronize(new Byte2ObjectOpenHashMap<>());
    private static final Object2ObjectMap<NodeEvaluator, NodeEvaluatorGenerator> nodeEvaluatorToGenerator = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

    private static @NotNull PriorityQueue<NodeEvaluator> getQueueForFeatures(@NotNull NodeEvaluatorFeatures nodeEvaluatorFeatures) {
        return evaluators.computeIfAbsent(nodeEvaluatorFeatures.pack(), key -> PriorityQueues.synchronize(new ObjectArrayFIFOQueue<>()));
    }

    public static @NotNull NodeEvaluator takeNodeEvaluator(@NotNull NodeEvaluatorGenerator generator, @NotNull NodeEvaluator localNodeEvaluator) {
        final NodeEvaluatorFeatures nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        PriorityQueue<NodeEvaluator> nodeEvaluatorQueue = getQueueForFeatures(nodeEvaluatorFeatures);

        NodeEvaluator nodeEvaluator;
        synchronized (nodeEvaluatorQueue) {
            if (nodeEvaluatorQueue.isEmpty()) {
                nodeEvaluator = generator.generate(nodeEvaluatorFeatures);
            } else {
                nodeEvaluator = nodeEvaluatorQueue.dequeue();
            }
        }

        nodeEvaluatorToGenerator.put(nodeEvaluator, generator);

        return nodeEvaluator;
    }

    public static void returnNodeEvaluator(@NotNull NodeEvaluator nodeEvaluator) {
        final NodeEvaluatorGenerator generator = nodeEvaluatorToGenerator.remove(nodeEvaluator);
        Validate.notNull(generator, "NodeEvaluator already returned");

        final NodeEvaluatorFeatures nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(nodeEvaluator);
        getQueueForFeatures(nodeEvaluatorFeatures).enqueue(nodeEvaluator);
    }

    public static void removeNodeEvaluator(@NotNull NodeEvaluator nodeEvaluator) {
        nodeEvaluatorToGenerator.remove(nodeEvaluator);
    }
}
