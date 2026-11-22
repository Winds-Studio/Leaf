package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The server-wide Leaf configuration. */
public final class LeafGlobalConfig extends LeafConfigAccessor {

    // The version value the file had when it was loaded, null if it had none.
    private final @Nullable Object loadedVersion;

    LeafGlobalConfig(ConfigFile configFile, LeafConfig.VersionCheck version) {
        super(configFile);

        this.loadedVersion = configFile.get(LeafConfig.VERSION_PATH);
        configFile.set(LeafConfig.VERSION_PATH, version.valueToWrite());

        configFile.addComments(LeafConfig.VERSION_PATH, Objects.requireNonNull(pickStringRegionBased("""
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
                QQ社区群: 619278377""")));

        structureConfig();
    }

    /** The {@code config-version} the file had when it was loaded, {@code null} if it had none. */
    @Nullable Object loadedVersion() {
        return this.loadedVersion;
    }

    private void structureConfig() {
        for (ConfigCategory category : ConfigCategory.values()) {
            // Registering the title resets the category to what was loaded from the file, which would drop
            // options that were migrated into it before this point.
            Object existing = this.configFile.get(category.basePath());
            createTitledSection(category.name(), category.basePath());
            if (existing != null) {
                this.configFile.set(category.basePath(), existing);
            }
        }
    }
}
