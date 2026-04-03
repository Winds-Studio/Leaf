package org.dreeam.leaf.command.subcommands;

import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.command.LeafCommand;
import org.dreeam.leaf.command.PermissionedLeafSubcommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

public final class VersionCommand extends PermissionedLeafSubcommand {

    public final static String LITERAL_ARGUMENT = "version";
    public static final String PERM = LeafCommand.BASE_PERM + "." + LITERAL_ARGUMENT;

    public VersionCommand() {
        super(PERM, PermissionDefault.TRUE);
    }

    @Override
    public boolean execute(final CommandSender sender, final String subCommand, final String[] args) {
        final Command ver = MinecraftServer.getServer().server.getCommandMap().getCommand("version");

        if (ver != null) {
            ver.execute(sender, LeafCommand.COMMAND_LABEL, me.titaniumtown.ArrayConstants.emptyStringArray); // Gale - JettPack - reduce array allocations
        }

        return true;
    }

    @Override
    public boolean testPermission(CommandSender sender) {
        return super.testPermission(sender) && sender.hasPermission("bukkit.command.version");
    }
}
