package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;

import java.util.Objects;

/** The server-wide Leaf configuration. */
public final class LeafGlobalConfig extends LeafConfigAccessor {

    LeafGlobalConfig(ConfigFile configFile, boolean loadPreviousVersion) {
        super(configFile);

        if (loadPreviousVersion) {
            LeafConfig.loadPreviousConfigVersion(getString("config-version"));
        }

        configFile.set("config-version", LeafConfig.CURRENT_CONFIG_VERSION);

        configFile.addComments("config-version", Objects.requireNonNull(pickStringRegionBased("""
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

    private void structureConfig() {
        for (ConfigCategory category : ConfigCategory.values()) {
            createTitledSection(category.name(), category.basePath());
        }
    }
}
