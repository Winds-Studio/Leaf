package org.dreeam.leaf.chunk;

import ca.spottedleaf.concurrentutil.numa.OSNuma;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import io.netty.util.internal.PlatformDependent;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public enum ChunkSystemAlgorithm {
    MOONRISE((configWorkerThreads, configIoThreads) -> {
        int defaultWorkerThreads = OSNuma.getNativeInstance().getTotalCores() / 2;
        if (defaultWorkerThreads <= 4) {
            defaultWorkerThreads = defaultWorkerThreads <= 3 ? 1 : 2;
        } else {
            defaultWorkerThreads = defaultWorkerThreads / 2;
        }
        defaultWorkerThreads = Integer.getInteger(PlatformHooks.get().getBrand() + ".WorkerThreadCount", Integer.valueOf(defaultWorkerThreads));

        int workerThreads = configWorkerThreads;
        if (workerThreads <= 0) {
            workerThreads = defaultWorkerThreads;
        }
        final int ioThreads = Math.max(1, configIoThreads);
        return new int[]{workerThreads, ioThreads};
    }),
    C2ME_NEW((configWorkerThreads, configIoThreads) -> {
        int eval = configWorkerThreads <= 0 ? evalC2meNew() : configWorkerThreads;
        return new int[]{eval, Math.max(1, configIoThreads)};
    }),
    C2ME((configWorkerThreads, configIoThreads) -> {
        int eval = configWorkerThreads <= 0 ? evalC2me() : configWorkerThreads;
        return new int[]{eval, Math.max(1, configIoThreads)};
    });

    private final BiFunction<Integer, Integer, int[]> eval;

    ChunkSystemAlgorithm(BiFunction<Integer, Integer, int[]> eval) {
        this.eval = eval;
    }

    private static int evalC2meNew() {
        int cpus = Runtime.getRuntime().availableProcessors();
        double memGb = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0;
        double cpuPart = PlatformDependent.isWindows() ? (cpus / 1.6) : (cpus / 1.3);
        double memPart = (memGb - 0.5) / 0.6;
        return Math.max(1, (int) Math.min(cpuPart, memPart));
    }

    private static int evalC2me() {
        int cpus = Runtime.getRuntime().availableProcessors();
        double memGb = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0;
        double cpuPart = PlatformDependent.isWindows() ? (cpus / 1.6 - 2.0) : (cpus / 1.2 - 2.0);
        double memPart = PlatformDependent.isJ9Jvm() ? ((memGb - 0.2) / 0.4) : ((memGb - 0.6) / 0.6);
        return Math.max(1, (int) Math.min(cpuPart, memPart));
    }

    public int evalWorkers(final int configWorkerThreads, final int configIoThreads) {
        return eval.apply(configWorkerThreads, configIoThreads)[0];
    }

    public int evalIO(final int configWorkerThreads, final int configIoThreads) {
        return eval.apply(configWorkerThreads, configIoThreads)[1];
    }

    public @NotNull String asDebugString() {
        return this + "(" + evalWorkers(-1, -1) + ")";
    }
}
