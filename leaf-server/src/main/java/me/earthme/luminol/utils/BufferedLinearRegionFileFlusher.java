package me.earthme.luminol.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import me.earthme.luminol.data.BufferedLinearRegionFile;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class BufferedLinearRegionFileFlusher implements Runnable {
    private static final Logger logger = LogUtils.getLogger();

    private final Set<BufferedLinearRegionFile> inManagement = new ObjectArraySet<>();
    private final ScheduledFuture<?> flusherChecker;
    private final Executor ioWorkerPool;
    private final long flushOfWriteTimeoutMs;

    public BufferedLinearRegionFileFlusher(int nIoThreads, long checkIntervalMs, long flushOfWriteTimeoutMs) {
        Validate.isTrue(nIoThreads > 0, "Number of I/O threads must > 0!");
        Validate.isTrue(checkIntervalMs > 0, "Check interval must > 0");
        Validate.isTrue(flushOfWriteTimeoutMs > 0, "Flush of write timeout must > 0");

        this.ioWorkerPool = Executors.newFixedThreadPool(nIoThreads, new ThreadFactoryBuilder()
                .setNameFormat("BufferedLinearRegionFile I/O Worker %d")
                .setDaemon(true)
                .build()
        );
        this.flusherChecker = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                        .setNameFormat("BufferedLinearRegionFile Flusher Checker")
                        .setDaemon(true)
                        .build())
                .scheduleWithFixedDelay(this, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        this.flushOfWriteTimeoutMs = flushOfWriteTimeoutMs;
    }

    public void shutdown() {
        this.flusherChecker.cancel(false);

        ((ExecutorService) this.ioWorkerPool).shutdown();
        for (;;) {
            try {
                if (((ExecutorService) this.ioWorkerPool).awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        final long currentNanos = System.nanoTime();
        final BufferedLinearRegionFile[] copied;

        synchronized (this) {
            copied = this.inManagement.toArray(new BufferedLinearRegionFile[0]);
        }

        final List<BufferedLinearRegionFile> toRemove = new ObjectArrayList<>();
        for (BufferedLinearRegionFile file : copied) {
            // try acquiring the read lock
            if (!file.softReadLock()) {
                // if the read lock is unacquirable, it might mean there is another operations is processing(might be a writing operation)
                continue;
            }

            boolean closed;

            try {
                // check if the file is closed
                closed = file.isClosedRaw();
            } finally {
                file.releaseReadLock();
            }

            if (closed) {
                // add to pending remove list so that we could clean the closed file correctly
                toRemove.add(file);
                continue;
            }

            // skip non sync-required files
            if (!file.shouldSync()) {
                continue;
            }

            final long lastWriteNanos = file.getLastWritten();
            final long timeElapsed = (currentNanos - lastWriteNanos) / 1_000_000; // Convert to milliseconds

            // if deadline(timeout) reached
            if (timeElapsed >= this.flushOfWriteTimeoutMs) {
                // already marked to flush
                if (!file.markAsBeingSynced()) {
                    continue;
                }

                this.ioWorkerPool.execute(() -> {
                    try {
                        file.syncIfNeeded();
                    } catch (IOException e) {
                        logger.error("Failed to sync master file: ", e);
                    }
                });
            }
        }

        synchronized (this) {
            // clean closed files
            for (BufferedLinearRegionFile file : toRemove) {
                this.inManagement.remove(file);
            }
        }
    }

    public void removeFile(BufferedLinearRegionFile fileToRemove) {
        synchronized (this) {
            this.inManagement.remove(fileToRemove);
        }
    }

    public void addFile(BufferedLinearRegionFile fileToAdd) {
        synchronized (this) {
            this.inManagement.add(fileToAdd);
        }
    }
}
