package org.dreeam.leaf.config;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker and lifecycle contract for a server-wide Leaf configuration module.
 *
 * <p>Annotated global module fields must be static and mutable. World-scoped modules must
 * implement {@link WorldConfigModule} instead and use instance fields.</p>
 */
public interface ConfigModule {

    default void onLoaded() {
    }

    default void onPostLoaded() {
    }

    static void initModules()
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        ConfigModuleLoader.initModules();
    }

    static void loadAfterBootstrap() {
        ConfigModuleLoader.loadAfterBootstrap();
    }

    static void clearModules() {
        ConfigModuleLoader.clearModules();
    }

    static void loadWorldModules(LeafWorldConfig config) {
        ConfigModuleLoader.loadWorldModules(config);
    }
}
