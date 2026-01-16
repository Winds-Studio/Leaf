package org.leavesmc.leaves.protocol.core;

import net.minecraft.server.level.ServerPlayer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class ProtocolHandler {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Init {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PayloadReceiver {
        Class<? extends LeavesCustomPayload> payload();

        Stage stage() default Stage.GAME;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BytebufReceiver {
        String key() default "";

        boolean onlyNamespace() default false;

        Stage stage() default Stage.GAME;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Ticker {
        String tickerId() default "";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PlayerJoin {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PlayerLeave {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ReloadServer {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MinecraftRegister {
        String key() default "";

        boolean onlyNamespace() default false;

        Stage stage() default Stage.CONFIGURATION;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ReloadDataPack {
    }

    public enum Stage {
        CONFIGURATION(Context.class),
        GAME(ServerPlayer.class);

        private final Class<?> identifier;

        Stage(Class<?> identifier) {
            this.identifier = identifier;
        }

        public Class<?> identifier() {
            return identifier;
        }
    }
}