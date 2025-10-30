package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dreeam.leaf.util.map.AttributeInstanceArrayMap;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TrackerCtx {
    @SuppressWarnings("unchecked")
    private static final Object2ObjectFunction<ServerPlayerConnection, ObjectArrayList<Packet<?>>> INIT_PACKET_LIST = x -> ObjectArrayList.wrap(new Packet[16], 0);
    private final Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> packets = new Object2ObjectOpenHashMap<>();
    private final ServerLevel world;
    private final ObjectArrayList<ItemFrame> itemFrames = new ObjectArrayList<>();
    private final ObjectArrayList<BossEvent> witherBosses = new ObjectArrayList<>();
    private final ObjectArrayList<StopSeen> stopSeen = new ObjectArrayList<>();
    private final ObjectArrayList<StartSeen> startSeen = new ObjectArrayList<>();
    private final ObjectArrayList<ChunkMap.TrackedEntity> resync = new ObjectArrayList<>();
    private final ObjectArrayList<ChunkMap.TrackedEntity> pluginEntity = new ObjectArrayList<>();
    private final ObjectArrayList<ChunkMap.TrackedEntity> syncAttributes = new ObjectArrayList<>();

    private record BossEvent(WitherBoss witherBoss,
                             ObjectArrayList<ServerPlayer> add,
                             ObjectArrayList<ServerPlayer> remove) {
    }

    private record StopSeen(Entity e, ObjectArrayList<ServerPlayer> q) {
    }

    private record StartSeen(Entity e,
                             ObjectArrayList<ServerPlayerConnection> q) {
    }

    public TrackerCtx(ServerLevel world) {
        this.world = world;
    }

    public void stopSeenByPlayer(ServerPlayerConnection connection, Entity entity) {
        if (PlayerUntrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0) {
            if (stopSeen.isEmpty() || !stopSeen.getLast().e.equals(entity)) {
                stopSeen.add(new StopSeen(entity, new ObjectArrayList<>()));
            }
            stopSeen.getLast().q.add(connection.getPlayer());
        }
        if (entity instanceof WitherBoss witherBoss) {
            if (witherBosses.isEmpty() || !witherBosses.getLast().witherBoss.equals(witherBoss)) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            witherBosses.getLast().remove.add(connection.getPlayer());
        }
    }

    public void startSeenByPlayer(ServerPlayerConnection connection, Entity entity) {
        if (startSeen.isEmpty() || !startSeen.getLast().e.equals(entity)) {
            startSeen.add(new StartSeen(entity, new ObjectArrayList<>()));
        }
        startSeen.getLast().q.add(connection);
        if (entity instanceof WitherBoss witherBoss) {
            if (witherBosses.isEmpty() || !witherBosses.getLast().witherBoss.equals(witherBoss)) {
                witherBosses.add(new BossEvent(witherBoss, new ObjectArrayList<>(), new ObjectArrayList<>()));
            }
            witherBosses.getLast().add.add(connection.getPlayer());
        }
    }


    public void forceResync(ChunkMap.TrackedEntity entity) {
        resync.add(entity);
    }

    public void updateItemFrame(ItemFrame itemFrame) {
        itemFrames.add(itemFrame);
    }

    public void citizensEntity(ChunkMap.TrackedEntity entity) {
        pluginEntity.add(entity);
    }

    public void send(ServerPlayerConnection connection, Packet<?> packet) {
        packets.computeIfAbsent(connection, INIT_PACKET_LIST).add(packet);
    }

    public void sendRemove(ServerPlayerConnection connection, int id) {
        packets.computeIfAbsent(connection, INIT_PACKET_LIST).add(new ClientboundRemoveEntitiesPacket(id));
    }

    public void broadcast(ChunkMap.TrackedEntity entity, Packet<?> packet) {
        for (ServerPlayerConnection serverPlayerConnection : entity.seenBy()) {
            send(serverPlayerConnection, packet);
        }
    }

    public void broadcastIgnorePlayers(ChunkMap.TrackedEntity entity, Packet<?> packet, List<UUID> ignoredPlayers) {
        for (ServerPlayerConnection conn : entity.seenBy()) {
            if (!ignoredPlayers.contains(conn.getPlayer().getUUID())) {
                send(conn, packet);
            }
        }
    }

    public void broadcastAndSend(ChunkMap.TrackedEntity entity, Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet) {
        broadcast(entity, packet);
        if (entity.serverEntity.entity instanceof ServerPlayer serverPlayer) {
            send(serverPlayer.connection, packet);
        }
    }

    Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> join(TrackerCtx other) {
        itemFrames.addAll(other.itemFrames);
        stopSeen.addAll(other.stopSeen);
        startSeen.addAll(other.startSeen);
        pluginEntity.addAll(other.pluginEntity);
        return other.packets;
    }

    void reset() {
        itemFrames.clear();
        stopSeen.clear();
        startSeen.clear();
        pluginEntity.clear();
        packets.clear();
    }

    void handle(Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>>[] other) {
        if (!pluginEntity.isEmpty()) {
            for (ChunkMap.TrackedEntity tracker : pluginEntity) {
                handlePlugin(tracker);
            }
        }

        Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> prior = new Object2ObjectOpenHashMap<>();

        if (!startSeen.isEmpty()) {
            for (StartSeen startSeen : startSeen) {
                handleStartTrack(startSeen, prior);
            }
        }

        sendPackets(world, prior);

        for (Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> otherPackets : other) {
            sendPackets(world, otherPackets);
        }

        if (!resync.isEmpty()) {
            for (ChunkMap.TrackedEntity tracker : resync) {
                tracker.serverEntity.leaf$sendChanges(this, tracker, true);
            }
        }

        if (!syncAttributes.isEmpty()) {
            for (ChunkMap.TrackedEntity tracker : syncAttributes) {
                handleSyncAttribute(tracker);
            }
        }
        sendPackets(world, this.packets);

        if (!stopSeen.isEmpty()) {
            for (StopSeen untrack : stopSeen) {
                for (ServerPlayer player : untrack.q) {
                    if (world == player.level()) {
                        new PlayerUntrackEntityEvent(
                            player.getBukkitEntity(),
                            untrack.e.getBukkitEntity()
                        ).callEvent();
                    }
                }
            }
        }

        if (!itemFrames.isEmpty()) {
            for (ItemFrame itemFrame : itemFrames) {
                handleItemFrame(itemFrame);
            }
        }

        if (!witherBosses.isEmpty()) {
            for (BossEvent witherBoss : witherBosses) {
                for (ServerPlayer player : witherBoss.add) {
                    if (world == player.level()) {
                        witherBoss.witherBoss.bossEvent.leaf$addPlayer(this, player);
                    }
                }
                for (ServerPlayer player : witherBoss.remove) {
                    witherBoss.witherBoss.bossEvent.leaf$removePlayer(this, player);
                }
            }
        }

        sendPackets(world, this.packets);
    }

    private static void handlePlugin(ChunkMap.TrackedEntity tracker) {
        ChunkSystemEntity entity = tracker.serverEntity.entity;
        ChunkData chunk = entity.moonrise$getChunkData();
        tracker.moonrise$tick(chunk == null ? null : chunk.nearbyPlayers);
        boolean flag = tracker.moonrise$hasPlayers();
        if (!flag) {
            FullChunkStatus status = entity.moonrise$getChunkStatus();
            flag = status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING);
        }
        if (flag) {
            tracker.serverEntity.sendChanges();
        }
    }

    private void handleItemFrame(ItemFrame itemFrame) {
        MapId mapId = itemFrame.cachedMapId; // Paper - Perf: Cache map ids on item frames
        MapItemSavedData savedData = MapItem.getSavedData(mapId, world);
        if (savedData == null) {
            return;
        }
        ChunkMap.TrackedEntity tracker = itemFrame.moonrise$getTrackedEntity();
        if (tracker == null) {
            return;
        }
        ItemStack item = itemFrame.getItem();
        for (ServerPlayerConnection connection : tracker.seenBy()) {
            ServerPlayer serverPlayer = connection.getPlayer(); // Paper
            savedData.tickCarriedBy(serverPlayer, item);
            Packet<?> updatePacket = savedData.getUpdatePacket(mapId, serverPlayer);
            if (updatePacket != null) {
                send(serverPlayer.connection, updatePacket);
            }
        }
    }

    private void handleStartTrack(StartSeen startSeen, Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> prior) {
        ChunkMap.TrackedEntity tracker = startSeen.e.moonrise$getTrackedEntity();
        ObjectArrayList<Packet<? super ClientGamePacketListener>> list = new ObjectArrayList<>(4);
        if (tracker == null) {
            return;
        }
        list.add(startSeen.e.getAddEntityPacket(tracker.serverEntity));
        boolean flag = tracker.serverEntity.leaf$sendPairingData(list);
        ClientboundBundlePacket packet = new ClientboundBundlePacket(list);
        for (ServerPlayerConnection connection : startSeen.q) {
            if (PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0
                && !new PlayerTrackEntityEvent(
                connection.getPlayer().getBukkitEntity(),
                startSeen.e.getBukkitEntity()
            ).callEvent()) {
                sendRemove(connection, startSeen.e.getId());
            } else if (flag && connection.getPlayer() == startSeen.e) {
                var copy = new ObjectArrayList<>(list);
                copy.add(new ClientboundUpdateAttributesPacket(startSeen.e.getId(), List.of(connection.getPlayer().getBukkitEntity().getScaledMaxHealth())));
                var modified = new ClientboundBundlePacket(copy);
                prior.computeIfAbsent(connection, INIT_PACKET_LIST).add(modified);
            } else {
                prior.computeIfAbsent(connection, INIT_PACKET_LIST).add(packet);
            }
        }
    }

    public void sendDirtyEntityData(ChunkMap.TrackedEntity tracker) {
        Entity entity = tracker.serverEntity.entity;
        SynchedEntityData entityData = entity.getEntityData();
        List<SynchedEntityData.DataValue<?>> list = entityData.packDirty();
        if (list != null) {
            tracker.serverEntity.trackedDataValues = entityData.getNonDefaultValues();
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(entity.getId(), list);
            broadcastAndSend(tracker, packet);
        }
        if (entity instanceof LivingEntity livingEntity && livingEntity.getAttributes().attributeDirty()) {
            syncAttributes.add(tracker);
        }
    }

    private void handleSyncAttribute(ChunkMap.TrackedEntity tracker) {
        LivingEntity e = tracker.serverEntity.entity instanceof LivingEntity livingEntity? livingEntity : null;
        if (e == null) {
            return;
        }
        ObjectArrayList<ClientboundUpdateAttributesPacket.AttributeSnapshot> attributes;
        AttributeMap attributeMap = e.getAttributes();
        ServerPlayer p = e instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (attributeMap.attributes instanceof AttributeInstanceArrayMap map) {
            int[] ids = attributeMap.getAttributesToSyncIds();
            attributes = new ObjectArrayList<>(ids.length);
            for (int attributeIdx : ids) {
                AttributeInstance attributeInstance = map.getInstance(attributeIdx);
                if (attributeInstance == null) continue;
                Holder<Attribute> attribute = attributeInstance.getAttribute();
                if (p != null && attribute == Attributes.MAX_HEALTH) {
                    attributeInstance = p.getBukkitEntity().getScaledMaxHealth();
                }
                attributes.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(attribute, attributeInstance.getBaseValue(), attributeInstance.getModifiers()));
            }
        } else {
            Set<AttributeInstance> toSync = attributeMap.getAttributesToSync();
            attributes = new ObjectArrayList<>(toSync.size());
            for (AttributeInstance attributeInstance : toSync) {
                if (attributeInstance == null) continue;
                Holder<Attribute> attribute = attributeInstance.getAttribute();
                if (p != null && attribute == Attributes.MAX_HEALTH) {
                    attributeInstance = p.getBukkitEntity().getScaledMaxHealth();
                }
                attributes.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(attribute, attributeInstance.getBaseValue(), attributeInstance.getModifiers()));
            }
        }
        broadcastAndSend(tracker, new ClientboundUpdateAttributesPacket(e.getId(), attributes));
    }

    private static void sendPackets(ServerLevel world, Object2ObjectOpenHashMap<ServerPlayerConnection, ObjectArrayList<Packet<?>>> packets) {
        if (packets.isEmpty()) {
            return;
        }
        packets.object2ObjectEntrySet().fastForEach(entry -> {
            ServerPlayerConnection connection = entry.getKey();
            ObjectArrayList<Packet<?>> list = entry.getValue();
            if (world == connection.getPlayer().level()) {
                Packet<?>[] packetsRaw = list.elements();
                for (int i = 0, size = list.size(); i < size; i++) {
                    connection.send(packetsRaw[i]);
                }
            }
        });
        packets.clear();
    }
}
