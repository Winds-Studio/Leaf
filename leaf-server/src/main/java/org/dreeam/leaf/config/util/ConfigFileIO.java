package org.dreeam.leaf.config.util;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ConfigurationMaster file loading and atomic saving utilities.
 */
public final class ConfigFileIO {

    private static final Logger LOGGER = LogManager.getLogger(ConfigFileIO.class.getSimpleName());
    private static final int MAX_CODE_POINTS = 100 * 1024 * 1024;

    public static ConfigFile load(File file) throws Exception {
        Path path = file.toPath();
        if (!Files.isRegularFile(path)) {
            return ConfigFile.loadConfig(file);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(MAX_CODE_POINTS); // Increase YAML file size limit
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (YAMLException exception) {
            throw new IOException("Malformed YAML configuration: " + path, exception);
        }
        return ConfigFile.loadConfig(file);
    }

    public static void saveAtomically(ConfigFile... configs) throws Exception {
        Set<Path> targets = new HashSet<>();
        List<SerializedConfig> serializedConfigs = new ArrayList<>(configs.length);
        for (ConfigFile config : configs) {
            Objects.requireNonNull(config, "config");
            Path target = config.getFile().toPath().toAbsolutePath().normalize();
            if (!targets.add(target)) {
                throw new IllegalArgumentException("Cannot save the same config file twice: " + target);
            }
            serializedConfigs.add(new SerializedConfig(target, config.saveToString()));
        }

        List<StagedConfig> stagedConfigs = new ArrayList<>(serializedConfigs.size());
        List<Path> temporaryFiles = new ArrayList<>(serializedConfigs.size() * 2);
        try {
            for (SerializedConfig serializedConfig : serializedConfigs) {
                Path target = serializedConfig.target();
                Path parent = Objects.requireNonNull(target.getParent(), "Config file has no parent: " + target);
                Files.createDirectories(parent);

                String prefix = "." + target.getFileName() + ".";
                Path staged = Files.createTempFile(parent, prefix, ".tmp");
                temporaryFiles.add(staged);

                boolean targetExisted = Files.exists(target);
                @Nullable Path rollback = null;
                if (targetExisted) {
                    rollback = Files.createTempFile(parent, prefix, ".rollback");
                    temporaryFiles.add(rollback);
                    Files.copy(target, rollback, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    try {
                        Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(target));
                    } catch (UnsupportedOperationException ignored) {
                    }
                }

                Files.writeString(
                    staged,
                    serializedConfig.content(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
                stagedConfigs.add(new StagedConfig(target, staged, rollback, targetExisted));
            }

            int committed = 0;
            try {
                for (StagedConfig stagedConfig : stagedConfigs) {
                    Files.move(
                        stagedConfig.staged(),
                        stagedConfig.target(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                    committed++;
                }
            } catch (Exception exception) {
                for (int index = committed - 1; index >= 0; index--) {
                    StagedConfig stagedConfig = stagedConfigs.get(index);
                    try {
                        if (stagedConfig.targetExisted()) {
                            Files.move(
                                Objects.requireNonNull(stagedConfig.rollback()),
                                stagedConfig.target(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                            );
                        } else {
                            Files.deleteIfExists(stagedConfig.target());
                        }
                    } catch (Exception rollbackException) {
                        exception.addSuppressed(rollbackException);
                        if (stagedConfig.rollback() != null) {
                            temporaryFiles.remove(stagedConfig.rollback());
                            LOGGER.error(
                                "Failed to restore config {}; rollback copy was left at {}.",
                                stagedConfig.target(), stagedConfig.rollback(), rollbackException
                            );
                        }
                    }
                }
                throw exception;
            }
        } finally {
            for (Path temporaryFile : temporaryFiles) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    LOGGER.warn("Failed to delete temporary config file {}.", temporaryFile, exception);
                }
            }
        }
    }

    private record SerializedConfig(Path target, String content) {
    }

    private record StagedConfig(Path target, Path staged, @Nullable Path rollback, boolean targetExisted) {
    }
}
