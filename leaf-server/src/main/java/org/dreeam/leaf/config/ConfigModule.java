package org.dreeam.leaf.config;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker and lifecycle contract for a server-wide Leaf configuration module.
 *
 * <p>Annotated fields must be static and mutable.</p>
 */
public interface ConfigModule {

    /**
     * Runs after this module's configuration fields have been loaded.
     *
     * <p>This hook runs during both initial configuration loading and reload. Core registries
     * are not guaranteed to be available during the initial invocation.</p>
     */
    default void onLoaded() {
    }

    /**
     * Runs after this module's configuration fields have been loaded and core registries are available.
     */
    default void onRegistriesLoaded() {
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
}
