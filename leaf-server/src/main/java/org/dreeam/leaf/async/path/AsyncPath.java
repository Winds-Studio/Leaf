package org.dreeam.leaf.async.path;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.async.ai.VWaker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * i'll be using this to represent a path that not be processed yet!
 */
public class AsyncPath extends Path implements VWaker {

    /**
     * marks whether this async path has been processed
     */
    private volatile PathProcessState processState = PathProcessState.WAITING;

    /**
     * runnables waiting for this to be processed
     */
    private final List<Runnable> postProcessing = new ArrayList<>(0);

    /**
     * a list of positions that this path could path towards
     */
    private final Set<BlockPos> targetPositions;

    /*
     * Processed values
     */

    /**
     * this is a reference to the nodes list in the parent `Path` object
     */
    private final List<Node> nodes;
    /**
     * the block we're trying to path to
     * <p>
     * while processing, we have no idea where this is so consumers of `Path` should check that the path is processed before checking the target block
     */
    private @Nullable BlockPos target;
    /**
     * how far we are to the target
     * <p>
     * while processing, the target could be anywhere but theoretically we're always "close" to a theoretical target so default is 0
     */
    private float distToTarget = 0;
    /**
     * whether we can reach the target
     * <p>
     * while processing, we can always theoretically reach the target so default is true
     */
    private boolean canReach = true;

    private final PathFinder pathFinder;
    private final NodeEvaluator nodeEvaluator;
    private final Node node;
    private final List<Map.Entry<Target, BlockPos>> positions;
    private final float maxRange;
    private final int accuracy;
    private final float searchDepthMultiplier;

    public AsyncPath(Mob mob, PathFinder pathFinder, NodeEvaluator nodeEvaluator, Node node, List<Map.Entry<Target, BlockPos>> positions, float maxRange, int accuracy, float searchDepthMultiplier, @NotNull List<Node> emptyNodeList, @NotNull Set<BlockPos> targetPositions) {
        //noinspection ConstantConditions
        super(emptyNodeList, null, false);

        this.nodes = emptyNodeList;
        this.targetPositions = targetPositions;
        this.pathFinder = pathFinder;
        this.nodeEvaluator = nodeEvaluator;
        this.node = node;
        this.positions = positions;
        this.maxRange = maxRange;
        this.accuracy = accuracy;
        this.searchDepthMultiplier = searchDepthMultiplier;
        ((ServerLevel) mob.level()).asyncGoalExecutor.submitFindPath(mob.getId());
    }

    @Override
    public boolean isProcessed() {
        return this.processState == PathProcessState.COMPLETED;
    }

    /**
     * returns the future representing the processing state of this path
     */
    public synchronized void postProcessing(@NotNull Runnable runnable) {
        if (isProcessed()) {
            if (TickThread.isTickThread()) {
                runnable.run();
            } else {
                MinecraftServer.getServer().execute(runnable);
            }
        } else {
            this.postProcessing.add(runnable);
        }
    }

    /**
     * an easy way to check if this processing path is the same as an attempted new path
     *
     * @param positions - the positions to compare against
     * @return true if we are processing the same positions
     */
    public boolean hasSameProcessingPositions(final Set<BlockPos> positions) {
        if (this.targetPositions.size() != positions.size()) {
            return false;
        }

        return this.targetPositions.containsAll(positions);
    }

    /**
     * starts processing this path
     */
    @Override
    public synchronized List<Runnable> wake() {
        if (this.processState == PathProcessState.COMPLETED ||
            this.processState == PathProcessState.PROCESSING) {
            return null;
        }

        processState = PathProcessState.PROCESSING;

        Path bestPath;
        try {
            bestPath = pathFinder.processPath(nodeEvaluator, node, positions, maxRange, accuracy, searchDepthMultiplier);
            this.nodes.addAll(bestPath.nodes); // we mutate this list to reuse the logic in Path
            this.target = bestPath.getTarget();
            this.distToTarget = bestPath.getDistToTarget();
            this.canReach = bestPath.canReach();
            var postProcessing = this.postProcessing;
            this.postProcessing.clear();
            return postProcessing;
        } catch (Exception e) {
            AsyncPathProcessor.LOGGER.error(e);
            return null;
        } finally {
            processState = PathProcessState.COMPLETED;
            nodeEvaluator.done();
            NodeEvaluatorCache.returnNodeEvaluator(nodeEvaluator);
        }
    }

    /**
     * if this path is accessed while it hasn't processed, just process it in-place
     */
    private void checkProcessed() {
        if (this.processState == PathProcessState.WAITING ||
            this.processState == PathProcessState.PROCESSING) { // Block if we are on processing
            if (this.wake() instanceof List<Runnable> list) {
                try {
                    for (Runnable o : list) {
                        o.run();
                    }
                } catch (Exception e) {
                    AsyncPathProcessor.LOGGER.error(e);
                }
            }
        }
    }

    /*
     * overrides we need for final fields that we cannot modify after processing
     */

    @Override
    public @NotNull BlockPos getTarget() {
        this.checkProcessed();

        return this.target;
    }

    @Override
    public float getDistToTarget() {
        this.checkProcessed();

        return this.distToTarget;
    }

    @Override
    public boolean canReach() {
        this.checkProcessed();

        return this.canReach;
    }

    /*
     * overrides to ensure we're processed first
     */

    @Override
    public boolean isDone() {
        return this.processState == PathProcessState.COMPLETED && super.isDone();
    }

    @Override
    public void advance() {
        this.checkProcessed();

        super.advance();
    }

    @Override
    public boolean notStarted() {
        this.checkProcessed();

        return super.notStarted();
    }

    @Nullable
    @Override
    public Node getEndNode() {
        this.checkProcessed();

        return super.getEndNode();
    }

    @Override
    public Node getNode(int index) {
        this.checkProcessed();

        return super.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        this.checkProcessed();

        super.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, Node node) {
        this.checkProcessed();

        super.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        this.checkProcessed();

        return super.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        this.checkProcessed();

        return super.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int nodeIndex) {
        this.checkProcessed();

        super.setNextNodeIndex(nodeIndex);
    }

    @Override
    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        this.checkProcessed();

        return super.getEntityPosAtNode(entity, index);
    }

    @Override
    public BlockPos getNodePos(int index) {
        this.checkProcessed();

        return super.getNodePos(index);
    }

    @Override
    public Vec3 getNextEntityPos(Entity entity) {
        this.checkProcessed();

        return super.getNextEntityPos(entity);
    }

    @Override
    public BlockPos getNextNodePos() {
        this.checkProcessed();

        return super.getNextNodePos();
    }

    @Override
    public Node getNextNode() {
        this.checkProcessed();

        return super.getNextNode();
    }

    @Nullable
    @Override
    public Node getPreviousNode() {
        this.checkProcessed();

        return super.getPreviousNode();
    }

    public PathProcessState getProcessState() {
        return processState;
    }
}
