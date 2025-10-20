package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeafGlobalConfig {

    private static final String CURRENT_VERSION = "3.0";
    private static final String CURRENT_REGION = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT); // It will be in uppercase by default, just make sure
    private static final boolean isCN = CURRENT_REGION.equals("CN");

    private static ConfigFile configFile;

    public LeafGlobalConfig(boolean init) throws Exception {
        configFile = ConfigFile.loadConfig(new File(LeafConfig.I_CONFIG_FOLDER, LeafConfig.I_GLOBAL_CONFIG_FILE));

        LeafConfig.loadConfigVersion(getString("config-version"), CURRENT_VERSION);
        configFile.set("config-version", CURRENT_VERSION);

        configFile.addComments("config-version", pickStringRegionBased("""
                Leaf Config

                Website: https://www.leafmc.one/
                Docs: https://www.leafmc.one/docs/getting-started
                GitHub Repo: https://github.com/Winds-Studio/Leaf
                Discord: https://discord.com/invite/gfgAwdSEuM""",
            """
                Leaf 配置

                官网: https://www.leafmc.one/zh/
                文档: https://www.leafmc.one/zh/docs/getting-started
                GitHub 仓库: https://github.com/Winds-Studio/Leaf
                QQ社区群: 619278377"""));

        // Pre-structure to force order
        structureConfig();
    }

    protected void structureConfig() {
        for (EnumConfigCategory configCate : EnumConfigCategory.getCategoryValues()) {
            createTitledSection(configCate.name(), configCate.getBaseKeyName());
        }
    }

    public void saveConfig() throws Exception {
        configFile.save();
    }

    // Config Utilities

    /* getAndSet */

    public void createTitledSection(String title, String path) {
        configFile.addSection(title);
        configFile.addDefault(path, null);
    }

    public boolean getBoolean(String path, boolean def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getBoolean(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        configFile.addDefault(path, def);
        return configFile.getBoolean(path, def);
    }

    public String getString(String path, String def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getString(path, def);
    }

    public String getString(String path, String def) {
        configFile.addDefault(path, def);
        return configFile.getString(path, def);
    }

    public double getDouble(String path, double def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getDouble(path, def);
    }

    public double getDouble(String path, double def) {
        configFile.addDefault(path, def);
        return configFile.getDouble(path, def);
    }

    public int getInt(String path, int def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getInteger(path, def);
    }

    public int getInt(String path, int def) {
        configFile.addDefault(path, def);
        return configFile.getInteger(path, def);
    }

    public long getLong(String path, long def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getLong(path, def);
    }

    public long getLong(String path, long def) {
        configFile.addDefault(path, def);
        return configFile.getLong(path, def);
    }

    public List<String> getList(String path, List<String> def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getStringList(path);
    }

    public List<String> getList(String path, List<String> def) {
        configFile.addDefault(path, def);
        return configFile.getStringList(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue, String comment) {
        configFile.addDefault(path, null, comment);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    /* get */

    public Boolean getBoolean(String path) {
        String value = configFile.getString(path, null);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    public String getString(String path) {
        return configFile.getString(path, null);
    }

    public Double getDouble(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public Integer getInt(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public Long getLong(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public List<String> getList(String path) {
        return configFile.getList(path, null);
    }

    // TODO, check
    public ConfigSection getConfigSection(String path) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        //defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    public void addComment(String path, String comment) {
        configFile.addComment(path, comment);
    }

    public void addCommentIfCN(String path, String comment) {
        if (isCN) {
            configFile.addComment(path, comment);
        }
    }

    public void addCommentIfNonCN(String path, String comment) {
        if (!isCN) {
            configFile.addComment(path, comment);
        }
    }

    public void addCommentRegionBased(String path, String en, String cn) {
        configFile.addComment(path, isCN ? cn : en);
    }

    public String pickStringRegionBased(String en, String cn) {
        return isCN ? cn : en;
    }
}
