package org.dreeam.leaf.async.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * I'll be using this to represent a path that not be processed yet!
 */
public final class AsyncPath extends Path {

    private boolean ready = false;

    private final ArrayList<Consumer<Path>> postProcessing = new ArrayList<>();

    /**
     * A list of positions that this path could path towards
     */
    private final Set<BlockPos> positions;

    private @Nullable Function<PathFinder, Path> pathFn;

    /// Represents an asynchronous task. `null` indicates that is not ready.
    private volatile @Nullable Path ret;

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
    private final PathFinder finder;

    public AsyncPath(PathFinder finder, List<Node> emptyNodeList, Set<BlockPos> positions, Function<PathFinder, Path> pathFn) {
        super(emptyNodeList, BlockPos.ZERO, false);

        this.finder = finder;
        this.positions = positions;
        this.pathFn = pathFn;

        AsyncPathProcessor.queue(() -> {
            synchronized (finder) {
                if (this.ret == null) {
                    this.ret = pathFn.apply(finder);
                }
            }
        });
    }

    @Override
    public boolean isProcessed() {
        if (this.ready) {
            return true;
        }
        Path ret = this.ret;
        if (ret != null) {
            complete(ret);
            return true;
        }
        return false;
    }

    /**
     * Returns the future representing the processing state of this path
     */
    public void schedulePostProcessing(Consumer<Path> runnable) {
        if (this.ready) {
            runnable.accept(this);
        } else {
            this.postProcessing.add(runnable);
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

    private void process() {
        if (this.ready) {
            return;
        }
        Path ret = this.ret;
        if (ret == null) {
            synchronized (finder) {
                if ((ret = this.ret) == null) {
                    ret = (this.ret = Objects.requireNonNull(pathFn).apply(finder));
                }
            }
        }
        complete(ret);
    }

    /// not [#ready]
    ///
    /// @see #isDone()
    /// @see #process()
    /// @see #isProcessed()
    private void complete(Path bestPath) {
        this.nodes = bestPath.nodes;
        this.target = bestPath.getTarget();
        this.distToTarget = bestPath.getDistToTarget();
        this.canReach = bestPath.canReach();
        this.pathFn = null;
        this.ready = true;
        for (Consumer<Path> consumer : this.postProcessing) {
            consumer.accept(this);
        }
        this.postProcessing.clear();
    }

    /*
     * Overrides we need for final fields that we cannot modify after processing
     */

    @Override
    public BlockPos getTarget() {
        this.process();
        return this.target;
    }

    @Override
    public float getDistToTarget() {
        this.process();
        return this.distToTarget;
    }

    @Override
    public boolean canReach() {
        this.process();
        return this.canReach;
    }

    /*
     * Overrides to ensure we're processed first
     */
    @Override
    public boolean isDone() {
        boolean ready = this.ready;
        if (!ready) {
            Path ret = this.ret;
            if (ret != null) {
                complete(ret);
            }
        }
        return this.ready && super.isDone();
    }

    @Override
    public void advance() {
        this.process();
        super.advance();
    }

    @Override
    public boolean notStarted() {
        this.process();
        return super.notStarted();
    }

    @Override
    public @Nullable Node getEndNode() {
        this.process();
        return super.getEndNode();
    }

    @Override
    public Node getNode(int index) {
        this.process();
        return super.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        this.process();
        super.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, Node node) {
        this.process();
        super.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        this.process();
        return super.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        this.process();
        return super.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int nodeIndex) {
        this.process();
        super.setNextNodeIndex(nodeIndex);
    }

    @Override
    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        this.process();
        return super.getEntityPosAtNode(entity, index);
    }

    @Override
    public BlockPos getNodePos(int index) {
        this.process();
        return super.getNodePos(index);
    }

    @Override
    public Vec3 getNextEntityPos(Entity entity) {
        this.process();
        return super.getNextEntityPos(entity);
    }

    @Override
    public BlockPos getNextNodePos() {
        this.process();
        return super.getNextNodePos();
    }

    @Override
    public Node getNextNode() {
        this.process();
        return super.getNextNode();
    }

    @Override
    public @Nullable Node getPreviousNode() {
        this.process();
        return super.getPreviousNode();
    }
}
