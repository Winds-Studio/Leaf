package org.dreeam.leaf.config.modules.opt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DynamicActivationofBrain extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.PERF.getBaseKeyName() + ".dab";
    }

    public static boolean enabled = true;
    public static int startDistance = 12;
    public static int startDistanceSquared;
    public static int maximumActivationPrio = 20;
    public static int activationDistanceMod = 8;
    public static boolean dontEnableIfInWater = false;
    public static List<String> blackedEntities = new ArrayList<>();

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                Optimizes entity brains when
                they're far away from the player""",
            """
                根据距离动态优化生物 AI""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        dontEnableIfInWater = config.getBoolean(getBasePath() + ".dont-enable-if-in-water", dontEnableIfInWater, config.pickStringRegionBased("""
                After enabling this, non-aquatic entities in the water will not be affected by DAB.
                This could fix entities suffocate in the water.""",
            """
                启用此项后, 在水中的非水生生物将不会被 DAB 影响.
                可以避免距离玩家较远的生物在水里淹死."""));
        startDistance = config.getInt(getBasePath() + ".start-distance", startDistance, config.pickStringRegionBased("""
                This value determines how far away an entity has to be
                from the player to start being effected by DEAR.""",
            """
                生物距离玩家多少格 DAB 开始生效"""));
        maximumActivationPrio = config.getInt(getBasePath() + ".max-tick-freq", maximumActivationPrio, config.pickStringRegionBased("""
                This value defines how often in ticks, the furthest entity
                will get their pathfinders and behaviors ticked. 20 = 1s""",
            """
                最远处的实体每隔多少刻tick一次"""));
        activationDistanceMod = config.getInt(getBasePath() + ".activation-dist-mod", activationDistanceMod, """
            This value defines how much distance modifies an entity's
            tick frequency. freq = (distanceToPlayer^2) / (2^value)",
            If you want further away entities to tick less often, use 7.
            If you want further away entities to tick more often, try 9.""");
        blackedEntities = config.getList(getBasePath() + ".blacklisted-entities", blackedEntities,
            config.pickStringRegionBased("A list of entities to ignore for activation",
                "不会被 DAB 影响的实体列表"));

        startDistanceSquared = startDistance * startDistance;
    }

    @Override
    public void onPostLoaded() {
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            entityType.dabEnabled = true; // reset all, before setting the ones to true
        }

        final String DEFAULT_PREFIX = ResourceLocation.DEFAULT_NAMESPACE + ResourceLocation.NAMESPACE_SEPARATOR;

        for (String name : blackedEntities) {
            // Be compatible with both `minecraft:example` and `example` syntax
            // If unknown, show user config value in the logger instead of parsed result
            String lowerName = name.toLowerCase(Locale.ROOT);
            String typeId = lowerName.startsWith(DEFAULT_PREFIX) ? lowerName : DEFAULT_PREFIX + lowerName;

            EntityType.byString(typeId).ifPresentOrElse(entityType ->
                    entityType.dabEnabled = false,
                () -> LeafConfig.LOGGER.warn("Skip unknown entity {}, in {}", name, getBasePath() + ".blacklisted-entities")
            );
        }
    }
}
