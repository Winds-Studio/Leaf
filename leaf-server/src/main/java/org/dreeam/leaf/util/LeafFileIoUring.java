package org.dreeam.leaf.util;

import com.davidvlijmincx.lio.api.FileDescriptor;
import com.davidvlijmincx.lio.api.IoUringOptions;
import com.davidvlijmincx.lio.api.JUring;
import com.davidvlijmincx.lio.api.LinuxOpenOptions;
import com.davidvlijmincx.lio.api.ReadResult;
import com.davidvlijmincx.lio.api.Result;
import com.davidvlijmincx.lio.api.WriteResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LeafFileIoUring implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeafFileIoUring.class);
    private static final boolean PROPERTY_ENABLED = LeafConstants.ENABLE_FILE_IO_URING;
    private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    private static final int QUEUE_DEPTH = 256;
    private static final Object INIT_LOCK = new Object();
    private static final AtomicReference<Availability> AVAILABILITY = new AtomicReference<>(Availability.UNKNOWN);
    private static final AtomicBoolean LOGGED_ENABLED = new AtomicBoolean(false);
    private static final ThreadLocal<JUring> RINGS = new ThreadLocal<>();

    private enum Availability {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    private final FileDescriptor readFd;
    private final FileDescriptor writeFd;

    private LeafFileIoUring(Path path) {
        this.readFd = new FileDescriptor(path.toString(), LinuxOpenOptions.READ, 0);
        this.writeFd = new FileDescriptor(path.toString(), LinuxOpenOptions.WRITE, 0);
    }

    public static @Nullable LeafFileIoUring open(Path path) {
        if (!isAvailable()) {
            return null;
        }
        try {
            return new LeafFileIoUring(path);
        } catch (Throwable t) {
            LOGGER.debug("JUring file I/O open failed for {}", path, t);
            return null;
        }
    }

    public int read(ByteBuffer dst, long offset) throws IOException {
        JUring ring = ring();
        if (ring == null) {
            throw new IOException("JUring unavailable");
        }
        int requested = dst.remaining();
        if (requested == 0) {
            return 0;
        }

        ring.prepareRead(this.readFd, requested, offset);
        ring.submit();
        Result result = ring.waitForResult();

        if (!(result instanceof ReadResult readResult)) {
            throw new IOException("Unexpected io_uring result: " + result.getClass().getName());
        }

        try {
            long res = readResult.result();
            if (res < 0) {
                throw new IOException("io_uring read failed with " + res);
            }
            int bytes = (int) res;
            ByteBuffer src = readResult.buffer().asByteBuffer();
            src.position(0);
            src.limit(bytes);
            dst.put(src);
            return bytes;
        } finally {
            readResult.freeBuffer();
        }
    }

    public int write(ByteBuffer src, long offset) throws IOException {
        JUring ring = ring();
        if (ring == null) {
            throw new IOException("JUring unavailable");
        }
        int requested = src.remaining();
        if (requested == 0) {
            return 0;
        }

        byte[] data = new byte[requested];
        if (src.hasArray()) {
            int pos = src.position();
            System.arraycopy(src.array(), src.arrayOffset() + pos, data, 0, requested);
        } else {
            ByteBuffer dup = src.duplicate();
            dup.get(data);
        }

        ring.prepareWrite(this.writeFd, data, offset);
        ring.submit();
        Result result = ring.waitForResult();

        if (!(result instanceof WriteResult writeResult)) {
            throw new IOException("Unexpected io_uring result: " + result.getClass().getName());
        }

        long res = writeResult.result();
        if (res < 0) {
            throw new IOException("io_uring write failed with " + res);
        }
        int bytes = (int) res;
        src.position(src.position() + bytes);
        return bytes;
    }

    @Override
    public void close() {
        try {
            this.readFd.close();
        } catch (Exception ignored) {
        }
        try {
            this.writeFd.close();
        } catch (Exception ignored) {
        }
    }

    private static JUring ring() {
        if (!isAvailable()) {
            return null;
        }
        JUring ring = RINGS.get();
        if (ring != null) {
            return ring;
        }
        try {
            ring = new JUring(QUEUE_DEPTH, IoUringOptions.IORING_SETUP_SINGLE_ISSUER);
            RINGS.set(ring);
            return ring;
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private static boolean isAvailable() {
        if (!PROPERTY_ENABLED || !IS_LINUX) {
            return false;
        }
        Availability current = AVAILABILITY.get();
        if (current != Availability.UNKNOWN) {
            return current == Availability.AVAILABLE;
        }
        synchronized (INIT_LOCK) {
            current = AVAILABILITY.get();
            if (current != Availability.UNKNOWN) {
                return current == Availability.AVAILABLE;
            }
            try (JUring ring = new JUring(QUEUE_DEPTH, IoUringOptions.IORING_SETUP_SINGLE_ISSUER)) {
                AVAILABILITY.set(Availability.AVAILABLE);
                if (LOGGED_ENABLED.compareAndSet(false, true)) {
                    LOGGER.info("JUring file I/O enabled.");
                }
                return true;
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }
    }

    private static void disable(Throwable t) {
        if (AVAILABILITY.getAndSet(Availability.UNAVAILABLE) == Availability.UNAVAILABLE) {
            return;
        }
        LOGGER.warn("JUring file I/O disabled: {}", t.toString());
        LOGGER.debug("JUring initialization failure", t);
        JUring ring = RINGS.get();
        if (ring != null) {
            ring.close();
            RINGS.remove();
        }
    }
}
