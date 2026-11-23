package org.dreeam.leaf.config.migration;

import org.dreeam.leaf.config.LeafConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Purges and backs up old Leaf configs and the Pufferfish config. */
public final class LegacyConfigCleanup {

    private LegacyConfigCleanup() {
    }

    public static void purge(ConfigBackupSession backupSession) {
        boolean legacyFound = false;
        String pufferfishConfig = "pufferfish.yml";
        String leafConfigV1 = "leaf.yml";
        String leafConfigV2 = "leaf_config";

        File pufferfishConfigFile = new File(pufferfishConfig);
        File leafConfigV1File = new File(leafConfigV1);
        File leafConfigV2File = new File(leafConfigV2);

        // Only moves the legacy files, their content is not converted. A file that cannot be moved stays
        // in place and is tried again at the next start, so a failure only warns.
        if (pufferfishConfigFile.isFile()) {
            legacyFound |= moveLegacy(backupSession, pufferfishConfigFile, pufferfishConfig);
        }
        if (leafConfigV1File.isFile()) {
            legacyFound |= moveLegacy(backupSession, leafConfigV1File, leafConfigV1);
        }
        if (leafConfigV2File.isDirectory()) {
            legacyFound |= moveLegacy(backupSession, leafConfigV2File, leafConfigV2);
        }
        if (legacyFound) {
            LeafConfig.LOGGER.warn("Found legacy Leaf config files, moved to backup directory: {}", backupSession.directory());
            LeafConfig.LOGGER.warn("New Leaf config located at config/ folder, You need to transfer config to the new one manually and restart the server!");
        }
    }

    private static boolean moveLegacy(ConfigBackupSession backupSession, File file, String name) {
        try {
            backupSession.move(file.toPath(), Path.of(name));
            return true;
        } catch (IOException exception) {
            LeafConfig.LOGGER.warn("Could not move legacy config {} to the backup directory, it is left in place.", file, exception);
            return false;
        }
    }
}
