package org.dreeam.leaf.config;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.dreeam.leaf.config.annotations.Experimental;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class ConfigModule extends LeafConfig {

    private static final Set<ConfigModule> LOADED_MODULES = new HashSet<>();
    private static List<Class<? extends WorldConfigModule>> WORLD_MODULES = List.of();

    protected final LeafGlobalConfig globalConfig;

    public ConfigModule() {
        this.globalConfig = LeafConfig.globalConfig();
    }

    public static void initModules() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<Field> enabledExperimentalModules = new ArrayList<>();
        List<Field> deprecatedModules = new ArrayList<>();
        List<Class<? extends WorldConfigModule>> worldModules = new ArrayList<>();

        Class<?>[] classes = LeafConfig.getClasses(LeafConfig.CONFIG_MODULE_PACKAGE).toArray(new Class[0]);
        ObjectArrays.quickSort(classes, Comparator.comparing(Class::getSimpleName));
        for (Class<?> clazz : classes) {
            ConfigModule module = (ConfigModule) clazz.getConstructor().newInstance();
            module.onLoaded();

            if (WorldConfigModule.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                Class<? extends WorldConfigModule> worldModuleClass = (Class<? extends WorldConfigModule>) clazz;
                worldModules.add(worldModuleClass);
            }

            LOADED_MODULES.add(module);
            for (Field field : getAnnotatedStaticFields(clazz, Experimental.class)) {
                if (!(field.get(null) instanceof Boolean enabled)) continue;
                if (enabled) {
                    enabledExperimentalModules.add(field);
                }
            }
            for (Field field : getAnnotatedStaticFields(clazz, Deprecated.class)) {
                if (!(field.get(null) instanceof Boolean enabled)) continue;
                if (enabled) {
                    deprecatedModules.add(field);
                }
            }
        }

        if (!enabledExperimentalModules.isEmpty()) {
            LeafConfig.LOGGER.warn("You have following experimental module(s) enabled: {}, please proceed with caution!", formatModules(enabledExperimentalModules));
        }

        if (!deprecatedModules.isEmpty()) {
            LeafConfig.LOGGER.warn("The following enabled module(s) has been deprecated: {}, please proceed with caution!", formatModules(deprecatedModules));
        }

        WORLD_MODULES = List.copyOf(worldModules);
    }

    private static List<String> formatModules(List<Field> modules) {
        return modules.stream().map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getName()).toList();
    }

    public static void loadAfterBootstrap() {
        for (ConfigModule module : LOADED_MODULES) {
            module.onPostLoaded();
        }

        // Save config to disk
        try {
            LeafConfig.globalConfig().saveConfig();
        } catch (Exception e) {
            LeafConfig.LOGGER.error("Failed to save config file!", e);
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
        LOADED_MODULES.clear();
        WORLD_MODULES = List.of();
    }

    /** Instantiates the cached, stateless world modules for one world configuration. */
    public static void loadWorldModules(LeafWorldConfig config) {
        try {
            for (Class<? extends WorldConfigModule> moduleClass : WORLD_MODULES) {
                moduleClass.getConstructor().newInstance().loadWorldConfig(config);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Could not load Leaf world configuration modules", exception);
        }
    }

    public abstract void onLoaded();

    public void onPostLoaded() {
    }
}
