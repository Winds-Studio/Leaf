package org.dreeam.leaf.async;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

public class AsyncPlayerDataSaving {
    public final static AsyncPlayerDataSaving INSTANCE = new AsyncPlayerDataSaving();
    private final static Logger LOGGER = LogManager.getLogger("Leaf Async Player IO");
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
            if (fut != null) {
                try {
                    while (true) {
                        try {
                            fut.get();
                            break;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (ExecutionException e) {
                    LOGGER.error("Failed to save level.dat for {}", path, e);
                } finally {
                    levelDatFut.remove(path);
                }
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

        Future<?> fut = switch (type) {
            case ENTITY -> entityFut.get(uuid);
            case ADVANCEMENTS -> advancementsFut.get(uuid);
            case STATS -> statsFut.get(uuid);
        };
        if (fut == null) {
            return;
        }
        try {
            while (true) {
                try {
                    fut.get();
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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

    public static void safeReplace(Path current, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        safeReplace(current, bytes, 0, bytes.length);
    }

    public static void safeReplaceBackup(Path current, Path old, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        safeReplaceBackup(current, old, bytes, 0, bytes.length);
    }

    public static void safeReplace(Path current, byte[] bytes, int offset, int length) {
        File latest = writeTempFile(current, bytes, offset, length);
        Objects.requireNonNull(latest);
        for (int i = 1; i <= 10; i++) {
            try {
                try {
                    Files.move(latest.toPath(), current, ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(latest.toPath(), current, NO_ATOMIC_MOVE);
                }
                break;
            } catch (IOException e) {
                LOGGER.error("Failed move {} to {} retries ({} / 10)", latest, current, i, e);
            }
        }
    }

    public static void safeReplaceBackup(Path current, Path old, byte[] bytes, int offset, int length) {
        File latest = writeTempFile(current, bytes, offset, length);
        Objects.requireNonNull(latest);
        for (int i = 1; i <= 10; i++) {
            try {
                try {
                    Files.move(current, old, ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(current, old, NO_ATOMIC_MOVE);
                }
                break;
            } catch (IOException e) {
                LOGGER.error("Failed move {} to {} retries ({} / 10)", current, old, i, e);
            }
        }
        for (int i = 1; i <= 10; i++) {
            try {
                try {
                    Files.move(latest.toPath(), current, ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(latest.toPath(), current, NO_ATOMIC_MOVE);
                }
                break;
            } catch (IOException e) {
                LOGGER.error("Failed move {} to {} retries ({} / 10)", latest, current, i, e);
            }
        }
    }

    private static File writeTempFile(Path current, byte[] bytes, int offset, int length) {
        Path dir = current.getParent();
        for (int i = 1; i <= 10; i++) {
            File temp = null;
            try {
                if (!dir.toFile().isDirectory()) {
                    Files.createDirectories(dir);
                }
                temp = tempFileDateTime(current).toFile();
                if (temp.exists()) {
                    throw new FileAlreadyExistsException(temp.getPath());
                }
                // sync content and metadata to device
                try (RandomAccessFile stream = new RandomAccessFile(temp, "rws")) {
                    stream.write(bytes, offset, length);
                }
                return temp;
            } catch (IOException e) {
                LOGGER.error("Failed write {} retries ({} / 10)", temp == null ? current : temp, i, e);
            }
        }
        return null;
    }

    private static Path tempFileDateTime(Path path) {
        String now = LocalDateTime.now().format(FORMATTER);
        String last = path.getFileName().toString();
        int dot = last.lastIndexOf('.');

        String base = (dot == -1) ? last : last.substring(0, dot);
        String ext = (dot == -1) ? "" : last.substring(dot);

        String newExt = switch (ext) {
            case ".json", ".dat" -> ext;
            default -> ".temp";
        };
        return path.resolveSibling(base + "-" + now + newExt);
    }
}
