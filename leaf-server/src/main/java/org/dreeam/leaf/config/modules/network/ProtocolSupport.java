package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.protocol.DoABarrelRollPackets;
import org.dreeam.leaf.protocol.DoABarrelRollProtocol;

import java.util.concurrent.ThreadLocalRandom;

public class ProtocolSupport extends ConfigModule {

    public String basePath() {
        return ConfigCategory.NETWORK.basePath() + ".protocol-support";
    }

    public static boolean strictMode = false;
    public static boolean jadeProtocol = false;
    public static boolean appleskinProtocol = false;
    public static int appleskinSyncTickInterval = 20;
    public static boolean asteorBarProtocol = false;
    public static boolean chatImageProtocol = false;
    public static boolean xaeroMapProtocol = false;
    public static int xaeroMapServerID = ThreadLocalRandom.current().nextInt(); // Leaf - Faster Random
    public static boolean syncmaticaProtocol = false;
    public static boolean syncmaticaQuota = false;
    public static int syncmaticaQuotaLimit = 40000000;

    public static boolean doABarrelRollProtocol = false;
    public static boolean doABarrelRollAllowThrusting = false;
    public static boolean doABarrelRollForceEnabled = false;
    public static boolean doABarrelRollForceInstalled = false;
    public static int doABarrelRollInstalledTimeout = 40;

    @Override
    public void onLoaded() {
        strictMode = globalConfig.getBoolean(basePath() + ".strict-mode", strictMode);
        jadeProtocol = globalConfig.getBoolean(basePath() + ".jade-protocol", jadeProtocol);
        appleskinProtocol = globalConfig.getBoolean(basePath() + ".appleskin-protocol", appleskinProtocol);
        appleskinSyncTickInterval = globalConfig.getInt(basePath() + ".appleskin-protocol-sync-tick-interval", appleskinSyncTickInterval);
        asteorBarProtocol = globalConfig.getBoolean(basePath() + ".asteorbar-protocol", asteorBarProtocol);
        chatImageProtocol = globalConfig.getBoolean(basePath() + ".chatimage-protocol", chatImageProtocol);
        xaeroMapProtocol = globalConfig.getBoolean(basePath() + ".xaero-map-protocol", xaeroMapProtocol);
        xaeroMapServerID = globalConfig.getInt(basePath() + ".xaero-map-server-id", xaeroMapServerID);
        syncmaticaProtocol = globalConfig.getBoolean(basePath() + ".syncmatica-protocol", syncmaticaProtocol);
        syncmaticaQuota = globalConfig.getBoolean(basePath() + ".syncmatica-quota", syncmaticaQuota);
        syncmaticaQuotaLimit = globalConfig.getInt(basePath() + ".syncmatica-quota-limit", syncmaticaQuotaLimit);

        org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol.init(syncmaticaProtocol);

        doABarrelRollProtocol = globalConfig.getBoolean(basePath() + ".do-a-barrel-roll-protocol", doABarrelRollProtocol);
        doABarrelRollAllowThrusting = globalConfig.getBoolean(basePath() + ".do-a-barrel-roll-allow-thrusting", doABarrelRollAllowThrusting);
        doABarrelRollForceEnabled = globalConfig.getBoolean(basePath() + ".do-a-barrel-roll-force-enabled", doABarrelRollForceEnabled);
        doABarrelRollForceInstalled = globalConfig.getBoolean(basePath() + ".do-a-barrel-roll-force-installed", doABarrelRollForceInstalled);
        doABarrelRollInstalledTimeout = globalConfig.getInt(basePath() + ".do-a-barrel-roll-installed-timeout", 0);
        if (doABarrelRollInstalledTimeout <= 0) {
            doABarrelRollInstalledTimeout = 40;
        }
        if (doABarrelRollProtocol) {
            DoABarrelRollProtocol.init(
                doABarrelRollAllowThrusting,
                doABarrelRollForceEnabled,
                doABarrelRollForceInstalled,
                doABarrelRollInstalledTimeout,
                DoABarrelRollPackets.KineticDamage.VANILLA
            );
        } else {
            DoABarrelRollProtocol.deinit();
        }
    }
}
