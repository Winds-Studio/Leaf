package org.dreeam.leaf.config;

/**
 * Categories available to annotation-driven configuration modules.
 */
public enum ConfigCategory {
    ASYNC("async"),
    PERF("performance"),
    FIXES("fixes"),
    GAMEPLAY("gameplay-mechanisms"),
    NETWORK("network"),
    MISC("misc");

    private final String basePath;

    ConfigCategory(String basePath) {
        this.basePath = basePath;
    }

    public String basePath() {
        return this.basePath;
    }
}
