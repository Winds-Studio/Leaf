package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.protocol.DoABarrelRollPackets;
import org.dreeam.leaf.protocol.DoABarrelRollProtocol;

import java.util.concurrent.ThreadLocalRandom;

public class ProtocolSupport extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.NETWORK.getBaseKeyName() + ".protocol-support";
    }

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
        jadeProtocol = config.getBoolean(getBasePath() + ".jade-protocol", jadeProtocol);
        appleskinProtocol = config.getBoolean(getBasePath() + ".appleskin-protocol", appleskinProtocol);
        appleskinSyncTickInterval = config.getInt(getBasePath() + ".appleskin-protocol-sync-tick-interval", appleskinSyncTickInterval);
        asteorBarProtocol = config.getBoolean(getBasePath() + ".asteorbar-protocol", asteorBarProtocol);
        chatImageProtocol = config.getBoolean(getBasePath() + ".chatimage-protocol", chatImageProtocol);
        xaeroMapProtocol = config.getBoolean(getBasePath() + ".xaero-map-protocol", xaeroMapProtocol);
        xaeroMapServerID = config.getInt(getBasePath() + ".xaero-map-server-id", xaeroMapServerID);
        syncmaticaProtocol = config.getBoolean(getBasePath() + ".syncmatica-protocol", syncmaticaProtocol);
        syncmaticaQuota = config.getBoolean(getBasePath() + ".syncmatica-quota", syncmaticaQuota);
        syncmaticaQuotaLimit = config.getInt(getBasePath() + ".syncmatica-quota-limit", syncmaticaQuotaLimit);

        if (syncmaticaProtocol) {
            org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol.init();
        }

        doABarrelRollProtocol = config.getBoolean(getBasePath() + ".do-a-barrel-roll-protocol", doABarrelRollProtocol);
        doABarrelRollAllowThrusting = config.getBoolean(getBasePath() + ".do-a-barrel-roll-allow-thrusting", doABarrelRollAllowThrusting);
        doABarrelRollForceEnabled = config.getBoolean(getBasePath() + ".do-a-barrel-roll-force-enabled", doABarrelRollForceEnabled);
        doABarrelRollForceInstalled = config.getBoolean(getBasePath() + ".do-a-barrel-roll-force-installed", doABarrelRollForceInstalled);
        doABarrelRollInstalledTimeout = config.getInt(getBasePath() + ".do-a-barrel-roll-installed-timeout", 0);
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
