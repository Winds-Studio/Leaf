package org.dreeam.leaf.config;

/**
 * Marker and lifecycle contract for a world-scoped Leaf configuration module.
 *
 * <p>Annotated fields must be mutable instance fields. A separate module instance is created
 * for the defaults and for every world override.</p>
 */
public interface WorldConfigModule extends ConfigModule {
}
