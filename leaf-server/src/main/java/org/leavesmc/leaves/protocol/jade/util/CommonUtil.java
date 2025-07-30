package org.leavesmc.leaves.protocol.jade.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.protocol.jade.accessor.Accessor;
import org.leavesmc.leaves.protocol.jade.provider.IServerExtensionProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CommonUtil {

    public static Entity wrapPartEntityParent(Entity target) {
        if (target instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return target;
    }

    public static Entity getPartEntity(Entity parent, int index) {
        if (parent == null) {
            return null;
        }
        if (index < 0) {
            return parent;
        }
        if (parent instanceof EnderDragon dragon) {
            EnderDragonPart[] parts = dragon.getSubEntities();
            if (index < parts.length) {
                return parts[index];
            }
        }
        return parent;
    }


    @Nullable
    public static String getLastKnownUsername(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Optional<GameProfile> optional = SkullBlockEntity.fetchGameProfile(String.valueOf(uuid)).getNow(Optional.empty());
        return optional.map(GameProfile::getName).orElse(null);
    }


    public static <T> Map.Entry<ResourceLocation, List<ViewGroup<T>>> getServerExtensionData(
        Accessor<?> accessor,
        WrappedHierarchyLookup<IServerExtensionProvider<T>> lookup) {
        for (var provider : lookup.wrappedGet(accessor)) {
            List<ViewGroup<T>> groups;
            try {
                groups = provider.getGroups(accessor);
            } catch (Exception e) {
                LeavesLogger.LOGGER.severe(e.toString());
                continue;
            }
            if (groups != null) {
                return Map.entry(provider.getUid(), groups);
            }
        }
        return null;
    }
}
