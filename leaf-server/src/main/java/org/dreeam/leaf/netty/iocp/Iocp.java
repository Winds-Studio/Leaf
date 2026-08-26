package org.dreeam.leaf.netty.iocp;

import oshi.PlatformEnum;
import oshi.SystemInfo;

public final class Iocp {
    private static final Throwable UNAVAILABILITY_CAUSE = probeAvailability();

    private Iocp() {
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("IOCP transport is unavailable");
            error.initCause(UNAVAILABILITY_CAUSE);
            throw error;
        }
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private static Throwable probeAvailability() {
        if (SystemInfo.getCurrentPlatform() != PlatformEnum.WINDOWS) {
            return new UnsupportedOperationException("IOCP transport requires Windows");
        }
        SystemInfo systemInfo = new SystemInfo();
        if (systemInfo.getOperatingSystem().getBitness() != 64) {
            return new UnsupportedOperationException("IOCP transport only supports x64 Windows");
        }
        try {
            IocpNative.probe();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }
}
