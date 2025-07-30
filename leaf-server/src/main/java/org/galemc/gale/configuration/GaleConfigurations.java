// Gale - Gale configuration

package org.galemc.gale.configuration;

import com.google.common.collect.Table;
import com.mojang.logging.LogUtils;
import io.leangen.geantyref.TypeToken;
import io.papermc.paper.configuration.Configuration;
import io.papermc.paper.configuration.ConfigurationPart;
import io.papermc.paper.configuration.Configurations;
import io.papermc.paper.configuration.NestedSetting;
import io.papermc.paper.configuration.PaperConfigurations;
import io.papermc.paper.configuration.legacy.RequiresSpigotInitialization;
import io.papermc.paper.configuration.mapping.InnerClassFieldDiscoverer;
import io.papermc.paper.configuration.serializer.ComponentSerializer;
import io.papermc.paper.configuration.serializer.EnumValueSerializer;
import io.papermc.paper.configuration.serializer.ServerboundPacketClassSerializer;
import io.papermc.paper.configuration.serializer.StringRepresentableSerializer;
import io.papermc.paper.configuration.serializer.collection.TableSerializer;
import io.papermc.paper.configuration.serializer.collection.map.FastutilMapSerializer;
import io.papermc.paper.configuration.serializer.collection.map.MapSerializer;
import io.papermc.paper.configuration.serializer.registry.RegistryHolderSerializer;
import io.papermc.paper.configuration.serializer.registry.RegistryValueSerializer;
import io.papermc.paper.configuration.transformation.Transformations;
import io.papermc.paper.configuration.type.BooleanOrDefault;
import io.papermc.paper.configuration.type.Duration;
import io.papermc.paper.configuration.type.EngineMode;
import io.papermc.paper.configuration.type.fallback.FallbackValueSerializer;
import io.papermc.paper.configuration.type.number.DoubleOr;
import io.papermc.paper.configuration.type.number.IntOr;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.transformation.ConfigurationTransformation;
import org.spongepowered.configurate.transformation.TransformAction;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.leangen.geantyref.GenericTypeReflector.erase;

@SuppressWarnings("Convert2Diamond")
public class GaleConfigurations extends Configurations<GaleGlobalConfiguration, GaleWorldConfiguration> {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String GLOBAL_CONFIG_FILE_NAME = "gale-global.yml";
    static final String WORLD_DEFAULTS_CONFIG_FILE_NAME = "gale-world-defaults.yml";
    static final String WORLD_CONFIG_FILE_NAME = "gale-world.yml";
    public static final String CONFIG_DIR = "config";

    private static final String GLOBAL_HEADER = String.format("""
        This is the global configuration file for Gale.
        As you can see, there's a lot to configure. Some options may impact gameplay, so use
        with caution, and make sure you know what each option does before configuring.
        
        If you need help with the configuration or have any questions related to Gale,
        join us in our Discord, or check the GitHub Wiki pages.
        
        The world configuration options are inside
        their respective world folder. The files are named %s
        
        Wiki: https://github.com/GaleMC/Gale/wiki
        Discord: https://discord.gg/gwezNT8c24""", WORLD_CONFIG_FILE_NAME);

    private static final String WORLD_DEFAULTS_HEADER = """
        This is the world defaults configuration file for Gale.
        As you can see, there's a lot to configure. Some options may impact gameplay, so use
        with caution, and make sure you know what each option does before configuring.
        
        If you need help with the configuration or have any questions related to Gale,
        join us in our Discord, or check the GitHub Wiki pages.
        
        Configuration options here apply to all worlds, unless you specify overrides inside
        the world-specific config file inside each world folder.
        
        Wiki: https://github.com/GaleMC/Gale/wiki
        Discord: https://discord.gg/gwezNT8c24""";

    private static final Function<ContextMap, String> WORLD_HEADER = map -> String.format("""
            This is a world configuration file for Gale.
            This file may start empty but can be filled with settings to override ones in the %s/%s
            
            World: %s (%s)""",
        CONFIG_DIR,
        WORLD_DEFAULTS_CONFIG_FILE_NAME,
        map.require(WORLD_NAME),
        map.require(WORLD_KEY)
    );

    private static final String MOVED_NOTICE = """
        The global and world default configuration files have moved to %s
        and the world-specific configuration file has been moved inside
        the respective world folder.
        
        See https://github.com/GaleMC/Gale/wiki for more information.
        """;

    public GaleConfigurations(final Path globalFolder) {
        super(globalFolder, GaleGlobalConfiguration.class, GaleWorldConfiguration.class, GLOBAL_CONFIG_FILE_NAME, WORLD_DEFAULTS_CONFIG_FILE_NAME, WORLD_CONFIG_FILE_NAME);
    }

    @Override
    protected YamlConfigurationLoader.Builder createLoaderBuilder() {
        return super.createLoaderBuilder()
            .defaultOptions(GaleConfigurations::defaultOptions);
    }

    private static ConfigurationOptions defaultOptions(ConfigurationOptions options) {
        return options.serializers(builder -> builder
            .register(MapSerializer.TYPE, new MapSerializer(false))
            .register(new EnumValueSerializer())
            .register(new ComponentSerializer())
        );
    }

    @Override
    protected ObjectMapper.Factory.Builder createGlobalObjectMapperFactoryBuilder() {
        return defaultGlobalFactoryBuilder(super.createGlobalObjectMapperFactoryBuilder());
    }

    private static ObjectMapper.Factory.Builder defaultGlobalFactoryBuilder(ObjectMapper.Factory.Builder builder) {
        return builder.addDiscoverer(InnerClassFieldDiscoverer.globalConfig(PaperConfigurations.defaultFieldProcessors()));
    }

    @Override
    protected YamlConfigurationLoader.Builder createGlobalLoaderBuilder(RegistryAccess registryAccess) {
        return super.createGlobalLoaderBuilder(registryAccess)
            .defaultOptions((options) -> defaultGlobalOptions(registryAccess, options));
    }

    private static ConfigurationOptions defaultGlobalOptions(RegistryAccess registryAccess, ConfigurationOptions options) {
        return options
            .header(GLOBAL_HEADER)
            .serializers(builder -> builder.register(new ServerboundPacketClassSerializer())
                .register(new RegistryValueSerializer<>(new TypeToken<DataComponentType<?>>() {}, registryAccess, Registries.DATA_COMPONENT_TYPE, false))
            );
    }

    @Override
    public GaleGlobalConfiguration initializeGlobalConfiguration(final RegistryAccess registryAccess) throws ConfigurateException {
        GaleGlobalConfiguration configuration = super.initializeGlobalConfiguration(registryAccess);
        GaleGlobalConfiguration.set(configuration);
        return configuration;
    }

    @Override
    protected ContextMap.Builder createDefaultContextMap(final RegistryAccess registryAccess) {
        return super.createDefaultContextMap(registryAccess)
            .put(PaperConfigurations.SPIGOT_WORLD_CONFIG_CONTEXT_KEY, PaperConfigurations.SPIGOT_WORLD_DEFAULTS);
    }

    @Override
    protected ObjectMapper.Factory.Builder createWorldObjectMapperFactoryBuilder(final ContextMap contextMap) {
        return super.createWorldObjectMapperFactoryBuilder(contextMap)
            .addNodeResolver(new RequiresSpigotInitialization.Factory(contextMap.require(PaperConfigurations.SPIGOT_WORLD_CONFIG_CONTEXT_KEY).get()))
            .addNodeResolver(new NestedSetting.Factory())
            .addDiscoverer(InnerClassFieldDiscoverer.galeWorldConfig(contextMap, PaperConfigurations.defaultFieldProcessors()));
    }

    @Override
    protected YamlConfigurationLoader.Builder createWorldConfigLoaderBuilder(final ContextMap contextMap) {
        final RegistryAccess access = contextMap.require(REGISTRY_ACCESS);
        return super.createWorldConfigLoaderBuilder(contextMap)
            .defaultOptions(options -> options
                .header(contextMap.require(WORLD_NAME).equals(WORLD_DEFAULTS) ? WORLD_DEFAULTS_HEADER : WORLD_HEADER.apply(contextMap))
                .serializers(serializers -> serializers
                    .register(new TypeToken<Reference2IntMap<?>>() {}, new FastutilMapSerializer.SomethingToPrimitive<Reference2IntMap<?>>(Reference2IntOpenHashMap::new, Integer.TYPE))
                    .register(new TypeToken<Reference2LongMap<?>>() {}, new FastutilMapSerializer.SomethingToPrimitive<Reference2LongMap<?>>(Reference2LongOpenHashMap::new, Long.TYPE))
                    .register(new TypeToken<Table<?, ?, ?>>() {}, new TableSerializer())
                    .register(new StringRepresentableSerializer())
                    .register(IntOr.Default.SERIALIZER)
                    .register(IntOr.Disabled.SERIALIZER)
                    .register(DoubleOr.Default.SERIALIZER)
                    .register(BooleanOrDefault.SERIALIZER)
                    .register(Duration.SERIALIZER)
                    .register(EngineMode.SERIALIZER)
                    .register(FallbackValueSerializer.create(contextMap.require(PaperConfigurations.SPIGOT_WORLD_CONFIG_CONTEXT_KEY).get(), MinecraftServer::getServer))
                    .register(new RegistryValueSerializer<>(new TypeToken<EntityType<?>>() {}, access, Registries.ENTITY_TYPE, true))
                    .register(new RegistryValueSerializer<>(Item.class, access, Registries.ITEM, true))
                    .register(new RegistryHolderSerializer<>(new TypeToken<ConfiguredFeature<?, ?>>() {}, access, Registries.CONFIGURED_FEATURE, false))
                    .register(new RegistryHolderSerializer<>(Item.class, access, Registries.ITEM, true))
                )
            );
    }

    @Override
    protected void applyWorldConfigTransformations(final ContextMap contextMap, final ConfigurationNode node, final @Nullable ConfigurationNode defaultsNode) throws ConfigurateException {
        final ConfigurationNode version = node.node(Configuration.VERSION_FIELD);
        final String world = contextMap.require(WORLD_NAME);
        if (version.virtual()) {
            LOGGER.warn("The Gale world config file for {} didn't have a version set, assuming latest", world);
            version.raw(GaleWorldConfiguration.CURRENT_VERSION);
        }
        if (GaleRemovedConfigurations.REMOVED_WORLD_PATHS.length > 0) {
            ConfigurationTransformation.Builder builder = ConfigurationTransformation.builder();
            for (NodePath path : GaleRemovedConfigurations.REMOVED_WORLD_PATHS) {
                builder.addAction(path, TransformAction.remove());
            }
            builder.build().apply(node);
        }
        // ADD FUTURE TRANSFORMS HERE
    }

    @Override
    protected void applyGlobalConfigTransformations(ConfigurationNode node) throws ConfigurateException {
        if (GaleRemovedConfigurations.REMOVED_GLOBAL_PATHS.length > 0) {
            ConfigurationTransformation.Builder builder = ConfigurationTransformation.builder();
            for (NodePath path : GaleRemovedConfigurations.REMOVED_GLOBAL_PATHS) {
                builder.addAction(path, TransformAction.remove());
            }
            builder.build().apply(node);
        }
        // ADD FUTURE TRANSFORMS HERE
    }

    private static final List<Transformations.DefaultsAware> DEFAULT_AWARE_TRANSFORMATIONS = Collections.emptyList();

    @Override
    protected void applyDefaultsAwareWorldConfigTransformations(final ContextMap contextMap, final ConfigurationNode worldNode, final ConfigurationNode defaultsNode) throws ConfigurateException {
        final ConfigurationTransformation.Builder builder = ConfigurationTransformation.builder();
        // ADD FUTURE TRANSFORMS HERE (these transforms run after the defaults have been merged into the node)
        DEFAULT_AWARE_TRANSFORMATIONS.forEach(transform -> transform.apply(builder, contextMap, defaultsNode));

        ConfigurationTransformation transformation;
        try {
            transformation = builder.build(); // build throws IAE if no actions were provided (bad zml)
        } catch (IllegalArgumentException ignored) {
            return;
        }
        transformation.apply(worldNode);
    }

    @Override
    public GaleWorldConfiguration createWorldConfig(final ContextMap contextMap) {
        final String levelName = contextMap.require(WORLD_NAME);
        try {
            return super.createWorldConfig(contextMap);
        } catch (IOException exception) {
            throw new RuntimeException("Could not create Gale world config for " + levelName, exception);
        }
    }

    @Override
    protected boolean isConfigType(final Type type) {
        return ConfigurationPart.class.isAssignableFrom(erase(type));
    }

    public void reloadConfigs(MinecraftServer server) {
        try {
            this.initializeGlobalConfiguration(server.registryAccess(), reloader(this.globalConfigClass, GaleGlobalConfiguration.get()));
            this.initializeWorldDefaultsConfiguration(server.registryAccess());
            for (ServerLevel level : server.getAllLevels()) {
                this.createWorldConfig(PaperConfigurations.createWorldContextMap(level), reloader(this.worldConfigClass, level.galeConfig()));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Could not reload Gale configuration files", ex);
        }
    }

    public static GaleConfigurations setup(final Path configDir) throws Exception {
        try {
            PaperConfigurations.createDirectoriesSymlinkAware(configDir);
            return new GaleConfigurations(configDir);
        } catch (final IOException ex) {
            throw new RuntimeException("Could not setup GaleConfigurations", ex);
        }
    }

    @Override
    protected int globalConfigVersion() {
        return GaleGlobalConfiguration.CURRENT_VERSION;
    }

    @Override
    protected int worldConfigVersion() {
        return getWorldConfigurationCurrentVersion();
    }

    @Override
    public int getWorldConfigurationCurrentVersion() {
        return GaleWorldConfiguration.CURRENT_VERSION;
    }

}
