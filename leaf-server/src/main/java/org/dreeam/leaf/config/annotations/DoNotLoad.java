package org.dreeam.leaf.config.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a non-configuration field whose value is initialized by module lifecycle hooks from
 * already loaded configuration fields.
 *
 * <p>Fields annotated with {@code DoNotLoad} are never read from or written to configuration
 * and should not be annotated with {@link ConfigInfo}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DoNotLoad {
}
