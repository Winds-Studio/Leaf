package org.dreeam.leaf.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigStatus {

    public static Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        File oldConfigFile = new File(LeafConfig.I_CONFIG_FOLDER, LeafConfig.I_GLOBAL_CONFIG_FILE);
        status.put("oldConfigExists", oldConfigFile.exists());
        status.put("oldConfigFile", LeafConfig.I_GLOBAL_CONFIG_FILE);

        Map<String, Boolean> categoryFiles = new HashMap<>();
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            File categoryFile = new File(LeafConfig.I_CONFIG_FOLDER, category.getFileName());
            categoryFiles.put(category.getFileName(), categoryFile.exists());
        }
        status.put("categoryFiles", categoryFiles);

        boolean needsMigration = oldConfigFile.exists() && !allCategoryFilesExist();
        status.put("needsMigration", needsMigration);
        status.put("multiFileSystemReady", allCategoryFilesExist());

        return status;
    }

    private static boolean allCategoryFilesExist() {
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            File categoryFile = new File(LeafConfig.I_CONFIG_FOLDER, category.getFileName());
            if (!categoryFile.exists()) {
                return false;
            }
        }
        return true;
    }
}
