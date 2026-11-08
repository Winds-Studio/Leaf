package org.dreeam.leaf.command.subcommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.dreeam.leaf.command.LeafCommand;
import org.dreeam.leaf.command.PermissionedLeafSubcommand;
import org.dreeam.leaf.config.LeafConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

public final class ReloadCommand extends PermissionedLeafSubcommand {

    public final static String LITERAL_ARGUMENT = "reload";
    public static final String PERM = LeafCommand.BASE_PERM + "." + LITERAL_ARGUMENT;

    public ReloadCommand() {
        super(PERM, PermissionDefault.OP);
    }

    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        this.doLeafReload(sender);
        return true;
    }

    private void doLeafReload(final CommandSender sender) {
        Command.broadcastCommandMessage(sender, Component.text("Reloading Leaf config...", NamedTextColor.GREEN));

        LeafConfig.reloadAsync(sender);
    }
}
