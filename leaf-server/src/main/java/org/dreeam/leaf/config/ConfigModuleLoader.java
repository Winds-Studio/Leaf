package org.dreeam.leaf.config;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.Experimental;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ConfigModuleLoader {

    private static final Set<ConfigModule> LOADED_MODULES = new LinkedHashSet<>();
    private static List<Class<? extends WorldConfigModule>> worldModules = List.of();
    private static boolean alreadyInitialized;

    private ConfigModuleLoader() {
    }

    static void initModules()
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<Field> enabledExperimentalModules = new ArrayList<>();
        List<Field> deprecatedModules = new ArrayList<>();
        List<Class<? extends WorldConfigModule>> discoveredWorldModules = new ArrayList<>();

        Class<?>[] classes = LeafConfig.getClasses(LeafConfig.CONFIG_MODULE_PACKAGE).toArray(new Class[0]);
        ObjectArrays.quickSort(classes, Comparator.comparing((Class<?> clazz) -> clazz.getSimpleName())
            .thenComparing(Class::getName));
        for (Class<?> moduleClass : classes) {
            if (moduleClass.isInterface()
                || Modifier.isAbstract(moduleClass.getModifiers())) {
                continue;
            }

            if (WorldConfigModule.class.isAssignableFrom(moduleClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends WorldConfigModule> worldModuleClass =
                    (Class<? extends WorldConfigModule>) moduleClass;
                discoveredWorldModules.add(worldModuleClass);
                validateAnnotatedModule(moduleClass);
                continue;
            }
            if (!ConfigModule.class.isAssignableFrom(moduleClass)) {
                continue;
            }

            ConfigModule module = (ConfigModule) moduleClass.getConstructor().newInstance();
            validateAnnotatedModule(moduleClass);
            ConfigBinder.bindGlobal(module, LeafConfig.globalConfig(), alreadyInitialized);
            module.onLoaded();
            LOADED_MODULES.add(module);
            collectEnabledFields(moduleClass, Experimental.class, enabledExperimentalModules);
            collectEnabledFields(moduleClass, Deprecated.class, deprecatedModules);
        }

        if (!enabledExperimentalModules.isEmpty()) {
            LeafConfig.LOGGER.warn(
                "You have following experimental module(s) enabled: {}, please proceed with caution!",
                formatFields(enabledExperimentalModules)
            );
        }
        if (!deprecatedModules.isEmpty()) {
            LeafConfig.LOGGER.warn(
                "The following enabled module(s) has been deprecated: {}, please proceed with caution!",
                formatFields(deprecatedModules)
            );
        }
        worldModules = List.copyOf(discoveredWorldModules);
    }

    static void loadAfterBootstrap() {
        for (ConfigModule module : LOADED_MODULES) {
            module.onRegistriesLoaded();
        }

        try {
            LeafConfig.globalConfig().saveConfig();
            LeafConfig.finalizeGlobalConfigMigration();
        } catch (Exception exception) {
            LeafConfig.LOGGER.error("Failed to save config file!", exception);
        }
    }

    static void loadWorldModules(LeafWorldConfig config) {
        try {
            boolean alreadyInitialized = config.isReload();
            for (Class<? extends WorldConfigModule> moduleClass : worldModules) {
                loadWorldModule(config, moduleClass, alreadyInitialized);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Could not load Leaf world configuration modules", exception);
        }
    }

    private static <T extends WorldConfigModule> void loadWorldModule(
        LeafWorldConfig config,
        Class<T> moduleClass,
        boolean alreadyInitialized
    ) throws ReflectiveOperationException {
        T module = alreadyInitialized ? config.reloadModule(moduleClass) : null;
        if (module == null) {
            module = moduleClass.getConstructor().newInstance();
        }

        ConfigBinder.bindWorld(module, config, alreadyInitialized);
        config.registerModule(moduleClass, module);
    }

    static void clearModules() {
        LOADED_MODULES.clear();
        worldModules = List.of();
    }

    static void markInitialized() {
        alreadyInitialized = true;
    }

    private static void validateAnnotatedModule(Class<?> moduleClass) {
        if (!moduleClass.isAnnotationPresent(ConfigClassInfo.class)) {
            throw new IllegalStateException("Configuration module " + moduleClass.getName()
                + " is missing @ConfigClassInfo");
        }
    }

    private static void collectEnabledFields(
        Class<?> moduleClass,
        Class<? extends Annotation> annotation,
        List<Field> enabledFields
    ) throws IllegalAccessException {
        for (Field field : moduleClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(annotation) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            if (field.get(null) instanceof Boolean enabled && enabled) {
                enabledFields.add(field);
            }
        }
    }

    private static List<String> formatFields(List<Field> fields) {
        return fields.stream()
            .map(field -> field.getDeclaringClass().getSimpleName() + "." + field.getName())
            .toList();
    }
}
