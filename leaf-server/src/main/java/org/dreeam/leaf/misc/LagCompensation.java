package org.dreeam.leaf.misc;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Collections;
import java.util.List;

public class LagCompensation {

    public static boolean isTouchingFluid(Level level, BlockPos pos, TagKey<Fluid> fluidTag) {
        final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (final Direction direction : Direction.VALUES) {
            final FluidState fluidState = level.getFluidIfLoaded(neighbor.setWithOffset(pos, direction));
            if (fluidState != null && fluidState.is(fluidTag)) {
                return true;
            }
        }
        return false;
    }

    public static int fluidDelay(Level level, BlockPos pos, int rawDelay, boolean enabledForThisFluid, TagKey<Fluid> oppositeTag) {
        return org.dreeam.leaf.config.modules.misc.LagCompensation.enabled && enabledForThisFluid && !isTouchingFluid(level, pos, oppositeTag)
            ? tt20(rawDelay, true)
            : rawDelay;
    }

    public static float tt20(float ticks, boolean limitZero) {
        float newTicks = (float) rawTT20(ticks);

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static int tt20(int ticks, boolean limitZero) {
        int newTicks = (int) Math.ceil(rawTT20(ticks));

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static double tt20(double ticks, boolean limitZero) {
        double newTicks = rawTT20(ticks);

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static double rawTT20(double ticks) {
        return ticks == 0 ? 0 : ticks * TPSCalculator.getMostAccurateTPS() / TPSCalculator.MAX_TPS;
    }

    public static class TPSCalculator {

        public static Long lastTick;
        public static Long currentTick;
        private static double allMissedTicks = 0;
        private static final List<Double> tpsHistory = Collections.synchronizedList(new DoubleArrayList());
        private static final int historyLimit = 40;

        public static final int MAX_TPS = 20;
        public static final int FULL_TICK = 50;

        private TPSCalculator() {
        }

        public static void onTick() {
            if (currentTick != null) {
                lastTick = currentTick;
            }

            currentTick = System.currentTimeMillis();

            addToHistory(getTPS());
            clearMissedTicks();
            missedTick();
        }

        private static void addToHistory(double tps) {
            if (tpsHistory.size() >= historyLimit) {
                tpsHistory.removeFirst();
            }

            tpsHistory.add(tps);
        }

        public static long getMSPT() {
            return currentTick - lastTick;
        }

        public static double getAverageTPS() {
            double sum = 0.0;

            for (double value : tpsHistory) {
                sum += value;
            }

            return tpsHistory.isEmpty() ? 0.1 : sum / tpsHistory.size();
        }

        public static double getTPS() {
            if (lastTick == null) return -1;
            if (getMSPT() <= 0) return 0.1;

            double tps = 1000 / (double) getMSPT();

            return tps > MAX_TPS ? MAX_TPS : tps;
        }

        public static void missedTick() {
            if (lastTick == null) return;

            long mspt = getMSPT() <= 0 ? 50 : getMSPT();
            double missedTicks = (mspt / (double) FULL_TICK) - 1;

            allMissedTicks += missedTicks <= 0 ? 0 : missedTicks;
        }

        public static double getMostAccurateTPS() {
            return Math.min(getTPS(), getAverageTPS());
        }

        public double getAllMissedTicks() {
            return allMissedTicks;
        }

        public static int applicableMissedTicks() {
            return (int) Math.floor(allMissedTicks);
        }

        public static void clearMissedTicks() {
            allMissedTicks -= applicableMissedTicks();
        }

        public void resetMissedTicks() {
            allMissedTicks = 0;
        }
    }
}
