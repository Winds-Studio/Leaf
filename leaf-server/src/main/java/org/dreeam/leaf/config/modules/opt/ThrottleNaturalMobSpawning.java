package org.dreeam.leaf.config.modules.opt;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "throttle-mob-spawning", comments = {
    "Skip mob spawning for chunks with repeated failures at least `min-failed`.\nValid range for `spawn-chance` is 0.0 to 100.0.\nFailure counter does not increment when reach spawn limits.",
    "跳过区块中重复失败次数至少达到 `min-failed` 的生物生成.\n`spawn-chance` 的有效范围为 0.0 到 100.0.\n达到生成限制时, 失败计数器不会增加."
})
public class ThrottleNaturalMobSpawning implements ConfigModule {

    @HotReloadUnsupported
    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;
    @DoNotLoad
    public static long[] failedAttempts;
    @DoNotLoad
    public static int[] spawnChance;

    @Override
    public void onLoaded() {
        MobCategory[] categories = NaturalSpawner.SPAWNING_CATEGORIES;
        failedAttempts = new long[categories.length];
        spawnChance = new int[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String category = "performance.throttle-mob-spawning." + categories[i].getSerializedName();
            long attempts = LeafConfig.globalConfig().getLong(category + ".min-failed", 8);
            double chance = LeafConfig.globalConfig().getDouble(category + ".spawn-chance", 25.0);

            failedAttempts[i] = Math.max(-1, attempts);
            chance = Math.clamp(chance, 0.0, 100.0) / 100.0;
            spawnChance[i] = Math.toIntExact(Math.round((chance * Integer.MAX_VALUE)));
        }
    }
}
