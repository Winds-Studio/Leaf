package org.leavesmc.leaves.protocol.syncmatica;

import org.apache.commons.io.FilenameUtils;
import org.dreeam.leaf.config.modules.network.ProtocolSupport;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

public class SyncmaticaProtocol {

    public static final String PROTOCOL_ID = "syncmatica";
    public static final String PROTOCOL_VERSION = "leaves-syncmatica-1.1.0";
    private static final File litematicFolder = new File("." + File.separator + "syncmatics");
    private static final PlayerIdentifierProvider playerIdentifierProvider = new PlayerIdentifierProvider();
    private static final CommunicationManager communicationManager = new CommunicationManager();
    private static final FeatureSet featureSet = new FeatureSet(Arrays.asList(Feature.values()));
    private static final SyncmaticManager syncmaticManager = new SyncmaticManager();
    private static final FileStorage fileStorage = new FileStorage();
    private static final int[] ILLEGAL_CHARS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47, 34, 60, 62, 124};
    private static final String ILLEGAL_PATTERNS = "(^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$)|(^\\.\\.*$)";
    private static boolean loaded = false;

    public static File getLitematicFolder() {
        return litematicFolder;
    }

    public static PlayerIdentifierProvider getPlayerIdentifierProvider() {
        return playerIdentifierProvider;
    }

    public static CommunicationManager getCommunicationManager() {
        return communicationManager;
    }

    public static FeatureSet getFeatureSet() {
        return featureSet;
    }

    public static SyncmaticManager getSyncmaticManager() {
        return syncmaticManager;
    }

    public static FileStorage getFileStorage() {
        return fileStorage;
    }

    public static void init(boolean status) {
        if (status && !loaded) {
            litematicFolder.mkdirs();
            syncmaticManager.startup();
            loaded = true;
        } else if (!status && loaded) {
            syncmaticManager.updateServerPlacement();
            loaded = false;
        }
    }

    @NotNull
    public static UUID createChecksum(final @NotNull InputStream fis) throws NoSuchAlgorithmException, IOException {
        final byte[] buffer = new byte[4096];
        final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                messageDigest.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return UUID.nameUUIDFromBytes(messageDigest.digest());
    }

    @NotNull
    public static String sanitizeFileName(final @NotNull String badFileName) {
        String input = badFileName;
        try {
            input = FilenameUtils.getName(input);
        } catch (Exception ignored) {
        }
        final StringBuilder sanitized = new StringBuilder();
        final int len = input.codePointCount(0, input.length());

        for (int i = 0; i < len; i++) {
            final int c = input.codePointAt(i);
            if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
                sanitized.appendCodePoint(c);
                if (sanitized.length() == 255) {
                    break;
                }
            }
        }

        return sanitized.toString().replaceAll(ILLEGAL_PATTERNS, "_");
    }

    public static boolean isOverQuota(int sent) {
        return ProtocolSupport.syncmaticaQuota && sent > ProtocolSupport.syncmaticaQuotaLimit;
    }

    public static void backupAndReplace(final Path backup, final Path current, final Path incoming) {
        if (!Files.exists(incoming)) {
            return;
        }
        if (overwrite(backup, current, 2) && !overwrite(current, incoming, 4)) {
            overwrite(current, backup, 8);
        }
    }

    private static boolean overwrite(final Path backup, final Path current, final int tries) {
        if (!Files.exists(current)) {
            return true;
        }
        try {
            Files.deleteIfExists(backup);
            Files.move(current, backup);
        } catch (final IOException exception) {
            if (tries <= 0) {
                return false;
            }
            return overwrite(backup, current, tries - 1);
        }
        return true;
    }
}
