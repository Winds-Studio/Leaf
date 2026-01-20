package org.dreeam.leaf.util;

public class LeafConstants {

    public static final boolean DISABLE_VANILLA_PROFILER = Boolean.getBoolean("Leaf.disable-vanilla-profiler");
    public static final boolean ENABLE_FMA = Boolean.getBoolean("Leaf.enableFMA");
    public static final boolean ENABLE_IO_URING = Boolean.getBoolean("Leaf.enable-io-uring");

    public static final String DISABLE_VANILLA_PROFILER_DOCS_URL = "https://www.leafmc.one/docs/config/system-properties#dleaf-disable-vanilla-profiler";
}
