package org.dreeam.leaf.config;

import org.dreeam.leaf.config.annotations.Experimental;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ConfigModules extends LeafConfig {

    private static final Set<ConfigModules> MODULES = new HashSet<>();

    public LeafCategoryConfig config;
    private EnumConfigCategory category;

    public ConfigModules() {
        // Determine category from the module's base path
        this.category = determineCategory();
        this.config = LeafConfig.config(this.category);
    }

    // Abstract method that modules must implement to specify their base path
    public abstract String getBasePath();

    // Determine category from base path
    private EnumConfigCategory determineCategory() {
        String basePath = getBasePath();
        for (EnumConfigCategory cat : EnumConfigCategory.getCategoryValues()) {
            if (basePath.startsWith(cat.getBaseKeyName())) {
                return cat;
            }
        }
        // Fallback to MISC if no category matches
        return EnumConfigCategory.MISC;
    }

    public EnumConfigCategory getCategory() {
        return category;
    }

    public static void initModules() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<Field> enabledExperimentalModules = new ArrayList<>();

        for (Class<?> clazz : LeafConfig.getClasses(LeafConfig.I_CONFIG_PKG)) {
            ConfigModules module = (ConfigModules) clazz.getConstructor().newInstance();
            module.onLoaded();

            MODULES.add(module);
            for (Field field : getAnnotatedStaticFields(clazz, Experimental.class)) {
                if (!(field.get(null) instanceof Boolean enabled)) continue;
                if (enabled) {
                    enabledExperimentalModules.add(field);
                }
            }
        }

        if (!enabledExperimentalModules.isEmpty()) {
            LeafConfig.LOGGER.warn("You have following experimental module(s) enabled: {}, please proceed with caution!",
                enabledExperimentalModules.stream().map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getName()).toList());
        }
    }

    public static void loadAfterBootstrap() {
        for (ConfigModules module : MODULES) {
            module.onPostLoaded();
        }

        // Save all config files to disk
        for (EnumConfigCategory category : EnumConfigCategory.getCategoryValues()) {
            try {
                LeafConfig.config(category).saveConfig();
            } catch (Exception e) {
                LeafConfig.LOGGER.error("Failed to save config file for category {}!", category.name(), e);
            }
        }
    }

    private static List<Field> getAnnotatedStaticFields(Class<?> clazz, Class<? extends Annotation> annotation) {
        List<Field> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation) && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }

        return fields;
    }

    public static void clearModules() {
        MODULES.clear();
    }

    public abstract void onLoaded();

    public void onPostLoaded() {
    }
}
