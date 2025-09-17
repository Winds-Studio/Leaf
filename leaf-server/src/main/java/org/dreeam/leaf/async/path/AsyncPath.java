package org.dreeam.leaf.async.path;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.dreeam.leaf.async.GlobalDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * I'll be using this to represent a path that not be processed yet!
 */
public final class AsyncPath extends Path implements Callable<Void> {

    /**
     * Runnable waiting for this to be processed
     * ConcurrentLinkedQueue is thread-safe, non-blocking and non-synchronized
     */
    private final ConcurrentLinkedQueue<Runnable> postProcessing = new ConcurrentLinkedQueue<>();

    /**
     * A list of positions that this path could path towards
     */
    private final Set<BlockPos> positions;

    /**
     * The supplier of the real processed path
     */
    private Supplier<Path> innerTask;

    /**
     * The block we're trying to path to
     * <p>
     * While processing, we have no idea where this is so consumers of `Path` should check that the path is processed before checking the target block
     */
    private BlockPos target;
    /**
     * How far we are to the target
     * <p>
     * While processing, the target could be anywhere, but theoretically we're always "close" to a theoretical target so default is 0
     */
    private float distToTarget = 0;
    /**
     * Whether we can reach the target
     * <p>
     * While processing, we can always theoretically reach the target so default is true
     */
    private boolean canReach = true;

    private final FutureTask<Void> task;

    @SuppressWarnings("ConstantConditions")
    public AsyncPath(@NotNull List<Node> emptyNodeList, @NotNull Set<BlockPos> positions, @NotNull Supplier<Path> pathSupplier) {
        super(emptyNodeList, null, false);

        this.positions = positions;
        this.innerTask = pathSupplier;

        task = GlobalDispatcher.INSTANCE.submit(this);
    }

    @Override
    public Void call() {
        final Path bestPath = this.innerTask.get();
        this.innerTask = null;
        this.nodes.addAll(bestPath.nodes);
        this.target = bestPath.getTarget();
        this.distToTarget = bestPath.getDistToTarget();
        this.canReach = bestPath.canReach();
        this.runAllPostProcessing(TickThread.isTickThread());
        return null;
    }

    @Override
    public boolean isProcessed() {
        return this.task.isDone();
    }

    /**
     * Returns the future representing the processing state of this path
     */
    public void schedulePostProcessing(@NotNull Runnable runnable) {
        if (this.task.isDone()) {
            runnable.run();
        } else {
            this.postProcessing.offer(runnable);
            if (this.task.isDone()) {
                this.runAllPostProcessing(true);
            }
        }
    }

    /**
     * An easy way to check if this processing path is the same as an attempted new path
     *
     * @param positions - the positions to compare against
     * @return true if we are processing the same positions
     */
    public boolean hasSameProcessingPositions(final Set<BlockPos> positions) {
        return this.positions.equals(positions);
    }

    private void runAllPostProcessing(boolean isTickThread) {
        Runnable runnable;
        while ((runnable = this.postProcessing.poll()) != null) {
            if (isTickThread) {
                runnable.run();
            } else {
                MinecraftServer.getServer().scheduleOnMain(runnable);
            }
        }
    }

    /*
     * Overrides we need for final fields that we cannot modify after processing
     */

    @Override
    public @NotNull BlockPos getTarget() {
        this.task.run();
        return this.target;
    }

    @Override
    public float getDistToTarget() {
        this.task.run();
        return this.distToTarget;
    }

    @Override
    public boolean canReach() {
        this.task.run();
        return this.canReach;
    }

    /*
     * Overrides to ensure we're processed first
     */

    @Override
    public boolean isDone() {
        return this.task.isDone() && super.isDone();
    }

    @Override
    public void advance() {
        this.task.run();
        super.advance();
    }

    @Override
    public boolean notStarted() {
        this.task.run();
        return super.notStarted();
    }

    @Override
    public @Nullable Node getEndNode() {
        this.task.run();
        return super.getEndNode();
    }

    @Override
    public @NotNull Node getNode(int index) {
        this.task.run();
        return super.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        this.task.run();
        super.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, @NotNull Node node) {
        this.task.run();
        super.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        this.task.run();
        return super.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        this.task.run();
        return super.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int nodeIndex) {
        this.task.run();
        super.setNextNodeIndex(nodeIndex);
    }

    @Override
    public @NotNull Vec3 getEntityPosAtNode(@NotNull Entity entity, int index) {
        this.task.run();
        return super.getEntityPosAtNode(entity, index);
    }

    @Override
    public @NotNull BlockPos getNodePos(int index) {
        this.task.run();
        return super.getNodePos(index);
    }

    @Override
    public @NotNull Vec3 getNextEntityPos(@NotNull Entity entity) {
        this.task.run();
        return super.getNextEntityPos(entity);
    }

    @Override
    public @NotNull BlockPos getNextNodePos() {
        this.task.run();
        return super.getNextNodePos();
    }

    @Override
    public @NotNull Node getNextNode() {
        this.task.run();
        return super.getNextNode();
    }

    @Override
    public @Nullable Node getPreviousNode() {
        this.task.run();
        return super.getPreviousNode();
    }

}
