// Gale - Gale configuration

package org.galemc.gale.configuration;

import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import io.papermc.paper.configuration.PaperConfigurations;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "InnerClassMayBeStatic"})
public class GaleWorldConfiguration extends ConfigurationPart {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int CURRENT_VERSION = 1;

    private transient final SpigotWorldConfig spigotConfig;
    private transient final Identifier worldKey;

    public GaleWorldConfiguration(SpigotWorldConfig spigotConfig, Identifier worldKey) {
        this.spigotConfig = spigotConfig;
        this.worldKey = worldKey;
    }

    public boolean isDefault() {
        return this.worldKey.equals(PaperConfigurations.WORLD_DEFAULTS_KEY);
    }

    @Setting(Configuration.VERSION_FIELD)
    public int version = CURRENT_VERSION;

    public SmallOptimizations smallOptimizations;

    public class SmallOptimizations extends ConfigurationPart {

        public boolean saveFireworks = true; // Gale - EMC - make saving fireworks configurable
        public boolean useOptimizedSheepOffspringColor = true; // Gale - carpet-fixes - optimize sheep offspring color

        // Gale start - Airplane - reduce projectile chunk loading
        public MaxProjectileChunkLoads maxProjectileChunkLoads;

        public class MaxProjectileChunkLoads extends ConfigurationPart {

            public int perTick = 10;

            public PerProjectile perProjectile;

            public class PerProjectile extends ConfigurationPart {
                public int max = 10;
                public boolean resetMovementAfterReachLimit = false;
                public boolean removeFromWorldAfterReachLimit = false;
            }

        }
        // Gale end - Airplane - reduce projectile chunk loading

        public ReducedIntervals reducedIntervals;

        public class ReducedIntervals extends ConfigurationPart {

            public int acquirePoiForStuckEntity = 60; // Gale - Airplane - reduce acquire POI for stuck entities
            public int checkStuckInWall = 10; // Gale - Pufferfish - reduce in wall checks
            public int villagerItemRepickup = 100; // Gale - EMC - reduce villager item re-pickup

            public CheckNearbyItem checkNearbyItem;

            public class CheckNearbyItem extends ConfigurationPart {

                // Gale start - EMC - reduce hopper item checks
                public Hopper hopper;

                public class Hopper extends ConfigurationPart {

                    public int interval = 1;

                    public Minecart minecart;

                    public class Minecart extends ConfigurationPart {

                        public int interval = 1;

                        public TemporaryImmunity temporaryImmunity;

                        public class TemporaryImmunity extends ConfigurationPart {
                            public int duration = 100;
                            public int nearbyItemMaxAge = 1200;
                            public int checkForMinecartNearItemInterval = 20;
                            // Still recommend to turn-off `checkForMinecartNearItemWhileActive`
                            // Since `Reduce-hopper-item-checks.patch` will cause lag under massive dropped items
                            public boolean checkForMinecartNearItemWhileActive = false; // Leaf - Reduce active items finding hopper nearby check
                            public boolean checkForMinecartNearItemWhileInactive = true;
                            public double maxItemHorizontalDistance = 24.0;
                            public double maxItemVerticalDistance = 4.0;
                        }

                    }

                }
                // Gale end - EMC - reduce hopper item checks

            }

        }

        public LoadChunks loadChunks;

        public class LoadChunks extends ConfigurationPart {
            public boolean toSpawnPhantoms = false; // Gale - MultiPaper - don't load chunks to spawn phantoms
            public boolean toActivateClimbingEntities = false; // Gale - don't load chunks to activate climbing entities
        }

    }

    public GameplayMechanics gameplayMechanics;

    public class GameplayMechanics extends ConfigurationPart {

        public Fixes fixes;

        public class Fixes extends ConfigurationPart {

            public boolean broadcastCritAnimationsAsTheEntityBeingCritted = false; // Gale - MultiPaper - broadcast crit animations as the entity being critted

            // Gale start - Purpur - fix MC-238526
            @Setting("mc-238526")
            public boolean mc238526 = false;
            // Gale end - Purpur - fix MC-238526

            // Gale start - Purpur - fix MC-121706
            @Setting("mc-121706")
            public boolean mc121706 = false;
            // Gale end - Purpur - fix MC-121706

        }

        public boolean entitiesCanRandomStrollIntoNonTickingChunks = true; // Gale - MultiPaper - prevent entities random strolling into non-ticking chunks
        public double entityWakeUpDurationRatioStandardDeviation = 0.2; // Gale - variable entity wake-up duration
        public boolean hideFlamesOnEntitiesWithFireResistance = false; // Gale - Slice - hide flames on entities with fire resistance
        public boolean tryRespawnEnderDragonAfterEndCrystalPlace = true; // Gale - Pufferfish - make ender dragon respawn attempt after placing end crystals configurable

    }

}
