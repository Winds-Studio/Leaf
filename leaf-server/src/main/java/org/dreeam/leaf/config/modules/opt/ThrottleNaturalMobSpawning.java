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
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        MobCategory[] categories = MobCategory.values();
        failedAttempts = new long[categories.length];
        spawnChance = new int[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String category = getBasePath() + "." + categories[i].getSerializedName();
            long attempts = config.getLong(category + ".min-failed", 8);
            double chance = config.getDouble(category + ".spawn-chance", 25.0);

            failedAttempts[i] = Math.max(-1, attempts);
            spawnChance[i] = Math.clamp(0, (int) Math.round(chance * 10.24), 1024);
        }
    }
}
