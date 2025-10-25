package org.dreeam.leaf.async.tracker;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.*;
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
    private static final Reference2ReferenceFunction<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> INIT_PACKET_LIST = x -> ReferenceArrayList.wrap(new Packet[16], 0);
    private final Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> packets = new Reference2ReferenceOpenHashMap<>();
    private final ServerLevel world;
    private final ObjectArrayList<ItemFrame> itemFrames = new ObjectArrayList<>();
    private final ObjectArrayList<BossEvent> witherBosses = new ObjectArrayList<>();
    private final ObjectArrayList<PaperStopSeen> paperStopSeen = new ObjectArrayList<>();
    private final ObjectArrayList<StartSeen> startSeen = new ObjectArrayList<>();
    private final ObjectArrayList<Entity> pluginEntity = new ObjectArrayList<>();
    private final ReferenceArrayList<SyncAttributes> syncAttributes = ReferenceArrayList.wrap(new SyncAttributes[8], 0);

    private record BossEvent(WitherBoss witherBoss, ObjectArrayList<ServerPlayer> add, ObjectArrayList<ServerPlayer> remove) {}
    private record PaperStopSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q) {}
    private record StartSeen(Entity e, ObjectArrayList<ServerPlayerConnection> q, Packet<? super ClientGamePacketListener> addEntityPacket) {}
    private record SyncAttributes(LivingEntity e, ServerPlayerConnection[] seenBy) {}

    public TrackerCtx(ServerLevel world) {
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

    public void startSeenByPlayer(ServerPlayerConnection connection, Entity entity, ServerEntity serverEntity) {
        if (startSeen.isEmpty()) {
            StartSeen elem = new StartSeen(entity, new ObjectArrayList<>(), entity.getAddEntityPacket(serverEntity));
            startSeen.add(elem);
        }
        if (!startSeen.getLast().e.equals(entity)) {
            StartSeen elem = new StartSeen(entity, new ObjectArrayList<>(), entity.getAddEntityPacket(serverEntity));
            startSeen.add(elem);
        }
        startSeen.getLast().q.add(connection);
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

    private void syncAttributes(LivingEntity livingEntity, ServerPlayerConnection[] seenBy) {
        syncAttributes.add(new SyncAttributes(livingEntity, seenBy));
    }

    public void updateItemFrame(ItemFrame itemFrame) {
        itemFrames.add(itemFrame);
    }

    public void citizensEntity(Entity entity) {
        pluginEntity.add(entity);
    }

    public void send(ServerPlayerConnection connection, Packet<? super ClientGamePacketListener> packet) {
        packets.computeIfAbsent(connection, INIT_PACKET_LIST).add(packet);
    }

    public void broadcast(ChunkMap.TrackedEntity entity, Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet) {
        for (ServerPlayerConnection serverPlayerConnection : entity.seenBy()) {
            send(serverPlayerConnection, packet);
        }
    }

    public void broadcastIgnorePlayers(ChunkMap.TrackedEntity entity, Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet, List<UUID> ignoredPlayers) {
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

    Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> join(TrackerCtx other) {
        itemFrames.addAll(other.itemFrames);
        paperStopSeen.addAll(other.paperStopSeen);
        startSeen.addAll(other.startSeen);
        pluginEntity.addAll(other.pluginEntity);
        return other.packets;
    }

    void handle(Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>>[] other) {
        if (!pluginEntity.isEmpty()) {
            for (final Entity entity : pluginEntity) {
                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunk = ((ChunkSystemEntity) entity).moonrise$getChunkData();
                // unlikely
                if (chunk == null) {
                    continue;
                }
                tracker.moonrise$tick(chunk.nearbyPlayers);
                boolean flag = false;
                if (tracker.moonrise$hasPlayers()) {
                    flag = true;
                } else {
                    FullChunkStatus status = ((ChunkSystemEntity) entity).moonrise$getChunkStatus();
                    if (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                        flag = true;
                    }
                }
                if (flag) {
                    tracker.serverEntity.sendChanges();
                }
            }
        }

        Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> prior = new Reference2ReferenceOpenHashMap<>();

        if (!startSeen.isEmpty()) {
            for (StartSeen startSeen : startSeen) {
                handleStartTrack(startSeen, prior);
            }
        }

        sendPackets(world, prior);

        for (Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> otherPackets : other) {
            sendPackets(world, otherPackets);
        }

        SyncAttributes[] raw = syncAttributes.elements();
        for (int i = 0, size = syncAttributes.size(); i < size; i++) {
            handleSyncAttribute(raw[i]);
        }
        sendPackets(world, this.packets);

        if (!paperStopSeen.isEmpty()) {
            for (PaperStopSeen stopSeen : paperStopSeen) {
                for (ServerPlayerConnection connection : stopSeen.q) {
                    if (!world.equals(connection.getPlayer().level())) {
                        continue;
                    }
                    new PlayerUntrackEntityEvent(
                        connection.getPlayer().getBukkitEntity(),
                        stopSeen.e.getBukkitEntity()
                    ).callEvent();
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
                    if (!world.equals(player.level())) {
                        continue;
                    }
                    witherBoss.witherBoss.bossEvent.leafAddPlayer(this, player);
                }
                for (ServerPlayer player : witherBoss.remove) {
                    witherBoss.witherBoss.bossEvent.leafRemovePlayer(this, player);
                }
            }
        }

        sendPackets(world, this.packets);
    }

    private void handleItemFrame(ItemFrame itemFrame) {
        MapId mapId = itemFrame.cachedMapId; // Paper - Perf: Cache map ids on item frames
        MapItemSavedData savedData = MapItem.getSavedData(mapId, world);
        if (savedData != null) {
            ChunkMap.TrackedEntity tracker = itemFrame.moonrise$getTrackedEntity();
            if (tracker != null) {
                ItemStack item = itemFrame.getItem();
                for (final ServerPlayerConnection connection : tracker.seenBy()) {
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

    private void handleStartTrack(StartSeen startSeen, Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> prior) {
        ChunkMap.TrackedEntity tracker = startSeen.e.moonrise$getTrackedEntity();
        ObjectArrayList<Packet<? super ClientGamePacketListener>> list = new ObjectArrayList<>(4);
        if (tracker == null) {
            return;
        }
        list.add(startSeen.addEntityPacket);
        boolean flag = tracker.serverEntity.leafSendPairingData(list);
        ClientboundBundlePacket packet = new ClientboundBundlePacket(list);
        for (ServerPlayerConnection connection : startSeen.q) {
            if (PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0
                && !new PlayerTrackEntityEvent(
                connection.getPlayer().getBukkitEntity(),
                startSeen.e.getBukkitEntity()
            ).callEvent()) {
                send(connection, new ClientboundRemoveEntitiesPacket(startSeen.e.getId()));
            } else {
                if (flag && connection.getPlayer() == startSeen.e) {
                    var copy = new ObjectArrayList<>(list);
                    copy.add(new ClientboundUpdateAttributesPacket(startSeen.e.getId(), List.of(connection.getPlayer().getBukkitEntity().getScaledMaxHealth())));
                    var modified = new ClientboundBundlePacket(copy);
                    prior.computeIfAbsent(connection, INIT_PACKET_LIST).add(modified);
                } else {
                    prior.computeIfAbsent(connection, INIT_PACKET_LIST).add(packet);
                }
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
            syncAttributes(livingEntity, tracker.seenBy());
        }
    }

    private void handleSyncAttribute(SyncAttributes syncAttribute) {
        LivingEntity e = syncAttribute.e;
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
        ClientboundUpdateAttributesPacket packet = new ClientboundUpdateAttributesPacket(e.getId(), attributes);
        for (ServerPlayerConnection connection : syncAttribute.seenBy) {
            send(connection, packet);
        }
        if (p != null) {
            send(p.connection, packet);
        }
    }

    private static void sendPackets(ServerLevel world, Reference2ReferenceOpenHashMap<ServerPlayerConnection, ReferenceArrayList<Packet<? super ClientGamePacketListener>>> packets) {
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
            Packet<? super ClientGamePacketListener>[] packetsRaw = list.elements();
            for (int i = 0, size = list.size(); i < size; i++) {
                connection.send(packetsRaw[i]);
            }
        }
        packets.clear();
    }
}
