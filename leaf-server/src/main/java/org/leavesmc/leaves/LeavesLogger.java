package org.leavesmc.leaves;

import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LeavesLogger extends Logger {

    public static final LeavesLogger LOGGER = new LeavesLogger();

    private LeavesLogger() {
        super("Leaves", null);
        setParent(Bukkit.getLogger());
        // Only set level to ALL if debug mode is enabled, otherwise inherit from parent
        Level currentLevel = Bukkit.getLogger().getLevel();
        setLevel(currentLevel != null ? currentLevel : Level.INFO);
    }

    public void severe(String msg, Exception exception) {
        this.log(Level.SEVERE, msg, exception);
    }

    public void warning(String msg, Exception exception) {
        this.log(Level.WARNING, msg, exception);
    }

    // Additional exception handling methods for better logging coverage
    public void info(String msg, Exception exception) {
        this.log(Level.INFO, msg, exception);
    }

    public void config(String msg, Exception exception) {
        this.log(Level.CONFIG, msg, exception);
    }

    public void fine(String msg, Exception exception) {
        this.log(Level.FINE, msg, exception);
    }
}
