package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * An optional world-level overlay for {@link LeafConfig#worldDefaultsConfig()}.
 *
 * <p>The file is never created by this class. Callers must check {@link #exists()} before
 * constructing it, so worlds without {@code leaf-world.yml} use the shared defaults directly.</p>
 */
public final class LeafWorldConfig extends LeafGlobalConfig {

    private final LeafGlobalConfig defaults;
    public boolean secureSeedEnabled;

    public static LeafWorldConfig loadDefaults(File file) throws Exception {
        return new LeafWorldConfig(file, null);
    }

    public LeafWorldConfig(File file, LeafGlobalConfig defaults) throws Exception {
        super(file, false);
        this.defaults = defaults;
        ConfigModule.loadWorldModules(this);
    }

    public static boolean exists(File file) {
        return file.isFile();
    }

    @Override
    protected void structureConfig() {
        // World files are override-only and must not be populated with the defaults structure.
    }

    private boolean overrides(String path) {
        return this.defaults == null || this.configFile.contains(path);
    }

    public boolean isDefaultsConfig() {
        return this.defaults == null;
    }

    @Override
    public boolean getBoolean(String path, boolean def, String comment) {
        return overrides(path) ? super.getBoolean(path, def, comment) : defaults.getBoolean(path, def, comment);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return overrides(path) ? super.getBoolean(path, def) : defaults.getBoolean(path, def);
    }

    @Override
    public String getString(String path, String def, String comment) {
        return overrides(path) ? super.getString(path, def, comment) : defaults.getString(path, def, comment);
    }

    @Override
    public String getString(String path, String def) {
        return overrides(path) ? super.getString(path, def) : defaults.getString(path, def);
    }

    @Override
    public double getDouble(String path, double def, String comment) {
        return overrides(path) ? super.getDouble(path, def, comment) : defaults.getDouble(path, def, comment);
    }

    @Override
    public double getDouble(String path, double def) {
        return overrides(path) ? super.getDouble(path, def) : defaults.getDouble(path, def);
    }

    @Override
    public int getInt(String path, int def, String comment) {
        return overrides(path) ? super.getInt(path, def, comment) : defaults.getInt(path, def, comment);
    }

    @Override
    public int getInt(String path, int def) {
        return overrides(path) ? super.getInt(path, def) : defaults.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def, String comment) {
        return overrides(path) ? super.getLong(path, def, comment) : defaults.getLong(path, def, comment);
    }

    @Override
    public long getLong(String path, long def) {
        return overrides(path) ? super.getLong(path, def) : defaults.getLong(path, def);
    }

    @Override
    public List<String> getList(String path, List<String> def, String comment) {
        return overrides(path) ? super.getList(path, def, comment) : defaults.getList(path, def, comment);
    }

    @Override
    public List<String> getList(String path, List<String> def) {
        return overrides(path) ? super.getList(path, def) : defaults.getList(path, def);
    }

    @Override
    public ConfigSection getConfigSection(String path, Map<String, Object> values, String comment) {
        return overrides(path) ? super.getConfigSection(path, values, comment) : defaults.getConfigSection(path, values, comment);
    }

    @Override
    public ConfigSection getConfigSection(String path, Map<String, Object> values) {
        return overrides(path) ? super.getConfigSection(path, values) : defaults.getConfigSection(path, values);
    }

    @Override
    public Boolean getBoolean(String path) {
        return overrides(path) ? super.getBoolean(path) : defaults.getBoolean(path);
    }

    @Override
    public String getString(String path) {
        return overrides(path) ? super.getString(path) : defaults.getString(path);
    }

    @Override
    public Double getDouble(String path) {
        return overrides(path) ? super.getDouble(path) : defaults.getDouble(path);
    }

    @Override
    public Integer getInt(String path) {
        return overrides(path) ? super.getInt(path) : defaults.getInt(path);
    }

    @Override
    public Long getLong(String path) {
        return overrides(path) ? super.getLong(path) : defaults.getLong(path);
    }

    @Override
    public List<String> getList(String path) {
        return overrides(path) ? super.getList(path) : defaults.getList(path);
    }

    @Override
    public ConfigSection getConfigSection(String path) {
        return overrides(path) ? super.getConfigSection(path) : defaults.getConfigSection(path);
    }

}
