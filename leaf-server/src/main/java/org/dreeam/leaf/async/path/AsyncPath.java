package org.dreeam.leaf.async.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * i'll be using this to represent a path that not be processed yet!
 */
public class AsyncPath extends Path {

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
    private final Set<BlockPos> positions;

    /**
     * the supplier of the real processed path
     */
    private final Supplier<Path> pathSupplier;

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

    public AsyncPath(@NotNull List<Node> emptyNodeList, @NotNull Set<BlockPos> positions, @NotNull Supplier<Path> pathSupplier) {
        //noinspection ConstantConditions
        super(emptyNodeList, null, false);

        this.nodes = emptyNodeList;
        this.positions = positions;
        this.pathSupplier = pathSupplier;

        AsyncPathProcessor.queue(this);
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
            runnable.run();
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
        if (this.positions.size() != positions.size()) {
            return false;
        }

        return this.positions.containsAll(positions);
    }

    /**
     * starts processing this path
     */
    public synchronized void process() {
        if (this.processState == PathProcessState.COMPLETED ||
            this.processState == PathProcessState.PROCESSING) {
            return;
        }

        processState = PathProcessState.PROCESSING;

        final Path bestPath = this.pathSupplier.get();

        this.nodes.addAll(bestPath.nodes); // we mutate this list to reuse the logic in Path
        this.target = bestPath.getTarget();
        this.distToTarget = bestPath.getDistToTarget();
        this.canReach = bestPath.canReach();

        processState = PathProcessState.COMPLETED;

        for (Runnable runnable : this.postProcessing) {
            runnable.run();
        } // Run tasks after processing
    }

    /**
     * if this path is accessed while it hasn't processed, just process it in-place
     */
    private void checkProcessed() {
        if (this.processState == PathProcessState.WAITING ||
            this.processState == PathProcessState.PROCESSING) { // Block if we are on processing
            this.process();
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
