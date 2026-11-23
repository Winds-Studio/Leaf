package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import org.dreeam.leaf.config.util.ConfigFileIO;
import org.dreeam.leaf.config.util.ConfigValues;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Shared configuration-file utilities for global and world configuration views. */
abstract class LeafConfigAccessor {

    protected ConfigFile configFile;

    protected LeafConfigAccessor(ConfigFile configFile) {
        this.configFile = configFile;
    }

    void setConfigFile(ConfigFile configFile) {
        this.configFile = configFile;
    }

    /**
     * Reads the value of a registered option. A missing or null value yields {@code def}; a value that is
     * present but does not fit {@code type} is rejected.
     */
    private Object value(Class<?> type, String path, Object def) {
        Object raw = configFile.get(path);
        return raw == null ? def : ConfigValues.parse(type, configFile, path, raw);
    }

    public void createTitledSection(String title, String path) {
        configFile.addSection(title);
        configFile.addDefault(path, null);
    }

    public boolean getBoolean(String path, boolean def, String comment) {
        configFile.addDefault(path, def, comment);
        return (boolean) value(boolean.class, path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        configFile.addDefault(path, def);
        return (boolean) value(boolean.class, path, def);
    }

    public String getString(String path, String def, String comment) {
        configFile.addDefault(path, def, comment);
        return (String) value(String.class, path, def);
    }

    public String getString(String path, String def) {
        configFile.addDefault(path, def);
        return (String) value(String.class, path, def);
    }

    public double getDouble(String path, double def, String comment) {
        configFile.addDefault(path, def, comment);
        return (double) value(double.class, path, def);
    }

    public double getDouble(String path, double def) {
        configFile.addDefault(path, def);
        return (double) value(double.class, path, def);
    }

    public int getInt(String path, int def, String comment) {
        configFile.addDefault(path, def, comment);
        return (int) value(int.class, path, def);
    }

    public int getInt(String path, int def) {
        configFile.addDefault(path, def);
        return (int) value(int.class, path, def);
    }

    public long getLong(String path, long def, String comment) {
        configFile.addDefault(path, def, comment);
        return (long) value(long.class, path, def);
    }

    public long getLong(String path, long def) {
        configFile.addDefault(path, def);
        return (long) value(long.class, path, def);
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String path, List<String> def, String comment) {
        configFile.addDefault(path, def, comment);
        return (List<String>) value(List.class, path, def);
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String path, List<String> def) {
        configFile.addDefault(path, def);
        return (List<String>) value(List.class, path, def);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue, String comment) {
        configFile.addDefault(path, null, comment);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((key, value) -> configFile.addExample(path + "." + key, value));
        return configFile.getConfigSection(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((key, value) -> configFile.addExample(path + "." + key, value));
        return configFile.getConfigSection(path);
    }

    public Boolean getBoolean(String path) {
        String value = configFile.getString(path, null);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    public String getString(String path) {
        return configFile.getString(path, null);
    }

    public Double getDouble(String path) {
        String value = configFile.getString(path, null);
        if (value == null) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, exception);
            return null;
        }
    }

    public Integer getInt(String path) {
        String value = configFile.getString(path, null);
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, exception);
            return null;
        }
    }

    public Long getLong(String path) {
        String value = configFile.getString(path, null);
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            LeafConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, exception);
            return null;
        }
    }

    public List<String> getList(String path) {
        return configFile.getList(path, null);
    }

    public ConfigSection getConfigSection(String path) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        return configFile.getConfigSection(path);
    }

    public void addComment(String path, String comment) {
        configFile.addComment(path, comment);
    }

    public void addCommentIfCN(String path, String comment) {
        if (LeafConfig.isChineseLocale()) configFile.addComment(path, comment);
    }

    public void addCommentIfNonCN(String path, String comment) {
        if (!LeafConfig.isChineseLocale()) configFile.addComment(path, comment);
    }

    public void addCommentRegionBased(String path, String en, String cn) {
        configFile.addComment(path, LeafConfig.isChineseLocale() ? cn : en);
    }

    // TODO - why so complicated?
    public @Nullable String pickStringRegionBased(String... localizedStrings) {
        if (localizedStrings == null || localizedStrings.length == 0) return null;
        if (localizedStrings.length == 1) return localizedStrings[0];
        return LeafConfig.isChineseLocale() ? localizedStrings[1] : localizedStrings[0];
    }
}
