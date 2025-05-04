package org.dreeam.leaf.config.modules.misc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

import java.util.*;

public class RaytraceTracker extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".raytrace-entity-tracker";
    }

    @Experimental
    public static boolean enabled = false;

    public static int maxTraceDistance = 64;
    public static boolean skipMarkerArmorStand = false;
    public static int boundingBoxLimit = 20;
    public static int traceInterval = 50;
    public static List<String> skippedEntities = List.of("PLAYER");

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                *** EXPERIMENTAL FEATURE ***
                Raytrace Entity Tracker uses async ray-tracing to untrack entities players cannot see,
                which can reduce bandwidth usage significantly,
                especially in some massive entities in small area situations.
                Also it provides a way to guard against Entity ESP hacks.""",
            """
                *** 实验性功能 ***
                使用异步射线追踪来动态取消跟踪玩家看不见的实体,
                可以显著降低带宽使用,
                在实体数量多且密集的情况下效果明显.
                也提供了一种对抗 Entity ESP 作弊的方案.""");

        enabled = config.getBoolean(getBasePath() + ".enabled", enabled);
        maxTraceDistance = config.getInt(getBasePath() + ".max-trace-distance", maxTraceDistance, config.pickStringRegionBased(
            """
                The maximum distance to trace entities in blocks.""",
            """
                最大追踪实体距离, 单位: 方块."""));
        skipMarkerArmorStand = config.getBoolean(getBasePath() + ".skip-marker-armor-stand", skipMarkerArmorStand, config.pickStringRegionBased(
            """
                Whether to skip tracing entities with marker armor stand.""",
            """
                是否跳过追踪带标记盔甲架的实体."""));
        boundingBoxLimit = config.getInt(getBasePath() + ".bounding-box-limit", boundingBoxLimit, config.pickStringRegionBased(
            """
                The maximum size of bounding box to trace.
                Entities with bounding box larger than this value will be skipped.""",
            """
                碰撞箱大小限制,
                实体碰撞箱大于该值将被跳过."""));
        traceInterval = config.getInt(getBasePath() + ".trace-interval", traceInterval, config.pickStringRegionBased(
            """
                The interval between each trace in milliseconds.
                Lower value means more frequent trace.""",
            """
                追踪间隔(单位: 毫秒), 越小越频繁."""));
        skippedEntities = config.getList(getBasePath() + ".skipped-entities", skippedEntities, config.pickStringRegionBased(
            """
                The entities to skip tracing.""",
            """
                跳过追踪的实体."""));
    }

    @Override
    public void onPostLoaded() {
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            entityType.skipRaytraceCheck = false;
        }

        final String DEFAULT_PREFIX = ResourceLocation.DEFAULT_NAMESPACE + ResourceLocation.NAMESPACE_SEPARATOR;

        for (String name : skippedEntities) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            String typeId = lowerName.startsWith(DEFAULT_PREFIX) ? lowerName : DEFAULT_PREFIX + lowerName;

            EntityType.byString(typeId).ifPresentOrElse(entityType ->
                    entityType.skipRaytraceCheck = true,
                () -> LeafConfig.LOGGER.warn("Skip unknown entity {}, in {}", name, getBasePath() + ".skipped-entities")
            );
        }
    }
}
