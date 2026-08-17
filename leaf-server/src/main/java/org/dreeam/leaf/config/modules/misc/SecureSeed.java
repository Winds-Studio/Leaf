package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.LeafWorldConfig;
import org.dreeam.leaf.config.WorldConfigModule;

public class SecureSeed extends ConfigModule implements WorldConfigModule {

    /**
     * Keeps the module discoverable by the existing global module loader. The option itself is
     * world-scoped and is loaded by {@link #loadWorldConfig(LeafWorldConfig)}.
     */
    public SecureSeed() {
    }

    @Override
    public void loadWorldConfig(LeafWorldConfig config) {
        String path = "misc.secure-seed";
        if (config.isWorldDefaults()) {
            config.addCommentRegionBased(path, """
                    Once you enable secure seed, all ores and structures are generated with a 1024-bit seed
                    instead of vanilla's 64-bit seed, making seed cracking impossible.""", """
                    安全种子开启后，所有矿物与结构都将使用 1024 位种子，而非原版的 64 位种子，
                    从而无法被破解。""");
        }
        config.secureSeedEnabled = config.getBoolean(path + ".enabled", false);
    }

    @Override
    public void onLoaded() {
        // Secure Seed is registered by loadWorldConfig.
    }

    /** Compatibility fallback for code that has no world context. */
    public static boolean isEnabled() {
        return LeafConfig.worldDefaultsConfig().secureSeedEnabled;
    }
}
