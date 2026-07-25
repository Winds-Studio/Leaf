package org.dreeam.leaf.command.subcommands;

import io.papermc.paper.command.CommandUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;
import org.dreeam.leaf.command.LeafCommand;
import org.dreeam.leaf.command.PermissionedLeafSubcommand;
import org.dreeam.leaf.config.modules.gameplay.WoolHopperCounter;
import org.leavesmc.leaves.util.HopperCounter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public final class CounterCommand extends PermissionedLeafSubcommand {

    public static final String LITERAL_ARGUMENT = "counter";
    public static final String PERM = LeafCommand.BASE_PERM + "." + LITERAL_ARGUMENT;
    private static final List<String> ROOT_ARGUMENTS = Arrays.stream(DyeColor.values())
        .map(DyeColor::getName)
        .toList();

    public CounterCommand() {
        super(PERM, PermissionDefault.TRUE);
    }

    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage(join(spaces(),
                text("Hopper counter is", GRAY),
                text(HopperCounter.isEnabled() ? "enabled" : "disabled", AQUA)
            ));
            return true;
        }

        final String argument = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            switch (argument) {
                case "enable" -> {
                    this.setEnabled(sender, true);
                    return true;
                }
                case "disable" -> {
                    this.setEnabled(sender, false);
                    return true;
                }
                case "reset" -> {
                    HopperCounter.resetAll(MinecraftServer.getServer(), false);
                    sender.sendMessage(text("Restarted all counters", GRAY));
                    return true;
                }
                default -> {
                    final DyeColor color = DyeColor.byName(argument, null);
                    if (color != null) {
                        this.displayCounter(sender, color, false);
                        return true;
                    }
                }
            }
        } else if (args.length == 2) {
            final DyeColor color = DyeColor.byName(argument, null);
            if (color != null) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "reset" -> {
                        HopperCounter.getCounter(color).reset(MinecraftServer.getServer());
                        sender.sendMessage(join(spaces(),
                            text("Restarted counter", GRAY),
                            text(color.getName(), TextColor.color(color.getTextColor()))
                        ));
                        return true;
                    }
                    case "realtime" -> {
                        this.displayCounter(sender, color, true);
                        return true;
                    }
                    default -> {
                    }
                }
            }
        }

        return false;
    }

    private void setEnabled(final CommandSender sender, final boolean enabled) {
        if (HopperCounter.isEnabled() == enabled) {
            sender.sendMessage(join(spaces(),
                text("Hopper counter is already", GRAY),
                text(enabled ? "enabled" : "disabled", AQUA)
            ));
            return;
        }

        HopperCounter.setEnabled(enabled);
        sender.sendMessage(join(spaces(),
            text("Hopper counter is now", GRAY),
            text(enabled ? "enabled" : "disabled", AQUA)
        ));
    }

    private void displayCounter(final CommandSender sender, final DyeColor color, final boolean realTime) {
        for (final Component component : HopperCounter.getCounter(color).format(MinecraftServer.getServer(), realTime)) {
            sender.sendMessage(component);
        }
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String subCommand, final String[] args) {
        if (!WoolHopperCounter.enabled) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            final List<String> arguments = new ArrayList<>(ROOT_ARGUMENTS);
            arguments.addAll(List.of("enable", "disable", "reset"));
            return CommandUtil.getListMatchingLast(sender, args, arguments);
        }

        if (args.length == 2 && DyeColor.byName(args[0].toLowerCase(Locale.ROOT), null) != null) {
            return CommandUtil.getListMatchingLast(sender, args, List.of("reset", "realtime"));
        }

        return Collections.emptyList();
    }

    @Override
    public boolean testPermission(final CommandSender sender) {
        return WoolHopperCounter.enabled && super.testPermission(sender);
    }
}
