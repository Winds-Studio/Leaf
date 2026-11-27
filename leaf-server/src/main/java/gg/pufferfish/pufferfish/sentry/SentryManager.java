package gg.pufferfish.pufferfish.sentry;

import com.mojang.logging.LogUtils;
import io.sentry.Sentry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.dreeam.leaf.config.modules.misc.SentryDSN;
import org.slf4j.Logger;

public class SentryManager {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private SentryManager() {
    }

    private static boolean initialized = false;

    public static synchronized void init(Level logLevel) {
        if (initialized) {
            return;
        }
        if (logLevel == null) {
            LOGGER.error("Invalid log level, defaulting to WARN.");
            logLevel = Level.WARN;
        }
        try {
            initialized = true;

            Sentry.init(options -> {
                options.setDsn(SentryDSN.sentryDsn);
                options.setMaxBreadcrumbs(100);
            });

            PufferfishSentryAppender appender = new PufferfishSentryAppender(logLevel);
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
            LOGGER.info("Sentry logging started!");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize sentry!", e);
            initialized = false;
        }
    }

}
