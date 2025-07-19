package org.dreeam.leaf.misc;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.Collections;
import java.util.List;

public class LagCompensation {

    /**
     * Calculates the compensated ticks (as float) based on the most accurate TPS.
     * If limitZero is true, returns at least 1.
     */
    public static float tt20(float ticks, boolean limitZero) {
        float newTicks = (float) rawTT20(ticks);
        return limitZero ? Math.max(newTicks, 1f) : newTicks;
    }

    /**
     * Calculates the compensated ticks (as int) based on the most accurate TPS.
     * If limitZero is true, returns at least 1.
     */
    public static int tt20(int ticks, boolean limitZero) {
        int newTicks = (int) Math.ceil(rawTT20(ticks));
        return limitZero ? (newTicks > 0 ? newTicks : 1) : newTicks;
    }

    /**
     * Calculates the compensated ticks (as double) based on the most accurate TPS.
     * If limitZero is true, returns at least 1.
     */
    public static double tt20(double ticks, boolean limitZero) {
        double newTicks = rawTT20(ticks);
        return limitZero ? (newTicks > 0 ? newTicks : 1) : newTicks;
    }

    /**
     * Converts the given ticks by compensating them with the average TPS values.
     */
    public static double rawTT20(double ticks) {
        return ticks == 0 ? 0 : ticks * TPSCalculator.getMostAccurateTPS() / TPSCalculator.MAX_TPS;
    }

    public static class TPSCalculator {

        public static Long lastTick;
        public static Long currentTick;
        // Holds any extra missed ticks that have not yet been applied.
        private static double allMissedTicks = 0;
        // Synchronized list to store TPS history for averaging.
        private static final List<Double> tpsHistory = Collections.synchronizedList(new DoubleArrayList());
        private static final int historyLimit = 40;

        public static final int MAX_TPS = 20;
        public static final int FULL_TICK = 50;

        private TPSCalculator() {
        }

        /**
         * Should be called on every tick. Updates the tick history and missed tick count.
         */
        public static void onTick() {
            if (currentTick != null) {
                lastTick = currentTick;
            }
            currentTick = System.currentTimeMillis();
            addToHistory(getTPS());
            clearMissedTicks();
            recordMissedTick();
        }

        /**
         * Adds the current TPS value to the history list.
         * When the limit is reached, the oldest value is removed.
         */
        private static void addToHistory(double tps) {
            synchronized (tpsHistory) {
                if (tpsHistory.size() >= historyLimit) {
                    // Using remove(0) for compatibility instead of removeFirst()
                    tpsHistory.remove(0);
                }
                tpsHistory.add(tps);
            }
        }

        /**
         * Returns the milliseconds per tick.
         * Assumes that lastTick is not null.
         */
        public static long getMSPT() {
            return currentTick - lastTick;
        }

        /**
         * Returns the average TPS from the stored history.
         * If no history is available, returns 0.1.
         */
        public static double getAverageTPS() {
            synchronized (tpsHistory) {
                if (tpsHistory.isEmpty()) {
                    return 0.1;
                }
                double sum = 0.0;
                for (double value : tpsHistory) {
                    sum += value;
                }
                return sum / tpsHistory.size();
            }
        }

        /**
         * Returns the current TPS based on the elapsed time between ticks.
         * If no previous tick is recorded, returns -1.
         */
        public static double getTPS() {
            if (lastTick == null) return -1;
            long mspt = getMSPT();
            if (mspt <= 0) return 0.1;
            double tps = 1000d / mspt;
            return tps > MAX_TPS ? MAX_TPS : tps;
        }

        /**
         * Registers any missed ticks based on the current MSPT.
         */
        public static void recordMissedTick() {
            if (lastTick == null) return;
            long mspt = getMSPT();
            mspt = mspt <= 0 ? FULL_TICK : mspt; // Use full tick if invalid
            double missedTicks = (mspt / (double) FULL_TICK) - 1;
            allMissedTicks += missedTicks > 0 ? missedTicks : 0;
        }

        /**
         * Determines the more accurate TPS measurement by comparing instantaneous TPS and average TPS.
         */
        public static double getMostAccurateTPS() {
            return Math.min(getTPS(), getAverageTPS());
        }

        /**
         * Returns the total applicable missed ticks (floored).
         */
        public static int applicableMissedTicks() {
            return (int) Math.floor(allMissedTicks);
        }

        /**
         * Deducts the applicable missed ticks from the total missed tick count.
         */
        public static void clearMissedTicks() {
            allMissedTicks -= applicableMissedTicks();
        }

        /**
         * Returns the total missed ticks accumulated so far.
         */
        public double getAllMissedTicks() {
            return allMissedTicks;
        }

        /**
         * Resets the total missed ticks to zero.
         */
        public void resetMissedTicks() {
            allMissedTicks = 0;
        }
    }
}
