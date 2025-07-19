package org.stupidcraft.linearpaper.region;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.mojang.logging.LogUtils;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.dreeam.leaf.config.modules.misc.RegionFormatConfig;
import org.slf4j.Logger;

public class LinearRegionFile implements IRegionFile {

    private static final long SUPERBLOCK = -4323716122432332390L;
    private static final byte VERSION = 2;
    private static final int HEADER_SIZE = 32;
    private static final int FOOTER_SIZE = 8;
    private static final int CHUNK_COUNT = 1024;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Byte> SUPPORTED_VERSIONS = Arrays.asList((byte) 1, (byte) 2);

    // Buffer arrays for each chunk
    private final byte[][] buffer = new byte[CHUNK_COUNT][];
    private final int[] bufferUncompressedSize = new int[CHUNK_COUNT];
    private final int[] chunkTimestamps = new int[CHUNK_COUNT];

    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;
    private final int compressionLevel;
    public boolean closed = false;
    public Path path;
    private volatile long lastFlushed = System.nanoTime();

    public LinearRegionFile(Path file, int compression) throws IOException {
        this.path = file;
        this.compressionLevel = compression;
        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();

        File regionFile = this.path.toFile();
        Arrays.fill(this.bufferUncompressedSize, 0);

        if (!regionFile.canRead()) {
            return;
        }

        try (FileInputStream fileStream = new FileInputStream(regionFile);
             DataInputStream rawDataStream = new DataInputStream(fileStream)) {

            long fileSuperBlock = rawDataStream.readLong();
            if (fileSuperBlock != SUPERBLOCK) {
                throw new RuntimeException("Invalid superblock: " + fileSuperBlock + " in " + file);
            }

            byte version = rawDataStream.readByte();
            if (!SUPPORTED_VERSIONS.contains(version)) {
                throw new RuntimeException("Invalid version: " + version + " in " + file);
            }

            // Skip newestTimestamp (Long) + Compression level (Byte) + Chunk count (Short): Unused.
            rawDataStream.skipBytes(11);

            int dataCount = rawDataStream.readInt();
            long fileLength = regionFile.length();
            if (fileLength != HEADER_SIZE + dataCount + FOOTER_SIZE) {
                throw new IOException("Invalid file length: " + this.path + " " + fileLength + " " +
                    (HEADER_SIZE + dataCount + FOOTER_SIZE));
            }

            rawDataStream.skipBytes(8); // Skip data hash (Long): Unused.

            byte[] rawCompressed = new byte[dataCount];
            rawDataStream.readFully(rawCompressed, 0, dataCount);

            // Verify footer superblock.
            fileSuperBlock = rawDataStream.readLong();
            if (fileSuperBlock != SUPERBLOCK) {
                throw new IOException("Footer superblock invalid " + this.path);
            }

            try (DataInputStream dataStream = new DataInputStream(
                new ZstdInputStream(new ByteArrayInputStream(rawCompressed)))) {

                int[] starts = new int[CHUNK_COUNT];
                // Read header data for each chunk.
                for (int i = 0; i < CHUNK_COUNT; i++) {
                    starts[i] = dataStream.readInt();
                    dataStream.skipBytes(4); // Skip timestamps (Int): Unused.
                }
                // Process each chunk with non-zero data.
                for (int i = 0; i < CHUNK_COUNT; i++) {
                    if (starts[i] > 0) {
                        int size = starts[i];
                        byte[] b = new byte[size];
                        dataStream.readFully(b, 0, size);
                        int maxCompressedLength = this.compressor.maxCompressedLength(size);
                        byte[] compressed = new byte[maxCompressedLength];
                        int compressedLength = this.compressor.compress(b, 0, size, compressed, 0, maxCompressedLength);
                        byte[] finalBuffer = new byte[compressedLength];
                        System.arraycopy(compressed, 0, finalBuffer, 0, compressedLength);
                        this.buffer[i] = finalBuffer;
                        this.bufferUncompressedSize[i] = size;
                    }
                }
            }
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private static int getTimestamp() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    public void flush() throws IOException {
        flushWrapper(); // sync flush call.
    }

    public void flushWrapper() {
        try {
            save();
        } catch (IOException e) {
            LOGGER.error("Failed to flush region file {}", path.toAbsolutePath(), e);
        }
    }

    public boolean doesChunkExist(ChunkPos pos) throws Exception {
        throw new Exception("doesChunkExist is a stub");
    }

    private synchronized void save() throws IOException {
        long timestamp = getTimestamp();
        short chunkCount = 0;
        File tempFile = new File(path.toString() + ".tmp");

        try (FileOutputStream fileStream = new FileOutputStream(tempFile);
             ByteArrayOutputStream zstdByteArray = new ByteArrayOutputStream();
             ZstdOutputStream zstdStream = new ZstdOutputStream(zstdByteArray, this.compressionLevel);
             DataOutputStream zstdDataStream = new DataOutputStream(zstdStream);
             DataOutputStream dataStream = new DataOutputStream(fileStream)) {

            // Write header.
            dataStream.writeLong(SUPERBLOCK);
            dataStream.writeByte(VERSION);
            dataStream.writeLong(timestamp);
            dataStream.writeByte(this.compressionLevel);

            ArrayList<byte[]> byteBuffers = new ArrayList<>(CHUNK_COUNT);
            // For each chunk, decompress recorded data if available.
            for (int i = 0; i < CHUNK_COUNT; i++) {
                if (this.bufferUncompressedSize[i] != 0) {
                    chunkCount++;
                    byte[] content = new byte[this.bufferUncompressedSize[i]];
                    this.decompressor.decompress(buffer[i], 0, content, 0, this.bufferUncompressedSize[i]);
                    byteBuffers.add(content);
                } else {
                    byteBuffers.add(null);
                }
            }

            // Write header info for each chunk: uncompressed size and timestamp.
            for (int i = 0; i < CHUNK_COUNT; i++) {
                zstdDataStream.writeInt(this.bufferUncompressedSize[i]); // Uncompressed size.
                zstdDataStream.writeInt(this.chunkTimestamps[i]);         // Timestamp.
            }
            // Write chunk data sequentially.
            for (int i = 0; i < CHUNK_COUNT; i++) {
                byte[] content = byteBuffers.get(i);
                if (content != null) {
                    zstdDataStream.write(content, 0, content.length);
                }
            }
            zstdDataStream.close();

            // Write additional file header/footer data.
            dataStream.writeShort(chunkCount);
            byte[] compressed = zstdByteArray.toByteArray();
            dataStream.writeInt(compressed.length);
            dataStream.writeLong(0); // Placeholder for data hash.
            dataStream.write(compressed, 0, compressed.length);
            dataStream.writeLong(SUPERBLOCK);
            dataStream.flush();
            fileStream.getFD().sync();
            fileStream.getChannel().force(true); // Ensure atomicity (e.g. with Btrfs).
        }
        Files.move(tempFile.toPath(), this.path, StandardCopyOption.REPLACE_EXISTING);
        this.lastFlushed = System.nanoTime();
    }

    public synchronized void write(ChunkPos pos, ByteBuffer buf) {
        try {
            byte[] data = toByteArray(new ByteArrayInputStream(buf.array()));
            int uncompressedSize = data.length;
            int maxCompressedLength = this.compressor.maxCompressedLength(uncompressedSize);
            byte[] compressed = new byte[maxCompressedLength];
            int compressedLength = this.compressor.compress(data, 0, uncompressedSize, compressed, 0, maxCompressedLength);
            byte[] finalCompressed = new byte[compressedLength];
            System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

            int index = getChunkIndex(pos.x, pos.z);
            this.buffer[index] = finalCompressed;
            this.chunkTimestamps[index] = getTimestamp();
            this.bufferUncompressedSize[index] = uncompressedSize;
        } catch (IOException e) {
            LOGGER.error("Chunk write IOException {} {}", e, this.path);
        }
        // Try flushing if enough time has passed.
        if ((System.nanoTime() - this.lastFlushed) >= TimeUnit.SECONDS.toNanos(RegionFormatConfig.linearFlushFrequency)) {
            flushWrapper();
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) {
        return new DataOutputStream(new BufferedOutputStream(new ChunkBuffer(pos)));
    }

    @Override
    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(CompoundTag data, ChunkPos pos) {
        final DataOutputStream out = this.getChunkDataOutputStream(pos);
        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
            data, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
            out, regionFile -> out.close()
        );
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tempBuffer = new byte[4096];
        int length;
        while ((length = in.read(tempBuffer)) >= 0) {
            out.write(tempBuffer, 0, length);
        }
        return out.toByteArray();
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) {
        int index = getChunkIndex(pos.x, pos.z);
        if (this.bufferUncompressedSize[index] != 0) {
            byte[] content = new byte[this.bufferUncompressedSize[index]];
            this.decompressor.decompress(this.buffer[index], 0, content, 0, this.bufferUncompressedSize[index]);
            return new DataInputStream(new ByteArrayInputStream(content));
        }
        return null;
    }

    public void clear(ChunkPos pos) {
        int index = getChunkIndex(pos.x, pos.z);
        this.buffer[index] = null;
        this.bufferUncompressedSize[index] = 0;
        this.chunkTimestamps[index] = getTimestamp();
        flushWrapper();
    }

    public Path getPath() {
        return this.path;
    }

    public boolean hasChunk(ChunkPos pos) {
        return this.bufferUncompressedSize[getChunkIndex(pos.x, pos.z)] > 0;
    }

    public void close() throws IOException {
        if (closed) return;
        closed = true;
        flush(); // Synchronous flush.
    }

    public boolean recalculateHeader() {
        return false;
    }

    public void setOversized(int x, int z, boolean something) {
        // Stub method
    }

    public CompoundTag getOversizedData(int x, int z) throws IOException {
        throw new IOException("getOversizedData is a stub " + this.path);
    }

    public boolean isOversized(int x, int z) {
        return false;
    }

    private class ChunkBuffer extends ByteArrayOutputStream {
        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos pos) {
            super();
            this.pos = pos;
        }

        @Override
        public void close() {
            ByteBuffer byteBuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            LinearRegionFile.this.write(this.pos, byteBuffer);
        }
    }
}
