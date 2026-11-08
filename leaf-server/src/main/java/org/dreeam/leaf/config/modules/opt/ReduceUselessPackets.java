package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF, name = "reduce-packets")
public class ReduceUselessPackets implements ConfigModule {

    @ConfigInfo(name = "reduce-entity-move-packets")
    public static boolean reduceUselessEntityMovePackets = false;
    @ConfigInfo(name = "reduce-entity-motion-packets")
    public static boolean filterClientboundSetEntityMotionPacket = false;
    @ConfigInfo(name = "disable-useless-particles")
    public static boolean disableUselessParticles = false;
}
