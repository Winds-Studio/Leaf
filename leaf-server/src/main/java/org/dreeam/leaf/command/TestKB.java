package org.dreeam.leaf.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class TestKB {
    public static boolean sendToTarget;
    public static boolean sendToSelf;
    public static boolean sendToBoth = true;
    public static boolean sendTargetVelocityToSelf = true;
    public static boolean sendTargetVelocityNoKnockbackToSelf;
    public static boolean doMoveTarget;
    public static boolean flush = true;
    public static boolean flushMulti;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("testkb")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("send_full_pos_to_target")
                    .executes(ctx -> {
                        sendToTarget = !sendToTarget;
                        sendToSelf = false;
                        sendToBoth = false;
                        return msg(ctx.getSource(), sendToTarget);
                    }))
                .then(Commands.literal("send_full_pos_to_self")
                    .executes(ctx -> {
                        sendToSelf = !sendToSelf;
                        sendToTarget = false;
                        sendToBoth = false;
                        return msg(ctx.getSource(), sendToSelf);
                    }))
                .then(Commands.literal("send_full_pos_to_both")
                    .executes(ctx -> {
                        sendToBoth = !sendToBoth;
                        sendToSelf = false;
                        sendToTarget = false;
                        return msg(ctx.getSource(), sendToBoth);
                    }))
                .then(Commands.literal("move_target_before_send_full_pos")
                    .executes(ctx -> {
                        doMoveTarget = !doMoveTarget;
                        return msg(ctx.getSource(), doMoveTarget);
                    }))
                .then(Commands.literal("send_target_velocity_to_self")
                    .executes(ctx -> {
                        sendTargetVelocityToSelf = !sendTargetVelocityToSelf;
                        return msg(ctx.getSource(), sendTargetVelocityToSelf);
                    }))
                .then(Commands.literal("send_target_no_knockback_velocity_to_self")
                    .executes(ctx -> {
                        sendTargetVelocityNoKnockbackToSelf = !sendTargetVelocityNoKnockbackToSelf;
                        return msg(ctx.getSource(), sendTargetVelocityNoKnockbackToSelf);
                    }))
                .then(Commands.literal("flush")
                    .executes(ctx -> {
                        flush = !flush;
                        flushMulti = false;
                        return msg(ctx.getSource(), flush);
                    }))
                .then(Commands.literal("flush_multi")
                    .executes(ctx -> {
                        flushMulti = !flushMulti;
                        flush = false;
                        return msg(ctx.getSource(), flushMulti);
                    })));
    }

    private static int msg(CommandSourceStack stack, boolean flag) {
        stack.sendSuccess("now set to: " + (flag ? "enabled" : "disabled"));
        return 1;
    }
}
