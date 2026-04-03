package org.dreeam.leaf.async.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.dreeam.leaf.util.queue.MpmcQueue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

@NullMarked
public final class AsyncChunkSender {

    private static final int CAPACITY = 255;

    private final MpmcQueue<ClientboundLevelChunkWithLightPacket> channel;
    private final LongOpenHashSet pending;
    private int size = 0;

    public AsyncChunkSender() {
        this.channel = new MpmcQueue<>(ClientboundLevelChunkWithLightPacket.class, CAPACITY);
        this.pending = new LongOpenHashSet();
    }

    public boolean add(long k) {
        return size < CAPACITY && pending.size() < CAPACITY && pending.add(k);
    }

    public boolean remove(long k) {
        return pending.remove(k);
    }

    public boolean contains(long k) {
        return pending.contains(k);
    }

    public void clear() {
        pending.clear();
        while (recv() != null) ;
    }

    public void submit(Supplier<ClientboundLevelChunkWithLightPacket> task) {
        size++;
        AsyncChunkSend.POOL.submit(() -> {
            ClientboundLevelChunkWithLightPacket chunk = task.get();
            while (!channel.send(chunk)) ;
        });
    }

    public @Nullable ClientboundLevelChunkWithLightPacket recv() {
        size--;
        return this.channel.recv();
    }
}
