package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.modules.misc.WorldConfigExample;
import org.dreeam.leaf.config.modules.opt.SaveFireworks;

/**
 * A world-level configuration initialized from {@link LeafConfig#worldDefaultsConfig()} with an
 * optional per-world overlay.
 *
 * <p>The file is never created by this class. Every world receives its own configuration instance,
 * initially inherits the shared defaults, and then applies values explicitly defined in
 * {@code leaf-world.yml}. World modules are exposed as typed fields for direct access through a
 * level's Leaf configuration.</p>
 */
public final class LeafWorldConfig extends LeafConfigAccessor {

    enum Source {
        WORLD_DEFAULTS_FILE,
        WORLD_CONFIG
    }

    private final Source source;

    public WorldConfigExample worldConfigExample = new WorldConfigExample();
    public SaveFireworks saveFireworks = new SaveFireworks();

    public boolean secureSeedEnabled;

    LeafWorldConfig(
        ConfigFile configFile,
        Source source
    ) {
        super(configFile);
        this.source = source;
    }

    public boolean isWorldDefaultsFile() {
        return this.source == Source.WORLD_DEFAULTS_FILE;
    }
}
