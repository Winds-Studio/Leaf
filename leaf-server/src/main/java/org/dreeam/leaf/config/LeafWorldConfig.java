package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.modules.misc.WorldConfigExample;
import org.dreeam.leaf.config.modules.opt.SaveFireworks;

/**
 * An optional world-level overlay for {@link LeafConfig#worldDefaultsConfig()}.
 *
 * <p>The file is never created by this class. Worlds without {@code leaf-world.yml} use the
 * shared defaults directly. World modules are exposed as typed fields for direct access through
 * a level's Leaf configuration.</p>
 */
public final class LeafWorldConfig extends LeafConfigAccessor {

    enum Source {
        WORLD_DEFAULTS_FILE,
        WORLD_OVERRIDE_FILE
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
