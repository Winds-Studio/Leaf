package org.leavesmc.leaves.replay;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.bot.BotStatsCounter;
import org.leavesmc.leaves.entity.photographer.CraftPhotographer;
import org.leavesmc.leaves.entity.photographer.Photographer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerPhotographer extends ServerPlayer {

    private static final List<ServerPhotographer> photographers = new CopyOnWriteArrayList<>();

    public PhotographerCreateState createState;
    private ServerPlayer followPlayer;
    private Recorder recorder;
    private File saveFile;
    private Vec3 lastPos;

    private final ServerStatsCounter stats;

    private ServerPhotographer(MinecraftServer server, ServerLevel world, GameProfile profile) {
        super(server, world, profile, ClientInformation.createDefault());
        this.gameMode = new ServerPhotographerGameMode(this);
        this.followPlayer = null;
        this.stats = new BotStatsCounter(server);
        this.lastPos = this.position();
    }

    public static ServerPhotographer createPhotographer(@NotNull PhotographerCreateState state) throws IOException {
        if (!isCreateLegal(state.id)) {
            throw new IllegalArgumentException(state.id + " is a invalid photographer id");
        }

        MinecraftServer server = MinecraftServer.getServer();

        ServerLevel world = ((CraftWorld) state.loc.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), state.id);

        ServerPhotographer photographer = new ServerPhotographer(server, world, profile);
        photographer.absSnapTo(state.loc.x(), state.loc.y(), state.loc.z(), state.loc.getYaw(), state.loc.getPitch());

        photographer.recorder = new Recorder(photographer, state.option, new File("replay", state.id));
        photographer.saveFile = new File("replay", state.id + ".mcpr");
        photographer.createState = state;

        photographer.recorder.start();
        MinecraftServer.getServer().getPlayerList().placeNewPhotographer(photographer.recorder, photographer, world);
        photographer.level().chunkSource.move(photographer);
        photographer.setInvisible(true);
        photographers.add(photographer);

        LeavesLogger.LOGGER.info("Photographer " + state.id + " created");

        // TODO record distance

        return photographer;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getServer().getTickCount() % 10 == 0) {
            connection.resetPosition();
            this.level().chunkSource.move(this);
        }

        if (this.followPlayer != null) {
            if (this.getCamera() == this || this.getCamera().level() != this.level()) {
                this.getBukkitPlayer().teleport(this.getCamera().getBukkitEntity().getLocation());
                this.setCamera(followPlayer);
            }
            if (lastPos.distanceToSqr(this.position()) > 1024D) {
                this.getBukkitPlayer().teleport(this.getCamera().getBukkitEntity().getLocation());
            }
        }

        lastPos = this.position();
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        super.die(damageSource);
        remove(true);
    }

    @Override
    public boolean isInvulnerableTo(@NotNull ServerLevel world, @NotNull DamageSource damageSource) {
        return true;
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel world, @NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public void setHealth(float health) {
    }

    @NotNull
    @Override
    public ServerStatsCounter getStats() {
        return stats;
    }

    public void remove(boolean async) {
        this.remove(async, true);
    }

    public void remove(boolean async, boolean save) {
        super.remove(RemovalReason.KILLED);
        photographers.remove(this);
        this.recorder.stop();
        this.getServer().getPlayerList().removePhotographer(this);

        LeavesLogger.LOGGER.info("Photographer " + createState.id + " removed");

        if (!recorder.isSaved()) {
            CompletableFuture<Void> future = recorder.saveRecording(saveFile, save);
            if (!async) {
                future.join();
            }
        }
    }

    public void setFollowPlayer(ServerPlayer followPlayer) {
        this.setCamera(followPlayer);
        this.followPlayer = followPlayer;
    }

    public ServerPlayer getFollowPlayer() {
        return followPlayer;
    }

    public void setSaveFile(File saveFile) {
        this.saveFile = saveFile;
    }

    public void pauseRecording() {
        this.recorder.pauseRecording();
    }

    public void resumeRecording() {
        this.recorder.resumeRecording();
    }

    public static ServerPhotographer getPhotographer(String id) {
        for (ServerPhotographer photographer : photographers) {
            if (photographer.createState.id.equals(id)) {
                return photographer;
            }
        }
        return null;
    }

    public static ServerPhotographer getPhotographer(UUID uuid) {
        for (ServerPhotographer photographer : photographers) {
            if (photographer.getUUID().equals(uuid)) {
                return photographer;
            }
        }
        return null;
    }

    public static List<ServerPhotographer> getPhotographers() {
        return photographers;
    }

    public Photographer getBukkitPlayer() {
        return getBukkitEntity();
    }

    @Override
    @NotNull
    public CraftPhotographer getBukkitEntity() {
        return (CraftPhotographer) super.getBukkitEntity();
    }

    public static boolean isCreateLegal(@NotNull String name) {
        if (!name.matches("^[a-zA-Z0-9_]{4,16}$")) {
            return false;
        }

        return Bukkit.getPlayerExact(name) == null && ServerPhotographer.getPhotographer(name) == null;
    }

    public static class PhotographerCreateState {

        public RecorderOption option;
        public Location loc;
        public final String id;

        public PhotographerCreateState(Location loc, String id, RecorderOption option) {
            this.loc = loc;
            this.id = id;
            this.option = option;
        }

        public ServerPhotographer createSync() {
            try {
                return createPhotographer(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
