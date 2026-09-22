package me.earthme.luminol.data;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import me.earthme.luminol.utils.BufferedLinearRegionFileFlusher;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.openhft.hashing.LongHashFunction;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock hierarchy (always acquire top to bottom, never the reverse):
 * <ol>
 *     <li>{@code syncLock}         — serializes master file syncs against close</li>
 *     <li>{@code Bucket.lock}      — per-bucket lazy-load guard</li>
 *     <li>{@code masterFileLock}   — master file read / append / replace</li>
 *     <li>{@code regionObjectLock} — in-memory sector table + swap file channel</li>
 * </ol>
 * The atomic flags (closed / synced / beingSynced / lastWritten), the bucket epochs
 * and the swap space counters (currentAcquiredIndex / liveBytes, mutated only under
 * the region write lock) are lock-free readable and may be touched while holding any
 * (or no) lock.
 * <p>
 * The swap file is fully transient: it is deleted at open, opened with
 * DELETE_ON_CLOSE and never parsed back after a crash, so it carries no header and
 * is never fsynced. Durability comes exclusively from the master file, whose v3
 * on-disk format is unchanged.
 */
public class BufferedLinearRegionFile implements me.earthme.luminol.data.RegionFile {
    private static final double SWAP_FILE_AUTO_COMPACT_PERCENT = 3.0 / 5.0; // 60 %
    private static final long SWAP_FILE_AUTO_COMPACT_SIZE = 1024 * 1024; // 1 MiB

    // master file WAL appends leave the replaced bucket records behind as garbage; once
    // it piles up past this threshold the next sync compacts via a full tmp-file rewrite
    private static final double MASTER_FILE_AUTO_COMPACT_PERCENT = SWAP_FILE_AUTO_COMPACT_PERCENT;
    private static final long MASTER_FILE_AUTO_COMPACT_SIZE = SWAP_FILE_AUTO_COMPACT_SIZE;

    private static final int XXHASH32_SEED = 0x0721; // ～(∠・ω< )⌒★

    private static final long MASTER_FILE_SUPER_BLOCK = -0x200812250269L;
    private static final byte MASTER_FILE_VERSION = 0x02; // ver 2.0
    private static final byte MASTER_FILE_VERSION_BUCKET = 0x03; // ver 3.0

    private static final long LINEAR_FILE_SUPER_BLOCK = 0xc3ff13183cca9d9aL;

    private static final int BUCKET_SHIFT = 6;
    private static final int BUCKET_SIZE = 1 << BUCKET_SHIFT;
    private static final int BUCKET_COUNT = 1024 / BUCKET_SIZE;

    private static final long MAX_SIZE_PER_CHUNK = RegionFile.MAX_CHUNK_SIZE;

    // on-disk sector layout in the swap file:
    //   dataLen(int) + timestamp(long) + xxhash32(int) + lz4(chunk data)
    // the 16 meta bytes stay OUTSIDE the compression so neither the write nor the read
    // path needs a full-size intermediate copy of the chunk data; dataLen doubles as
    // the lz4 original size, so no separate length prefix is needed
    private static final int SECTOR_META_SIZE = Integer.BYTES + Long.BYTES + Integer.BYTES;

    // all three are stateless and thread-safe
    private static final LZ4Compressor LZ4_COMPRESSOR = LZ4Factory.fastestInstance().highCompressor();
    private static final LZ4FastDecompressor LZ4_DECOMPRESSOR = LZ4Factory.fastestInstance().fastDecompressor();
    private static final XXHash32 XX_HASH_32 = XXHashFactory.fastestInstance().hash32();

    // per-thread staging buffer for the hot chunk read/write paths: the compressed
    // bytes never outlive the single pread/pwrite they are staged for, so they never
    // need to escape into a fresh allocation
    private static final int SCRATCH_RETAIN_LIMIT = 2 * 1024 * 1024; // 2 MiB
    private static final ThreadLocal<ByteBuffer> SCRATCH = ThreadLocal.withInitial(() -> ByteBuffer.allocate(64 * 1024));

    private static final StandardOpenOption[] SWAP_FILE_CHANNEL_OPTIONS = new StandardOpenOption[]{
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.READ,
        StandardOpenOption.DELETE_ON_CLOSE
    };

    private static final class Bucket {
        private final Object lock = new Object();

        private final AtomicLong writeEpoch = new AtomicLong();
        private final AtomicLong syncedEpoch = new AtomicLong();
        private volatile boolean loaded = false;
    }

    private final Bucket[] buckets = new Bucket[BUCKET_COUNT];

    private final Path masterFilePath;
    private final Path swapFilePath;

    // outermost lock: serializes syncToMasterFile() against closeInternal(), so the
    // swap channel can never be torn down while a sync is still reading from it
    private final Object syncLock = new Object();

    private final ReadWriteLock regionObjectLock = new ReentrantReadWriteLock();
    private Sector[] sectors = new Sector[1024];
    private FileChannel swapFileChannel;

    // mutated only under regionObjectLock's write lock; volatile so flushInternal()
    // can run its garbage estimate without taking any lock at all
    private volatile long currentAcquiredIndex;
    private volatile long liveBytes;

    private final byte compressionLevel;
    private final MasterFileParser masterFileParser = new MasterFileParser();

    // managed by VarHandles following
    private boolean closed = false;
    private boolean beingSynced = false;
    private boolean synced = false;
    private long lastWritten = System.nanoTime();

    private static final VarHandle CLOSED_HANDLE = ConcurrentUtil.getVarHandle(BufferedLinearRegionFile.class, "closed", boolean.class);
    private static final VarHandle SYNCED_HANDLE = ConcurrentUtil.getVarHandle(BufferedLinearRegionFile.class, "synced", boolean.class);
    private static final VarHandle BEING_SYNCED_HANDLE = ConcurrentUtil.getVarHandle(BufferedLinearRegionFile.class, "beingSynced", boolean.class);
    private static final VarHandle LAST_WRITTEN_HANDLE = ConcurrentUtil.getVarHandle(BufferedLinearRegionFile.class, "lastWritten", long.class);

    private final BufferedLinearRegionFileFlusher flusher;

    public BufferedLinearRegionFile(Path masterFilePath, int compressionLevel, @NotNull BufferedLinearRegionFileFlusher flusher) throws IOException {
        this.masterFilePath = masterFilePath;
        this.swapFilePath = Path.of(this.masterFilePath.toString() + ".swp");

        Validate.inclusiveBetween(1, 22, compressionLevel);

        for (int i = 0; i < this.buckets.length; i++) {
            this.buckets[i] = new Bucket();
        }

        this.compressionLevel = (byte) compressionLevel;

        this.cleanUpSwapFile();
        this.initSwapFile();
        this.tryLoadOldBlinearMasterFileData();

        // resume WAL mode directly from an existing v3 master file: without this, the
        // first sync after every open rewrites the whole file even for one dirty chunk
        this.masterFileParser.tryEnterWalMode(this.masterFilePath);

        this.flusher = flusher;
        this.flusher.addFile(this);
    }

    private static @NotNull ByteBuffer acquireScratch(int capacity) {
        ByteBuffer buf = SCRATCH.get();

        if (buf.capacity() < capacity) {
            buf = ByteBuffer.allocate(Math.max(capacity, buf.capacity() << 1));

            // oversized one-off requests get a throwaway buffer instead of pinning
            // megabytes onto every io thread forever
            if (buf.capacity() <= SCRATCH_RETAIN_LIMIT) {
                SCRATCH.set(buf);
            }
        }

        buf.clear();
        return buf;
    }

    private static void writeFullyAt(FileChannel channel, @NonNull ByteBuffer buf, long startOffset) throws IOException {
        long offset = startOffset;
        while (buf.hasRemaining()) {
            offset += channel.write(buf, offset);
        }
    }

    private static void readFullyAt(FileChannel channel, @NonNull ByteBuffer buf, long startOffset) throws IOException {
        long offset = startOffset;
        while (buf.hasRemaining()) {
            final int read = channel.read(buf, offset);
            if (read < 0) throw new EOFException("Unexpected EOF at offset " + offset);
            offset += read;
        }
    }

    private static void transferFully(FileChannel source, long sourceOffset, long count, FileChannel target, long targetOffset) throws IOException {
        target.position(targetOffset);

        long transferred = 0;
        while (transferred < count) {
            transferred += source.transferTo(sourceOffset + transferred, count - transferred, target);
        }
    }

    // replaces target with source, deleting source if both attempts fail
    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable e) {
            // atomic move might be unsupported on some file systems, so give it an attempt to retry without atomic move
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ex) {
                e.addSuppressed(ex);

                // delete file that failed to replace
                Files.deleteIfExists(source);

                throw new IOException("Failed to replace " + target + "!", e);
            }
        }
    }

    private void cleanUpSwapFile() throws IOException {
        Files.deleteIfExists(this.swapFilePath);

        // a crash between compact's tmp creation and the atomic replace leaves a stale
        // .swp.tmp behind, which would make every future compact fail at CREATE_NEW
        Files.deleteIfExists(Path.of(this.swapFilePath + ".tmp"));
    }

    private void ensureBucketLoaded(int chunkIndex) throws IOException {
        final int bucketIndex = chunkIndex >> BUCKET_SHIFT;
        final Bucket bucket = this.buckets[bucketIndex];

        if (bucket.loaded) { // volatile fast path
            return;
        }

        // bucket lock -> master read lock -> swap write lock
        synchronized (bucket.lock) {
            if (bucket.loaded) {
                return;
            }

            this.masterFileParser.loadBucketsFor(this.masterFilePath, bucketIndex);
            bucket.loaded = true;
        }
    }

    // used by the legacy parsers: their data goes through the write path directly,
    // so the bucket must be flagged loaded first to avoid a recursive lazy-load
    private void markBucketLoaded(int chunkIndex) {
        final Bucket bucket = this.buckets[chunkIndex >> BUCKET_SHIFT];

        synchronized (bucket.lock) {
            bucket.loaded = true;
        }
    }

    private void markBucketDirty(int chunkIndex) {
        this.buckets[chunkIndex >> BUCKET_SHIFT].writeEpoch.incrementAndGet();
    }

    private long getBucketWriteEpoch(int bucketIndex) {
        return this.buckets[bucketIndex].writeEpoch.get();
    }

    private void markBucketSynced(int bucketIndex, long syncedEpoch) {
        this.buckets[bucketIndex].syncedEpoch.accumulateAndGet(syncedEpoch, Math::max);
    }

    private boolean isBucketDirty(int bucketIndex) {
        final Bucket bucket = this.buckets[bucketIndex];

        return bucket.writeEpoch.get() != bucket.syncedEpoch.get();
    }

    public boolean markAsBeingSynced() {
        return BEING_SYNCED_HANDLE.compareAndSet(this, false, true);
    }


    public long getLastWritten() {
        return (long) LAST_WRITTEN_HANDLE.getVolatile(this);
    }

    public boolean shouldSync() {
        return !((boolean) SYNCED_HANDLE.getVolatile(this));
    }

    public boolean readLock(boolean soft) {
        if (!soft) {
            this.regionObjectLock.readLock().lock();
            return true;
        }

        return this.regionObjectLock.readLock().tryLock();
    }

    public void releaseReadLock() {
        this.regionObjectLock.readLock().unlock();
    }

    private void guardAgainstClosed() throws IOException {
        if (this.isClosedRaw()) {
            throw new IOException("Closed");
        }
    }

    public boolean isClosedRaw() {
        return (boolean) CLOSED_HANDLE.getVolatile(this);
    }

    public boolean isClosed() {
        this.regionObjectLock.readLock().lock();
        try {
            return (boolean) CLOSED_HANDLE.getVolatile(this);
        } finally {
            this.regionObjectLock.readLock().unlock();
        }
    }

    public void syncIfNeeded() throws IOException {
        try {
            this.syncToMasterFile(false, false, false, false);
        } finally {
            BEING_SYNCED_HANDLE.setVolatile(this, false); // mark as not being synced
        }
    }

    // noSwapLock/noMasterLock: caller (closeInternal) must already hold
    // regionObjectLock.write and masterFileLock.write respectively.
    private void syncToMasterFile(boolean forceSync, boolean forceCompact, boolean noSwapLock, boolean noMasterLock) throws IOException {
        // serialized against close: the swap channel cannot go away under a running sync
        synchronized (this.syncLock) {
            // skip if closed already
            if (this.isClosedRaw()) {
                return;
            }

            // fast skip when there is nothing to sync; writers flip the flag back
            // via markAsToSync() which triggers the next round
            if (!SYNCED_HANDLE.compareAndSet(this, false, true) && !forceSync) {
                return;
            }

            try {
                this.masterFileParser.sync(this.masterFilePath, forceCompact, noSwapLock, noMasterLock);
            } catch (Throwable e) {
                // set back
                SYNCED_HANDLE.setVolatile(this, false);

                throw new IOException("Failed to sync to master file!", e);
            }
        }
    }

    private void tryLoadOldBlinearMasterFileData() throws IOException {
        this.masterFileParser.tryParseMainFileOld(this.masterFilePath);
    }

    private void initSwapFile() throws IOException {
        this.swapFileChannel = FileChannel.open(
            this.swapFilePath,
            SWAP_FILE_CHANNEL_OPTIONS
        );

        // fill default sectors; the swap file has no header, data starts at offset 0
        for (int i = 0; i < 1024; i++) {
            this.sectors[i] = new Sector(i, 0, 0);
        }

        this.currentAcquiredIndex = 0;
        this.liveBytes = 0;
    }

    private void recalculateCounters() {
        long acquired = 0;
        long live = 0;

        for (Sector sector : this.sectors) {
            // cleared sectors keep their stale extent for in-place reuse (see store()),
            // so their extent MUST still be counted into the acquired watermark here,
            // or later appends could land inside it and get overwritten by a reuse
            acquired = Math.max(acquired, sector.offset + sector.length);

            if (sector.hasData()) {
                live += sector.length;
            }
        }

        this.currentAcquiredIndex = acquired;
        this.liveBytes = live;
    }

    private void flushInternal() throws IOException {
        if (this.isClosedRaw()) {
            return;
        }

        // lock-free garbage estimate from the incrementally maintained counters:
        // this runs after EVERY chunk write, so no write lock, no O(1024) sector
        // scan and no Files.exists() stat on the hot path
        final long live = this.liveBytes;
        final long spare = this.currentAcquiredIndex - live;
        final boolean compactRequested = spare > SWAP_FILE_AUTO_COMPACT_SIZE && (double) spare > (double) live * SWAP_FILE_AUTO_COMPACT_PERCENT;

        // try auto compact to clean the garbage area
        if (compactRequested) {
            this.regionObjectLock.writeLock().lock();
            try {
                if (!this.isClosedRaw()) {
                    // recheck with the authoritative values under the lock
                    final long liveNow = this.liveBytes;
                    final long spareNow = this.currentAcquiredIndex - liveNow;

                    if (spareNow > SWAP_FILE_AUTO_COMPACT_SIZE && (double) spareNow > (double) liveNow * SWAP_FILE_AUTO_COMPACT_PERCENT) {
                        // do compact
                        this.compactSwapFile();
                    }
                }
            } finally {
                this.regionObjectLock.writeLock().unlock();
            }
        }

        // create the master file eagerly on the very first write of a fresh region;
        // afterwards this is a single volatile read per chunk write.
        // prevent syncing after compact because it could be time costing sometimes
        if (!compactRequested && !this.masterFileParser.masterFileExists()) {
            this.syncToMasterFile(false, false, false, false);
        }
    }

    private void closeInternal() throws IOException {
        // note: any new sync attempt is blocked inside this block
        synchronized (this.syncLock) {
            this.masterFileParser.masterFileLock.writeLock().lock();
            try {
                // note: any read/write ops is blocked inside this block
                this.regionObjectLock.writeLock().lock();

                try {
                    if (this.isClosedRaw()) {
                        boolean duplicateClosed = false;

                        if (this.swapFileChannel.isOpen()) {
                            this.swapFileChannel.close();

                            duplicateClosed = true;
                        }

                        duplicateClosed &= this.masterFileParser.tryCloseNoLock();

                        if (!duplicateClosed) {
                            throw new IOException("Already closed");
                        }
                        return;
                    }

                    // final sync so no buffered data is lost; holding syncLock also guarantees no
                    // concurrent flusher sync is still running when we tear down below.
                    // if this throws we deliberately stay open: the flusher can retry the sync
                    // later, and the not-yet-synced swap data is not dropped on the floor
                    // since we hold the write lock and any read/write/sync ops is currently blocked all along the close logic, acquiring the locks inside sync is a disaster
                    this.syncToMasterFile(true, true, true, true);

                    // remove from flusher
                    this.markClosed();

                    IOException failure = null;

                    try {
                        this.swapFileChannel.close();
                    } catch (IOException ex) {
                        if (failure == null) failure = ex;
                        else failure.addSuppressed(ex);
                    }

                    try {
                        this.masterFileParser.closeNoLock();
                    } catch (IOException e) {
                        if (failure == null) failure = e;
                        else failure.addSuppressed(e);
                    }

                    if (failure != null) {
                        throw failure;
                    }
                } finally {
                    this.regionObjectLock.writeLock().unlock();
                }
            } finally {
                this.masterFileParser.masterFileLock.writeLock().unlock();
            }
        }
    }

    private void markClosed() {
        // lenient CAS: the disaster path of compactSwapFile() may have closed us already
        if (CLOSED_HANDLE.compareAndSet(this, false, true)) {
            this.flusher.removeFile(this);
        }
    }

    private void compactSwapFile() throws IOException {
        final Sector[] newSectorsToBeReplaced = new Sector[this.sectors.length];

        for (int i = 0; i < this.sectors.length; i++) {
            final Sector old = this.sectors[i];

            if (old.hasData()) {
                newSectorsToBeReplaced[i] = old;
                continue;
            }

            // note:
            // we reset length to 0 and this would make length <= newLength(which is >= 0) is always true.
            // so that the following write operation wouldn't override the data of other sectors
            // see the store method in Sector class
            newSectorsToBeReplaced[i] = new Sector(i, 0, 0);
        }

        long newAcquiredIndex;

        final Path targetTemp = Path.of(this.swapFilePath + ".tmp");

        try (FileChannel tempChannel = FileChannel.open(
            targetTemp,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            long offsetPointer = 0;

            for (Sector sector : newSectorsToBeReplaced) {
                // skip cleared or no data-contained sectors
                if (!sector.hasData()) {
                    continue;
                }

                // transfer to target
                transferFully(this.swapFileChannel, sector.offset, sector.length, tempChannel, offsetPointer);

                // recalculate the offset and length
                final Sector newRecalculated = new Sector(sector.index, offsetPointer, sector.length);
                newRecalculated.hasData = true;

                offsetPointer += sector.length;
                newSectorsToBeReplaced[sector.index] = newRecalculated; // update sector infos
            }

            // note: NO force here — the swap file is transient and never read back
            // after a crash, so fsyncing it (twice, like before) was pure overhead

            newAcquiredIndex = offsetPointer;
        } catch (Throwable ex) {
            // recalculate counters
            this.recalculateCounters();
            // delete the target temp file
            Files.deleteIfExists(targetTemp);
            // fast-fail
            // note: we don't block new write operations here as this is recoverable
            throw new IOException("Failed to compact swap file!", ex);
        }

        this.swapFileChannel.close();

        // replace swap file
        try {
            atomicReplace(targetTemp, this.swapFilePath);
        } catch (Throwable e) {
            // fast-fail
            this.markClosed(); // prevent new writing & sync operations
            throw new IOException("Failed to replace original swap file!", e);
        }

        try {
            // reopen file channel
            this.reopenSwapFileChannel();

            // replace with recalculated infos: after a compact everything left is live
            this.sectors = newSectorsToBeReplaced;
            this.currentAcquiredIndex = newAcquiredIndex;
            this.liveBytes = newAcquiredIndex;
        } catch (Throwable ex) {
            // we are totally failed here,
            // directly mark as closed as the swap file is already replaced, and we failed to update the
            // data which is still in the memory
            //
            // which means we might write any data into any incorrect indexed sectors which will blow the whole data
            this.markClosed();
            throw new IOException(ex);
        }
    }

    private void reopenSwapFileChannel() throws IOException {
        if (this.swapFileChannel.isOpen()) {
            this.swapFileChannel.close();
        }

        this.swapFileChannel = FileChannel.open(
            this.swapFilePath,
            SWAP_FILE_CHANNEL_OPTIONS
        );
    }

    // stores an already lz4-encoded sector (meta + compressed data), typically staged
    // in the thread-local scratch: nothing here escapes to the heap
    private void storeSector(int index, @NotNull ByteBuffer encoded, boolean skipSync) throws IOException {
        this.regionObjectLock.writeLock().lock();
        try {
            this.guardAgainstClosed();

            this.sectors[index].store(encoded, this.swapFileChannel);

            if (!skipSync) {
                this.markBucketDirty(index);
            }
        } finally {
            this.regionObjectLock.writeLock().unlock();
        }

        if (skipSync) {
            return;
        }

        this.markAsToSync();
    }

    // section = dataLen(int) + timestamp(long) + xxhash32(int) + data, i.e. the exact
    // per-chunk byte layout persisted inside master file bucket records
    private void writeSection(int index, @NotNull ByteBuffer section, boolean skipSync) throws IOException {
        if (section.remaining() < SECTOR_META_SIZE) {
            throw new IOException("Truncated chunk section (" + section.remaining() + " bytes) for index " + index);
        }

        final int dataLen = section.remaining() - SECTOR_META_SIZE;
        final ByteBuffer out = acquireScratch(SECTOR_META_SIZE + LZ4_COMPRESSOR.maxCompressedLength(dataLen));

        // meta bytes are carried over verbatim, only the chunk data goes through lz4
        final int oldLimit = section.limit();
        section.limit(section.position() + SECTOR_META_SIZE);
        out.put(section);
        section.limit(oldLimit);

        LZ4_COMPRESSOR.compress(section, out);
        out.flip();

        this.storeSector(index, out, skipSync);
    }

    private void clearChunkData(int index) throws IOException {
        this.guardAgainstClosed();

        this.ensureBucketLoaded(index);

        this.regionObjectLock.writeLock().lock();
        try {
            this.guardAgainstClosed();

            this.sectors[index].clear();
            this.markBucketDirty(index);
        } finally {
            this.regionObjectLock.writeLock().unlock();
        }

        this.markAsToSync();
    }

    private void markAsToSync() {
        SYNCED_HANDLE.setVolatile(this, false); // mark as unsynced
        LAST_WRITTEN_HANDLE.setVolatile(this, System.nanoTime()); // update last written time
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private boolean hasData(int index) throws IOException {
        this.guardAgainstClosed();
        this.ensureBucketLoaded(index);

        this.regionObjectLock.readLock().lock();
        try {
            this.guardAgainstClosed();

            return this.sectors[index].hasData();
        } finally {
            this.regionObjectLock.readLock().unlock();
        }
    }

    private void writeChunk(int x, int z, @NotNull ByteBuffer data) throws IOException {
        this.guardAgainstClosed();

        final int chunkIndex = getChunkIndex(x, z);

        this.ensureBucketLoaded(chunkIndex);

        final int dataLen = data.remaining();

        if (dataLen > MAX_SIZE_PER_CHUNK) {
            throw new RegionFileStorage.RegionFileSizeException("Writing too large chunk, limit : " + MAX_SIZE_PER_CHUNK + " but got : " + dataLen);
        }

        // absolute-offset hash: no position save/restore dance needed
        final int xxHash32OfData = XX_HASH_32.hash(data, data.position(), dataLen, XXHASH32_SEED);

        // meta + compressed data are built directly in the reusable scratch: no
        // full-size intermediate copy of the chunk data, no allocation that escapes
        final ByteBuffer out = acquireScratch(SECTOR_META_SIZE + LZ4_COMPRESSOR.maxCompressedLength(dataLen));

        out.putInt(dataLen);                     // uncompressed length, doubles as the lz4 original size
        out.putLong(System.currentTimeMillis()); // timestamp
        out.putInt(xxHash32OfData);              // xxHash32 of the original data
        LZ4_COMPRESSOR.compress(data, out);
        out.flip();

        this.storeSector(chunkIndex, out, false);
    }

    private @Nullable ByteBuffer readChunk(int x, int z) throws IOException {
        this.guardAgainstClosed();

        final int chunkIndex = getChunkIndex(x, z);

        this.ensureBucketLoaded(chunkIndex);

        final ByteBuffer stage;

        this.regionObjectLock.readLock().lock();
        try {
            this.guardAgainstClosed();

            final Sector sector = this.sectors[chunkIndex];

            if (!sector.hasData()) {
                return null;
            }

            // only the pread runs under the lock, staged into the reusable scratch
            stage = acquireScratch((int) sector.length);
            stage.limit((int) sector.length);

            readFullyAt(this.swapFileChannel, stage, sector.offset);
        } finally {
            this.regionObjectLock.readLock().unlock();
        }

        stage.flip();

        final int dataLen = stage.getInt();
        stage.getLong(); // TODO use this timestamp(long) for something?
        final int expectedXXHash32 = stage.getInt();

        // lz4 decompresses straight from the scratch into the result buffer: the
        // compressed bytes are never copied into an intermediate array
        final byte[] data = new byte[dataLen];
        LZ4_DECOMPRESSOR.decompress(stage.array(), stage.arrayOffset() + SECTOR_META_SIZE, data, 0, dataLen);

        final int actualXXHash32 = XX_HASH_32.hash(data, 0, dataLen, XXHASH32_SEED);
        if (actualXXHash32 != expectedXXHash32) {
            throw new IOException("XXHash32 check failed ! Expected: " + expectedXXHash32 + ",but got: " + actualXXHash32); // prevent from loading
        }

        return ByteBuffer.wrap(data);
    }

    @Override
    public Path getPath() {
        return this.masterFilePath;
    }

    @Override
    public DataInputStream getChunkDataInputStream(@NotNull ChunkPos pos) throws IOException {
        final ByteBuffer data = this.readChunk(pos.x(), pos.z());

        if (data == null) {
            return null;
        }

        return new DataInputStream(new ByteBufferInputStream(data));
    }

    @Override
    public boolean doesChunkExist(@NotNull ChunkPos pos) throws IOException {
        return this.hasData(getChunkIndex(pos.x(), pos.z()));
    }

    @Override
    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) {
        return new DataOutputStream(new ChunkBufferHelper(pos));
    }

    @Override
    public void clear(@NotNull ChunkPos pos) throws IOException {
        this.clearChunkData(getChunkIndex(pos.x(), pos.z()));
    }

    @Override
    public boolean hasChunk(@NotNull ChunkPos pos) {
        try {
            return this.hasData(getChunkIndex(pos.x(), pos.z()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(@NotNull ChunkPos pos, ByteBuffer buf) throws IOException {
        this.writeChunk(pos.x(), pos.z(), buf);
    }

    // MCC 的玩意,这东西也用不上给Linear了()
    @Override
    public CompoundTag getOversizedData(int x, int z) {
        return null;
    }

    @Override
    public boolean isOversized(int x, int z) {
        return false;
    }

    @Override
    public boolean recalculateHeader() {
        return false;
    }

    @Override
    public void setOversized(int x, int z, boolean oversized) {

    }
    // MCC end

    @Override
    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(CompoundTag data, ChunkPos pos) {
        final ChunkBufferHelper buffer = new ChunkBufferHelper(pos);
        buffer.moonrise$setWriteOnClose(false);
        final DataOutputStream out = new DataOutputStream(buffer);

        return new MoonriseRegionFileIO.RegionDataController.WriteData(
            data, MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
            out, buffer::moonrise$write
        );
    }

    @Override
    public void flush() throws IOException {
        this.flushInternal();
    }

    @Override
    public void close() throws IOException {
        this.closeInternal();
    }

    public static class ByteBufferInputStream extends InputStream {
        protected final ByteBuffer internal;

        public ByteBufferInputStream(ByteBuffer buf) {
            this.internal = buf;
        }

        @Override
        public int available() {
            return this.internal.remaining();
        }

        @Override
        public int read() throws IOException {
            return this.internal.hasRemaining() ? (this.internal.get() & 0xFF) : -1;
        }

        @Override
        public int read(byte @NotNull [] bytes, int off, int len) throws IOException {
            if (!this.internal.hasRemaining()) return -1;
            len = Math.min(len, this.internal.remaining());
            this.internal.get(bytes, off, len);
            return len;
        }
    }

    public class Sector {
        private final int index;
        private long offset;
        private long length;
        private boolean hasData = false;

        private Sector(int index, long offset, long length) {
            this.index = index;
            this.offset = offset;
            this.length = length;
        }

        public void store(@NotNull ByteBuffer newData, @NotNull FileChannel channel) throws IOException {
            final long oldLength = this.length;
            final long oldLive = this.hasData ? oldLength : 0L;
            final long newDataLength = newData.remaining();

            this.hasData = true;
            this.length = newDataLength;

            // data fits into the extent this sector already owns (a cleared sector keeps
            // its stale extent exactly for this reuse), write it in place
            if (newDataLength <= oldLength) {
                writeFullyAt(channel, newData, this.offset);
            } else {
                // or we will append to the end of file
                this.offset = BufferedLinearRegionFile.this.currentAcquiredIndex;
                BufferedLinearRegionFile.this.currentAcquiredIndex = this.offset + newDataLength;

                writeFullyAt(channel, newData, this.offset);
            }

            // single mutator under the region write lock; keeps the garbage estimate
            // in flushInternal() lock-free and scan-free
            BufferedLinearRegionFile.this.liveBytes += newDataLength - oldLive;
        }

        public void clear() {
            if (this.hasData) {
                BufferedLinearRegionFile.this.liveBytes -= this.length;
            }

            this.hasData = false;
        }

        public boolean hasData() {
            return this.hasData;
        }
    }

    private class ChunkBufferHelper extends ByteArrayOutputStream implements ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer {
        private final ChunkPos pos;
        private boolean writeOnClose = true;

        private ChunkBufferHelper(ChunkPos pos) {
            // chunk NBT payloads are tens to hundreds of KiB: BAOS's default 32 bytes
            // means a dozen grow-and-copy rounds per single chunk serialization
            super(8192);
            this.pos = pos;
        }

        @Override
        public void close() throws IOException {
            if (this.writeOnClose) {
                this.moonrise$write(BufferedLinearRegionFile.this);
            }
        }

        @Override
        public boolean moonrise$getWriteOnClose() {
            return this.writeOnClose;
        }

        @Override
        public void moonrise$setWriteOnClose(boolean value) {
            this.writeOnClose = value;
        }

        @Override
        public void moonrise$write(me.earthme.luminol.data.RegionFile regionFile) throws IOException {
            regionFile.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
            regionFile.flush();
        }

        @Override
        public void write(int value) {
            if (this.count >= MAX_SIZE_PER_CHUNK) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + ((long) this.count + 1));
            }
            super.write(value);
        }

        @Override
        public void write(byte[] data, int offset, int length) {
            if (length > MAX_SIZE_PER_CHUNK - this.count) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + ((long) this.count + length));
            }
            super.write(data, offset, length);
        }
    }

    private class MasterFileParser {
        // V3 bucketed format layout (UNCHANGED, fully compatible with existing files):
        //   [0,  14): header — superblock(8) + version(1) + compressionLevel(1) + xxHash32Seed(4)
        //   [14, 142): position table — BUCKET_COUNT(16) × long(8) each; 0 = no data for that bucket
        //   [142, EOF): bucket records — originalLen(int) + compressedLen(int) + compressedData
        private static final int V3_HEADER_SIZE = 14;
        private static final long V3_POS_TABLE_OFFSET = V3_HEADER_SIZE;
        private static final int V3_POS_TABLE_SIZE = BUCKET_COUNT * Long.BYTES; // 128
        private static final long V3_DATA_AREA_OFFSET = V3_POS_TABLE_OFFSET + V3_POS_TABLE_SIZE; // 142
        private static final int V3_RECORD_HEADER_SIZE = Integer.BYTES * 2; // originalLen + compressedLen

        private final ReadWriteLock masterFileLock = new ReentrantReadWriteLock();

        // WAL(append) state, guarded by masterFileLock: non-null whenever a valid v3
        // master file is open for appending — restored directly at open time by
        // tryEnterWalMode(), or (re)established by rewriteFully(); syncs then only
        // append changed buckets to the tail and update the position table in place.
        // recordSizes mirrors positionTable (size of each live record) so the garbage
        // ratio can be computed without touching the disk
        private @Nullable FileChannel appendChannel;
        private long[] positionTable;
        private long[] recordSizes;
        private long appendOffset;

        // single volatile read instead of a Files.exists() stat per chunk write
        private volatile boolean fileExists;

        // a consistent snapshot of one bucket taken from the swap file;
        // payload == null means the bucket holds no chunks at all
        private record BucketRecord(long epoch, @Nullable ByteBuffer payload) {
        }

        public boolean masterFileExists() {
            return this.fileExists;
        }

        // resumes WAL mode from an existing, structurally valid v3 master file so the
        // first sync after open can append instead of rewriting the entire file.
        // bails out silently (leaving the full-rewrite path armed) if the file is
        // missing, not v3, or its position table doesn't validate
        public void tryEnterWalMode(@NotNull Path mainFile) throws IOException {
            this.masterFileLock.writeLock().lock();
            try {
                // legacy migration in tryParseMainFileOld() may have entered WAL already
                if (this.appendChannel != null) {
                    return;
                }

                if (!Files.exists(mainFile)) {
                    return;
                }

                this.fileExists = true;

                final FileChannel channel = FileChannel.open(mainFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
                boolean success = false;
                try {
                    final long fileSize = channel.size();

                    if (fileSize < V3_DATA_AREA_OFFSET) {
                        return;
                    }

                    final ByteBuffer header = ByteBuffer.allocate(V3_HEADER_SIZE);
                    readFullyAt(channel, header, 0);
                    header.flip();

                    if (header.getLong() != MASTER_FILE_SUPER_BLOCK || header.get() != MASTER_FILE_VERSION_BUCKET) {
                        return;
                    }

                    final long[] table = this.parseOffsetTable(channel);
                    final long[] sizes = new long[BUCKET_COUNT];
                    long dataEnd = V3_DATA_AREA_OFFSET;

                    for (int i = 0; i < BUCKET_COUNT; i++) {
                        final long recordOffset = table[i];

                        if (recordOffset == 0) {
                            continue;
                        }

                        if (recordOffset < V3_DATA_AREA_OFFSET || recordOffset + V3_RECORD_HEADER_SIZE > fileSize) {
                            return; // corrupted table: stay in full-rewrite mode
                        }

                        final ByteBuffer lens = this.readRecordLengths(channel, recordOffset);
                        final int originalLen = lens.getInt();
                        final int compressedLen = lens.getInt();

                        if (originalLen < 0 || compressedLen < 0 || recordOffset + V3_RECORD_HEADER_SIZE + compressedLen > fileSize) {
                            return; // corrupted record header: stay in full-rewrite mode
                        }

                        sizes[i] = V3_RECORD_HEADER_SIZE + (long) compressedLen;
                        dataEnd = Math.max(dataEnd, recordOffset + sizes[i]);
                    }

                    // append after the last referenced record: anything past that is
                    // uncommitted garbage from a torn previous append and may be reused
                    this.appendChannel = channel;
                    this.positionTable = table;
                    this.recordSizes = sizes;
                    this.appendOffset = dataEnd;
                    success = true;
                } finally {
                    if (!success) {
                        channel.close();
                    }
                }
            } finally {
                this.masterFileLock.writeLock().unlock();
            }
        }

        // must be called under syncLock (see syncToMasterFile)
        public void sync(@NotNull Path mainFile, boolean forceCompact, boolean noSwapLock, boolean noMasterLock) throws IOException {
            if (!noMasterLock) this.masterFileLock.writeLock().lock();
            try {
                // full rewrite whenever no valid append state exists (fresh region /
                // corrupted table / legacy migration), and afterwards whenever the
                // appended garbage passed the auto-compact threshold: writes a tmp file,
                // then atomically replaces the master file with it
                if (this.appendChannel == null || this.shouldCompactMasterFile() || forceCompact) {
                    this.rewriteFully(mainFile, noSwapLock);
                } else {
                    // WAL-style otherwise: only append the dirty buckets
                    this.appendDirtyBuckets(noSwapLock);
                }
            } finally {
                if (!noMasterLock) this.masterFileLock.writeLock().unlock();
            }
        }

        // only valid in WAL mode (appendChannel != null); mirrors the swap file heuristic
        private boolean shouldCompactMasterFile() {
            long liveSize = 0;
            for (final long size : this.recordSizes) {
                liveSize += size;
            }

            final long spareSize = this.appendOffset - V3_DATA_AREA_OFFSET - liveSize;

            return spareSize > MASTER_FILE_AUTO_COMPACT_SIZE && (double) spareSize > ((double) liveSize) * MASTER_FILE_AUTO_COMPACT_PERCENT;
        }

        private void rewriteFully(@NotNull Path mainFile, boolean noSwapLock) throws IOException {
            final boolean wal = this.appendChannel != null;
            final Path tmpFilePath = Path.of(mainFile + ".tmp");
            final long[] syncedBucketEpochs = new long[BUCKET_COUNT];
            final long[] newPositionTable = new long[BUCKET_COUNT];
            final long[] newRecordSizes = new long[BUCKET_COUNT];
            final long newAppendOffset;

            FileChannel legacySource = null;
            try {
                final FileChannel oldChannel;
                final long[] oldPositionTable;

                if (wal) {
                    // reuse the live append channel as the copy source together with the
                    // cached table/sizes: no reopen and no per-bucket length pread needed
                    oldChannel = this.appendChannel;
                    oldPositionTable = this.positionTable;
                } else {
                    legacySource = this.openV3MasterFile(mainFile);
                    oldChannel = legacySource;
                    oldPositionTable = oldChannel == null ? null : this.parseOffsetTable(oldChannel);
                }

                try (FileChannel outChannel = FileChannel.open(tmpFilePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    this.writeV3Header(outChannel);

                    // position table placeholder (all zeros, filled in at the end)
                    writeFullyAt(outChannel, ByteBuffer.allocate(V3_POS_TABLE_SIZE), V3_POS_TABLE_OFFSET);

                    long dataOffset = V3_DATA_AREA_OFFSET;

                    for (int bucketIndex = 0; bucketIndex < BUCKET_COUNT; bucketIndex++) {
                        if (BufferedLinearRegionFile.this.isBucketDirty(bucketIndex)) {
                            final BucketRecord record = this.buildBucketRecord(bucketIndex, noSwapLock);

                            if (record.payload() != null) {
                                final int recordSize = record.payload().remaining();

                                writeFullyAt(outChannel, record.payload(), dataOffset);
                                newPositionTable[bucketIndex] = dataOffset;
                                newRecordSizes[bucketIndex] = recordSize;
                                dataOffset += recordSize;
                            }
                            // else: the bucket is empty now, its table entry stays 0

                            syncedBucketEpochs[bucketIndex] = record.epoch();
                        } else if (oldPositionTable != null && oldPositionTable[bucketIndex] != 0) {
                            // not dirty: copy the record bytes straight from the old file
                            final long oldOffset = oldPositionTable[bucketIndex];
                            final long recordSize;

                            if (wal) {
                                recordSize = this.recordSizes[bucketIndex];
                            } else {
                                final ByteBuffer lens = this.readRecordLengths(oldChannel, oldOffset);
                                lens.getInt(); // skip originalLen
                                recordSize = V3_RECORD_HEADER_SIZE + (long) lens.getInt();
                            }

                            transferFully(oldChannel, oldOffset, recordSize, outChannel, dataOffset);
                            newPositionTable[bucketIndex] = dataOffset;
                            newRecordSizes[bucketIndex] = recordSize;
                            dataOffset += recordSize;
                        }
                    }

                    // write the finalized position table
                    writeFullyAt(outChannel, this.encodePositionTable(newPositionTable), V3_POS_TABLE_OFFSET);

                    outChannel.force(true);

                    newAppendOffset = dataOffset;
                }
            } catch (Throwable e) {
                // don't leak the half-written tmp file; in WAL mode the append state is
                // untouched so the next sync just retries the compact, in legacy mode
                // the next sync retries this full-rewrite path
                try {
                    Files.deleteIfExists(tmpFilePath);
                } catch (Throwable e2) {
                    e.addSuppressed(e2);
                }

                throw e instanceof IOException io ? io : new IOException("Failed to rewrite master file!", e);
            } finally {
                if (legacySource != null) {
                    legacySource.close();
                }
            }

            // close the append channel before the replace: some platforms (windows)
            // refuse to replace a file that still has open handles
            if (wal) {
                final FileChannel toClose = this.appendChannel;
                this.appendChannel = null; // if close() throws, fall back to full rewrite next sync
                toClose.close();
            }

            atomicReplace(tmpFilePath, mainFile);

            // (re)enter WAL mode: keep the freshly written master file open for appending syncs
            this.appendChannel = FileChannel.open(mainFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.positionTable = newPositionTable;
            this.recordSizes = newRecordSizes;
            this.appendOffset = newAppendOffset;
            this.fileExists = true;

            this.markBucketsSynced(syncedBucketEpochs);
        }

        private void appendDirtyBuckets(boolean noSwapLock) throws IOException {
            final FileChannel channel = this.appendChannel;
            final long[] syncedBucketEpochs = new long[BUCKET_COUNT];
            final long[] newPositionTable = this.positionTable.clone();
            final long[] newRecordSizes = this.recordSizes.clone();
            final ByteBuffer[] pending = new ByteBuffer[BUCKET_COUNT];
            long dataOffset = this.appendOffset;
            int pendingCount = 0;
            boolean anyDirty = false;

            for (int bucketIndex = 0; bucketIndex < BUCKET_COUNT; bucketIndex++) {
                if (!BufferedLinearRegionFile.this.isBucketDirty(bucketIndex)) {
                    continue;
                }

                final BucketRecord record = this.buildBucketRecord(bucketIndex, noSwapLock);
                final ByteBuffer payload = record.payload();

                if (payload != null) {
                    pending[pendingCount++] = payload;
                    newPositionTable[bucketIndex] = dataOffset;
                    newRecordSizes[bucketIndex] = payload.remaining();
                    dataOffset += payload.remaining();
                } else {
                    // the bucket is empty now
                    newPositionTable[bucketIndex] = 0;
                    newRecordSizes[bucketIndex] = 0;
                }

                syncedBucketEpochs[bucketIndex] = record.epoch();
                anyDirty = true;
            }

            if (!anyDirty) {
                return;
            }

            if (pendingCount > 0) {
                // all records land contiguously at the tail: one gathering write (writev)
                // instead of one pwrite per dirty bucket
                channel.position(this.appendOffset);

                final ByteBuffer last = pending[pendingCount - 1];
                while (last.hasRemaining()) {
                    channel.write(pending, 0, pendingCount);
                }

                // make the appended records durable before the position table may point at them
                channel.force(false);
            }

            // commit the new tail first: even a torn position table write can then never
            // cause a later append to overwrite records the on-disk table already references
            this.appendOffset = dataOffset;

            writeFullyAt(channel, this.encodePositionTable(newPositionTable), V3_POS_TABLE_OFFSET);
            channel.force(true);

            this.positionTable = newPositionTable;
            this.recordSizes = newRecordSizes;

            this.markBucketsSynced(syncedBucketEpochs);
        }

        // snapshots one bucket under a short read lock (raw sector bytes only, with
        // sectors that sit back to back in the swap file coalesced into single preads);
        // LZ4 decompression and zstd compression both run outside any lock so writers
        // are only blocked while the raw bytes are copied
        private @NotNull BucketRecord buildBucketRecord(int bucketIndex, final boolean noLock) throws IOException {
            final int baseChunkIndex = bucketIndex << BUCKET_SHIFT;
            final ByteBuffer[] rawSectors = new ByteBuffer[BUCKET_SIZE]; // slices into run buffers, null = no data

            final long[] offsets = new long[BUCKET_SIZE];
            final long[] lengths = new long[BUCKET_SIZE];
            final int[] slots = new int[BUCKET_SIZE];
            int liveCount = 0;

            final long epoch;

            if (!noLock) BufferedLinearRegionFile.this.regionObjectLock.readLock().lock();
            try {
                // the epoch is taken before the data: writes completing afterwards bump
                // it further, so they simply get picked up by the next sync round
                epoch = BufferedLinearRegionFile.this.getBucketWriteEpoch(bucketIndex);

                for (int i = 0; i < BUCKET_SIZE; i++) {
                    final Sector sector = BufferedLinearRegionFile.this.sectors[baseChunkIndex + i];

                    if (!sector.hasData()) {
                        continue;
                    }

                    offsets[liveCount] = sector.offset;
                    lengths[liveCount] = sector.length;
                    slots[liveCount] = i;
                    liveCount++;
                }

                if (liveCount == 0) {
                    return new BucketRecord(epoch, null);
                }

                sortByOffset(offsets, lengths, slots, liveCount);

                int i = 0;
                while (i < liveCount) {
                    int j = i;
                    long runEnd = offsets[i] + lengths[i];

                    while (j + 1 < liveCount && offsets[j + 1] == runEnd) {
                        j++;
                        runEnd += lengths[j];
                    }

                    final ByteBuffer run = ByteBuffer.allocate((int) (runEnd - offsets[i]));
                    readFullyAt(BufferedLinearRegionFile.this.swapFileChannel, run, offsets[i]);

                    for (int k = i; k <= j; k++) {
                        rawSectors[slots[k]] = run.slice((int) (offsets[k] - offsets[i]), (int) lengths[k]);
                    }

                    i = j + 1;
                }
            } finally {
                if (!noLock) BufferedLinearRegionFile.this.regionObjectLock.readLock().unlock();
            }

            // exact size budget up front: 4 bytes size prefix per chunk slot plus
            // meta + decompressed data for the live ones — one allocation, no growing
            // ByteArrayOutputStream and no toByteArray() copy at the end
            int sectionSize = BUCKET_SIZE * Integer.BYTES;
            for (int i = 0; i < BUCKET_SIZE; i++) {
                final ByteBuffer raw = rawSectors[i];

                if (raw != null) {
                    sectionSize += SECTOR_META_SIZE + raw.getInt(raw.position());
                }
            }

            final byte[] section = new byte[sectionSize];
            final ByteBuffer sectionBuf = ByteBuffer.wrap(section);

            for (int i = 0; i < BUCKET_SIZE; i++) {
                final ByteBuffer raw = rawSectors[i];

                // note: null -> no data contained
                if (raw == null) {
                    sectionBuf.putInt(0);
                    continue;
                }

                final byte[] runArray = raw.array();
                final int rawBase = raw.arrayOffset() + raw.position();
                final int dataLen = raw.getInt(raw.position());

                sectionBuf.putInt(SECTOR_META_SIZE + dataLen);
                sectionBuf.put(runArray, rawBase, SECTOR_META_SIZE); // meta bytes carried over verbatim

                // lz4 decompresses straight into the section buffer, no intermediate arrays
                final int destPos = sectionBuf.position();
                LZ4_DECOMPRESSOR.decompress(runArray, rawBase + SECTOR_META_SIZE, section, destPos, dataLen);
                sectionBuf.position(destPos + dataLen);
            }

            // zstd compresses straight into the final payload: skips Zstd.compress()'s
            // internal bound-sized temp array plus its exact-size copy at the end
            final int bound = (int) Zstd.compressBound(sectionSize);
            final byte[] payload = new byte[V3_RECORD_HEADER_SIZE + bound];
            final long compressedLen = Zstd.compressByteArray(payload, V3_RECORD_HEADER_SIZE, bound, section, 0, sectionSize, BufferedLinearRegionFile.this.compressionLevel);

            if (Zstd.isError(compressedLen)) {
                throw new IOException("Failed to zstd compress bucket " + bucketIndex + ": " + Zstd.getErrorName(compressedLen));
            }

            final ByteBuffer result = ByteBuffer.wrap(payload, 0, V3_RECORD_HEADER_SIZE + (int) compressedLen);
            result.putInt(sectionSize);         // original (uncompressed) length
            result.putInt((int) compressedLen); // compressed length
            result.position(0);

            return new BucketRecord(epoch, result);
        }

        private static void sortByOffset(long[] offsets, long[] lengths, int[] slots, int count) {
            // n <= 64, insertion sort is plenty and allocation-free
            for (int i = 1; i < count; i++) {
                final long offset = offsets[i];
                final long length = lengths[i];
                final int slot = slots[i];
                int j = i - 1;

                while (j >= 0 && offsets[j] > offset) {
                    offsets[j + 1] = offsets[j];
                    lengths[j + 1] = lengths[j];
                    slots[j + 1] = slots[j];
                    j--;
                }

                offsets[j + 1] = offset;
                lengths[j + 1] = length;
                slots[j + 1] = slot;
            }
        }

        private void markBucketsSynced(long @NonNull [] syncedBucketEpochs) {
            for (int i = 0; i < syncedBucketEpochs.length; i++) {
                // note: a dirty bucket always has a write epoch >= 1, so 0 = untouched
                if (syncedBucketEpochs[i] != 0L) {
                    BufferedLinearRegionFile.this.markBucketSynced(i, syncedBucketEpochs[i]);
                }
            }
        }

        // opens the master file for reading if it exists and is a valid V3 bucketed file, else null
        private @Nullable FileChannel openV3MasterFile(@NotNull Path mainFile) throws IOException {
            if (!Files.exists(mainFile)) {
                return null;
            }

            final FileChannel channel = FileChannel.open(mainFile, StandardOpenOption.READ);
            try {
                if (channel.size() >= V3_DATA_AREA_OFFSET) {
                    final ByteBuffer header = ByteBuffer.allocate(V3_HEADER_SIZE);
                    readFullyAt(channel, header, 0);
                    header.flip();

                    if (header.getLong() == MASTER_FILE_SUPER_BLOCK && header.get() == MASTER_FILE_VERSION_BUCKET) {
                        return channel;
                    }
                }
            } catch (Throwable e) {
                try {
                    channel.close();
                } catch (IOException e2) {
                    e.addSuppressed(e2);
                }

                throw e;
            }

            channel.close();
            return null;
        }

        private void writeV3Header(@NotNull FileChannel channel) throws IOException {
            final ByteBuffer header = ByteBuffer.allocate(V3_HEADER_SIZE);

            header.putLong(MASTER_FILE_SUPER_BLOCK);
            header.put(MASTER_FILE_VERSION_BUCKET);
            header.put(BufferedLinearRegionFile.this.compressionLevel);
            header.putInt(XXHASH32_SEED);
            header.flip();

            writeFullyAt(channel, header, 0);
        }

        private @NotNull ByteBuffer encodePositionTable(long[] table) {
            final ByteBuffer buf = ByteBuffer.allocate(V3_POS_TABLE_SIZE);

            for (final long pos : table) {
                buf.putLong(pos);
            }

            return buf.flip();
        }

        private @NotNull ByteBuffer readRecordLengths(@NotNull FileChannel channel, long recordOffset) throws IOException {
            final ByteBuffer lens = ByteBuffer.allocate(V3_RECORD_HEADER_SIZE);

            readFullyAt(channel, lens, recordOffset);

            return lens.flip();
        }

        public boolean tryCloseNoLock() throws IOException {
            if (this.appendChannel != null && this.appendChannel.isOpen()) {
                this.appendChannel.close();
                this.appendChannel = null;
                return true;
            }

            return false;
        }

        public void closeNoLock() throws IOException {
            if (this.appendChannel != null) {
                this.appendChannel.close();
                this.appendChannel = null;
            }
        }

        private void loadBucketsFor(@NotNull Path file, int bucketIndex) throws IOException {
            final int beginChunkIndex = bucketIndex << BUCKET_SHIFT;

            this.masterFileLock.readLock().lock();
            try {
                BufferedLinearRegionFile.this.guardAgainstClosed();

                final ByteBuffer decompressed;

                if (this.appendChannel != null) {
                    // WAL mode: reuse the always-open channel and the cached position table
                    decompressed = this.readBucketData(this.appendChannel, this.positionTable[bucketIndex]);
                } else {
                    if (!Files.exists(file)) {
                        return;
                    }

                    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                        if (channel.size() < V3_DATA_AREA_OFFSET) {
                            return;
                        }

                        this.checkV3Header(channel);

                        decompressed = this.readBucketData(channel, this.parseOffsetTable(channel)[bucketIndex]);
                    }
                }

                if (decompressed != null) {
                    this.loadChunksFromBucketData(decompressed, beginChunkIndex);
                }
            } finally {
                this.masterFileLock.readLock().unlock();
            }
        }

        private void checkV3Header(@NotNull FileChannel channel) throws IOException {
            final ByteBuffer headerBuf = ByteBuffer.allocate(V3_HEADER_SIZE);
            readFullyAt(channel, headerBuf, 0);
            headerBuf.flip();

            final long superblock = headerBuf.getLong();
            if (superblock != MASTER_FILE_SUPER_BLOCK)
                throw new IOException("Invalid superblock " + superblock + "!");

            final byte version = headerBuf.get();
            if (version != MASTER_FILE_VERSION_BUCKET)
                throw new IOException("Unknown version: " + version);

            // compressionLevel and hashSeed are not used here
        }

        // reads and decompresses one bucket record; null when the table entry is empty
        private @Nullable ByteBuffer readBucketData(@NotNull FileChannel channel, long recordOffset) throws IOException {
            if (recordOffset == 0) {
                return null;
            }

            final ByteBuffer lens = this.readRecordLengths(channel, recordOffset);
            final int originalLen = lens.getInt();
            final int compressedLen = lens.getInt();

            final byte[] compressedData = new byte[compressedLen];
            readFullyAt(channel, ByteBuffer.wrap(compressedData), recordOffset + V3_RECORD_HEADER_SIZE);

            return ByteBuffer.wrap(Zstd.decompress(compressedData, originalLen));
        }

        private long @NonNull [] parseOffsetTable(FileChannel channel) throws IOException {
            final ByteBuffer buf = ByteBuffer.allocate(V3_POS_TABLE_SIZE);
            readFullyAt(channel, buf, V3_POS_TABLE_OFFSET);
            buf.flip();

            final long[] table = new long[BUCKET_COUNT];

            for (int i = 0; i < BUCKET_COUNT; i++) {
                table[i] = buf.getLong();
            }

            return table;
        }

        private void loadChunksFromBucketData(ByteBuffer decompressed, int beginChunkIndex) throws IOException {
            for (int chunkIndex = beginChunkIndex; chunkIndex < beginChunkIndex + BUCKET_SIZE; chunkIndex++) {
                final int chunkSectionDataSize = decompressed.getInt();
                if (chunkSectionDataSize <= 0) continue;

                // slice instead of copying the section bytes out
                final ByteBuffer section = decompressed.slice(decompressed.position(), chunkSectionDataSize);
                decompressed.position(decompressed.position() + chunkSectionDataSize);

                BufferedLinearRegionFile.this.writeSection(chunkIndex, section, true);
            }
        }

        private void parseLinearV2(@NonNull DataInputStream ioStream, Path file) throws IOException {
            try (ioStream) {
                ioStream.readLong(); // Skip newestTimestamp (Long)

                byte gridSize = ioStream.readByte();
                if (gridSize != 1 && gridSize != 2 && gridSize != 4 && gridSize != 8 && gridSize != 16 && gridSize != 32)
                    throw new RuntimeException("Invalid grid size: " + gridSize + " file " + file);
                int bucketSize = 32 / gridSize;

                ioStream.readInt(); // Skip region_x (Int)
                ioStream.readInt(); // Skip region_z (Int)

                ioStream.skipBytes(128); // Skip existence bitmap

                // Skip NBT features
                while (true) {
                    byte featureNameLength = ioStream.readByte();
                    if (featureNameLength == 0) break;
                    byte[] featureNameBytes = new byte[featureNameLength];
                    ioStream.readFully(featureNameBytes);
                    ioStream.readInt(); // featureValue
                }

                // Read bucket metadata
                int totalBuckets = gridSize * gridSize;
                int[] bucketSizes = new int[totalBuckets];
                byte[] bucketCompressionLevels = new byte[totalBuckets];
                long[] bucketHashes = new long[totalBuckets];
                for (int i = 0; i < totalBuckets; i++) {
                    bucketSizes[i] = ioStream.readInt();
                    bucketCompressionLevels[i] = ioStream.readByte();
                    bucketHashes[i] = ioStream.readLong();
                }

                // Read and decompress each bucket, load chunks into swap
                for (int bx = 0; bx < gridSize; bx++) {
                    for (int bz = 0; bz < gridSize; bz++) {
                        int bucketIdx = bx * gridSize + bz;

                        if (bucketSizes[bucketIdx] <= 0) continue;

                        byte[] compressedBucket = new byte[bucketSizes[bucketIdx]];
                        ioStream.readFully(compressedBucket);

                        long rawHash = LongHashFunction.xx().hashBytes(compressedBucket);
                        if (rawHash != bucketHashes[bucketIdx]) {
                            throw new IOException("Region file hash incorrect for bucket " + bucketIdx + " in " + file);
                        }

                        ByteArrayInputStream bucketByteStream = new ByteArrayInputStream(compressedBucket);
                        ZstdInputStream zstdStream = new ZstdInputStream(bucketByteStream);
                        ByteBuffer bucketBuffer = ByteBuffer.wrap(zstdStream.readAllBytes());
                        zstdStream.close();

                        for (int cx = 0; cx < bucketSize; cx++) {
                            for (int cz = 0; cz < bucketSize; cz++) {
                                int chunkX = bx * bucketSize + cx;
                                int chunkZ = bz * bucketSize + cz;
                                int chunkIndex = chunkX + chunkZ * 32;

                                int chunkSize = bucketBuffer.getInt();
                                long timestamp = bucketBuffer.getLong();

                                if (chunkSize > 0) {
                                    // chunkSize includes the 8 bytes of timestamp already written
                                    int dataLen = chunkSize - 8;
                                    byte[] chunkData = new byte[dataLen];
                                    bucketBuffer.get(chunkData);

                                    // Mark bucket as loaded. writeChunk() bumps the bucket epoch so it gets synced to the new master format.
                                    BufferedLinearRegionFile.this.markBucketLoaded(chunkIndex);

                                    // Use writeChunk to go through the full path (adds length + timestamp + xxhash header)
                                    BufferedLinearRegionFile.this.writeChunk(chunkX, chunkZ, ByteBuffer.wrap(chunkData));
                                }
                            }
                        }
                    }
                }

                // Footer validation
                long footerSuperBlock = ioStream.readLong();
                if (footerSuperBlock != LINEAR_FILE_SUPER_BLOCK) {
                    throw new IOException("Footer superblock invalid " + file);
                }
            }
        }

        private boolean tryParseBlinearV2(@NotNull DataInputStream ioStream, Path file) throws IOException {
            final byte version = ioStream.readByte();

            // we will parse dynamically (V3)
            if (version == MASTER_FILE_VERSION_BUCKET) {
                ioStream.close();
                return false;
            }

            if (version != MASTER_FILE_VERSION)
                throw new RuntimeException("Invalid version: " + version + " in " + file);

            // Skip newestTimestamp (Long) + Compression level (Byte): Unused.
            ioStream.skipBytes(9);

            try (final ZstdInputStream decompressStream = new ZstdInputStream(ioStream)) {
                // only used as a helper stream
                // the parent stream will be closed in the try-catch block upper
                final DataInputStream decompressedStreamHelper = new DataInputStream(decompressStream);

                for (int index = 0; index < 1024; index++) {
                    int size = decompressedStreamHelper.readInt(); // len

                    if (size > 0) {
                        byte[] sectorData = new byte[size];
                        decompressedStreamHelper.readFully(sectorData, 0, size); // data

                        final ByteBuffer sectorDataNioBuffer = ByteBuffer.wrap(sectorData);

                        BufferedLinearRegionFile.this.markBucketLoaded(index);
                        // blinear v2 stored the exact section layout, feed it through the section path
                        BufferedLinearRegionFile.this.writeSection(index, sectorDataNioBuffer, false);
                    }
                }
            }

            return true;
        }

        @Contract(value = "_ -> new", pure = true)
        public static int @NotNull [] coordinatesFromIndex(int chunkIndex) {
            int x = chunkIndex & 31;
            int z = (chunkIndex >> 5) & 31;
            return new int[]{x, z};
        }

        private void parseLinearV1(@NotNull DataInputStream ioStream) throws IOException {
            // Skip newestTimestamp (Long) + Compression level (Byte) + Chunk count (Short): Unused.
            ioStream.skipBytes(11);
            // Skip chunk data len(Int)(Unused).
            ioStream.skipBytes(4);
            // Skip data hash (Long): Unused.
            ioStream.skipBytes(8);

            try (final ZstdInputStream decompressedStream = new ZstdInputStream(ioStream)) {
                // only used as a helper stream
                // the parent stream will be closed in the try-catch block upper
                final DataInputStream bufferHelper = new DataInputStream(decompressedStream);

                final int[] chunkStarts = new int[1024];
                for (int i = 0; i < 1024; i++) {
                    chunkStarts[i] = bufferHelper.readInt();
                    bufferHelper.skipBytes(4); // Skip timestamps (Int): Unused.
                }

                for (int i = 0; i < 1024; i++) {
                    if (chunkStarts[i] > 0) {
                        int size = chunkStarts[i];
                        byte[] chunkData = new byte[size];
                        bufferHelper.readFully(chunkData);

                        final ByteBuffer chunkDataNioBuffer = ByteBuffer.wrap(chunkData);

                        final int[] posByAxis = coordinatesFromIndex(i);

                        final int x = posByAxis[0];
                        final int z = posByAxis[1];

                        BufferedLinearRegionFile.this.markBucketLoaded(i);
                        BufferedLinearRegionFile.this.writeChunk(x, z, chunkDataNioBuffer);
                    }
                }
            }
        }

        // won't and need not hold any region locks as we are calling this in a safe point (initially newed)
        public void tryParseMainFileOld(@NotNull Path mainFilePath) throws IOException {
            final File file = mainFilePath.toFile();

            if (!file.exists() || !file.canRead()) {
                return;
            }

            // those streams will be closed in the parse logic, or we will close it manually
            final FileInputStream fileStream = new FileInputStream(file);
            final DataInputStream rawDataStream = new DataInputStream(fileStream);

            boolean oldParsed = false;
            final long superBlock;
            try {
                superBlock = rawDataStream.readLong();

                if (superBlock == MASTER_FILE_SUPER_BLOCK) {
                    oldParsed = this.tryParseBlinearV2(rawDataStream, mainFilePath);

                    // false -> v3 -> closed in parse block
                    if (!oldParsed) {
                        return;
                    }
                }

                if (superBlock == LINEAR_FILE_SUPER_BLOCK) {
                    final byte version = rawDataStream.readByte();

                    if (version == 1 || version == 2) {
                        this.parseLinearV1(rawDataStream);

                        oldParsed = true;
                    }

                    if (version == 3) {
                        this.parseLinearV2(rawDataStream, mainFilePath);

                        oldParsed = true;
                    }
                }

            } catch (Throwable ex) {
                try {
                    rawDataStream.close();
                } catch (IOException ex2) {
                    ex.addSuppressed(ex2);
                }

                throw new IOException("Failed to parse master file: " + mainFilePath, ex);
            }

            // old parsed, remove the original file, and we will recreate it as we sync
            if (oldParsed) {
                // immediately do sync operation
                BufferedLinearRegionFile.this.syncToMasterFile(true, true, false, false);
                return;
            }

            // anyone non-matched, close stream and throw the error
            rawDataStream.close();

            throw new IOException("Unknown or unsupported super block : " + superBlock);
        }
    }
}
