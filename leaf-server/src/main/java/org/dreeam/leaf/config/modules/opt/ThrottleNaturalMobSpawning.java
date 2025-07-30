package org.dreeam.leaf.config.modules.opt;

import net.minecraft.world.entity.MobCategory;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ThrottleNaturalMobSpawning extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".throttle-mob-spawning";
    }

    public static boolean enabled = false;
    public static long[] failedAttempts;
    public static int[] spawnChance;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
            Skip mob spawning for chunks with repeated failures exceeding `min-failed`.
            Randomly skip 1-`spawn-chance`% of these chunks from spawning attempts.
            Failure counter does not increment when spawn limits are reached.""",
            """
            跳过区块中重复失败次数超过 `min-failed` 的生物生成.
            随机跳过这些区块中 1-`spawn-chance`% 的生物生成尝试.
            达到生成限制时, 失败计数器不会增加.""");
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        MobCategory[] categories = MobCategory.values();
        failedAttempts = new long[categories.length];
        spawnChance = new int[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String category = getBasePath() + "." + categories[i].getSerializedName();
            long attempts = config.getLong(category + ".min-failed", 8);
            double chance = config.getDouble(category + ".spawn-chance", 25.0);

            failedAttempts[i] = Math.max(-1, attempts);
            chance = Math.clamp(chance, 0.0, 100.0) / 100.0;
            spawnChance[i] = Math.toIntExact(Math.round((chance * Integer.MAX_VALUE)));
        }
    }
}
