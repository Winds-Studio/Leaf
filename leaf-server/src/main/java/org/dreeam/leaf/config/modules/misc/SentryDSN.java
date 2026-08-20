package org.dreeam.leaf.config.modules.misc;

import org.apache.logging.log4j.Level;
import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "sentry")
public class SentryDSN implements ConfigModule {

    public static @DoNotLoad String sentryDsnConfigPath = "misc.sentry.dsn";

    @ConfigInfo(name = "dsn", comments = {
        """
            Sentry DSN for improved error logging, leave blank to disable,
            Obtain from https://sentry.io/""",
        """
            Sentry DSN (出现严重错误时将发送至配置的Sentry DSN地址) (留空关闭)"""
    })
    public static String sentryDsn = "";

    @ConfigInfo(name = "log-level", comments = {
        "Logs with a level higher than or equal to this level will be recorded.",
        "大于等于该等级的日志会被记录."
    })
    public static String logLevel = "WARN";

    @ConfigInfo(name = "only-log-thrown", comments = {
        "Only log with a Throwable will be recorded after enabling this.",
        "是否仅记录带有 Throwable 的日志."
    })
    public static boolean onlyLogThrown = true;

    @Override
    public void onLoaded() {
        String sentryEnvironment = System.getenv("SENTRY_DSN");
        if (sentryEnvironment != null) {
            sentryDsn = sentryEnvironment;
        }

        if (sentryDsn != null && !sentryDsn.isBlank()) {
            gg.pufferfish.pufferfish.sentry.SentryManager.init(Level.getLevel(logLevel));
        }
    }
}
