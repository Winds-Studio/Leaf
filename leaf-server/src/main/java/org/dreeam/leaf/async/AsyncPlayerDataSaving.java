package org.dreeam.leaf.async;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

@NullMarked
public final class AsyncPlayerDataSaving {

    public static final ThreadPoolExecutor IO_POOL;
    private static final Object2ObjectMap<Path, Future<?>> TASKS = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(), AsyncPlayerDataSaving.class);
    private static final Logger LOGGER = LogManager.getLogger("Leaf Async IO");

    private AsyncPlayerDataSaving() {
    }

    static {
        IO_POOL = new ThreadPoolExecutor(
            1,
            1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new com.google.common.util.concurrent.ThreadFactoryBuilder()
                .setPriority(Thread.NORM_PRIORITY - 2)
                .setNameFormat("Leaf IO Thread")
                .setUncaughtExceptionHandler(Util::onThreadException)
                .build(),
            new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    public static void submit(@Nullable Path name, boolean enabled) {
        if (name == null) {
            return;
        }
        if (enabled) {
            Future<?> fut = TASKS.get(name);
            if (fut != null) {
                try {
                    fut.get();
                    TASKS.remove(name, fut);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LOGGER.error("Failed to save {}", name, e.getCause());
                }
            }
        }
    }

    public static void submit(Callable<?> task, Path name, boolean enabled) {
        submit2(task, name, true, enabled);
    }

    public static void submit2(Callable<?> task, @Nullable Path name, boolean async, boolean enabled) {
        submit(name, enabled);
        if (name == null) {
            return;
        }
        if (!enabled || !async) {
            //FutureTask<?> f = new FutureTask<>(task);
            //TASKS.put(name, f);
            //return f;
            try {
                task.call();
            } catch (Exception e) {
                LOGGER.error("Failed to execute {}", name, e);
            }
        } else {
            TASKS.put(name, IO_POOL.submit(new SaveTask(name, task)));
        }
    }

    private record SaveTask(Path name, Callable<?> task) implements Callable<Void> {
        @Override
        public Void call() {
            try {
                task.call();
            } catch (Exception e) {
                LOGGER.error("Failed to save {}", name, e);
            } finally {
                TASKS.remove(name);
            }
            return null;
        }
    }

    public static Path tempFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String prefix = FilenameUtils.getBaseName(fileName);
        String suffix = FilenameUtils.getExtension(fileName);
        Path dir = file.getParent();
        return tempFile(dir, prefix, suffix);
    }

    public static Path tempFile(Path dir, String prefix, String suffix) throws IOException {
        if (!dir.toFile().exists()) {
            Files.createDirectories(dir);
        }
        return Files.createTempFile(dir, prefix + '-', suffix);
    }
}
