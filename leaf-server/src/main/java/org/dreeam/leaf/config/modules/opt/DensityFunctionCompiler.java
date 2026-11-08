package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.annotations.Experimental;
import org.dreeam.leaf.config.annotations.HotReloadUnsupported;

public class DensityFunctionCompiler extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".density-function-compiler";
    }

    @HotReloadUnsupported
    @Experimental
    public static boolean enabled = false;
    private static boolean densityFunctionCompilerInitialized;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Compile density functions used by both vanilla world generation and datapacks into JVM bytecode.
                This setting is fixed at server startup; restart the server to apply changes.""",
            """
                将原版世界生成和数据包使用的密度函数编译为 JVM 字节码。
                此配置仅在服务器启动时读取；修改后需要重启服务器才能生效。""");

        if (densityFunctionCompilerInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        densityFunctionCompilerInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
