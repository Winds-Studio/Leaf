package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeRandomTick implements ConfigModule {

    public static void migrate(org.dreeam.leaf.config.migration.ConfigMigrationContext migrations) {
        if (migrations.isBefore("3.1")) {
            migrations.migrate(
                org.dreeam.leaf.config.migration.ConfigMigrationContext.Scope.GLOBAL, "performance.optimise-random-tick",
                org.dreeam.leaf.config.migration.ConfigMigrationContext.Scope.GLOBAL, "performance.optimize-random-tick"
            );
        }
    }

    @Experimental
    @ConfigInfo(name = "optimize-random-tick")
    public static boolean enabled = false;
}
