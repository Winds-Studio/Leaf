package org.dreeam.leaf.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.schedule.Activity;

public final class RegistryTypeManager {

    /**
     * The total number of attributes in the Built-in Registry.
     */
    public static final int ATTRIBUTE_SIZE;
    public static final int ACTIVITY_SIZE;
    public static final Holder<Attribute>[] ATTRIBUTE;
    public static final Activity[] ACTIVITY_DIRECT;

    static {
        ATTRIBUTE_SIZE = BuiltInRegistries.ATTRIBUTE.size();
        ACTIVITY_SIZE = BuiltInRegistries.ACTIVITY.size();
        if (ATTRIBUTE_SIZE == 0 || ACTIVITY_SIZE == 0) {
            throw new ExceptionInInitializerError("RegistryTypeManager initialize before registries bootstrap");
        }
        if (ACTIVITY_SIZE > 32) {
            throw new ExceptionInInitializerError("minecraft:activity out of range int bitset (>32)");
        }
        ATTRIBUTE = new Holder[ATTRIBUTE_SIZE];
        for (int i = 0; i < ATTRIBUTE_SIZE; i++) {
            ATTRIBUTE[i] = BuiltInRegistries.ATTRIBUTE.get(i).orElseThrow();
            if (ATTRIBUTE[i].value().id != i) {
                throw new ExceptionInInitializerError();
            }
        }
        ACTIVITY_DIRECT = new Activity[ACTIVITY_SIZE];
        for (int i = 0; i < ACTIVITY_SIZE; i++) {
            ACTIVITY_DIRECT[i] = BuiltInRegistries.ACTIVITY.byIdOrThrow(i);
            if (ACTIVITY_DIRECT[i].id != i) {
                throw new ExceptionInInitializerError();
            }
        }
    }

    private RegistryTypeManager() {
    }
}
