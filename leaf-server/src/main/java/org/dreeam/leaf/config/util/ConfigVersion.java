package org.dreeam.leaf.config.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A dot-separated config version such as {@code 3.1}. Components are compared as numbers, missing
 * components count as 0: {@code 3.10 > 3.9} and {@code 3 == 3.0}.
 */
public record ConfigVersion(List<Integer> components) implements Comparable<ConfigVersion> {

    /**
     * Parses a version.
     *
     * @throws IllegalArgumentException if {@code version} is not made of numeric components
     */
    public static ConfigVersion parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Config version must not be blank");
        }

        String[] parts = version.split("\\.", -1);
        List<Integer> components = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(character -> character >= '0' && character <= '9')) {
                throw new IllegalArgumentException("Invalid config version: " + version);
            }
            try {
                components.add(Integer.parseInt(part));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid config version: " + version, exception);
            }
        }
        return new ConfigVersion(List.copyOf(components));
    }

    @Override
    public int compareTo(ConfigVersion other) {
        int componentCount = Math.max(this.components.size(), other.components.size());
        for (int index = 0; index < componentCount; index++) {
            int thisComponent = index < this.components.size() ? this.components.get(index) : 0;
            int otherComponent = index < other.components.size() ? other.components.get(index) : 0;
            int comparison = Integer.compare(thisComponent, otherComponent);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
