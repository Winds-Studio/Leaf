package dev.tr7zw.entityculling.versionless.access;

import net.minecraft.world.entity.player.Player;

public interface Cullable {

    void setTimeout();

    boolean isForcedVisible();

    void setCulled(boolean value, Player player);

    boolean isCulled(Player player);

}
