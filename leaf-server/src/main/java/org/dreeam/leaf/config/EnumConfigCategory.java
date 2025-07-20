package org.dreeam.leaf.config;

public enum EnumConfigCategory {
    ASYNC("async", "leaf-async.yml"),
    PERF("performance", "leaf-performance.yml"),
    FIXES("fixes", "leaf-fixes.yml"),
    GAMEPLAY("gameplay-mechanisms", "leaf-gameplay.yml"),
    NETWORK("network", "leaf-network.yml"),
    MISC("misc", "leaf-misc.yml");

    private final String baseKeyName;
    private final String fileName;
    private static final EnumConfigCategory[] VALUES = EnumConfigCategory.values();

    EnumConfigCategory(String baseKeyName, String fileName) {
        this.baseKeyName = baseKeyName;
        this.fileName = fileName;
    }

    public String getBaseKeyName() {
        return this.baseKeyName;
    }

    public String getFileName() {
        return this.fileName;
    }

    public static EnumConfigCategory[] getCategoryValues() {
        return VALUES;
    }

    public static EnumConfigCategory fromBaseKeyName(String baseKeyName) {
        for (EnumConfigCategory category : VALUES) {
            if (category.baseKeyName.equals(baseKeyName)) {
                return category;
            }
        }
        return null;
    }
}
