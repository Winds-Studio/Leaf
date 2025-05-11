package org.dreeam.leaf.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.dreeam.leaf.config.modules.gameplay.Knockback;

public class TestKB {
    public static boolean doMoveTarget;
    public static boolean resetMove;
    public static boolean changeKnownVelocity;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("testkb")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        Knockback.flushKnockback = !Knockback.flushKnockback;
                        return msg(ctx.getSource(), Knockback.flushKnockback);
                    }))
                .then(Commands.literal("move_target")
                    .executes(ctx -> {
                        doMoveTarget = !doMoveTarget;
                        return msg(ctx.getSource(), doMoveTarget);
                    }))
                .then(Commands.literal("reset_after_move_target")
                    .executes(ctx -> {
                        resetMove = !resetMove;
                        return msg(ctx.getSource(), resetMove);
                    }))
                .then(Commands.literal("set_known_velocity_after_move_target")
                    .executes(ctx -> {
                        changeKnownVelocity = !changeKnownVelocity;
                        return msg(ctx.getSource(), changeKnownVelocity);
                    }))
        );
    }

    private static int msg(CommandSourceStack stack, boolean flag) {
        stack.sendSuccess("flushKnockback: " + Knockback.flushKnockback);
        stack.sendSuccess("option now set to: " + (flag ? "enabled" : "disabled"));
        return 1;
    }
}
