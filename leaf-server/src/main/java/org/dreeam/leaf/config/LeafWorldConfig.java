package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.jspecify.annotations.Nullable;
import org.dreeam.leaf.config.modules.fixes.world.Fixes;
import org.dreeam.leaf.config.modules.gameplay.world.EnderDragonRespawn;
import org.dreeam.leaf.config.modules.gameplay.world.HideFlamesOnEntitiesWithFireResistance;
import org.dreeam.leaf.config.modules.gameplay.world.RandomStrollIntoNonTickingChunks;
import org.dreeam.leaf.config.modules.opt.world.EntityWakeUpDuration;
import org.dreeam.leaf.config.modules.opt.world.LoadChunks;
import org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads;
import org.dreeam.leaf.config.modules.opt.world.OptimizedSheepOffspringColor;
import org.dreeam.leaf.config.modules.opt.world.ReducedIntervals;
import org.dreeam.leaf.config.modules.opt.world.SaveFireworks;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * A world-level configuration initialized from {@link LeafConfig#worldDefaultsConfig()} with an
 * optional per-world overlay.
 *
 * <p>The file is never created by this class. Every world receives its own configuration instance,
 * initially inherits the shared defaults, and then applies values explicitly defined in
 * {@code leaf-world.yml}. World modules are exposed as typed fields for direct access through a
 * level's Leaf configuration.</p>
 */
public final class LeafWorldConfig extends LeafConfigAccessor {

    enum Source {
        WORLD_DEFAULTS,
        WORLD_OVERRIDE
    }

    private final Source source;
    // The values captured for @HotReloadUnsupported options when this instance was first bound.
    // Allocated on demand, so a configuration without such options holds nothing.
    private @Nullable Map<Field, Object> initialValues;

    public SaveFireworks saveFireworks = new SaveFireworks();
    public OptimizedSheepOffspringColor optimizedSheepOffspringColor = new OptimizedSheepOffspringColor();
    public MaxProjectileChunkLoads maxProjectileChunkLoads = new MaxProjectileChunkLoads();
    public ReducedIntervals reducedIntervals = new ReducedIntervals();
    public LoadChunks loadChunks = new LoadChunks();
    public Fixes fixes = new Fixes();
    public RandomStrollIntoNonTickingChunks randomStrollIntoNonTickingChunks = new RandomStrollIntoNonTickingChunks();
    public EntityWakeUpDuration entityWakeUpDuration = new EntityWakeUpDuration();
    public HideFlamesOnEntitiesWithFireResistance hideFlamesOnEntitiesWithFireResistance = new HideFlamesOnEntitiesWithFireResistance();
    public EnderDragonRespawn enderDragonRespawn = new EnderDragonRespawn();

    LeafWorldConfig(ConfigFile configFile, Source source) {
        super(configFile);
        this.source = source;
    }

    @Nullable Map<Field, Object> initialValues() {
        return this.initialValues;
    }

    Map<Field, Object> initialValuesForCapture() {
        if (this.initialValues == null) {
            this.initialValues = new HashMap<>();
        }
        return this.initialValues;
    }

    public boolean isWorldDefaults() {
        return this.source == Source.WORLD_DEFAULTS;
    }
}
