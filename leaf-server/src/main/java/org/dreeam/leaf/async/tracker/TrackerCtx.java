package org.dreeam.leaf.async.tracker;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.event.player.PlayerVelocityEvent;

import java.util.Arrays;

public final class TrackerCtx {
    private final Reference2ReferenceOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<? super ClientGamePacketListener>>> packets;
    private final ServerLevel world;
    private final ObjectArrayList<ServerPlayer> bukkitVelocityEvent = new ObjectArrayList<>();
    private final ObjectArrayList<ItemFrame> bukkitItemFrames = new ObjectArrayList<>();
    private final ObjectArrayList<BossEvent> witherBosses = new ObjectArrayList<>();
    private final ObjectArrayList<PaperStopSeen> paperStopSeen = new ObjectArrayList<>();
    private final ObjectArrayList<PaperStartSeen> paperStartSeen = new ObjectArrayList<>();

    private record BossEvent(WitherBoss witherBoss, ObjectArrayList<ServerPlayer> add, ObjectArrayList<ServerPlayer> remove) {}
    private record PaperStopSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q) {}
    private record PaperStartSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q) {}

    public TrackerCtx(ServerLevel world) {
        this.packets = new Reference2ReferenceOpenHashMap<>();
        this.world = world;
    }

    public void stopSeenByPlayer(ServerPlayerConnection connection, Entity entity) {
        if (io.papermc.paper.event.player.PlayerUntrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0) {
            if (paperStopSeen.isEmpty()) {
                paperStopSeen.add(new PaperStopSeen(entity, new ObjectArrayList<>()));
            }
            if (!paperStopSeen.getLast().e.equals(entity)) {
                paperStopSeen.add(new PaperStopSeen(entity, new ObjectArrayList<>()));
            }
            paperStopSeen.getLast().q.add(connection);
        }
        if (entity instanceof WitherBoss witherBoss) {
            if (witherBosses.isEmpty()) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            if (!witherBosses.getLast().witherBoss.equals(witherBoss)) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            witherBosses.getLast().remove.add(connection.getPlayer());
        }
    }

    public void startSeenByPlayer(ServerPlayerConnection connection, Entity entity) {
        if (io.papermc.paper.event.player.PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0) {
            if (paperStartSeen.isEmpty()) {
                paperStartSeen.add(new PaperStartSeen(entity, new ObjectArrayList<>()));
            }
            if (!paperStartSeen.getLast().e.equals(entity)) {
                paperStartSeen.add(new PaperStartSeen(entity, new ObjectArrayList<>()));
            }
            paperStartSeen.getLast().q.add(connection);
        }
        if (entity instanceof WitherBoss witherBoss) {
            if (witherBosses.isEmpty()) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            if (!witherBosses.getLast().witherBoss.equals(witherBoss)) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            witherBosses.getLast().add.add(connection.getPlayer());
        }
    }

    public void updateItemFrame(ItemFrame itemFrame) {
        bukkitItemFrames.add(itemFrame);
    }

    public void playerVelocity(ServerPlayer player) {
        if (PlayerVelocityEvent.getHandlerList().getRegisteredListeners().length == 0) {
            player.hurtMarked = false;
            player.moonrise$getTrackedEntity().leafBroadcastAndSend(this, new ClientboundSetEntityMotionPacket(player));
        } else {
            bukkitVelocityEvent.add(player);
        }
    }

    public void send(ServerPlayerConnection connection, Packet<? super ClientGamePacketListener> packet) {
        packets.computeIfAbsent(connection, x -> new ObjectArrayList<>()).add(packet);
    }

    void handle() {
        if (!bukkitVelocityEvent.isEmpty()) {
            for (ServerPlayer player : bukkitVelocityEvent) {
                boolean cancelled = false;

                org.bukkit.entity.Player player1 = player.getBukkitEntity();
                org.bukkit.util.Vector velocity = player1.getVelocity();

                PlayerVelocityEvent event = new PlayerVelocityEvent(player1, velocity.clone());
                if (!event.callEvent()) {
                    cancelled = true;
                } else if (!velocity.equals(event.getVelocity())) {
                    player1.setVelocity(event.getVelocity());
                }
                if (!cancelled) {
                    player.hurtMarked = false;
                    ChunkMap.TrackedEntity trackedEntity = player.moonrise$getTrackedEntity();
                    trackedEntity.leafBroadcast(this, new ClientboundSetEntityMotionPacket(player));
                }
            }
            bukkitVelocityEvent.clear();
        }
        if (!bukkitItemFrames.isEmpty()) {
            for (ItemFrame itemFrame : bukkitItemFrames) {
                MapId mapId = itemFrame.cachedMapId; // Paper - Perf: Cache map ids on item frames
                MapItemSavedData savedData = MapItem.getSavedData(mapId, world);
                if (savedData != null) {
                    ChunkMap.TrackedEntity trackedEntity = itemFrame.moonrise$getTrackedEntity();
                    if (trackedEntity != null) {
                        ItemStack item = itemFrame.getItem();
                        for (final net.minecraft.server.network.ServerPlayerConnection connection : trackedEntity.seenBy()) {
                            final ServerPlayer serverPlayer = connection.getPlayer(); // Paper
                            savedData.tickCarriedBy(serverPlayer, item);
                            Packet updatePacket = savedData.getUpdatePacket(mapId, serverPlayer);
                            if (updatePacket != null) {
                                send(serverPlayer.connection, updatePacket);
                            }
                        }
                    }
                }
            }
            bukkitItemFrames.clear();
        }
        if (!witherBosses.isEmpty()) {
            for (BossEvent witherBoss : witherBosses) {
                for (ServerPlayer serverPlayer : witherBoss.add) {
                    witherBoss.witherBoss.bossEvent.leafAddPlayer(this, serverPlayer);
                }
                for (ServerPlayer serverPlayer : witherBoss.remove) {
                    witherBoss.witherBoss.bossEvent.leafRemovePlayer(this, serverPlayer);
                }
            }
            witherBosses.clear();
        }
        if (!paperStartSeen.isEmpty()) {
            for (PaperStartSeen startSeen : paperStartSeen) {
                for (ServerPlayerConnection connection : startSeen.q) {
                    if (!new io.papermc.paper.event.player.PlayerTrackEntityEvent(
                        connection.getPlayer().getBukkitEntity(),
                        startSeen.e.getBukkitEntity()
                    ).callEvent()) {
                        // todo: handle cancel track
                    }
                }
            }
            paperStartSeen.clear();
        }
        if (!paperStopSeen.isEmpty()) {
            for (PaperStopSeen stopSeen : paperStopSeen) {
                for (ServerPlayerConnection connection : stopSeen.q) {
                    new io.papermc.paper.event.player.PlayerUntrackEntityEvent(
                        connection.getPlayer().getBukkitEntity(),
                        stopSeen.e.getBukkitEntity()
                    ).callEvent();
                }
            }
            paperStopSeen.clear();
        }

        var iter = packets.reference2ReferenceEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            ServerPlayerConnection k = entry.getKey();
            ObjectArrayList<Packet<? super ClientGamePacketListener>> v = entry.getValue();
            if (world.equals(k.getPlayer().level())) {
                int size = v.size();
                if (size > 4096) {
                    int from = 0;
                    while (from < size) {
                        int chunkLen = Math.min(4096, size - from);
                        Packet<? super ClientGamePacketListener>[] chunk = new Packet[chunkLen];
                        v.getElements(from, chunk, 0, chunkLen);
                        k.send(new ClientboundBundlePacket(Arrays.asList(chunk)));
                        from += chunkLen;
                    }
                } else {
                    k.send(new ClientboundBundlePacket(v));
                }
                if (k instanceof ServerGamePacketListenerImpl conn) {
                    conn.connection.flushChannel();
                }
            }
        }
    }
}
