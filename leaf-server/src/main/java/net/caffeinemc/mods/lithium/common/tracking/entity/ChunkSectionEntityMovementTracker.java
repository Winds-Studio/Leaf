package net.caffeinemc.mods.lithium.common.tracking.entity;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public abstract class ChunkSectionEntityMovementTracker {
    protected long lastChangeTime = 0;
    protected final ReferenceOpenHashSet<ChunkSectionEntityMovementListener> listeners = new ReferenceOpenHashSet<>();
    protected final ChunkSectionIdentifier identifier;
    protected int userCount = 0;

    public ChunkSectionEntityMovementTracker(long sectionKey, UUID levelId) {
        identifier = ChunkSectionIdentifier.of(sectionKey, levelId);
    }

    public void register() {
        this.userCount++;
    }

    public abstract void unregister();

    public static void unregister(@NotNull List<? extends ChunkSectionEntityMovementTracker> trackers) {
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            tracker.unregister();
        }
    }

    public boolean isUnchangedSince(long lastCheckedTime) {
        return this.lastChangeTime <= lastCheckedTime;
    }

    public static boolean isUnchangedSince(long lastCheckedTime, @NotNull List<? extends ChunkSectionEntityMovementTracker> trackers) {
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            if (!tracker.isUnchangedSince(lastCheckedTime)) {
                return false;
            }
        }
        return true;
    }

    public void listenToEntityMovementOnce(ChunkSectionEntityMovementListener listener) {
        this.listeners.add(listener);
    }

    public static void listenToEntityMovementOnce(ChunkSectionEntityMovementListener listener, @NotNull List<? extends ChunkSectionEntityMovementTracker> trackers) {
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            tracker.listenToEntityMovementOnce(listener);
        }
    }

    private void setChanged(long atTime) {
        if (atTime > this.lastChangeTime) {
            this.lastChangeTime = atTime;
        }
    }

    public void notifyAllListeners(long time) {
        if (!listeners.isEmpty()) {
            for (ChunkSectionEntityMovementListener listener : listeners) {
                listener.handleEntityMovement();
            }
            listeners.clear();
        }
        setChanged(time);
    }

    public record ChunkSectionIdentifier(long sectionKey, UUID levelId) {
        @Contract("_, _ -> new")
        public static @NotNull ChunkSectionIdentifier of(long sectionKey, UUID levelId) {
            return new ChunkSectionIdentifier(sectionKey, levelId);
        }
    }
}
