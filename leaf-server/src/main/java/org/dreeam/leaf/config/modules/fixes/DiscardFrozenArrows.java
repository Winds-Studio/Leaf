package org.dreeam.leaf.config.modules.fixes;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

import java.util.Locale;

public class DiscardFrozenArrows extends ConfigModule {

    public enum Mode {
        OFF,
        UNPICKABLE,
        UNPICKABLE_AND_IN_FLIGHT
    }

    public static Mode mode = Mode.OFF;

    public String basePath() {
        return ConfigCategory.FIXES.basePath() + ".discard-frozen-arrows";
    }

    @Override
    public void onLoaded() {
        String configured = globalConfig.getString(basePath(), "off", globalConfig.pickStringRegionBased("""
                Discards arrows that freeze outside the entity ticking area instead of saving them with the chunk.
                off: disabled.
                unpickable: discard arrows that survival players cannot pick up (Infinity, skeleton or creative arrows).
                unpickable-and-in-flight: also discard pickable arrows that are still in flight, except arrows without physics.
                Tridents and pickable arrows stuck in blocks are never discarded.""",
            """
                移除在实体刷新区域外被冻结的箭矢，而不是随区块保存。
                off: 禁用。
                unpickable: 移除生存模式玩家无法拾取的箭矢 (无限附魔、骷髅或创造模式射出的箭矢)。
                unpickable-and-in-flight: 同时移除仍在飞行中的可拾取箭矢, 无物理效果的箭矢除外。
                三叉戟和插在方块上的可拾取箭矢永远不会被移除。"""));
        mode = switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> Mode.OFF;
            case "unpickable" -> Mode.UNPICKABLE;
            case "unpickable-and-in-flight" -> Mode.UNPICKABLE_AND_IN_FLIGHT;
            default -> {
                LOGGER.warn("Unknown discard-frozen-arrows mode '{}', using off. Expected off, unpickable, or unpickable-and-in-flight.", configured);
                yield Mode.OFF;
            }
        };
    }
}
