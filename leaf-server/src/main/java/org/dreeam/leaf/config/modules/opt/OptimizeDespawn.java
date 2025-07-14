package org.dreeam.leaf.config.modules.opt;

import net.minecraft.world.entity.MobCategory;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.annotations.Experimental;

public class OptimizeDespawn extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".mob-despawn";
    }

    @Experimental
    public static boolean enabled = false;
    public static double[] hard = {};
    public static double[] sort = {};

    @Override
    public void onLoaded() {
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        String base = getBasePath() + ".despawn-ranges.";
        MobCategory[] caps = MobCategory.values();
        hard = new double[caps.length];
        sort = new double[caps.length];
        for (int i = 0; i < caps.length; i++) {
            MobCategory value = caps[i];
            sort[i] = config.getDouble(base + value.getSerializedName() + ".sort", 0.0);
            hard[i] = config.getDouble(base + value.getSerializedName() + ".hard", 0.0);
        }
        for (int i = 0; i < caps.length; i++) {
            if (sort[i] == 0.0) {
                sort[i] = caps[i].getNoDespawnDistance();
            }
            if (hard[i] == 0.0) {
                hard[i] = caps[i].getDespawnDistance();
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
}
