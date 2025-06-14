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
    private static final ThreadLocal<List<Object>> PACKET_BATCH = ThreadLocal.withInitial(() -> new ArrayList<>(8));
    private static final ThreadLocal<AbstractContainerMenu> CURRENT_MENU = new ThreadLocal<>();
    
    private static volatile long totalTasksProcessed = 0;
    private static volatile long totalPacketsSent = 0;
    private static volatile long backpressureSkips = 0;

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
            totalTasksProcessed += tasks.size();
            
            if (AsyncContainerBroadcast.enableBatchedSending) {
                CONTAINER_POOL.execute(() -> {
                    List<Object> packets = PACKET_BATCH.get();
                    AbstractContainerMenu currentMenu = null;
                    
                    for (Runnable task : tasks) {
                        if (task instanceof PacketTask packetTask) {
                            if (currentMenu != packetTask.menu) {
                                if (currentMenu != null && !packets.isEmpty()) {
                                    sendBatchedPackets(currentMenu, packets);
                                    packets.clear();
                                }
                                currentMenu = packetTask.menu;
                            }
                            packets.add(packetTask.packet);
                        } else {
                            task.run();
                        }
                    }
                    
                    if (currentMenu != null && !packets.isEmpty()) {
                        sendBatchedPackets(currentMenu, packets);
                    }
                    
                    packets.clear();
                });
            } else {
                CONTAINER_POOL.execute(() -> {
                    for (Runnable task : tasks) {
                        task.run();
                    }
                });
            }
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
        if (synchronizer != null) {
            final ItemStack asyncItemCopy = item.copy();
            final int containerId = menu.containerId;
            final int stateId = menu.incrementStateId();

            ClientboundContainerSetSlotPacket packet = new ClientboundContainerSetSlotPacket(
                    containerId, stateId, slotIndex, asyncItemCopy);

            tasks.add(new PacketTask(menu, packet, () -> {
                try {
                    menu.synchronizeSlotToRemote(slotIndex, asyncItemCopy, asyncItemCopy::copy);
                } catch (Exception e) {
                    LOGGER.error("Failed to send async slot change packet", e);
                }
            }));
        }
    }

    public static void addDataSyncTask(List<Runnable> tasks, ContainerSynchronizer synchronizer,
            AbstractContainerMenu menu, int dataIndex, int value) {
        if (synchronizer != null) {
            final int containerId = menu.containerId;

            ClientboundContainerSetDataPacket packet = new ClientboundContainerSetDataPacket(
                    containerId, dataIndex, value);

            tasks.add(new PacketTask(menu, packet, () -> {
                try {
                    menu.synchronizeDataSlotToRemote(dataIndex, value);
                } catch (Exception e) {
                    LOGGER.error("Failed to send async data change packet", e);
                }
            }));
        }
    }

    private static void sendPacketToContainerListeners(AbstractContainerMenu menu, Object packet) {
        List<net.minecraft.world.inventory.ContainerListener> listeners = menu.containerListeners;
        for (net.minecraft.world.inventory.ContainerListener listener : listeners) {
            if (listener instanceof ServerPlayer player) {
                Connection connection = player.connection.connection;
                if (connection.channel != null && connection.channel.isActive()) {
                    if (AsyncContainerBroadcast.enableBackpressureHandling && !connection.channel.isWritable()) {
                        backpressureSkips++;
                        LOGGER.debug("Channel not writable for player {}, skipping container update", player.getName().getString());
                        continue;
                    }
                    
                    totalPacketsSent++;
                    connection.channel.eventLoop().execute(() -> {
                        try {
                            if (connection.channel.isActive()) {
                                connection.send((net.minecraft.network.protocol.Packet<?>) packet);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to send packet to player {}: {}", player.getName().getString(), e.getMessage());
                        }
                    });
                }
            }
        }
    }

    private static void sendBatchedPackets(AbstractContainerMenu menu, List<Object> packets) {
        List<net.minecraft.world.inventory.ContainerListener> listeners = menu.containerListeners;
        for (net.minecraft.world.inventory.ContainerListener listener : listeners) {
            if (listener instanceof ServerPlayer player) {
                Connection connection = player.connection.connection;
                if (connection.channel != null && connection.channel.isActive()) {
                    if (AsyncContainerBroadcast.enableBackpressureHandling && !connection.channel.isWritable()) {
                        backpressureSkips += packets.size();
                        LOGGER.debug("Channel not writable for player {}, skipping {} container updates", 
                            player.getName().getString(), packets.size());
                        continue;
                    }
                    
                    totalPacketsSent += packets.size();
                    connection.channel.eventLoop().execute(() -> {
                        try {
                            if (connection.channel.isActive()) {
                                for (Object packet : packets) {
                                    connection.send((net.minecraft.network.protocol.Packet<?>) packet);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to send batched packets to player {}: {}", 
                                player.getName().getString(), e.getMessage());
                        }
                    });
                }
            }
        }
    }

    public static void logStatistics() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Async Container Broadcast Stats - Tasks: {}, Packets: {}, Backpressure Skips: {}",
                totalTasksProcessed, totalPacketsSent, backpressureSkips);
        }
    }
    
    public static void resetStatistics() {
        totalTasksProcessed = 0;
        totalPacketsSent = 0;
        backpressureSkips = 0;
    }

    private static record PacketTask(AbstractContainerMenu menu, Object packet, Runnable fallback) implements Runnable {
        @Override
        public void run() {
            try {
                sendPacketToContainerListeners(menu, packet);
            } catch (Exception e) {
                LOGGER.error("Failed to send packet, using fallback", e);
                fallback.run();
            }
        }
    }
}
