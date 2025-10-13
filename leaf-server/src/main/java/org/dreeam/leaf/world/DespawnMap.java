package org.dreeam.leaf.world;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.util.KDTree3D;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class DespawnMap implements Consumer<Entity> {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private final KDTree3D tree = new KDTree3D();
    private static final MobCategory[] CATEGORIES = MobCategory.values();
    private final double[] hard = new double[CATEGORIES.length];
    private final double[] sort = new double[CATEGORIES.length];
    private final boolean fallback;
    public boolean difficultyIsPeaceful = true;
    private ServerPlayer[] players = EMPTY_PLAYERS;

    public DespawnMap(WorldConfiguration worldConfiguration) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            sort[i] = CATEGORIES[i].getNoDespawnDistance();
            hard[i] = CATEGORIES[i].getDespawnDistance();
        }
        boolean fallback = false;
        for (Map.Entry<MobCategory, WorldConfiguration.Entities.Spawning.DespawnRangePair> e : worldConfiguration.entities.spawning.despawnRanges.entrySet()) {
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
        }
        this.fallback = fallback;
    }

    public void tick(final ServerLevel world, final EntityTickList entityTickList) {
        players = world.players().toArray(EMPTY_PLAYERS);
        final double[] pxl = new double[players.length];
        final double[] pyl = new double[players.length];
        final double[] pzl = new double[players.length];
        int i = 0;
        for (final ServerPlayer p : players) {
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
            entityTickList.forEach(entity -> entity.leaf$checkDespawnFallback(this));
        } else {
            entityTickList.forEach(this);
        }
        players = EMPTY_PLAYERS;
    }

    public void checkDespawn(final Mob mob) {
        final int i = mob.getType().getCategory().ordinal();
        final double hardDist = this.hard[i];
        final Vec3 vec3 = mob.position;
        final double dist = this.tree.nearestSqr(vec3.x, vec3.y, vec3.z, hardDist);
        if (dist == Double.POSITIVE_INFINITY) {
            return;
        }

        if (dist >= hardDist && mob.removeWhenFarAway(dist)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (dist > this.sort[i]) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(dist)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }

    public ServerPlayer checkDespawnFallback(final Mob mob) {
        final Vec3 vec3 = mob.position;
        final int i = tree.nearestIdx(vec3.x, vec3.y, vec3.z, Double.POSITIVE_INFINITY);
        return i == -1 ? null : this.players[i];
    }

    @Override
    public void accept(final Entity entity) {
        entity.leaf$checkDespawn(this);
    }
}
