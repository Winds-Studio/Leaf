package org.dreeam.leaf.async;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

public final class AsyncPlayerDataSaving {

    public static final ThreadPoolExecutor IO_POOL = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setPriority(Thread.NORM_PRIORITY - 2)
            .setNameFormat("Leaf IO Thread")
            .setUncaughtExceptionHandler(Util::onThreadException)
            .build(),
        new ThreadPoolExecutor.DiscardPolicy()
    );
    private static final Map<String, Future<Void>> TASKS_E = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    private static final Map<String, Future<Void>> TASKS_A = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    private static final Map<String, Future<Void>> TASKS_S = it.unimi.dsi.fastutil.objects.Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
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

    private static void submit(Callable<Void> task, String name, Map<String, Future<Void>> tasks, String ty, boolean enabled) {
        if (enabled) {
            Future<Void> fut = tasks.get(name);
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
                            Map<String, Future<Void>> tasks,
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

    public static Path tempFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String prefix = FilenameUtils.getBaseName(fileName);
        String suffix = FilenameUtils.getExtension(fileName);
        return tempFile(file.getParent(), prefix, suffix);
    }

    public static Path tempFile(Path dir, String prefix, String suffix) throws IOException {
        return Files.createTempFile(dir, prefix + '-', suffix);
    }
}
