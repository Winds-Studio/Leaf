package org.leavesmc.leaves.protocol.jade.accessor;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class Accessor<T extends HitResult> {

    private final ServerLevel level;
    private final Player player;
    private final Supplier<T> hit;
    protected boolean verify;
    private RegistryFriendlyByteBuf buffer;

    public Accessor(ServerLevel level, Player player, Supplier<T> hit) {
        this.level = level;
        this.player = player;
        this.hit = hit;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    private RegistryFriendlyByteBuf buffer() {
        if (buffer == null) {
            buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        }
        buffer.clear();
        return buffer;
    }

    public <D> Tag encodeAsNbt(StreamEncoder<RegistryFriendlyByteBuf, D> streamCodec, D value) {
        RegistryFriendlyByteBuf buffer = buffer();
        streamCodec.encode(buffer, value);
        ByteArrayTag tag = new ByteArrayTag(ArrayUtils.subarray(buffer.array(), 0, buffer.readableBytes()));
        buffer.clear();
        return tag;
    }

    public T getHitResult() {
        return hit.get();
    }

    @Nullable
    public abstract Object getTarget();
}
