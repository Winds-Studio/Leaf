package org.dreeam.leaf;

import io.papermc.paper.PaperBootstrap;
import joptsimple.OptionSet;

public class LeafBootstrap {

    public static final boolean enableFMA = Boolean.parseBoolean(System.getProperty("Leaf.enableFMA", "false")); // Leaf - FMA feature

    public static void boot(final OptionSet options) {
        //runPreBootTasks();

        PaperBootstrap.boot(options);
    }

    private static void runPreBootTasks() {
    }
}
