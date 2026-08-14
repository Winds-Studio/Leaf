package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class LocatorBarRange extends ConfigModule {

    public String basePath() {
        return ConfigCategory.GAMEPLAY.basePath();
    }

    public static double maxDistance = 0.0D;

    @Override
    public void onLoaded() {
        maxDistance = globalConfig.getDouble(basePath() + ".locator-bar-max-distance", maxDistance, globalConfig.pickStringRegionBased("""
                Maximum distance in blocks at which players show up on each other's
                locator bar. Vanilla derives this from the waypoint_transmit_range and
                waypoint_receive_range attributes, which default to 6.0E7 blocks, so
                effectively every player in the dimension is transmitted.

                Lowering this cuts the number of waypoint connections and the packets
                they produce, which matters most on servers with many players in one
                dimension. 100 is a reasonable starting point.

                Set to 0 to keep vanilla behaviour. This never raises the range beyond
                what the attributes allow, so plugins lowering those still win.""",
            """
                玩家在彼此定位栏上显示的最大距离 (单位: 方块).
                原版通过 waypoint_transmit_range 和 waypoint_receive_range 属性决定该值,
                其默认值为 6.0E7 方块, 因此实际上同维度内的所有玩家都会被传输.

                降低此值可减少路径点连接数量及其产生的数据包,
                在单一维度内玩家较多时效果最明显. 100 是一个合理的起点.

                设置为 0 保持原版行为. 此选项不会超出属性允许的范围,
                因此插件调低属性时仍以插件为准."""));
    }
}
