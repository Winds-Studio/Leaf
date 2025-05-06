package org.dreeam.leaf.protocol;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public interface LeafCustomPayload extends CustomPacketPayload {
    @NotNull
    Type<? extends LeafCustomPayload> type();
}
