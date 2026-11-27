package abomination;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.mojang.logging.LogUtils;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.openhft.hashing.LongHashFunction;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

// LinearRegionFile_implementation_version_0_5byXymb
// Just gonna use this string to inform other forks about updates ;-)
public class LinearRegionFile implements IRegionFile {
    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;
    private static final byte VERSION = 3;
    private static final int HEADER_SIZE = 27;
    private static final int FOOTER_SIZE = 8;
    private static final Logger LOGGER = LogUtils.getLogger();

    private byte[][] bucketBuffers;
    private final byte[][] buffer = new byte[1024][];
    private final int[] bufferUncompressedSize = new int[1024];

    private final long[] chunkTimestamps = new long[1024];
    private final Object markedToSaveLock = new Object();

    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;

    private boolean markedToSave = false;
    private boolean close = false;

    public final ReentrantLock fileLock = new ReentrantLock(true);
    public Path regionFile;

    private final int compressionLevel;
    private int gridSize = 8;
    private int bucketSize = 4;
    private final Thread bindThread;

    public Path getRegionFile() {
        return this.regionFile;
    }

    public ReentrantLock getFileLock() {
        return this.fileLock;
    }

    private int chunkToBucketIdx(int chunkX, int chunkZ) {
        int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;
        return bx * gridSize + bz;
    }

    private void openBucket(int chunkX, int chunkZ) {
        chunkX = Math.floorMod(chunkX, 32);
        chunkZ = Math.floorMod(chunkZ, 32);
        int idx = chunkToBucketIdx(chunkX, chunkZ);

        if (bucketBuffers == null) return;
        if (bucketBuffers[idx] != null) {
            try {
                ByteArrayInputStream bucketByteStream = new ByteArrayInputStream(bucketBuffers[idx]);
                ZstdInputStream zstdStream = new ZstdInputStream(bucketByteStream);
                ByteBuffer bucketBuffer = ByteBuffer.wrap(zstdStream.readAllBytes());

                int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;

                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * (32 / gridSize) + cx) + (bz * (32 / gridSize) + cz) * 32;

                        int chunkSize = bucketBuffer.getInt();
                        long timestamp = bucketBuffer.getLong();
                        this.chunkTimestamps[chunkIndex] = timestamp;

                        if (chunkSize > 0) {
                            byte[] chunkData = new byte[chunkSize - 8];
                            bucketBuffer.get(chunkData);

                            int maxCompressedLength = this.compressor.maxCompressedLength(chunkData.length);
                            byte[] compressed = new byte[maxCompressedLength];
                            int compressedLength = this.compressor.compress(chunkData, 0, chunkData.length, compressed, 0, maxCompressedLength);
                            byte[] finalCompressed = new byte[compressedLength];
                            System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                            // TODO: Optimization - return the requested chunk immediately to save on one LZ4 decompression
                            this.buffer[chunkIndex] = finalCompressed;
                            this.bufferUncompressedSize[chunkIndex] = chunkData.length;
                        }
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException("Region file corrupted: " + regionFile + " bucket: " + idx);
                // TODO: Make sure the server crashes instead of corrupting the world
            }
            bucketBuffers[idx] = null;
        }
    }

    public boolean regionFileOpen = false;

    private synchronized void openRegionFile() {
        if (regionFileOpen) return;
        regionFileOpen = true;

        File regionFile = new File(this.regionFile.toString());

        if (!regionFile.canRead()) {
            this.bindThread.start();
            return;
        }

        try {
            byte[] fileContent = Files.readAllBytes(this.regionFile);
            ByteBuffer buffer = ByteBuffer.wrap(fileContent);

            long superBlock = buffer.getLong();
            if (superBlock != SUPERBLOCK)
                throw new RuntimeException("Invalid superblock: " + superBlock + " file " + this.regionFile);

            byte version = buffer.get();
            if (version == 1 || version == 2) {
                parseLinearV1(buffer);
            } else if (version == 3) {
                parseLinearV2(buffer);
            } else {
                throw new RuntimeException("Invalid version: " + version + " file " + this.regionFile);
            }

            this.bindThread.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open region file " + this.regionFile, e);
        }
    }

    private void parseLinearV1(ByteBuffer buffer) throws IOException {
        final int HEADER_SIZE = 32;
        final int FOOTER_SIZE = 8;

        // Skip newestTimestamp (Long) + Compression level (Byte) + Chunk count (Short): Unused.
        buffer.position(buffer.position() + 11);

        int dataCount = buffer.getInt();
        long fileLength = this.regionFile.toFile().length();
        if (fileLength != HEADER_SIZE + dataCount + FOOTER_SIZE) {
            throw new IOException("Invalid file length: " + this.regionFile + " " + fileLength + " " + (HEADER_SIZE + dataCount + FOOTER_SIZE));
        }

        buffer.position(buffer.position() + 8); // Skip data hash (Long): Unused.

        byte[] rawCompressed = new byte[dataCount];
        buffer.get(rawCompressed);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawCompressed);
        ZstdInputStream zstdInputStream = new ZstdInputStream(byteArrayInputStream);
        ByteBuffer decompressedBuffer = ByteBuffer.wrap(zstdInputStream.readAllBytes());

        int[] starts = new int[1024];
        for (int i = 0; i < 1024; i++) {
            starts[i] = decompressedBuffer.getInt();
            decompressedBuffer.getInt(); // Skip timestamps (Int): Unused.
        }

        for (int i = 0; i < 1024; i++) {
            if (starts[i] > 0) {
                int size = starts[i];
                byte[] chunkData = new byte[size];
                decompressedBuffer.get(chunkData);

                int maxCompressedLength = this.compressor.maxCompressedLength(size);
                byte[] compressed = new byte[maxCompressedLength];
                int compressedLength = this.compressor.compress(chunkData, 0, size, compressed, 0, maxCompressedLength);
                byte[] finalCompressed = new byte[compressedLength];
                System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                this.buffer[i] = finalCompressed;
                this.bufferUncompressedSize[i] = size;
                this.chunkTimestamps[i] = getTimestamp(); // Use current timestamp as we don't have the original
            }
        }
    }

    private void parseLinearV2(ByteBuffer buffer) throws IOException {
        buffer.getLong(); // Skip newestTimestamp (Long)
        gridSize = buffer.get();
        if (gridSize != 1 && gridSize != 2 && gridSize != 4 && gridSize != 8 && gridSize != 16 && gridSize != 32)
            throw new RuntimeException("Invalid grid size: " + gridSize + " file " + this.regionFile);
        bucketSize = 32 / gridSize;

        buffer.getInt(); // Skip region_x (Int)
        buffer.getInt(); // Skip region_z (Int)

        boolean[] chunkExistenceBitmap = deserializeExistenceBitmap(buffer);

        while (true) {
            byte featureNameLength = buffer.get();
            if (featureNameLength == 0) break;
            byte[] featureNameBytes = new byte[featureNameLength];
            buffer.get(featureNameBytes);
            String featureName = new String(featureNameBytes);
            int featureValue = buffer.getInt();
            // System.out.println("NBT Feature: " + featureName + " = " + featureValue);
        }

        int[] bucketSizes = new int[gridSize * gridSize];
        byte[] bucketCompressionLevels = new byte[gridSize * gridSize];
        long[] bucketHashes = new long[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            bucketSizes[i] = buffer.getInt();
            bucketCompressionLevels[i] = buffer.get();
            bucketHashes[i] = buffer.getLong();
        }

        bucketBuffers = new byte[gridSize * gridSize][];
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (bucketSizes[i] > 0) {
                bucketBuffers[i] = new byte[bucketSizes[i]];
                buffer.get(bucketBuffers[i]);
                long rawHash = LongHashFunction.xx().hashBytes(bucketBuffers[i]);
                if (rawHash != bucketHashes[i]) throw new IOException("Region file hash incorrect " + this.regionFile);
            }
        }

        long footerSuperBlock = buffer.getLong();
        if (footerSuperBlock != SUPERBLOCK)
            throw new IOException("Footer superblock invalid " + this.regionFile);
    }

    public LinearRegionFile(RegionStorageInfo storageKey, Path directory, Path path, boolean dsync, int compressionLevel) throws IOException {
        this(storageKey, directory, path, RegionFileVersion.getSelected(), dsync, compressionLevel);
    }

    public LinearRegionFile(RegionStorageInfo storageKey, Path path, Path directory, RegionFileVersion compressionFormat, boolean dsync, int compressionLevel) throws IOException {
        Runnable flushCheck = () -> {
            while (!close) {
                synchronized (saveLock) {
                    if (markedToSave && activeSaveThreads < SAVE_THREAD_MAX_COUNT) {
                        activeSaveThreads++;
                        Runnable flushOperation = () -> {
                            try {
                                flush();
                            } catch (IOException ex) {
                                LOGGER.error("Region file {} flush failed", this.regionFile.toAbsolutePath(), ex);
                            } finally {
                                synchronized (saveLock) {
                                    activeSaveThreads--;
                                }
                            }
                        };

                        Thread saveThread = USE_VIRTUAL_THREAD ?
                                Thread.ofVirtual().name("Linear IO - " + LinearRegionFile.this.hashCode()).unstarted(flushOperation) :
                                Thread.ofPlatform().name("Linear IO - " + LinearRegionFile.this.hashCode()).unstarted(flushOperation);
                        saveThread.setPriority(Thread.NORM_PRIORITY - 3);
                        saveThread.start();
                    }
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(SAVE_DELAY_MS));
            }
        };
        this.bindThread = USE_VIRTUAL_THREAD ? Thread.ofVirtual().unstarted(flushCheck) : Thread.ofPlatform().unstarted(flushCheck);
        this.bindThread.setName("Linear IO Schedule - " + this.hashCode());
        this.regionFile = path;
        this.compressionLevel = compressionLevel;

        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    }

    private synchronized void markToSave() {
        synchronized (markedToSaveLock) {
            markedToSave = true;
        }
    }

    private synchronized boolean isMarkedToSave() {
        synchronized (markedToSaveLock) {
            if (markedToSave) {
                markedToSave = false;
                return true;
            }
            return false;
        }
    }

    public static int SAVE_THREAD_MAX_COUNT = 6;
    public static int SAVE_DELAY_MS = 100;
    public static boolean USE_VIRTUAL_THREAD = true;
    private static final Object saveLock = new Object();
    private static int activeSaveThreads = 0;

    /*public void run() {
        while (!close) {
            synchronized (saveLock) {
                if (markedToSave && activeSaveThreads < SAVE_THREAD_MAX_COUNT) {
                    activeSaveThreads++;
                    Thread saveThread = new Thread(() -> {
                        try {
                            flush();
                        } catch (IOException ex) {
                            LOGGER.error("Region file " + this.regionFile.toAbsolutePath() + " flush failed", ex);
                        } finally {
                            synchronized (saveLock) {
                                activeSaveThreads--;
                            }
                        }
                    }, "RegionFileFlush");
                    saveThread.setPriority(Thread.NORM_PRIORITY - 3);
                    saveThread.start();
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(SAVE_DELAY_MS));
        }
    }*/

    public synchronized boolean doesChunkExist(ChunkPos pos) throws Exception {
        openRegionFile();
        throw new Exception("doesChunkExist is a stub");
    }

    public synchronized void flush() throws IOException {
        if (!isMarkedToSave()) return;

        openRegionFile();

        long timestamp = getTimestamp();

        long writeStart = System.nanoTime();
        File tempFile = new File(regionFile.toString() + ".tmp");
        FileOutputStream fileStream = new FileOutputStream(tempFile);
        DataOutputStream dataStream = new DataOutputStream(fileStream);

        dataStream.writeLong(SUPERBLOCK);
        dataStream.writeByte(VERSION);
        dataStream.writeLong(timestamp);
        dataStream.writeByte(gridSize);

        String fileName = regionFile.getFileName().toString();
        String[] parts = fileName.split("\\.");
        int regionX = 0;
        int regionZ = 0;
        try {
            if (parts.length >= 4) {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } else {
                LOGGER.warn("Unexpected file name format: " + fileName);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Failed to parse region coordinates from file name: " + fileName, e);
        }

        dataStream.writeInt(regionX);
        dataStream.writeInt(regionZ);

        boolean[] chunkExistenceBitmap = new boolean[1024];
        for (int i = 0; i < 1024; i++) {
            chunkExistenceBitmap[i] = (this.bufferUncompressedSize[i] > 0);
        }
        writeSerializedExistenceBitmap(dataStream, chunkExistenceBitmap);

        writeNBTFeatures(dataStream);

        int bucketMisses = 0;
        byte[][] buckets = new byte[gridSize * gridSize][];
        for (int bx = 0; bx < gridSize; bx++) {
            for (int bz = 0; bz < gridSize; bz++) {
                if (bucketBuffers != null && bucketBuffers[bx * gridSize + bz] != null) {
                    buckets[bx * gridSize + bz] = bucketBuffers[bx * gridSize + bz];
                    continue;
                }
                bucketMisses++;

                ByteArrayOutputStream bucketStream = new ByteArrayOutputStream();
                ZstdOutputStream zstdStream = new ZstdOutputStream(bucketStream, this.compressionLevel);
                DataOutputStream bucketDataStream = new DataOutputStream(zstdStream);

                boolean hasData = false;
                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * 32 / gridSize + cx) + (bz * 32 / gridSize + cz) * 32;
                        if (this.bufferUncompressedSize[chunkIndex] > 0) {
                            hasData = true;
                            byte[] chunkData = new byte[this.bufferUncompressedSize[chunkIndex]];
                            this.decompressor.decompress(this.buffer[chunkIndex], 0, chunkData, 0, this.bufferUncompressedSize[chunkIndex]);
                            bucketDataStream.writeInt(chunkData.length + 8);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                            bucketDataStream.write(chunkData);
                        } else {
                            bucketDataStream.writeInt(0);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                        }
                    }
                }
                bucketDataStream.close();

                if (hasData) {
                    buckets[bx * gridSize + bz] = bucketStream.toByteArray();
                }
            }
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            dataStream.writeInt(buckets[i] != null ? buckets[i].length : 0);
            dataStream.writeByte(this.compressionLevel);
            long rawHash = 0;
            if (buckets[i] != null) {
                rawHash = LongHashFunction.xx().hashBytes(buckets[i]);
            }
            dataStream.writeLong(rawHash);
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            if (buckets[i] != null) {
                dataStream.write(buckets[i]);
            }
        }

        dataStream.writeLong(SUPERBLOCK);

        dataStream.flush();
        fileStream.getFD().sync();
        fileStream.getChannel().force(true); // Ensure atomicity on Btrfs
        dataStream.close();

        fileStream.close();
        Files.move(tempFile.toPath(), this.regionFile, StandardCopyOption.REPLACE_EXISTING);
//System.out.println("writeStart REGION FILE FLUSH " + (System.nanoTime() - writeStart) + " misses: " + bucketMisses);
    }

    private void writeNBTFeatures(DataOutputStream dataStream) throws IOException {
        // writeNBTFeature(dataStream, "example", 1);
        dataStream.writeByte(0); // End of NBT features
    }

    private void writeNBTFeature(DataOutputStream dataStream, String featureName, int featureValue) throws IOException {
        byte[] featureNameBytes = featureName.getBytes();
        dataStream.writeByte(featureNameBytes.length);
        dataStream.write(featureNameBytes);
        dataStream.writeInt(featureValue);
    }

    public static final int MAX_CHUNK_SIZE = 500 * 1024 * 1024; // Abomination - prevent chunk dupe

    public synchronized void write(ChunkPos pos, ByteBuffer buffer) {
        openRegionFile();
        openBucket(pos.x(), pos.z());

        int uncompressedSize = buffer.remaining();
        byte[] b = new byte[uncompressedSize];
        buffer.get(b);

        if (uncompressedSize > MAX_CHUNK_SIZE) {
            LOGGER.error("Chunk dupe attempt " + this.regionFile);
            clear(pos);
        } else {
            int maxCompressedLength = this.compressor.maxCompressedLength(uncompressedSize);
            byte[] compressed = new byte[maxCompressedLength];
            int compressedLength = this.compressor.compress(b, 0, uncompressedSize, compressed, 0, maxCompressedLength);
            b = new byte[compressedLength];
            System.arraycopy(compressed, 0, b, 0, compressedLength);

            int index = getChunkIndex(pos.x(), pos.z());
            this.buffer[index] = Arrays.copyOf(compressed, compressedLength);;
            this.chunkTimestamps[index] = getTimestamp();
            this.bufferUncompressedSize[index] = uncompressedSize;
        }

        markToSave();
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) {
        openRegionFile();
        openBucket(pos.x(), pos.z());
        return new DataOutputStream(new BufferedOutputStream(new LinearRegionFile.ChunkBuffer(pos)));
    }

    @Override
    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(CompoundTag data, ChunkPos pos) throws IOException {
        final DataOutputStream out = this.getChunkDataOutputStream(pos);

        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
                data, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
                out, regionFile -> out.close()
        );
    }

    private class ChunkBuffer extends ByteArrayOutputStream {

        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos chunkcoordintpair) {
            super();
            this.pos = chunkcoordintpair;
        }

        public void close() throws IOException {
            ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            LinearRegionFile.this.write(this.pos, bytebuffer);
        }
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) {
        openRegionFile();
        openBucket(pos.x(), pos.z());

        if (this.bufferUncompressedSize[getChunkIndex(pos.x(), pos.z())] != 0) {
            byte[] content = new byte[bufferUncompressedSize[getChunkIndex(pos.x(), pos.z())]];
            this.decompressor.decompress(this.buffer[getChunkIndex(pos.x(), pos.z())], 0, content, 0, bufferUncompressedSize[getChunkIndex(pos.x(), pos.z())]);
            return new DataInputStream(new ByteArrayInputStream(content));
        }
        return null;
    }

    public synchronized void clear(ChunkPos pos) {
        openRegionFile();
        openBucket(pos.x(), pos.z());
        int i = getChunkIndex(pos.x(), pos.z());
        this.buffer[i] = null;
        this.bufferUncompressedSize[i] = 0;
        this.chunkTimestamps[i] = 0;
        markToSave();
    }

    public synchronized boolean hasChunk(ChunkPos pos) {
        openRegionFile();
        openBucket(pos.x(), pos.z());
        return this.bufferUncompressedSize[getChunkIndex(pos.x(), pos.z())] > 0;
    }

    public synchronized void close() throws IOException {
        openRegionFile();
        close = true;
        try {
            flush();
        } catch (IOException e) {
            throw new IOException("Region flush IOException " + e + " " + this.regionFile);
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private static int getTimestamp() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    public boolean recalculateHeader() {
        return false;
    }

    public void setOversized(int x, int z, boolean something) {
    }

    public CompoundTag getOversizedData(int x, int z) throws IOException {
        throw new IOException("getOversizedData is a stub " + this.regionFile);
    }

    public boolean isOversized(int x, int z) {
        return false;
    }

    public Path getPath() {
        return this.regionFile;
    }

    private boolean[] deserializeExistenceBitmap(ByteBuffer buffer) {
        boolean[] result = new boolean[1024];
        for (int i = 0; i < 128; i++) {
            byte b = buffer.get();
            for (int j = 0; j < 8; j++) {
                result[i * 8 + j] = ((b >> (7 - j)) & 1) == 1;
            }
        }
        return result;
    }

    private void writeSerializedExistenceBitmap(DataOutputStream out, boolean[] bitmap) throws IOException {
        for (int i = 0; i < 128; i++) {
            byte b = 0;
            for (int j = 0; j < 8; j++) {
                if (bitmap[i * 8 + j]) {
                    b |= (1 << (7 - j));
                }
            }
            out.writeByte(b);
        }
    }
}
