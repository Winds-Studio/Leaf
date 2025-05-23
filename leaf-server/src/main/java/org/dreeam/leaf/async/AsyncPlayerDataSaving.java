package org.dreeam.leaf.async;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.UUID;
import java.util.concurrent.*;

public class AsyncPlayerDataSaving {
    public final static AsyncPlayerDataSaving INSTANCE = new AsyncPlayerDataSaving();
    private final static Logger LOGGER = LogManager.getLogger("Async Player IO");
    public static final ExecutorService IO_POOL = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new ThreadFactoryBuilder()
            .setPriority(Thread.NORM_PRIORITY - 2)
            .setNameFormat("Leaf IO Thread")
            .setUncaughtExceptionHandler(Util::onThreadException)
            .build(),
        new ThreadPoolExecutor.DiscardPolicy()
    );
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendValue(ChronoField.NANO_OF_SECOND, 9)
        .toFormatter();

    private final Object2ObjectMap<UUID, Future<?>> entityFut = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(), this);
    private final Object2ObjectMap<UUID, Future<?>> statsFut = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(), this);
    private final Object2ObjectMap<UUID, Future<?>> advancementsFut = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(), this);
    private final Object2ObjectMap<Path, Future<?>> levelDatFut = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(), this);

    private record SaveTask(Ty ty, Callable<Void> callable, String name, UUID uuid) implements Runnable {
        @Override
        public void run() {
            try {
                callable.call();
            } catch (Exception e) {
                LOGGER.error("Failed to save player {} data for {}", ty, name, e);
            } finally {
                switch (ty) {
                    case ENTITY -> INSTANCE.entityFut.remove(uuid);
                    case STATS -> INSTANCE.statsFut.remove(uuid);
                    case ADVANCEMENTS -> INSTANCE.advancementsFut.remove(uuid);
                }
            }
        }
    }

    public enum Ty {
        ENTITY,
        STATS,
        ADVANCEMENTS,
    }

    private AsyncPlayerDataSaving() {
    }

    public void submit(Ty type, UUID uuid, String playerName, Callable<Void> callable) {
        if (!AsyncPlayerDataSave.enabled) {
            try {
                callable.call();
            } catch (Exception e) {
                LOGGER.error("Failed to save player {} data for {}", type, playerName, e);
            }
        } else {
            block(type, uuid, playerName);
            var fut = IO_POOL.submit(new SaveTask(type, callable, playerName, uuid));
            switch (type) {
                case ENTITY -> entityFut.put(uuid, fut);
                case ADVANCEMENTS -> advancementsFut.put(uuid, fut);
                case STATS -> statsFut.put(uuid, fut);
            }
        }
    }

    public void saveLevelData(Path path, @Nullable Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            if (runnable != null) {
                runnable.run();
            }
        } else {
            var fut = levelDatFut.get(path);
            try {
                if (fut != null) {
                    fut.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                LOGGER.error("Failed to save level.dat for {}", path, e);
            }
            if (runnable != null) {
                IO_POOL.submit(() -> {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        LOGGER.error(e);
                    } finally {
                        levelDatFut.remove(path);
                    }
                });
            }
        }
    }

    public void block(Ty type, UUID uuid, String playerName) {
        if (!AsyncPlayerDataSave.enabled) {
            return;
        }

        if (!TickThread.isTickThread() || TickThread.isServerLevelTickThread()) {
            LOGGER.warn("load player data off-main {} {} {}", type, uuid, playerName, new Throwable());
        }

        Future<?> fut = switch (type) {
            case ENTITY -> entityFut.get(uuid);
            case ADVANCEMENTS -> advancementsFut.get(uuid);
            case STATS -> statsFut.get(uuid);
        };
        if (fut == null) {
            return;
        }
        try {
            fut.get();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            LOGGER.warn("Failed to save player {} data for {}", type, playerName, exception);
            fut.cancel(true);
        } finally {
            switch (type) {
                case ENTITY -> entityFut.remove(uuid);
                case ADVANCEMENTS -> advancementsFut.remove(uuid);
                case STATS -> statsFut.remove(uuid);
            }
        }
    }

    private static final StandardCopyOption[] ATOMIC_MOVE = new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING};
    private static final StandardCopyOption[] NO_ATOMIC_MOVE = new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING};

    public static void safeReplace(final Path origin, final Path temp) throws IOException {
        try {
            Files.move(temp, origin, ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, origin, NO_ATOMIC_MOVE);
        }
    }

    public static Path safeReplacePath(Path origin) {
        LocalDateTime now = LocalDateTime.now();
        String newFileName = origin.getFileName() + ".temp" + now.format(FORMATTER);
        return origin.resolveSibling(newFileName);
    }
}
