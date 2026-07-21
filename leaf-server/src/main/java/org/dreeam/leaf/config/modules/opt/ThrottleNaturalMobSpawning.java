package org.dreeam.leaf.config.modules.opt;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
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
            Skip mob spawning for chunks with repeated failures at least `min-failed`.
            Valid range for `spawn-chance` is 0.0 to 100.0.
            Failure counter does not increment when reach spawn limits.""",
            """
            跳过区块中重复失败次数至少达到 `min-failed` 的生物生成.
            `spawn-chance` 的有效范围为 0.0 到 100.0.
            达到生成限制时, 失败计数器不会增加.""");
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        MobCategory[] categories = NaturalSpawner.SPAWNING_CATEGORIES;
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
