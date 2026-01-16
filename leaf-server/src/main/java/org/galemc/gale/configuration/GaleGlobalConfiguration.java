// Gale - Gale configuration

package org.galemc.gale.configuration;

import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import org.spongepowered.configurate.objectmapping.meta.PostProcess;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.bukkit.plugin.java.JavaPluginLoader;

@SuppressWarnings({"CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal", "InnerClassMayBeStatic"})
public class GaleGlobalConfiguration extends ConfigurationPart {
    static final int CURRENT_VERSION = 1;
    private static GaleGlobalConfiguration instance;

    public static GaleGlobalConfiguration get() {
        return instance;
    }

    static void set(GaleGlobalConfiguration instance) {
        GaleGlobalConfiguration.instance = instance;
    }

    @Setting(Configuration.VERSION_FIELD)
    public int version = CURRENT_VERSION;

    public SmallOptimizations smallOptimizations;

    public class SmallOptimizations extends ConfigurationPart {

        public ReducedIntervals reducedIntervals;

        public class ReducedIntervals extends ConfigurationPart {

            public int increaseTimeStatistics = 20; // Gale - Hydrinity - increase time statistics in intervals
            public int updateEntityLineOfSight = 4; // Gale - Petal - reduce line of sight updates

            @PostProcess
            public void postProcess() {
                net.minecraft.server.level.ServerPlayer.increaseTimeStatisticsInterval = Math.max(1, increaseTimeStatistics); // Gale - Hydrinity - increase time statistics in intervals - store as static field for fast access
            }

        }

    }

    public GameplayMechanics gameplayMechanics;

    public class GameplayMechanics extends ConfigurationPart {

        public boolean enableBookWriting = true; // Gale - Pufferfish - make book writing configurable

    }

    public Misc misc;

    public class Misc extends ConfigurationPart {

        public boolean verifyChatOrder = true; // Gale - Pufferfish - make chat order verification configurable
        public int premiumAccountSlowLoginTimeout = -1; // Gale - make slow login timeout configurable
        public boolean ignoreNullLegacyStructureData = false; // Gale - MultiPaper - ignore null legacy structure data

        public Keepalive keepalive;

        public class Keepalive extends ConfigurationPart {
            public boolean sendMultiple = false; // Gale - Purpur - send multiple keep-alive packets
        }

        // Gale start - YAPFA - last tick time - in TPS command
        public LastTickTimeInTpsCommand lastTickTimeInTpsCommand;

        public class LastTickTimeInTpsCommand extends ConfigurationPart {
            public boolean enabled = false;
            public boolean addOversleep = false;
        }
        // Gale end - YAPFA - last tick time - in TPS command

    }

    public LogToConsole logToConsole;

    public class LogToConsole extends ConfigurationPart { // Gale - EMC - softly log invalid pool element errors

        public boolean invalidStatistics = true; // Gale - EMC - do not log invalid statistics
        public boolean ignoredAdvancements = true; // Gale - Purpur - do not log ignored advancements
        public boolean setBlockInFarChunk = true; // Gale - Purpur - do not log setBlock in far chunks
        public boolean unrecognizedRecipes = false; // Gale - Purpur - do not log unrecognized recipes
        public boolean legacyMaterialInitialization = false; // Gale - Purpur - do not log legacy Material initialization
        public boolean nullIdDisconnections = true; // Gale - Pufferfish - do not log disconnections with null id
        public boolean playerLoginLocations = true; // Gale - JettPack - make logging login location configurable
        public boolean invalidLegacyTextComponent = true; // Leaf - do not log invalid flatten text component parse

        public Chat chat;

        public class Chat extends ConfigurationPart {
            public boolean emptyMessageWarning = false; // Gale - do not log empty message warnings
            public boolean expiredMessageWarning = false; // Gale - do not log expired message warnings
            public boolean notSecureMarker = true; // Gale - do not log Not Secure marker
        }

        // Gale start - Purpur - do not log plugin library loads
        public PluginLibraryLoader pluginLibraryLoader;

        public class PluginLibraryLoader extends ConfigurationPart {

            public boolean downloads = true;
            public boolean startLoadLibrariesForPlugin = true;
            public boolean libraryLoaded = true;

            @PostProcess
            public void postProcess() {
                JavaPluginLoader.logDownloads = this.downloads;
                JavaPluginLoader.logStartLoadLibrariesForPlugin = this.startLoadLibrariesForPlugin;
                JavaPluginLoader.logLibraryLoaded = this.libraryLoaded;
            }

        }
        // Gale end - Purpur - do not log plugin library loads
    }
}
