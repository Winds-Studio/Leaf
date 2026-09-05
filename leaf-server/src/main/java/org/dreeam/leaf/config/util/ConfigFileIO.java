package org.dreeam.leaf.config.util;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ConfigurationMaster file loading and saving utilities.
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

    /**
     * Saves each configuration after all callers have completed their validation.
     *
     * <p>This deliberately follows Paper's configuration persistence policy: an access-denied
     * failure is reported but leaves the fully loaded configuration active in memory. Other save
     * failures are reported to the caller after every requested target has been attempted. There
     * is no cross-file rollback.</p>
     */
    public static SaveReport save(ConfigFile... configs) throws ConfigSaveException {
        Set<Path> targets = new HashSet<>();
        List<SaveResult> results = new ArrayList<>(configs.length);
        Exception firstFailure = null;
        for (ConfigFile config : configs) {
            Objects.requireNonNull(config, "config");
            Path target = config.getFile().toPath().toAbsolutePath().normalize();
            if (!targets.add(target)) {
                throw new IllegalArgumentException("Cannot save the same config file twice: " + target);
            }
            try {
                Files.createDirectories(Objects.requireNonNull(target.getParent(), "Config file has no parent: " + target));
                config.save();
                results.add(new SaveResult(target, SaveStatus.SAVED, null));
            } catch (Exception exception) {
                if (isAccessDenied(exception)) {
                    results.add(new SaveResult(target, SaveStatus.ACCESS_DENIED, exception));
                    LOGGER.warn("Could not save Leaf config {}; using the in-memory configuration for this run.", target, exception);
                } else {
                    results.add(new SaveResult(target, SaveStatus.FAILED, exception));
                    if (firstFailure == null) {
                        firstFailure = exception;
                    } else {
                        firstFailure.addSuppressed(exception);
                    }
                }
            }
        }
        SaveReport report = new SaveReport(List.copyOf(results));
        if (firstFailure != null) {
            throw new ConfigSaveException(report, firstFailure);
        }
        return report;
    }

    private static boolean isAccessDenied(Throwable throwable) {
        for (Throwable current = throwable; current != null && current != current.getCause(); current = current.getCause()) {
            if (current instanceof AccessDeniedException) {
                return true;
            }
        }
        return false;
    }

    public enum SaveStatus {
        SAVED,
        ACCESS_DENIED,
        FAILED
    }

    public record SaveResult(Path target, SaveStatus status, Exception failure) {
    }

    public record SaveReport(List<SaveResult> results) {
        public boolean hasAccessDenied() {
            return this.results.stream().anyMatch(result -> result.status == SaveStatus.ACCESS_DENIED);
        }

        public String describe() {
            return this.results.stream()
                .map(result -> result.status + ": " + result.target)
                .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    public static final class ConfigSaveException extends Exception {

        private final SaveReport report;

        private ConfigSaveException(SaveReport report, Exception cause) {
            super("Failed to save Leaf config files: " + report.describe(), cause);
            this.report = report;
        }

        public SaveReport report() {
            return this.report;
        }
    }
}
