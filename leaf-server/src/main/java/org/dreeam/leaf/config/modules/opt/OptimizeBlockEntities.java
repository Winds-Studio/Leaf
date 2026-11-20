package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeBlockEntities implements ConfigModule {

    public static void migrate(org.dreeam.leaf.config.migration.ConfigMigrationContext migrations) {
        if (migrations.isBefore("3.1")) {
            migrations.migrate(
                org.dreeam.leaf.config.migration.ConfigMigrationContext.Scope.GLOBAL, "performance.optimise-block-entities",
                org.dreeam.leaf.config.migration.ConfigMigrationContext.Scope.GLOBAL, "performance.optimize-block-entities"
            );
        }
    }

    @ConfigInfo(name = "optimize-block-entities")
    public static boolean enabled = true;
}
