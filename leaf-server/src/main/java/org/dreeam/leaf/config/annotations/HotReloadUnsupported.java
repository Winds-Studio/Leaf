package org.dreeam.leaf.config.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps an option at the value established during the first successful configuration load.
 * When applied to a module, all of its options and lifecycle callbacks are excluded from reload.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface HotReloadUnsupported {
}
