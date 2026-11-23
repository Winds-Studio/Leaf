package org.dreeam.leaf.config.annotations;

import org.dreeam.leaf.config.ConfigCategory;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigClassInfo {

    /**
     * Config category
     */
    ConfigCategory category();

    /**
     * Section path, optional
     */
    String name() default "";

    /**
     * Section comment, optional
     */
    String[] comments() default {};
}
