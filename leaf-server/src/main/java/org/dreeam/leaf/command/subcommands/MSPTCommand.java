package org.dreeam.leaf.command.subcommands;

import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.dreeam.leaf.command.LeafCommand;
import org.dreeam.leaf.command.PermissionedLeafSubcommand;
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
        MinecraftServer server = MinecraftServer.getServer();

        // Overall server MSPT
        List<Component> times = new ArrayList<>();
        times.addAll(eval(server.tickTimes5s.getTimes()));
        times.addAll(eval(server.tickTimes10s.getTimes()));
        times.addAll(eval(server.tickTimes60s.getTimes()));

        sender.sendMessage(text().content("Server tick times ").color(GOLD)
            .append(text().color(YELLOW)
                .append(
                    text("("),
                    text("avg", GRAY),
                    text("/"),
                    text("min", GRAY),
                    text("/"),
                    text("max", GRAY),
                    text(")")
                )
            ).append(
                text(" from last 5s"),
                text(",", GRAY),
                text(" 10s"),
                text(",", GRAY),
                text(" 1m"),
                text(":", YELLOW)
            )
        );
        sender.sendMessage(text().content("◴ ").color(GOLD)
            .append(text().color(GRAY)
                .append(
                    times.get(0), SLASH, times.get(1), SLASH, times.get(2), text(", ", YELLOW),
                    times.get(3), SLASH, times.get(4), SLASH, times.get(5), text(", ", YELLOW),
                    times.get(6), SLASH, times.get(7), SLASH, times.get(8)
                )
            )
        );

        // World-specific MSPT
        sender.sendMessage(text());
        sender.sendMessage(text().content("World tick times ").color(GOLD)
            .append(text().color(YELLOW)
                .append(
                    text("("),
                    text("avg", GRAY),
                    text("/"),
                    text("min", GRAY),
                    text("/"),
                    text("max", GRAY),
                    text(")")
                )
            ).append(
                text(" from last 5s"),
                text(",", GRAY),
                text(" 10s"),
                text(",", GRAY),
                text(" 1m"),
                text(":", YELLOW)
            )
        );

        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            List<Component> worldTimes = new ArrayList<>();
            worldTimes.addAll(eval(serverLevel.tickTimes5s.getTimes()));
            worldTimes.addAll(eval(serverLevel.tickTimes10s.getTimes()));
            worldTimes.addAll(eval(serverLevel.tickTimes60s.getTimes()));

            sender.sendMessage(text().content("◴ " + serverLevel.getWorld().getName() + ": ").color(GOLD)
                .append(text().color(GRAY)
                    .append(
                        worldTimes.get(0), SLASH, worldTimes.get(1), SLASH, worldTimes.get(2), text(", ", YELLOW),
                        worldTimes.get(3), SLASH, worldTimes.get(4), SLASH, worldTimes.get(5), text(", ", YELLOW),
                        worldTimes.get(6), SLASH, worldTimes.get(7), SLASH, worldTimes.get(8)
                    )
                )
            );
        }

        return true;
    }

    private static List<Component> eval(long[] times) {
        long min = Integer.MAX_VALUE;
        long max = 0L;
        long total = 0L;
        for (long value : times) {
            if (value > 0L && value < min) min = value;
            if (value > max) max = value;
            total += value;
        }
        double avgD = ((double) total / (double) times.length) * 1.0E-6D;
        double minD = ((double) min) * 1.0E-6D;
        double maxD = ((double) max) * 1.0E-6D;
        return Arrays.asList(getColor(avgD), getColor(minD), getColor(maxD));
    }

    private static Component getColor(double avg) {
        return text(DF.format(avg), avg >= 50 ? RED : avg >= 40 ? YELLOW : GREEN);
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String subCommand, final String[] args) {
        return Collections.emptyList();
    }
}
