package org.dreeam.leaf.config.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/** A lazily-created backup directory shared by all configuration migrations in one startup. */
public final class ConfigBackupSession {

    private Path directory;
    private boolean directorySelected;

    private ConfigBackupSession(Path directory) {
        this.directory = directory;
    }

    public static ConfigBackupSession create(Path configDirectory) {
        String timestamp = new SimpleDateFormat("yyMMdd-HHmmss").format(new Date());
        return new ConfigBackupSession(configDirectory.resolve("backup-" + timestamp));
    }

    public boolean move(Path source, Path relativeTarget) throws IOException {
        Path relative = relativeTarget.normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Invalid configuration backup path: " + relative);
        }
        Path target = this.directory().resolve(relative).normalize();
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        return true;
    }

    public Path directory() {
        this.ensureDirectory();
        return this.directory;
    }

    private void ensureDirectory() {
        if (this.directorySelected) {
            return;
        }
        this.directorySelected = true;
        if (!Files.exists(this.directory)) {
            return;
        }
        int suffix = 1;
        Path candidate;
        do {
            candidate = this.directory.resolveSibling(this.directory.getFileName() + "-" + suffix++);
        } while (Files.exists(candidate));
        this.directory = candidate;
    }
}
