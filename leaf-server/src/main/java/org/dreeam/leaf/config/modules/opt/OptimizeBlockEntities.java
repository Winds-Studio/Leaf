package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.dreeam.leaf.config.migration.ConfigMigrationContext;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeBlockEntities implements ConfigModule {

    @ConfigInfo(name = "optimize-block-entities")
    public static boolean enabled = true;

    public static void migrate(ConfigMigrationContext migrations) {
        if (migrations.isBefore("3.1")) {
            migrations.migrate(
                ConfigMigrationContext.Scope.GLOBAL, "performance.optimise-block-entities",
                ConfigMigrationContext.Scope.GLOBAL, "performance.optimize-block-entities"
            );
        }
    }
}
