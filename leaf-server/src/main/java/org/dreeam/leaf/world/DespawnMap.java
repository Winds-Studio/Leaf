package org.dreeam.leaf.world;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.util.KDTree3D;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class DespawnMap implements Consumer<Entity> {

    private static final MobCategory[] CATEGORIES = MobCategory.values();
    private static final ServerPlayer[] EMPTY_PLAYERS = {};

    private final KDTree3D tree = new KDTree3D();
    private final double[] hard = new double[CATEGORIES.length];
    private final double[] sort = new double[CATEGORIES.length];
    private boolean difficultyIsPeaceful = true;

    public boolean tick(final ServerLevel world, final EntityTickList entityTickList) {
        if (!org.dreeam.leaf.config.modules.opt.OptimizeDespawn.enabled) {
            return false;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            sort[i] = CATEGORIES[i].getNoDespawnDistance();
            hard[i] = CATEGORIES[i].getDespawnDistance();
        }
        boolean fallback = false;
        for (Map.Entry<MobCategory, WorldConfiguration.Entities.Spawning.DespawnRangePair> e : world.paperConfig().entities.spawning.despawnRanges.entrySet()) {
            OptionalInt softVertical = e.getValue().soft().verticalLimit.value();
            OptionalInt softHorizontal = e.getValue().soft().horizontalLimit.value();
            OptionalInt hardVertical = e.getValue().hard().verticalLimit.value();
            OptionalInt hardHorizontal = e.getValue().hard().horizontalLimit.value();
            if (softVertical.isPresent() && softHorizontal.isPresent() && softVertical.getAsInt() == softHorizontal.getAsInt()) {
                sort[e.getKey().ordinal()] = softVertical.getAsInt();
            } else if (softVertical.isPresent() || softHorizontal.isPresent()) {
                fallback = true;
            }
            if (hardVertical.isPresent() && hardHorizontal.isPresent() && hardVertical.getAsInt() == hardHorizontal.getAsInt()) {
                hard[e.getKey().ordinal()] = hardVertical.getAsInt();
            } else if (hardVertical.isPresent() || hardHorizontal.isPresent()) {
                fallback = true;
            }
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (sort[i] > 0.0) {
                sort[i] = sort[i] * sort[i];
            }
            if (hard[i] > 0.0) {
                hard[i] = hard[i] * hard[i];
            }
            if (sort[i] > hard[i]) {
                sort[i] = hard[i];
            }
        }
        ServerPlayer[] players = world.players().toArray(EMPTY_PLAYERS);
        final double[] pxl = new double[players.length];
        final double[] pyl = new double[players.length];
        final double[] pzl = new double[players.length];
        int i = 0;
        for (ServerPlayer p : players) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(p)) {
                pxl[i] = p.getX();
                pyl[i] = p.getY();
                pzl[i] = p.getZ();
                i++;
            }
        }
        final int[] indices = new int[i];
        for (int j = 0; j < i; j++) {
            indices[j] = j;
        }
        tree.build(new double[][]{pxl, pyl, pzl}, indices);
        this.difficultyIsPeaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        if (fallback) {
            return false;
        } else {
            entityTickList.forEach(this);
            return true;
        }
    }

    private boolean checkDespawn(final Entity entity) {
        if (!(entity instanceof Mob mob)) {
            // ShulkerBullet#checkDespawn
            return entity instanceof ShulkerBullet && difficultyIsPeaceful;
        }

        // EnderDragon#checkDespawn nop
        if (mob instanceof EnderDragon) {
            return false;
        }

        // WitherBoss#checkDespawn
        if (mob instanceof WitherBoss) {
            if (difficultyIsPeaceful && mob.shouldDespawnInPeaceful()) {
                return true;
            }
            mob.noActionTime = 0;
            return false;
        }

        // Mob#checkDespawn

        if (difficultyIsPeaceful && mob.shouldDespawnInPeaceful()) {
            return true;
        }

        if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
            mob.noActionTime = 0;
            return false;
        }

        final int category = mob.getType().getCategory().ordinal();
        final double hardDist = this.hard[category];
        final Vec3 pos = mob.position;
        final double dist = this.tree.nearestSqr(pos.x, pos.y, pos.z, hardDist);

        if (dist == Double.POSITIVE_INFINITY) {
            return false;
        }

        if (dist >= hardDist) {
            return mob.removeWhenFarAway(dist);
        }

        if (dist > this.sort[category]) {
            return mob.noActionTime > 600
                && mob.random.nextInt(800) == 0
                && mob.removeWhenFarAway(dist);
        }

        mob.noActionTime = 0;
        return false;
    }

    @Override
    public void accept(final Entity entity) {
        if (!entity.isRemoved() && checkDespawn(entity)) {
            entity.discard(EntityRemoveEvent.Cause.DESPAWN);
        }
    }
}
