package org.dreeam.leaf.config.modules.opt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "dab", comments = {
    """
        Optimizes entity brains when
        they're far away from the player""",
    """
        根据距离动态优化生物 AI"""
})
public class DynamicActivationofBrain implements ConfigModule {

    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;

    @ConfigInfo(name = "dont-enable-if-in-water", comments = {
        """
            After enabling this, non-aquatic entities in the water will not be affected by DAB.
            This could fix entities suffocate in the water.""",
        """
            启用此项后, 在水中的非水生生物将不会被 DAB 影响.
            可以避免距离玩家较远的生物在水里淹死."""
    })
    public static boolean dontEnableIfInWater = false;

    @ConfigInfo(name = "start-distance", comments = {
        """
            This value determines how far away an entity has to be
            from the player to start being effected by DEAR.""",
        """
            生物距离玩家多少格 DAB 开始生效"""
    })
    public static double startDistance = 12.0;

    @ConfigInfo(name = "max-tick-freq", comments = {
        """
            This value defines how often in ticks, the furthest entity
            will get their pathfinders and behaviors ticked. 20 = 1s""",
        """
            最远处的实体每隔多少刻tick一次"""
    })
    public static int maximumActivationPrio = 20;

    @ConfigInfo(name = "activation-dist-mod", comments = {
        """
            This value defines how much distance modifies an entity's
            tick frequency. freq = (distanceToPlayer^2) / (2^value)",
            If you want further away entities to tick less often, use 7.
            If you want further away entities to tick more often, try 9."""
    })
    public static double activationDistanceMod = 8.0;

    @ConfigInfo(name = "blacklisted-entities", comments = {
        "A list of entities to ignore for activation",
        "不会被 DAB 影响的实体列表"
    })
    public static List<String> blackedEntities = new ArrayList<>(Arrays.asList(
        "villager",
        "axolotl",
        "hoglin",
        "zombified_piglin",
        "goat"
    ));

    public static @DoNotLoad double startDistanceSquared;

    @Override
    public void onLoaded() {
        startDistanceSquared = startDistance * startDistance;
    }

    @Override
    public void onRegistriesLoaded() {
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            entityType.dabEnabled = true; // reset all, before setting the ones to true
        }

        final String DEFAULT_PREFIX = Identifier.DEFAULT_NAMESPACE + Identifier.NAMESPACE_SEPARATOR;

        for (String name : blackedEntities) {
            // Be compatible with both `minecraft:example` and `example` syntax
            // If unknown, show user config value in the logger instead of parsed result
            String lowerName = name.toLowerCase(Locale.ROOT);
            String typeId = lowerName.startsWith(DEFAULT_PREFIX) ? lowerName : DEFAULT_PREFIX + lowerName;

            EntityType.byString(typeId).ifPresentOrElse(entityType ->
                    entityType.dabEnabled = false,
                () -> LeafConfig.LOGGER.warn("Skip unknown entity {}, in performance.dab.blacklisted-entities", name)
            );
        }
    }
}
