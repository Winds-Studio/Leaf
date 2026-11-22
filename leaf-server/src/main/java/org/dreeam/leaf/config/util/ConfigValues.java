package org.dreeam.leaf.config.util;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts raw YAML values that are present in a configuration file into option values.
 *
 * <p>ConfigurationMaster's getters that take a fallback hide unparsable scalars and stringify
 * lists and sections. Every value that is present and not null is therefore checked here, and
 * rejected with the file, option path and expected type when it does not fit.</p>
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * Converts a present, non-null raw value to the type of an option field.
     *
     * @throws IllegalArgumentException if the value cannot be converted to {@code type}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object parse(Class<?> type, ConfigFile file, String path, Object raw) {
        if (type == boolean.class || type == Boolean.class) {
            String text = scalar(file, path, raw, "boolean");
            if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
            throw invalid(file, path, "boolean", raw, null);
        }
        if (type == int.class || type == Integer.class) {
            String text = scalar(file, path, raw, "int");
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                throw invalid(file, path, "int", raw, exception);
            }
        }
        if (type == long.class || type == Long.class) {
            String text = scalar(file, path, raw, "long");
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw invalid(file, path, "long", raw, exception);
            }
        }
        if (type == double.class || type == Double.class) {
            String text = scalar(file, path, raw, "double");
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                throw invalid(file, path, "double", raw, exception);
            }
        }
        if (type == String.class) {
            return scalar(file, path, raw, "string");
        }
        if (List.class.isAssignableFrom(type)) {
            if (!(raw instanceof List<?> list)) {
                throw invalid(file, path, "list", raw, null);
            }
            List<String> values = new ArrayList<>(list.size());
            for (Object element : list) {
                values.add(String.valueOf(element));
            }
            return values;
        }
        if (type.isEnum()) {
            String expected = "one of " + List.of(type.getEnumConstants());
            String text = scalar(file, path, raw, expected);
            try {
                return Enum.valueOf((Class<? extends Enum>) type, text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalid(file, path, expected, raw, exception);
            }
        }
        throw new IllegalArgumentException("Unsupported configuration value type: " + type.getName());
    }

    /** Creates the error reported for a value that is present but does not fit its option. */
    public static IllegalArgumentException invalid(
        ConfigFile file,
        String path,
        String expected,
        @Nullable Object raw,
        @Nullable Throwable cause
    ) {
        return new IllegalArgumentException("Invalid value '" + raw + "' for " + path + " in " + file.getFile()
            + "; expected " + expected, cause);
    }

    private static String scalar(ConfigFile file, String path, Object raw, String expected) {
        if (raw instanceof List<?> || raw instanceof Map<?, ?> || raw instanceof ConfigSection) {
            throw invalid(file, path, expected, raw, null);
        }
        return String.valueOf(raw);
    }
}
