package org.dreeam.leaf.config.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps an option at the value established during the first successful configuration load.
 * When applied to a module, all of its options and lifecycle callbacks are excluded from reload.
 *
 * <p>A reload still writes the new value to the configuration file, so it takes effect on the next
 * start. A global option keeps its value for the lifetime of the server. A world module option is kept
 * per configuration instance: the world defaults and every world hold the value they were first bound
 * with, and a world that is unloaded and loaded again starts with the current effective value.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface HotReloadUnsupported {
}
