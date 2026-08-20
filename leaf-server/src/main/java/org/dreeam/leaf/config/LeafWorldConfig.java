package org.dreeam.leaf.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import org.dreeam.leaf.config.modules.fixes.world.Fixes;
import org.dreeam.leaf.config.modules.gameplay.world.EnderDragonRespawn;
import org.dreeam.leaf.config.modules.gameplay.world.HideFlamesOnEntitiesWithFireResistance;
import org.dreeam.leaf.config.modules.gameplay.world.RandomStrollIntoNonTickingChunks;
import org.dreeam.leaf.config.modules.misc.WorldConfigExample;
import org.dreeam.leaf.config.modules.misc.world.SecureSeed;
import org.dreeam.leaf.config.modules.opt.world.EntityWakeUpDuration;
import org.dreeam.leaf.config.modules.opt.world.LoadChunks;
import org.dreeam.leaf.config.modules.opt.world.MaxProjectileChunkLoads;
import org.dreeam.leaf.config.modules.opt.world.OptimizedSheepOffspringColor;
import org.dreeam.leaf.config.modules.opt.world.ReducedIntervals;
import org.dreeam.leaf.config.modules.opt.world.SaveFireworks;

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

    public WorldConfigExample worldConfigExample = new WorldConfigExample();
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
    public SecureSeed secureSeed = new SecureSeed();

    LeafWorldConfig(ConfigFile configFile, Source source) {
        super(configFile);
        this.source = source;
    }

    public boolean isWorldDefaults() {
        return this.source == Source.WORLD_DEFAULTS;
    }
}
