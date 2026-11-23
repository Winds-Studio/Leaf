package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.dreeam.leaf.config.migration.ConfigMigrationContext;

@ConfigClassInfo(category = ConfigCategory.PERF)
public class OptimizeRandomTick implements ConfigModule {

    @Experimental
    @ConfigInfo(name = "optimize-random-tick")
    public static boolean enabled = false;

    public static void migrate(ConfigMigrationContext migrations) {
        if (migrations.isBefore("3.1")) {
            migrations.migrate(
                ConfigMigrationContext.Scope.GLOBAL, "performance.optimise-random-tick",
                ConfigMigrationContext.Scope.GLOBAL, "performance.optimize-random-tick"
            );
        }
    }
}
