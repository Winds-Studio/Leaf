package org.dreeam.leaf.config.util;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.annotations.ConfigClassInfo;
import org.dreeam.leaf.config.annotations.ConfigInfo;
import org.dreeam.leaf.config.annotations.DoNotLoad;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The order the options of a configuration file are saved in.
 *
 * <p>The library saves keys in the order they were first set, so options written before the modules are
 * bound (migrated values, the version of an override) would otherwise be saved first. A file is put into its
 * layout right before it is saved. Only the order changes; values and comments stay as they are.</p>
 */
public final class ConfigLayout {

    private ConfigLayout() {
    }

    /**
     * The layout of a file holding the given modules: the version, the categories in declaration order, and
     * within a category its modules (or the options of a module without a name) sorted by name. The options of
     * a module keep their declaration order.
     */
    public static List<String> of(String versionPath, List<Class<?>> moduleClasses) {
        List<Map<String, List<String>>> categories = new ArrayList<>();
        for (ConfigCategory ignored : ConfigCategory.values()) {
            categories.add(new TreeMap<>());
        }
        for (Class<?> moduleClass : moduleClasses) {
            ConfigCategory category = moduleClass.getAnnotation(ConfigClassInfo.class).category();
            for (Field field : moduleClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(ConfigInfo.class) || field.isAnnotationPresent(DoNotLoad.class)) {
                    continue;
                }
                String path = ConfigPaths.fieldPath(moduleClass, field);
                String relative = path.substring(category.basePath().length() + 1);
                int dot = relative.indexOf('.');
                categories.get(category.ordinal())
                    .computeIfAbsent(dot < 0 ? relative : relative.substring(0, dot), key -> new ArrayList<>())
                    .add(path);
            }
        }

        List<String> paths = new ArrayList<>();
        paths.add(versionPath);
        for (ConfigCategory category : ConfigCategory.values()) {
            paths.add(category.basePath());
        }
        for (Map<String, List<String>> entries : categories) {
            for (List<String> entryPaths : entries.values()) {
                paths.addAll(entryPaths);
            }
        }
        return List.copyOf(paths);
    }

    /**
     * Puts the keys of {@code file} into the order of {@code layout}: a section where its first path is
     * listed, an option where it is listed. Keys that are not listed keep their relative order after them.
     */
    public static void apply(ConfigFile file, List<String> layout) {
        order(file, layout);
    }

    @SuppressWarnings("unchecked")
    private static void order(Map<String, Object> section, List<String> paths) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (String path : paths) {
            int dot = path.indexOf('.');
            List<String> subpaths = children.computeIfAbsent(dot < 0 ? path : path.substring(0, dot), key -> new ArrayList<>());
            if (dot >= 0) {
                subpaths.add(path.substring(dot + 1));
            }
        }

        Map<String, Object> remaining = new LinkedHashMap<>(section);
        section.clear();
        for (Map.Entry<String, List<String>> child : children.entrySet()) {
            if (!remaining.containsKey(child.getKey())) {
                continue;
            }
            Object value = remaining.remove(child.getKey());
            section.put(child.getKey(), value);
            if (value instanceof Map<?, ?> subsection && !child.getValue().isEmpty()) {
                order((Map<String, Object>) subsection, child.getValue());
            }
        }
        section.putAll(remaining);
    }
}
