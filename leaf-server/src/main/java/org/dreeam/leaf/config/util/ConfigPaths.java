package org.dreeam.leaf.config.util;

import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;

import java.lang.reflect.Field;

/** Resolves annotation-driven module and option paths. */
public final class ConfigPaths {

    public static String modulePath(Class<?> moduleClass) {
        ConfigClassInfo info = moduleClass.getAnnotation(ConfigClassInfo.class);
        if (info == null) {
            throw new IllegalStateException("Configuration module " + moduleClass.getName()
                + " is missing @ConfigClassInfo");
        }

        return info.category().basePath() + '.' + info.name();
    }

    public static String fieldPath(Class<?> moduleClass, Field field) {
        ConfigInfo info = field.getAnnotation(ConfigInfo.class);
        if (info == null) {
            throw new IllegalStateException("Configuration field " + field + " is missing @ConfigInfo");
        }

        return modulePath(moduleClass) + '.' + info.name();
    }
}
