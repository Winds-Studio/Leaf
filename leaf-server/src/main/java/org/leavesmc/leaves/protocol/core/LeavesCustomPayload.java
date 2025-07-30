package org.leavesmc.leaves.protocol.core;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface LeavesCustomPayload extends CustomPacketPayload {

    Type<? extends CustomPacketPayload> LEAVES_TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("leaves", "custom_payload"));

    @Override
    default @NotNull Type<? extends CustomPacketPayload> type() {
        return LEAVES_TYPE;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ID {
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Codec {
    }
}
