package org.dreeam.leaf.util;

import net.minecraft.core.registries.BuiltInRegistries;

public final class RegistryTypeManager {

    /**
     * The total number of attributes in the Built-in Registry.
     */
    public static final int ATTRIBUTE_SIZE;
    public static final int ACTIVITY_SIZE;

    static {
        ATTRIBUTE_SIZE = BuiltInRegistries.ATTRIBUTE.size();
        ACTIVITY_SIZE = BuiltInRegistries.ACTIVITY.size();
        if (ATTRIBUTE_SIZE == 0 || ACTIVITY_SIZE == 0) {
            throw new ExceptionInInitializerError("RegistryTypeManager initialize before registries bootstrap");
        }
        if (ACTIVITY_SIZE > 32) {
            throw new ExceptionInInitializerError("minecraft:activity out of range int bitset (>32)");
        }
    }

    private RegistryTypeManager() {
    }
}
