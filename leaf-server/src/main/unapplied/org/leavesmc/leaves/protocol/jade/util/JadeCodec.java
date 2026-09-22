package org.leavesmc.leaves.protocol.jade.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

public class JadeCodec {
    public static final StreamCodec<ByteBuf, Object> PRIMITIVE_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull Object decode(@NotNull ByteBuf buf) {
            byte b = buf.readByte();
            if (b == 0) {
                return false;
            } else if (b == 1) {
                return true;
            } else if (b == 2) {
                return ByteBufCodecs.VAR_INT.decode(buf);
            } else if (b == 3) {
                return ByteBufCodecs.FLOAT.decode(buf);
            } else if (b == 4) {
                return ByteBufCodecs.STRING_UTF8.decode(buf);
            } else if (b > 20) {
                return b - 20;
            }
            throw new IllegalArgumentException("Unknown primitive type: " + b);
        }

        @Override
        public void encode(@NotNull ByteBuf buf, @NotNull Object o) {
            switch (o) {
                case Boolean b -> buf.writeByte(b ? 1 : 0);
                case Number n -> {
                    float f = n.floatValue();
                    if (f != (int) f) {
                        buf.writeByte(3);
                        ByteBufCodecs.FLOAT.encode(buf, f);
                    }
                    int i = n.intValue();
                    if (i <= Byte.MAX_VALUE - 20 && i >= 0) {
                        buf.writeByte(i + 20);
                    } else {
                        ByteBufCodecs.VAR_INT.encode(buf, i);
                    }
                }
                case String s -> {
                    buf.writeByte(4);
                    ByteBufCodecs.STRING_UTF8.encode(buf, s);
                }
                case Enum<?> anEnum -> {
                    buf.writeByte(4);
                    ByteBufCodecs.STRING_UTF8.encode(buf, anEnum.name());
                }
                case null -> throw new NullPointerException();
                default -> throw new IllegalArgumentException("Unknown primitive type: %s (%s)".formatted(o, o.getClass()));
            }
        }
    };
}
