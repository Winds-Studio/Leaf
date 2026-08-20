package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.GAMEPLAY, name = "knockback")
public class Knockback implements ConfigModule {

    @ConfigInfo(name = "snowball-knockback-players", comments = {
        "Make snowball can knockback players.",
        "使雪球可以击退玩家."
    })
    public static boolean snowballCanKnockback = false;

    @ConfigInfo(name = "egg-knockback-players", comments = {
        "Make egg can knockback players.",
        "使鸡蛋可以击退玩家."
    })
    public static boolean eggCanKnockback = false;

    @ConfigInfo(name = "can-player-knockback-zombie", comments = {
        "Make players can knockback zombie.",
        "使玩家可以击退僵尸."
    })
    public static boolean canPlayerKnockbackZombie = true;

    @ConfigInfo(name = "flush-location-while-knockback-player", comments = {
        "Synchronize player immediately when knocked back.",
        "被击退时立即同步玩家."
    })
    public static @Experimental boolean flushKnockback = false;

    @ConfigInfo(name = "old-blast-protection-explosion-knockback")
    public static boolean oldBlastProtectionKnockbackBehavior = false;
}
