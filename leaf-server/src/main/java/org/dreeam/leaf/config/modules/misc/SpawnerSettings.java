package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SpawnerSettings extends ConfigModules {
    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".spawner-settings";
    }

    // Global toggle
    public static boolean enabled = false;

    // Default values for spawner settings
    public static boolean lightLevelCheck = false;
    public static boolean spawnerMaxNearbyCheck = true;
    public static boolean checkForNearbyPlayers = true;
    public static boolean spawnerBlockChecks = false;
    public static boolean waterPreventSpawnCheck = false;

    public static int minSpawnDelay = 400;
    public static int maxSpawnDelay = 2000;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "This section contains settings for mob spawner blocks.",
            "此部分包含怪物刷怪箱方块的设置.");

        // Global toggle
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled,
            config.pickStringRegionBased(
                "Enable custom spawner settings. Set to true to enable all features below.",
                "启用自定义刷怪箱设置。设为true以启用以下所有功能。"
            ));

        // Checks section
        config.addCommentRegionBased(getBasePath() + ".checks",
            "Various checks that can be enabled or disabled for spawner blocks.",
            "可以为刷怪箱启用或禁用的各种检查.");

        lightLevelCheck = config.getBoolean(getBasePath() + ".checks.light-level-check", lightLevelCheck,
            config.pickStringRegionBased(
                "Check if there is the required light level to spawn the mob",
                "检查是否有所需的光照等级来生成怪物"
            ));

        spawnerMaxNearbyCheck = config.getBoolean(getBasePath() + ".checks.spawner-max-nearby-check", spawnerMaxNearbyCheck,
            config.pickStringRegionBased(
                "Check if there are the max amount of nearby mobs to spawn the mob",
                "检查附近是否已达到最大怪物数量限制"
            ));

        checkForNearbyPlayers = config.getBoolean(getBasePath() + ".checks.check-for-nearby-players", checkForNearbyPlayers,
            config.pickStringRegionBased(
                "Check if any players are in a radius to spawn the mob",
                "检查是否有玩家在生成怪物的半径范围内"
            ));

        spawnerBlockChecks = config.getBoolean(getBasePath() + ".checks.spawner-block-checks", spawnerBlockChecks,
            config.pickStringRegionBased(
                "Check if there are blocks blocking the spawner to spawn the mob",
                "检查是否有方块阻挡刷怪箱生成怪物"
            ));

        waterPreventSpawnCheck = config.getBoolean(getBasePath() + ".checks.water-prevent-spawn-check", waterPreventSpawnCheck,
            config.pickStringRegionBased(
                "Checks if there is water around that prevents spawning",
                "检查周围是否有水阻止生成"
            ));

        // Delay settings
        config.addCommentRegionBased(getBasePath() + ".min-spawn-delay",
            "Minimum delay (in ticks) between spawner spawns. Higher values slow down spawners.",
            "刷怪箱生成怪物之间的最小延迟（以刻为单位）。较高的值会减缓刷怪箱的速度。");

        minSpawnDelay = config.getInt(getBasePath() + ".min-spawn-delay", minSpawnDelay);

        config.addCommentRegionBased(getBasePath() + ".max-spawn-delay",
            "Maximum delay (in ticks) between spawner spawns. Higher values slow down spawners.",
            "刷怪箱生成怪物之间的最大延迟（以刻为单位）。较高的值会减缓刷怪箱的速度。");

        maxSpawnDelay = config.getInt(getBasePath() + ".max-spawn-delay", maxSpawnDelay);
    }
}
