package org.dreeam.leaf.async;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public final class AsyncPlayerDataSaving {

    public static final LocalDispatcher IO_POOL = new LocalDispatcher();
    private static final Map<String, FutureTask<Void>> TASKS_E = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    private static final Map<String, FutureTask<Void>> TASKS_A = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    private static final Map<String, FutureTask<Void>> TASKS_S = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .appendValue(ChronoField.NANO_OF_SECOND, 9)
        .toFormatter();
    private static final Logger LOGGER = LogManager.getLogger("Leaf Async Player Storage");

    private AsyncPlayerDataSaving() {
    }

    public static void submit(Callable<Void> task, Path path, int ty) {
        Path fileName = path.getFileName();
        if (fileName != null) {
            submit(task, fileName.toString(), ty);
        }
    }

    public static void submit(Callable<Void> task, String path, int ty) {
        String name = FilenameUtils.getBaseName(path);
        if (name == null || name.isEmpty()) {
            return;
        }

        if (ty == 0) {
            submit(task, name, TASKS_E, "playerdata", AsyncPlayerDataSave.playerdata);
        } else if (ty == 1) {
            submit(task, name, TASKS_A, "advancements", AsyncPlayerDataSave.advancements);
        } else {
            submit(task, name, TASKS_S, "stats", AsyncPlayerDataSave.stats);
        }
    }

    private static void submit(Callable<Void> task, String name, Map<String, FutureTask<Void>> tasks, String ty, boolean enabled) {
        if (enabled) {
            FutureTask<Void> fut = tasks.get(name);
            if (fut != null) {
                try {
                    fut.get();
                    tasks.remove(name, fut);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LOGGER.error("Failed to save {} for {}", ty, fut, e.getCause());
                }
            }
        }
        if (task == null) {
            return;
        }
        if (!enabled) {
            try {
                runDirectly(task);
            } catch (Exception e) {
                LOGGER.error("Failed to save {} for {}", ty, name, e);
            }
        } else {
            tasks.put(name, IO_POOL.submit(new SaveTask(name, task, tasks, ty)));
        }
    }

    private record SaveTask(String name,
                            Callable<Void> task,
                            Map<String, FutureTask<Void>> tasks,
                            String ty) implements Callable<Void> {
        @Override
        public Void call() {
            try {
                task.call();
            } catch (Exception e) {
                LOGGER.error("Failed to save {} for {}", ty, this, e);
            } finally {
                tasks.remove(name);
            }
            return null;
        }

        @Override
        public @NotNull String toString() {
            return "SaveTask{name='" + name + "', type='" + ty + "'}";
        }
    }

    private static void runDirectly(Callable<Void> callable) throws Exception {
        callable.call();
    }

    public static Path tempFileTime(Path file) {
        String fileName = file.getFileName().toString();
        String prefix = FilenameUtils.getBaseName(fileName);
        String suffix = FilenameUtils.getExtension(fileName);
        return tempFileTime(file.getParent(), prefix, suffix);
    }

    public static Path tempFileTime(Path dir, String prefix, String suffix) {
        File file;
        int attempt = 0;
        do {
            file = dir.resolve(prefix + '-' + FORMATTER.format(LocalDateTime.now()) + '.' + suffix).toFile();
        } while (file.exists() && attempt++ < 10);
        return file.toPath();
    }
}
