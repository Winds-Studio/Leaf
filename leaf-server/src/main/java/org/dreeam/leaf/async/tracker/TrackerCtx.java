package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
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

public final class TrackerCtx {
    private final Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> packets;
    private final ServerLevel world;
    private final ObjectArrayList<ServerPlayer> bukkitVelocityEvent = new ObjectArrayList<>();
    private final ObjectArrayList<ItemFrame> bukkitItemFrames = new ObjectArrayList<>();
    private final ObjectArrayList<BossEvent> witherBosses = new ObjectArrayList<>();
    private final ObjectArrayList<PaperStopSeen> paperStopSeen = new ObjectArrayList<>();
    private final ObjectArrayList<PaperStartSeen> paperStartSeen = new ObjectArrayList<>();
    private final ObjectArrayList<Entity> pluginEntity = new ObjectArrayList<>();

    private record BossEvent(WitherBoss witherBoss, ObjectArrayList<ServerPlayer> add, ObjectArrayList<ServerPlayer> remove) {}
    private record PaperStopSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q) {}
    private record PaperStartSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q) {}

    public TrackerCtx(ServerLevel world) {
        this.packets = new Reference2ReferenceOpenHashMap<>();
        this.world = world;
    }

    public void stopSeenByPlayer(ServerPlayerConnection connection, Entity entity) {
        if (PlayerUntrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0) {
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
        if (PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0) {
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
        bukkitVelocityEvent.add(player);
    }

    public void citizensEntity(Entity entity) {
        pluginEntity.add(entity);
    }

    public void send(ServerPlayerConnection connection, Packet<? super ClientGamePacketListener> packet) {
        packets.computeIfAbsent(connection, x -> ReferenceArrayList.wrap(new Packet[16])).add(packet);
    }

    void join(TrackerCtx other) {
        bukkitVelocityEvent.addAll(other.bukkitVelocityEvent);
        bukkitItemFrames.addAll(other.bukkitItemFrames);
        paperStopSeen.addAll(other.paperStopSeen);
        paperStartSeen.addAll(other.paperStartSeen);
        pluginEntity.addAll(other.pluginEntity);
        if (other.packets.isEmpty()) {
            return;
        }
        var iterator = other.packets.reference2ReferenceEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            packets.computeIfAbsent(entry.getKey(), x -> ReferenceArrayList.wrap(new Packet[0])).addAll(entry.getValue());
        }
    }

    void handle(boolean flush) {
        handlePackets(world, packets, flush);

        if (!pluginEntity.isEmpty()) {
            for (final Entity entity : pluginEntity) {
                final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                NearbyPlayers.TrackedChunk trackedChunk = world.moonrise$getNearbyPlayers().getChunk(entity.chunkPosition());
                tracker.leafTick(this, trackedChunk);
                boolean flag = false;
                if (tracker.moonrise$hasPlayers()) {
                    flag = true;
                } else {
                    FullChunkStatus status = ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkStatus();
                    if (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                        flag = true;
                    }
                }
                if (flag) {
                    tracker.serverEntity.leafSendChanges(this, tracker);
                }
            }
            pluginEntity.clear();
        }
        if (!bukkitVelocityEvent.isEmpty()) {
            for (ServerPlayer player : bukkitVelocityEvent) {
                if (!world.equals(player.level())) {
                    continue;
                }
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
                    trackedEntity.leafBroadcastAndSend(this, new ClientboundSetEntityMotionPacket(player));
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
                            Packet<? super ClientGamePacketListener> updatePacket = (Packet<? super ClientGamePacketListener>) savedData.getUpdatePacket(mapId, serverPlayer);
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
                for (ServerPlayer player : witherBoss.add) {
                    if (!world.equals(player.level())) {
                        continue;
                    }
                    witherBoss.witherBoss.bossEvent.leafAddPlayer(this, player);
                }
                for (ServerPlayer player : witherBoss.remove) {
                    witherBoss.witherBoss.bossEvent.leafRemovePlayer(this, player);
                }
            }
            witherBosses.clear();
        }
        if (!paperStartSeen.isEmpty()) {
            for (PaperStartSeen startSeen : paperStartSeen) {
                for (ServerPlayerConnection connection : startSeen.q) {
                    if (!new PlayerTrackEntityEvent(
                        connection.getPlayer().getBukkitEntity(),
                        startSeen.e.getBukkitEntity()
                    ).callEvent()) {
                        send(connection, new ClientboundRemoveEntitiesPacket(startSeen.e.getId()));
                    }
                }
            }
            paperStartSeen.clear();
        }
        if (!paperStopSeen.isEmpty()) {
            for (PaperStopSeen stopSeen : paperStopSeen) {
                for (ServerPlayerConnection connection : stopSeen.q) {
                    new PlayerUntrackEntityEvent(
                        connection.getPlayer().getBukkitEntity(),
                        stopSeen.e.getBukkitEntity()
                    ).callEvent();
                }
            }
            paperStopSeen.clear();
        }

        handlePackets(world, packets, flush);
    }

    private static void handlePackets(ServerLevel world, Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> packets, boolean flush) {
        if (packets.isEmpty()) {
            return;
        }
        var iter = packets.reference2ReferenceEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            ServerPlayerConnection connection = entry.getKey();
            ReferenceArrayList<Packet<? super ClientGamePacketListener>> list = entry.getValue();
            if (!world.equals(connection.getPlayer().level())) {
                continue;
            }
            Packet[] packetsRaw = list.elements();
            for (int i = 0, size = list.size(); i < size; i++) {
                connection.send(packetsRaw[i]);
            }
            if (flush && connection instanceof ServerGamePacketListenerImpl playerConnection) {
                playerConnection.connection.flushChannel();
            }
        }
        packets.clear();
    }
}
