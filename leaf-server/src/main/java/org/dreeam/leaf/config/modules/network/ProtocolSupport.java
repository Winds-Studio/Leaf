package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;
import org.dreeam.leaf.protocol.DoABarrelRollPackets;
import org.dreeam.leaf.protocol.DoABarrelRollProtocol;

import java.util.concurrent.ThreadLocalRandom;

@ConfigClassInfo(category = ConfigCategory.NETWORK, name = "protocol-support")
public class ProtocolSupport implements ConfigModule {

    @ConfigInfo(name = "strict-mode")
    public static boolean strictMode = false;

    @ConfigInfo(name = "jade-protocol")
    public static boolean jadeProtocol = false;

    @ConfigInfo(name = "appleskin-protocol")
    public static boolean appleskinProtocol = false;

    @ConfigInfo(name = "appleskin-protocol-sync-tick-interval")
    public static int appleskinSyncTickInterval = 20;

    @ConfigInfo(name = "asteorbar-protocol")
    public static boolean asteorBarProtocol = false;

    @ConfigInfo(name = "chatimage-protocol")
    public static boolean chatImageProtocol = false;

    @ConfigInfo(name = "xaero-map-protocol")
    public static boolean xaeroMapProtocol = false;

    @ConfigInfo(name = "xaero-map-server-id")
    public static int xaeroMapServerID = ThreadLocalRandom.current().nextInt(); // Leaf - Faster Random

    @ConfigInfo(name = "syncmatica-protocol")
    public static boolean syncmaticaProtocol = false;

    @ConfigInfo(name = "syncmatica-quota")
    public static boolean syncmaticaQuota = false;

    @ConfigInfo(name = "syncmatica-quota-limit")
    public static int syncmaticaQuotaLimit = 40000000;

    @ConfigInfo(name = "do-a-barrel-roll-protocol")
    public static boolean doABarrelRollProtocol = false;

    @ConfigInfo(name = "do-a-barrel-roll-allow-thrusting")
    public static boolean doABarrelRollAllowThrusting = false;

    @ConfigInfo(name = "do-a-barrel-roll-force-enabled")
    public static boolean doABarrelRollForceEnabled = false;

    @ConfigInfo(name = "do-a-barrel-roll-force-installed")
    public static boolean doABarrelRollForceInstalled = false;

    @ConfigInfo(name = "do-a-barrel-roll-installed-timeout")
    public static int doABarrelRollInstalledTimeout = 40;

    @Override
    public void onLoaded() {
        org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol.init(syncmaticaProtocol);

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
