package org.dreeam.leaf;

import io.papermc.paper.PaperBootstrap;
import joptsimple.OptionSet;

public class LeafBootstrap {

    public static final boolean enableFMA = Boolean.getBoolean("Leaf.enableFMA"); // Leaf - Optimize Entity distanceTo

    public static void boot(final OptionSet options) {
        //runPreBootTasks();

        PaperBootstrap.boot(options);
    }

    private static void runPreBootTasks() {
    }
}
