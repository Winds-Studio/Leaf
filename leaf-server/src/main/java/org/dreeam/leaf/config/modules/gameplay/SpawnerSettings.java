package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class SpawnerSettings extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".spawner-settings";
    }

    // Global toggle
    public static boolean enabled = false;

    // Default values for spawner settings
    public static boolean lightLevelCheck = false;
    public static boolean spawnerMaxNearbyCheck = true;
    public static boolean checkForNearbyPlayers = true;
    public static boolean spawnerBlockChecks = false;
    public static boolean waterPreventSpawnCheck = false;
    public static boolean ignoreSpawnRules = false;

    public static int minSpawnDelay = 200;
    public static int maxSpawnDelay = 800;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "This section contains settings for mob spawner blocks.",
            "此部分包含刷怪笼生物生成的设置.");

        // Global toggle
        enabled = config.getBoolean(getBasePath() + ".enabled", enabled,
            config.pickStringRegionBased(
                "Enable custom spawner settings. Set to true to enable all features below.",
                "启用自定义刷怪笼设置. 设为 true 以启用以下所有功能."
            ));

        // Checks section
        config.addCommentRegionBased(getBasePath() + ".checks",
            "Various checks that can be enabled or disabled for spawner blocks.",
            "可以为刷怪笼启用或禁用的各种检查.");

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
                "Check if there are physical blocks obstructing the spawn location, or if custom spawn rules (isValidPosition) fail due to block conditions.",
                "检查是否有物理方块阻挡生成位置, 或自定义生成规则(isValidPosition)因方块条件失败."
            ));

        waterPreventSpawnCheck = config.getBoolean(getBasePath() + ".checks.water-prevent-spawn-check", waterPreventSpawnCheck,
            config.pickStringRegionBased(
                "Checks if there is water around that prevents spawning",
                "检查周围是否有水阻止生成"
            ));

        ignoreSpawnRules = config.getBoolean(getBasePath() + ".checks.ignore-spawn-rules", ignoreSpawnRules,
            config.pickStringRegionBased(
                "Ignore mob-specific spawn rules, like animals needing grass or specific biomes/blocks (does not affect light level or physical obstruction checks).",
                "忽略特定于生物的生成规则, 例如动物需要草方块或特定的生物群系/方块 (不影响光照等级或物理障碍物检查)."
            ));

        // Delay settings

        minSpawnDelay = config.getInt(getBasePath() + ".min-spawn-delay", minSpawnDelay,
            config.pickStringRegionBased(
                "Minimum delay (in ticks) between spawner spawns. Higher values slow down spawners.",
                "刷怪笼生成怪物之间的最小延迟 (以刻为单位). 较高的值会减缓刷怪笼的速度."
            ));

        maxSpawnDelay = config.getInt(getBasePath() + ".max-spawn-delay", maxSpawnDelay,
            config.pickStringRegionBased(
                "Maximum delay (in ticks) between spawner spawns. Higher values slow down spawners.",
                "刷怪笼生成怪物之间的最大延迟 (以刻为单位). 较高的值会减缓刷怪笼的速度."
            ));
    }
}
