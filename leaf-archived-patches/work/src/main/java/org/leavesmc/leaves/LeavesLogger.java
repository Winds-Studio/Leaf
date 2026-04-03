package org.leavesmc.leaves;

import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LeavesLogger extends Logger {

    public static final LeavesLogger LOGGER = new LeavesLogger();

    private LeavesLogger() {
        super("Leaves", null);
        setParent(Bukkit.getLogger());
        setLevel(Level.ALL);
    }

    public void severe(String msg, Exception exception) {
        this.log(Level.SEVERE, msg, exception);
    }

    public void warning(String msg, Exception exception) {
        this.log(Level.WARNING, msg, exception);
    }
}
