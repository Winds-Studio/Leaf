package org.leavesmc.leaves.protocol.syncmatica;

import org.jetbrains.annotations.Nullable;

public enum Feature {
    CORE,
    FEATURE,
    MODIFY,
    MESSAGE,
    QUOTA,
    DEBUG,
    CORE_EX;

    @Nullable
    public static Feature fromString(final String s) {
        for (final Feature f : Feature.values()) {
            if (f.toString().equals(s)) {
                return f;
            }
        }
        return null;
    }
}
