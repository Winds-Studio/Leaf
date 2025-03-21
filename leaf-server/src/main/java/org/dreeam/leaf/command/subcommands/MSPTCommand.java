package org.dreeam.leaf.command.subcommands;

import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.dreeam.leaf.command.LeafCommand;
import org.dreeam.leaf.command.PermissionedLeafSubcommand;
import org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@DefaultQualifier(NonNull.class)
public final class MSPTCommand extends PermissionedLeafSubcommand {

    public static final String LITERAL_ARGUMENT = "mspt";
    public static final String PERM = LeafCommand.BASE_PERM + "." + LITERAL_ARGUMENT;
    private static final DecimalFormat DF = new DecimalFormat("########0.0");
    private static final Component SLASH = text("/");

    public MSPTCommand() {
        super(PERM, PermissionDefault.TRUE);
    }

    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        // Check if parallel world ticking is enabled
        if (!SparklyPaperParallelWorldTicking.enabled) {
            sender.sendMessage(Component.text()
                .content("Per-world MSPT tracking is only available when parallel world ticking is enabled.")
                .color(RED)
                .build());
            sender.sendMessage(Component.text()
                .content("Please enable it in your Leaf configuration to use this command.")
                .color(GRAY)
                .build());
            return true;
        }

        // Check if compact mode is requested
        boolean compactMode = args.length > 0 && args[0].equalsIgnoreCase("compact");

        MinecraftServer server = MinecraftServer.getServer();

        if (compactMode) {
            displayCompactStats(sender, server);
        } else {
            // Display header
            sender.sendMessage(Component.text()
                .content("━━━━━━━━━━━━━ ")
                .color(GOLD)
                .append(Component.text("MSPT Statistics").color(YELLOW))
                .append(Component.text(" ━━━━━━━━━━━━━").color(GOLD))
                .build());

            // Overall server MSPT
            displayServerMSPT(sender, server);

            // Add separator
            sender.sendMessage(Component.text(""));

            // World-specific MSPT
            displayWorldMSPT(sender, server);
        }

        return true;
    }

    private void displayCompactStats(CommandSender sender, MinecraftServer server) {
        // Get server stats (only 5s data with avg/min/max)
        List<Component> serverTimes = eval(server.tickTimes5s.getTimes());

        // Display server stats in compact form
        sender.sendMessage(Component.text()
            .content("Server: ")
            .color(GOLD)
            .append(serverTimes.get(0)).append(SLASH).append(serverTimes.get(1)).append(SLASH).append(serverTimes.get(2))
            .build());

        // Display world stats in compact form
        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            List<Component> worldTimes = eval(serverLevel.tickTimes5s.getTimes());

            sender.sendMessage(Component.text()
                .content(serverLevel.getWorld().getName() + ": ")
                .color(GOLD)
                .append(worldTimes.get(0)).append(SLASH).append(worldTimes.get(1)).append(SLASH).append(worldTimes.get(2))
                .build());
        }
    }

    private void displayServerMSPT(CommandSender sender, MinecraftServer server) {
        List<Component> times = new ArrayList<>();
        times.addAll(eval(server.tickTimes5s.getTimes()));
        times.addAll(eval(server.tickTimes10s.getTimes()));
        times.addAll(eval(server.tickTimes60s.getTimes()));

        sender.sendMessage(Component.text()
            .content("Server tick times ")
            .color(GOLD)
            .append(Component.text()
                .content("(avg/min/max)")
                .color(YELLOW)
            )
            .build());

        sender.sendMessage(Component.text()
            .content("  5s: ")
            .color(GOLD)
            .append(times.get(0)).append(SLASH).append(times.get(1)).append(SLASH).append(times.get(2))
            .build());

        sender.sendMessage(Component.text()
            .content(" 10s: ")
            .color(GOLD)
            .append(times.get(3)).append(SLASH).append(times.get(4)).append(SLASH).append(times.get(5))
            .build());

        sender.sendMessage(Component.text()
            .content(" 60s: ")
            .color(GOLD)
            .append(times.get(6)).append(SLASH).append(times.get(7)).append(SLASH).append(times.get(8))
            .build());
    }

    private void displayWorldMSPT(CommandSender sender, MinecraftServer server) {
        sender.sendMessage(Component.text()
            .content("World-specific tick times ")
            .color(GOLD)
            .append(Component.text()
                .content("(avg/min/max)")
                .color(YELLOW)
            )
            .build());

        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            List<Component> worldTimes = new ArrayList<>();
            worldTimes.addAll(eval(serverLevel.tickTimes5s.getTimes()));
            worldTimes.addAll(eval(serverLevel.tickTimes10s.getTimes()));
            worldTimes.addAll(eval(serverLevel.tickTimes60s.getTimes()));

            // World name header
            sender.sendMessage(Component.text()
                .content("➤ ")
                .color(YELLOW)
                .append(Component.text(serverLevel.getWorld().getName()).color(GOLD))
                .build());

            // Display time periods
            sender.sendMessage(Component.text()
                .content("  5s: ")
                .color(GRAY)
                .append(worldTimes.get(0)).append(SLASH).append(worldTimes.get(1)).append(SLASH).append(worldTimes.get(2))
                .build());

            sender.sendMessage(Component.text()
                .content(" 10s: ")
                .color(GRAY)
                .append(worldTimes.get(3)).append(SLASH).append(worldTimes.get(4)).append(SLASH).append(worldTimes.get(5))
                .build());

            sender.sendMessage(Component.text()
                .content(" 60s: ")
                .color(GRAY)
                .append(worldTimes.get(6)).append(SLASH).append(worldTimes.get(7)).append(SLASH).append(worldTimes.get(8))
                .build());

            boolean hasMoreWorlds = false;
            Iterable<net.minecraft.server.level.ServerLevel> levels = server.getAllLevels();
            for (net.minecraft.server.level.ServerLevel level : levels) {
                if (level != serverLevel) {
                    hasMoreWorlds = true;
                    break;
                }
            }

            if (hasMoreWorlds) {
                sender.sendMessage(Component.text(""));
            }
        }
    }

    private static List<Component> eval(long[] times) {
        long min = Integer.MAX_VALUE;
        long max = 0L;
        long total = 0L;
        int count = 0;

        for (long value : times) {
            if (value > 0L) {
                count++;
                if (value < min) min = value;
                if (value > max) max = value;
                total += value;
            }
        }

        if (count == 0) {
            // No data available yet
            return Arrays.asList(
                text("N/A", GRAY),
                text("N/A", GRAY),
                text("N/A", GRAY)
            );
        }

        double avgD = ((double) total / (double) count) * 1.0E-6D;
        double minD = ((double) min) * 1.0E-6D;
        double maxD = ((double) max) * 1.0E-6D;

        return Arrays.asList(getColoredValue(avgD), getColoredValue(minD), getColoredValue(maxD));
    }

    private static Component getColoredValue(double value) {
        return text(DF.format(value) + "ms",
            value >= 50 ? RED :
                value >= 40 ? YELLOW :
                    value >= 30 ? GOLD :
                        value >= 20 ? GREEN :
                            AQUA);
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String subCommand, final String[] args) {
        if (!SparklyPaperParallelWorldTicking.enabled) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Collections.singletonList("compact");
        }

        return Collections.emptyList();
    }
}
