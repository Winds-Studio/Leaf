package org.leavesmc.leaves.protocol.core;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.protocol.core.invoker.BytebufReceiverInvokerHolder;
import org.leavesmc.leaves.protocol.core.invoker.EmptyInvokerHolder;
import org.leavesmc.leaves.protocol.core.invoker.InitInvokerHolder;
import org.leavesmc.leaves.protocol.core.invoker.MinecraftRegisterInvokerHolder;
import org.leavesmc.leaves.protocol.core.invoker.PayloadReceiverInvokerHolder;
import org.leavesmc.leaves.protocol.core.invoker.PlayerInvokerHolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LeavesProtocolManager {

    private static final LeavesLogger LOGGER = LeavesLogger.LOGGER;

    private static final Map<Class<? extends LeavesCustomPayload>, PayloadReceiverInvokerHolder> PAYLOAD_RECEIVERS = new HashMap<>();
    private static final Map<Class<? extends LeavesCustomPayload>, ResourceLocation> IDS = new HashMap<>();
    private static final Map<Class<? extends LeavesCustomPayload>, StreamCodec<? super RegistryFriendlyByteBuf, LeavesCustomPayload>> CODECS = new HashMap<>();
    private static final Map<ResourceLocation, StreamCodec<? super RegistryFriendlyByteBuf, LeavesCustomPayload>> ID2CODEC = new HashMap<>();

    private static final Map<String, BytebufReceiverInvokerHolder> STRICT_BYTEBUF_RECEIVERS = new HashMap<>();
    private static final Map<String, BytebufReceiverInvokerHolder> NAMESPACED_BYTEBUF_RECEIVERS = new HashMap<>();
    private static final List<BytebufReceiverInvokerHolder> GENERIC_BYTEBUF_RECEIVERS = new ArrayList<>();

    private static final Map<String, MinecraftRegisterInvokerHolder> STRICT_MINECRAFT_REGISTER = new HashMap<>();
    private static final Map<String, MinecraftRegisterInvokerHolder> NAMESPACED_MINECRAFT_REGISTER = new HashMap<>();
    private static final List<MinecraftRegisterInvokerHolder> WILD_MINECRAFT_REGISTER = new ArrayList<>();

    private static final List<EmptyInvokerHolder<ProtocolHandler.Ticker>> TICKERS = new ArrayList<>();

    private static final List<PlayerInvokerHolder<ProtocolHandler.PlayerJoin>> PLAYER_JOIN = new ArrayList<>();
    private static final List<PlayerInvokerHolder<ProtocolHandler.PlayerLeave>> PLAYER_LEAVE = new ArrayList<>();
    private static final List<EmptyInvokerHolder<ProtocolHandler.ReloadServer>> RELOAD_SERVER = new ArrayList<>();
    private static final List<EmptyInvokerHolder<ProtocolHandler.ReloadDataPack>> RELOAD_DATAPACK = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public static void init() {
        for (Class<?> clazz : getClasses("org.leavesmc.leaves.protocol")) {
            if (LeavesCustomPayload.class.isAssignableFrom(clazz) && !clazz.equals(LeavesCustomPayload.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    if (!Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    try {
                        final LeavesCustomPayload.ID id = field.getAnnotation(LeavesCustomPayload.ID.class);
                        if (id != null && field.getType().equals(ResourceLocation.class)) {
                            IDS.put((Class<? extends LeavesCustomPayload>) clazz, (ResourceLocation) field.get(null));
                        }
                        final LeavesCustomPayload.Codec codec = field.getAnnotation(LeavesCustomPayload.Codec.class);
                        if (codec != null && field.getType().equals(StreamCodec.class)) {
                            CODECS.put((Class<? extends LeavesCustomPayload>) clazz, (StreamCodec<? super RegistryFriendlyByteBuf, LeavesCustomPayload>) field.get(null));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                continue;
            }

            final LeavesProtocol.Register register = clazz.getAnnotation(LeavesProtocol.Register.class);
            if (register == null) {
                continue;
            }
            LeavesProtocol protocol;
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                protocol = (LeavesProtocol) constructor.newInstance();
            } catch (Throwable throwable) {
                LOGGER.severe("Failed to load class " + clazz.getName() + ". " + throwable);
                return;
            }

            // Leaf start - optimize leaves protocol manager
            boolean active = true;
            try {
                Method isActiveMethod = clazz.getDeclaredMethod("isActive");
                isActiveMethod.setAccessible(true);
                active = (Boolean) isActiveMethod.invoke(protocol);
            } catch (Throwable e) {
                LOGGER.warning("Failed to check isActive for " + clazz.getName() + ": " + e);
                continue;
            }
            // Leaf end - optimize leaves protocol manager

            for (final Method method : clazz.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                method.setAccessible(true);

                final ProtocolHandler.Init init = method.getAnnotation(ProtocolHandler.Init.class);
                if (init != null) {
                    InitInvokerHolder holder = new InitInvokerHolder(protocol, method, init);
                    try {
                        holder.invoke();
                    } catch (RuntimeException exception) {
                        LOGGER.severe("Failed to invoke init method " + method.getName() + " in " + clazz.getName() + ", " + exception.getCause() + ": " + exception.getMessage());
                    }
                    continue;
                }

                if (!active) continue; // Leaf - optimize leaves protocol manager

                // Leaf start - optimize leaves protocol manager - move up
                final ProtocolHandler.ReloadServer reloadServer = method.getAnnotation(ProtocolHandler.ReloadServer.class);
                if (reloadServer != null) {
                    RELOAD_SERVER.add(new EmptyInvokerHolder<>(protocol, method, reloadServer));
                    continue;
                }

                final ProtocolHandler.ReloadDataPack reloadDataPack = method.getAnnotation(ProtocolHandler.ReloadDataPack.class);
                if (reloadDataPack != null) {
                    RELOAD_DATAPACK.add(new EmptyInvokerHolder<>(protocol, method, reloadDataPack));
                    continue;
                }
                // Leaf end - optimize leaves protocol manager - move up

                final ProtocolHandler.PayloadReceiver payloadReceiver = method.getAnnotation(ProtocolHandler.PayloadReceiver.class);
                if (payloadReceiver != null) {
                    PAYLOAD_RECEIVERS.put(payloadReceiver.payload(), new PayloadReceiverInvokerHolder(protocol, method, payloadReceiver));
                    continue;
                }

                final ProtocolHandler.BytebufReceiver bytebufReceiver = method.getAnnotation(ProtocolHandler.BytebufReceiver.class);
                if (bytebufReceiver != null) {
                    String key = bytebufReceiver.key();
                    BytebufReceiverInvokerHolder holder = new BytebufReceiverInvokerHolder(protocol, method, bytebufReceiver);
                    if (bytebufReceiver.onlyNamespace()) {
                        NAMESPACED_BYTEBUF_RECEIVERS.put(key.isEmpty() ? register.namespace() : key, holder);
                    } else {
                        if (key.isEmpty()) {
                            GENERIC_BYTEBUF_RECEIVERS.add(holder);
                        } else {
                            if (key.contains(":")) {
                                STRICT_BYTEBUF_RECEIVERS.put(key, holder);
                            } else {
                                STRICT_BYTEBUF_RECEIVERS.put(register.namespace() + ":" + key, holder);
                            }
                        }
                    }
                    continue;
                }

                final ProtocolHandler.Ticker ticker = method.getAnnotation(ProtocolHandler.Ticker.class);
                if (ticker != null) {
                    TICKERS.add(new EmptyInvokerHolder<>(protocol, method, ticker));
                    continue;
                }

                final ProtocolHandler.PlayerJoin playerJoin = method.getAnnotation(ProtocolHandler.PlayerJoin.class);
                if (playerJoin != null) {
                    PLAYER_JOIN.add(new PlayerInvokerHolder<>(protocol, method, playerJoin));
                    continue;
                }

                final ProtocolHandler.PlayerLeave playerLeave = method.getAnnotation(ProtocolHandler.PlayerLeave.class);
                if (playerLeave != null) {
                    PLAYER_LEAVE.add(new PlayerInvokerHolder<>(protocol, method, playerLeave));
                    continue;
                }

                final ProtocolHandler.MinecraftRegister minecraftRegister = method.getAnnotation(ProtocolHandler.MinecraftRegister.class);
                if (minecraftRegister != null) {
                    String key = minecraftRegister.key();
                    MinecraftRegisterInvokerHolder holder = new MinecraftRegisterInvokerHolder(protocol, method, minecraftRegister);
                    if (minecraftRegister.onlyNamespace()) {
                        NAMESPACED_MINECRAFT_REGISTER.put(key.isEmpty() ? register.namespace() : key, holder);
                    } else {
                        if (key.isEmpty()) {
                            WILD_MINECRAFT_REGISTER.add(holder);
                        } else {
                            if (key.contains(":")) {
                                STRICT_MINECRAFT_REGISTER.put(key, holder);
                            } else {
                                STRICT_MINECRAFT_REGISTER.put(register.namespace() + ":" + key, holder);
                            }
                        }
                    }
                }
            }
        }
        for (var idInfo : IDS.entrySet()) {
            var codec = CODECS.get(idInfo.getKey());
            if (codec == null) {
                throw new IllegalArgumentException("Payload " + idInfo.getKey() + " is not configured correctly");
            }
            ID2CODEC.put(idInfo.getValue(), codec);
        }
    }

    public static LeavesCustomPayload decode(ResourceLocation location, FriendlyByteBuf buf) {
        var codec = ID2CODEC.get(location);
        if (codec == null) {
            return null;
        }
        return codec.decode(ProtocolUtils.decorate(buf));
    }

    public static void encode(FriendlyByteBuf buf, LeavesCustomPayload payload) {
        var location = IDS.get(payload.getClass());
        var codec = CODECS.get(payload.getClass());
        if (location == null || codec == null) {
            throw new IllegalArgumentException("Payload " + payload.getClass() + " is not configured correctly " + location + " " + codec);
        }
        buf.writeResourceLocation(location);
        codec.encode(ProtocolUtils.decorate(buf), payload);
    }

    public static void handlePayload(ServerPlayer player, LeavesCustomPayload payload) {
        PayloadReceiverInvokerHolder holder;
        if ((holder = PAYLOAD_RECEIVERS.get(payload.getClass())) != null) {
            holder.invoke(player, payload);
        }
    }

    public static boolean handleBytebuf(ServerPlayer player, ResourceLocation location, ByteBuf buf) {
        RegistryFriendlyByteBuf buf1 = ProtocolUtils.decorate(buf);
        BytebufReceiverInvokerHolder holder;
        if ((holder = STRICT_BYTEBUF_RECEIVERS.get(location.toString())) != null) {
            holder.invoke(player, buf1);
            return true;
        }
        if ((holder = NAMESPACED_BYTEBUF_RECEIVERS.get(location.getNamespace())) != null) {
            if (holder.invoke(player, buf1)) {
                return true;
            }
        }
        for (var holder1 : GENERIC_BYTEBUF_RECEIVERS) {
            if (holder1.invoke(player, buf1)) {
                return true;
            }
        }
        return false;
    }

    public static void handleTick(long tickCount) {
        for (var tickerInfo : TICKERS) {
            if (tickCount % tickerInfo.owner().tickerInterval(tickerInfo.handler().tickerId()) == 0) {
                tickerInfo.invoke();
            }
        }
    }

    public static void handlePlayerJoin(ServerPlayer player) {
        sendKnownId(player);
        for (var join : PLAYER_JOIN) {
            join.invoke(player);
        }
    }

    public static void handlePlayerLeave(ServerPlayer player) {
        for (var leave : PLAYER_LEAVE) {
            leave.invoke(player);
        }
    }

    public static void handleServerReload() {
        for (var reload : RELOAD_SERVER) {
            reload.invoke();
        }
    }

    public static void handleDataPackReload() {
        for (var reload : RELOAD_DATAPACK) {
            reload.invoke();
        }
    }

    public static void handleMinecraftRegister(String channelId, ServerPlayer player) {
        ResourceLocation location = ResourceLocation.tryParse(channelId);
        if (location == null) {
            return;
        }

        for (var wildHolder : WILD_MINECRAFT_REGISTER) {
            wildHolder.invoke(player, location);
        }

        MinecraftRegisterInvokerHolder holder;
        if ((holder = STRICT_MINECRAFT_REGISTER.get(location.toString())) != null) {
            holder.invoke(player, location);
        }
        if ((holder = NAMESPACED_MINECRAFT_REGISTER.get(location.getNamespace())) != null) {
            holder.invoke(player, location);
        }
    }

    private static void sendKnownId(ServerPlayer player) {
        Set<String> set = new HashSet<>();
        // Leaf start - optimize leaves protocol manager
        PAYLOAD_RECEIVERS.forEach((clazz, holder) -> set.add(IDS.get(clazz).toString()));
        STRICT_BYTEBUF_RECEIVERS.forEach((key, holder) -> set.add(key));
        if (set.isEmpty()) return;
        // Leaf end - optimize leaves protocol manager
        ProtocolUtils.sendBytebufPacket(player, ResourceLocation.fromNamespaceAndPath("minecraft", "register"), buf -> {
            for (String channel : set) {
                buf.writeBytes(channel.getBytes(StandardCharsets.US_ASCII));
                buf.writeByte(0);
            }
            buf.writerIndex(Math.max(buf.writerIndex() - 1, 0));
        });
    }

    public static Set<Class<?>> getClasses(String pack) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String packageDirName = pack.replace('.', '/');
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                    findClassesInPackageByFile(pack, filePath, classes);
                } else if ("jar".equals(protocol)) {
                    JarFile jar;
                    try {
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        Enumeration<JarEntry> entries = jar.entries();
                        findClassesInPackageByJar(pack, entries, packageDirName, classes);
                    } catch (IOException exception) {
                        LOGGER.warning("Failed to load jar file, " + exception.getCause() + ": " + exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            LOGGER.warning("Failed to load classes, " + exception.getCause() + ": " + exception.getMessage());
        }
        return classes;
    }

    private static void findClassesInPackageByFile(String packageName, String packagePath, Set<Class<?>> classes) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirfiles = dir.listFiles((file) -> file.isDirectory() || file.getName().endsWith(".class"));
        if (dirfiles != null) {
            for (File file : dirfiles) {
                if (file.isDirectory()) {
                    findClassesInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
                } else {
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException exception) {
                        LOGGER.warning("Failed to load class " + className + ", " + exception.getCause() + ": " + exception.getMessage());
                    }
                }
            }
        }
    }

    private static void findClassesInPackageByJar(String packageName, Enumeration<JarEntry> entries, String packageDirName, Set<Class<?>> classes) {
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }
            if (name.startsWith(packageDirName)) {
                int idx = name.lastIndexOf('/');
                if (idx != -1) {
                    packageName = name.substring(0, idx).replace('/', '.');
                }
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException exception) {
                        LOGGER.warning("Failed to load class " + className + ", " + exception.getCause() + ": " + exception.getMessage());
                    }
                }
            }
        }
    }
}
