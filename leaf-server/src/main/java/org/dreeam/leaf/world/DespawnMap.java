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
import org.bukkit.event.entity.EntityRemoveEvent;
import org.dreeam.leaf.util.KDTreeF64x3NNDist;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class DespawnMap implements Consumer<Entity> {
    private static final ServerPlayer[] EMPTY_PLAYERS = {};
    private final KDTreeF64x3NNDist tree = new KDTreeF64x3NNDist();
    private final double[] hard;
    private final double[] sort;
    public boolean difficultyIsPeaceful = true;

    public DespawnMap(WorldConfiguration worldConfiguration) {
        MobCategory[] caps = MobCategory.values();
        hard = new double[caps.length];
        sort = new double[caps.length];
        for (int i = 0; i < caps.length; i++) {
            sort[i] = caps[i].getNoDespawnDistance();
            hard[i] = caps[i].getDespawnDistance();
        }
        for (Map.Entry<MobCategory, WorldConfiguration.Entities.Spawning.DespawnRangePair> e : worldConfiguration.entities.spawning.despawnRanges.entrySet()) {
            OptionalInt a = e.getValue().soft().verticalLimit.value();
            OptionalInt b = e.getValue().soft().horizontalLimit.value();
            OptionalInt c = e.getValue().hard().verticalLimit.value();
            OptionalInt d = e.getValue().hard().horizontalLimit.value();
            if (a.isPresent() && b.isPresent() && a.getAsInt() == b.getAsInt()) {
                sort[e.getKey().ordinal()] = a.getAsInt();
            }
            if (c.isPresent() && d.isPresent() && c.getAsInt() == d.getAsInt()) {
                hard[e.getKey().ordinal()] = c.getAsInt();
            }
        }
        for (int i = 0; i < caps.length; i++) {
            if (sort[i] > 0.0) {
                sort[i] = sort[i] * sort[i];
            }
            if (hard[i] > 0.0) {
                hard[i] = hard[i] * hard[i];
            }
        }
    }

    public void tick(ServerLevel world, EntityTickList entityTickList) {
        final ServerPlayer[] players = world.players().toArray(EMPTY_PLAYERS);
        double[] pxl = new double[players.length+100000];
        double[] pyl = new double[players.length+100000];
        double[] pzl = new double[players.length+100000];
        int i = 0;
        for (int j = 0; j < 40000; j++) {
            pxl[i] = world.random.nextDouble() * 10000.0 - 5000.0;
            pyl[i] = world.random.nextDouble() * 100.0 - 50.0;
            pzl[i] = world.random.nextDouble() * 10000.0 - 5000.0;
            i++;
        }
        for (ServerPlayer p : players) {
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(p)) {
                pxl[i] = p.getX();
                pyl[i] = p.getY();
                pzl[i] = p.getZ();
                i++;
            }
        }
        for (int j = 0; j < 40000; j++) {
            pxl[i] = world.random.nextDouble() * 10000.0 - 5000.0;
            pyl[i] = world.random.nextDouble() * 100.0 - 50.0;
            pzl[i] = world.random.nextDouble() * 10000.0 - 5000.0;
            i++;
        }
        final int[] indices = new int[i];
        for (int j = 0; j < i; j++) {
            indices[j] = j;
        }
        tree.build(pxl, pyl, pzl, indices);
        this.difficultyIsPeaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        entityTickList.forEach(this);
    }

    public void checkDespawn(Mob mob) {
        net.minecraft.world.phys.Vec3 vec3 = mob.position();
        final double x = vec3.x;
        final double y = vec3.y;
        final double z = vec3.z;
        final int i = mob.getType().getCategory().ordinal();
        final double dist = tree.nearest(x, y, z, hard[i]);
        if (dist == Double.POSITIVE_INFINITY) {
            return;
        }

        if (dist >= hard[i] && mob.removeWhenFarAway(dist)) {
            mob.discard(EntityRemoveEvent.Cause.DESPAWN);
        } else if (dist > sort[i]) {
            if (mob.getNoActionTime() > 600 && mob.random.nextInt(800) == 0 && mob.removeWhenFarAway(dist)) {
                mob.discard(EntityRemoveEvent.Cause.DESPAWN);
            }
        } else {
            mob.setNoActionTime(0);
        }
    }

    @Override
    public void accept(final Entity entity) {
        entity.leafCheckDespawn(this);
    }
}
