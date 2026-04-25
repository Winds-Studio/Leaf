package org.leavesmc.leaves.protocol.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.*;
import org.bukkit.Bukkit;
import org.dreeam.leaf.config.modules.network.ProtocolSupport;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.leavesmc.leaves.protocol.jade.accessor.BlockAccessor;
import org.leavesmc.leaves.protocol.jade.accessor.EntityAccessor;
import org.leavesmc.leaves.protocol.jade.payload.ClientHandshakePayload;
import org.leavesmc.leaves.protocol.jade.payload.ReceiveDataPayload;
import org.leavesmc.leaves.protocol.jade.payload.RequestBlockPayload;
import org.leavesmc.leaves.protocol.jade.payload.RequestEntityPayload;
import org.leavesmc.leaves.protocol.jade.payload.ServerHandshakePayload;
import org.leavesmc.leaves.protocol.jade.provider.ItemStorageExtensionProvider;
import org.leavesmc.leaves.protocol.jade.provider.ItemStorageProvider;
import org.leavesmc.leaves.protocol.jade.provider.JadeProvider;
import org.leavesmc.leaves.protocol.jade.provider.ServerDataProvider;
import org.leavesmc.leaves.protocol.jade.provider.ServerExtensionProvider;
import org.leavesmc.leaves.protocol.jade.provider.block.*;
import org.leavesmc.leaves.protocol.jade.provider.entity.AnimalOwnerProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.MobBreedingProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.MobGrowthProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.NextEntityDropProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.PetArmorProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.StatusEffectsProvider;
import org.leavesmc.leaves.protocol.jade.provider.entity.ZombieVillagerProvider;
import org.leavesmc.leaves.protocol.jade.util.HierarchyLookup;
import org.leavesmc.leaves.protocol.jade.util.LootTableMineableCollector;
import org.leavesmc.leaves.protocol.jade.util.PairHierarchyLookup;
import org.leavesmc.leaves.protocol.jade.util.PriorityStore;
import org.leavesmc.leaves.protocol.jade.util.WrappedHierarchyLookup;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@LeavesProtocol.Register(namespace = "jade")
public class JadeProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "jade";
    public static final String PROTOCOL_VERSION = "9";
    public static final HierarchyLookup<ServerDataProvider<EntityAccessor>> entityDataProviders = new HierarchyLookup<>(Entity.class);
    public static final PairHierarchyLookup<ServerDataProvider<BlockAccessor>> blockDataProviders = new PairHierarchyLookup<>(new HierarchyLookup<>(Block.class), new HierarchyLookup<>(BlockEntity.class));
    public static final WrappedHierarchyLookup<ServerExtensionProvider<ItemStack>> itemStorageProviders = WrappedHierarchyLookup.forAccessor();
    private static final Set<ServerPlayer> enabledPlayers = new HashSet<>();

    public static PriorityStore<Identifier, JadeProvider> priorities;
    private static List<Block> shearableBlocks = null;

    @Contract("_ -> new")
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PROTOCOL_ID, path);
    }

    @Contract("_ -> new")
    public static @NotNull Identifier mc_id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    @ProtocolHandler.Init
    public static void init() {
        priorities = new PriorityStore<>(JadeProvider::getDefaultPriority, JadeProvider::getUid);

        // core plugin
        blockDataProviders.register(BlockEntity.class, BlockNameProvider.INSTANCE);

        // universal plugin
        entityDataProviders.register(Entity.class, ItemStorageProvider.getEntity());
        blockDataProviders.register(Block.class, ItemStorageProvider.getBlock());

        itemStorageProviders.register(Object.class, ItemStorageExtensionProvider.INSTANCE);
        itemStorageProviders.register(Block.class, ItemStorageExtensionProvider.INSTANCE);

        // vanilla plugin
        entityDataProviders.register(Entity.class, AnimalOwnerProvider.INSTANCE);
        entityDataProviders.register(LivingEntity.class, StatusEffectsProvider.INSTANCE);
        entityDataProviders.register(AgeableMob.class, MobGrowthProvider.INSTANCE);
        entityDataProviders.register(Tadpole.class, MobGrowthProvider.INSTANCE);
        entityDataProviders.register(Animal.class, MobBreedingProvider.INSTANCE);
        entityDataProviders.register(Allay.class, MobBreedingProvider.INSTANCE);
        entityDataProviders.register(Mob.class, PetArmorProvider.INSTANCE);

        entityDataProviders.register(Chicken.class, NextEntityDropProvider.INSTANCE);
        entityDataProviders.register(Armadillo.class, NextEntityDropProvider.INSTANCE);
        entityDataProviders.register(Sniffer.class, NextEntityDropProvider.INSTANCE);

        entityDataProviders.register(ZombieVillager.class, ZombieVillagerProvider.INSTANCE);

        blockDataProviders.register(BrewingStandBlockEntity.class, BrewingStandProvider.INSTANCE);
        blockDataProviders.register(BeehiveBlockEntity.class, BeehiveProvider.INSTANCE);
        blockDataProviders.register(CommandBlockEntity.class, CommandBlockProvider.INSTANCE);
        blockDataProviders.register(JukeboxBlockEntity.class, JukeboxProvider.INSTANCE);
        blockDataProviders.register(LecternBlockEntity.class, LecternProvider.INSTANCE);

        blockDataProviders.register(ComparatorBlockEntity.class, RedstoneProvider.INSTANCE);
        blockDataProviders.register(HopperBlockEntity.class, HopperLockProvider.INSTANCE);
        blockDataProviders.register(CalibratedSculkSensorBlockEntity.class, RedstoneProvider.INSTANCE);

        blockDataProviders.register(AbstractFurnaceBlockEntity.class, FurnaceProvider.INSTANCE);
        blockDataProviders.register(ChiseledBookShelfBlockEntity.class, ChiseledBookshelfProvider.INSTANCE);
        blockDataProviders.register(TrialSpawnerBlockEntity.class, MobSpawnerCooldownProvider.INSTANCE);

        itemStorageProviders.register(CampfireBlock.class, CampfireProvider.INSTANCE);

        blockDataProviders.idMapped();
        entityDataProviders.idMapped();

        blockDataProviders.loadComplete(priorities);
        entityDataProviders.loadComplete(priorities);
        itemStorageProviders.loadComplete(priorities);

        rebuildShearableBlocks();
    }

    @ProtocolHandler.PayloadReceiver(payload = ClientHandshakePayload.class)
    public static void clientHandshake(ServerPlayer player, ClientHandshakePayload payload) {
        if (!payload.protocolVersion().equals(PROTOCOL_VERSION)) {
            player.sendSystemMessage(Component.literal("You are using a different version of Jade than the server. Please update Jade or report to the server operator").withColor(0xff0000));
            return;
        }
        ProtocolUtils.sendPayloadPacket(player, new ServerHandshakePayload(Collections.emptyMap(), shearableBlocks, blockDataProviders.mappedIds(), entityDataProviders.mappedIds()));
        enabledPlayers.add(player);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(ServerPlayer player) {
        enabledPlayers.remove(player);
    }

    @ProtocolHandler.PayloadReceiver(payload = RequestEntityPayload.class)
    public static void requestEntityData(ServerPlayer player, RequestEntityPayload payload) {
        Bukkit.getGlobalRegionScheduler().run(MinecraftInternalPlugin.INSTANCE, (task) -> {
            EntityAccessor accessor = payload.data().unpack(player);
            if (accessor == null) {
                return;
            }

            Entity entity = accessor.getEntity();
            double maxDistance = Mth.square(player.entityInteractionRange() + 21);
            if (entity == null || player.distanceToSqr(entity) > maxDistance) {
                return;
            }

            List<ServerDataProvider<EntityAccessor>> providers = entityDataProviders.get(entity);
            if (providers.isEmpty()) {
                return;
            }

            CompoundTag tag = new CompoundTag();
            for (ServerDataProvider<EntityAccessor> provider : providers) {
                if (!payload.dataProviders().contains(provider)) {
                    continue;
                }
                try {
                    provider.appendServerData(tag, accessor);
                } catch (Exception e) {
                    LeavesLogger.LOGGER.warning("Error while saving data for entity " + entity);
                }
            }
            tag.putInt("EntityId", entity.getId());

            ProtocolUtils.sendPayloadPacket(player, new ReceiveDataPayload(tag));
        });
    }

    @ProtocolHandler.PayloadReceiver(payload = RequestBlockPayload.class)
    public static void requestBlockData(ServerPlayer player, RequestBlockPayload payload) {
        Bukkit.getGlobalRegionScheduler().run(MinecraftInternalPlugin.INSTANCE, (task) -> {
            BlockAccessor accessor = payload.data().unpack(player);
            if (accessor == null) {
                return;
            }

            BlockPos pos = accessor.getPosition();
            Block block = accessor.getBlock();
            BlockEntity blockEntity = accessor.getBlockEntity();
            double maxDistance = Mth.square(player.blockInteractionRange() + 21);
            if (pos.distSqr(player.blockPosition()) > maxDistance || !accessor.getLevel().isLoaded(pos)) {
                return;
            }

            List<ServerDataProvider<BlockAccessor>> providers;
            if (blockEntity != null) {
                providers = blockDataProviders.getMerged(block, blockEntity);
            } else {
                providers = blockDataProviders.first.get(block);
            }

            if (providers.isEmpty()) {
                return;
            }

            CompoundTag tag = new CompoundTag();
            for (ServerDataProvider<BlockAccessor> provider : providers) {
                if (!payload.dataProviders().contains(provider)) {
                    continue;
                }
                try {
                    provider.appendServerData(tag, accessor);
                } catch (Exception e) {
                    LeavesLogger.LOGGER.warning("Error while saving data for block " + accessor.getBlockState());
                }
            }
            org.leavesmc.leaves.util.NbtUtils.writeBlockPosToTag(pos, tag);
            tag.putString("BlockId", BuiltInRegistries.BLOCK.getKey(block).toString());

            ProtocolUtils.sendPayloadPacket(player, new ReceiveDataPayload(tag));
        });
    }

    @ProtocolHandler.ReloadServer
    public static void onServerReload() {
        rebuildShearableBlocks();
        for (ServerPlayer player : enabledPlayers) {
            ProtocolUtils.sendPayloadPacket(player, new ServerHandshakePayload(Collections.emptyMap(), shearableBlocks, blockDataProviders.mappedIds(), entityDataProviders.mappedIds()));
        }
    }

    private static void rebuildShearableBlocks() {
        try {
            shearableBlocks = Collections.unmodifiableList(LootTableMineableCollector.execute(
                MinecraftServer.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE),
                Items.SHEARS.getDefaultInstance()
            ));
        } catch (Throwable ignore) {
            shearableBlocks = List.of();
            LeavesLogger.LOGGER.severe("Failed to collect shearable blocks");
        }
    }

    @Override
    public boolean isActive() {
        return ProtocolSupport.jadeProtocol;
    }
}
