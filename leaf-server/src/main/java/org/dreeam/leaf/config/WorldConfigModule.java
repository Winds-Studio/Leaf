package org.dreeam.leaf.config;

/** Loads a module's effective values from a world-defaults/override configuration view. */
public interface WorldConfigModule {

    void loadWorldConfig(LeafWorldConfig config);
}
