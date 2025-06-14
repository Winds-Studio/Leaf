package org.dreeam.leaf.async.container;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import org.dreeam.leaf.config.modules.async.AsyncContainerBroadcast;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncContainerBroadcaster {

    public static ExecutorService CONTAINER_POOL = null;
    public static final Logger LOGGER = LogManager.getLogger("Leaf Async Container Broadcast");
    private static final ThreadLocal<List<Runnable>> TASK_POOL = ThreadLocal.withInitial(() -> new ArrayList<>(16));

    public static void init() {
        if (CONTAINER_POOL != null) {
            CONTAINER_POOL.shutdown();
        }

        CONTAINER_POOL = new ThreadPoolExecutor(
            AsyncContainerBroadcast.minThreads,
            AsyncContainerBroadcast.maxThreads,
            AsyncContainerBroadcast.keepalive, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder()
                .setPriority(Thread.NORM_PRIORITY)
                .setNameFormat("Leaf Async Container Broadcast Thread-%d")
                .setUncaughtExceptionHandler(Util::onThreadException)
                .setThreadFactory(AsyncContainerBroadcastThread::new)
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        LOGGER.info("Initialized Async Container Broadcast with {}-{} threads",
            AsyncContainerBroadcast.minThreads, AsyncContainerBroadcast.maxThreads);
    }

    public static void executeAsync(List<Runnable> tasks) {
        if (CONTAINER_POOL != null && !tasks.isEmpty()) {
            CONTAINER_POOL.execute(() -> {
                for (Runnable task : tasks) {
                    task.run();
                }
            });
            tasks.clear();
        }
    }

    public static List<Runnable> getTasks() {
        List<Runnable> tasks = TASK_POOL.get();
        tasks.clear();
        return tasks;
    }

    public static void addSlotSyncTask(List<Runnable> tasks, ContainerSynchronizer synchronizer,
            AbstractContainerMenu menu, int slotIndex, ItemStack item) {
        if (synchronizer == null) return;

        final ItemStack asyncItemCopy = item.copy();
        final int containerId = menu.containerId;
        final int stateId = menu.incrementStateId();

        tasks.add(() -> {
            ClientboundContainerSetSlotPacket packet = new ClientboundContainerSetSlotPacket(
                    containerId, stateId, slotIndex, asyncItemCopy);
            sendPacket(menu, packet);
        });
    }

    public static void addDataSyncTask(List<Runnable> tasks, ContainerSynchronizer synchronizer,
            AbstractContainerMenu menu, int dataIndex, int value) {
        if (synchronizer == null) return;

        final int containerId = menu.containerId;

        tasks.add(() -> {
            ClientboundContainerSetDataPacket packet = new ClientboundContainerSetDataPacket(
                    containerId, dataIndex, value);
            sendPacket(menu, packet);
        });
    }

    private static void sendPacket(AbstractContainerMenu menu, Object packet) {
        for (var listener : menu.containerListeners) {
            if (listener instanceof ServerPlayer player) {
                Connection connection = player.connection.connection;
                if (connection.channel != null && connection.channel.isActive() &&
                    (!AsyncContainerBroadcast.enableBackpressureHandling || connection.channel.isWritable())) {

                    connection.channel.eventLoop().execute(() -> {
                        try {
                            if (connection.channel.isActive()) {
                                connection.send((net.minecraft.network.protocol.Packet<?>) packet);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to send packet to {}: {}", player.getName().getString(), e.getMessage());
                        }
                    });
                }
            }
        }
    }
}
