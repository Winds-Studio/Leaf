package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ConfigMigrator {

    private static final String BACKUP_PREFIX = "backup-migration-";

    public void migrate() throws Exception {
        File oldConfigFile = new File(LeafConfig.I_CONFIG_FOLDER, LeafConfig.I_GLOBAL_CONFIG_FILE);
        if (!oldConfigFile.exists()) {
            return;
        }

        // Ensure config/leaf directory exists
        LeafConfig.createDirectory(LeafConfig.I_LEAF_CONFIG_FOLDER);

        // Load the old config
        ConfigFile oldConfig = ConfigFile.loadConfig(oldConfigFile);

        // Create backup
        createBackup(oldConfigFile);

        // Create separate config files for each category in config/leaf folder
        Map<EnumConfigCategory, ConfigFile> newConfigs = new HashMap<>();

        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            File newConfigFile = new File(LeafConfig.I_LEAF_CONFIG_FOLDER, category.getFileName());
            ConfigFile newConfig = ConfigFile.loadConfig(newConfigFile);
            newConfigs.put(category, newConfig);

            // Set up basic structure
            newConfig.set("config-version", "3.0");
            newConfig.addComments("config-version", String.format("Leaf %s Config\nGitHub Repo: https://github.com/Winds-Studio/Leaf", category.name()));
        }

        // Migrate data from old config to new configs
        migrateConfigData(oldConfig, newConfigs);

        // Save all new config files
        for (ConfigFile newConfig : newConfigs.values()) {
            newConfig.save();
        }

        LeafConfig.LOGGER.info("Config migration completed. Old config backed up.");
        LeafConfig.LOGGER.info("New config files created in config/leaf/ folder.");
    }

    private void createBackup(File oldConfigFile) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String timestamp = dateFormat.format(new Date());
        String backupName = BACKUP_PREFIX + timestamp + "-" + oldConfigFile.getName();
        Path backupPath = oldConfigFile.toPath().resolveSibling(backupName);

        Files.copy(oldConfigFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
        LeafConfig.LOGGER.info("Created backup: {}", backupName);
    }

    private void migrateConfigData(ConfigFile oldConfig, Map<EnumConfigCategory, ConfigFile> newConfigs) {
        // Iterate through each category and migrate relevant sections
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            ConfigFile newConfig = newConfigs.get(category);
            String basePath = category.getBaseKeyName();

            // Get the section from old config
            ConfigSection oldSection = oldConfig.getConfigSection(basePath);
            if (oldSection != null) {
                // Copy all values from the old section to the new config
                copySection(oldSection, newConfig, basePath);
                LeafConfig.LOGGER.debug("Migrated {} section to {}", basePath, category.getFileName());
            }
        }
    }

    private void copySection(ConfigSection source, ConfigFile target, String basePath) {
        // Get all keys from the source section (shallow keys only)
        for (String key : source.getKeys(false)) {
            String fullPath = basePath + "." + key;
            Object value = source.get(key);

            if (value instanceof ConfigSection) {
                // Recursively copy subsections
                copySection((ConfigSection) value, target, fullPath);
            } else {
                // Copy the value
                target.set(fullPath, value);
            }
        }
    }
}
