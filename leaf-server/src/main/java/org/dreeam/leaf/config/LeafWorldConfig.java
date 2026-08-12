package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An optional world-level overlay for {@link LeafConfig#worldDefaultsConfig()}.
 *
 * <p>The file is never created by this class. Callers must check {@link #exists()} before
 * constructing it, so worlds without {@code leaf-world.yml} use the shared defaults directly.</p>
 */
public final class LeafWorldConfig extends LeafConfigAccessor {

    private final LeafWorldConfig defaults;
    private LeafWorldConfig reloadSource;
    private final Map<Class<? extends WorldConfigModule>, WorldConfigModule> modules = new LinkedHashMap<>();
    public boolean secureSeedEnabled;

    public static LeafWorldConfig loadDefaults(File file) throws Exception {
        return loadDefaults(file, null);
    }

    static LeafWorldConfig loadDefaults(File file, LeafWorldConfig reloadSource) throws Exception {
        return new LeafWorldConfig(file, null, reloadSource);
    }

    public LeafWorldConfig(File file, LeafWorldConfig defaults) throws Exception {
        this(file, defaults, null);
    }

    private LeafWorldConfig(File file, LeafWorldConfig defaults, LeafWorldConfig reloadSource) throws Exception {
        super(file);
        this.defaults = defaults;
        this.reloadSource = reloadSource;
        try {
            ConfigModuleLoader.loadWorldModules(this);
        } finally {
            this.reloadSource = null;
        }
    }

    public static boolean exists(File file) {
        return file.isFile();
    }

    private boolean usesWorldDefaults(String path) {
        return this.defaults != null && !this.configFile.contains(path);
    }

    public boolean isDefaultsConfig() {
        return this.defaults == null;
    }

    boolean isReload() {
        return this.reloadSource != null;
    }

    /**
     * Returns this world's annotation-driven module instance.
     */
    public <T extends WorldConfigModule> T getModule(Class<T> moduleClass) {
        WorldConfigModule module = this.modules.get(moduleClass);
        if (module == null) {
            throw new IllegalArgumentException("World configuration module is not registered: "
                + moduleClass.getName());
        }
        return moduleClass.cast(module);
    }

    <T extends WorldConfigModule> void registerModule(Class<T> moduleClass, T module) {
        WorldConfigModule previousModule = this.modules.putIfAbsent(moduleClass, module);
        if (previousModule != null) {
            throw new IllegalStateException("Duplicate world configuration module: " + moduleClass.getName());
        }
    }

    <T extends WorldConfigModule> T reloadModule(Class<T> moduleClass) {
        if (this.reloadSource == null) {
            return null;
        }
        WorldConfigModule module = this.reloadSource.modules.get(moduleClass);
        return module == null ? null : moduleClass.cast(module);
    }

    @Override
    public boolean getBoolean(String path, boolean def, String comment) {
        return usesWorldDefaults(path) ? defaults.getBoolean(path, def, comment) : super.getBoolean(path, def, comment);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return usesWorldDefaults(path) ? defaults.getBoolean(path, def) : super.getBoolean(path, def);
    }

    @Override
    public String getString(String path, String def, String comment) {
        return usesWorldDefaults(path) ? defaults.getString(path, def, comment) : super.getString(path, def, comment);
    }

    @Override
    public String getString(String path, String def) {
        return usesWorldDefaults(path) ? defaults.getString(path, def) : super.getString(path, def);
    }

    @Override
    public double getDouble(String path, double def, String comment) {
        return usesWorldDefaults(path) ? defaults.getDouble(path, def, comment) : super.getDouble(path, def, comment);
    }

    @Override
    public double getDouble(String path, double def) {
        return usesWorldDefaults(path) ? defaults.getDouble(path, def) : super.getDouble(path, def);
    }

    @Override
    public int getInt(String path, int def, String comment) {
        return usesWorldDefaults(path) ? defaults.getInt(path, def, comment) : super.getInt(path, def, comment);
    }

    @Override
    public int getInt(String path, int def) {
        return usesWorldDefaults(path) ? defaults.getInt(path, def) : super.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def, String comment) {
        return usesWorldDefaults(path) ? defaults.getLong(path, def, comment) : super.getLong(path, def, comment);
    }

    @Override
    public long getLong(String path, long def) {
        return usesWorldDefaults(path) ? defaults.getLong(path, def) : super.getLong(path, def);
    }

    @Override
    public List<String> getList(String path, List<String> def, String comment) {
        return usesWorldDefaults(path) ? defaults.getList(path, def, comment) : super.getList(path, def, comment);
    }

    @Override
    public List<String> getList(String path, List<String> def) {
        return usesWorldDefaults(path) ? defaults.getList(path, def) : super.getList(path, def);
    }

    @Override
    public ConfigSection getConfigSection(String path, Map<String, Object> values, String comment) {
        return usesWorldDefaults(path) ? defaults.getConfigSection(path, values, comment) : super.getConfigSection(path, values, comment);
    }

    @Override
    public ConfigSection getConfigSection(String path, Map<String, Object> values) {
        return usesWorldDefaults(path) ? defaults.getConfigSection(path, values) : super.getConfigSection(path, values);
    }

    @Override
    public Boolean getBoolean(String path) {
        return usesWorldDefaults(path) ? defaults.getBoolean(path) : super.getBoolean(path);
    }

    @Override
    public String getString(String path) {
        return usesWorldDefaults(path) ? defaults.getString(path) : super.getString(path);
    }

    @Override
    public Double getDouble(String path) {
        return usesWorldDefaults(path) ? defaults.getDouble(path) : super.getDouble(path);
    }

    @Override
    public Integer getInt(String path) {
        return usesWorldDefaults(path) ? defaults.getInt(path) : super.getInt(path);
    }

    @Override
    public Long getLong(String path) {
        return usesWorldDefaults(path) ? defaults.getLong(path) : super.getLong(path);
    }

    @Override
    public List<String> getList(String path) {
        return usesWorldDefaults(path) ? defaults.getList(path) : super.getList(path);
    }

    @Override
    public ConfigSection getConfigSection(String path) {
        return usesWorldDefaults(path) ? defaults.getConfigSection(path) : super.getConfigSection(path);
    }

}
