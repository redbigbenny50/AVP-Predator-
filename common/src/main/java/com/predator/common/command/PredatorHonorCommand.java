package com.predator.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.predator.common.config.YautjaHonorConfig;
import com.predator.common.gameplay.hunt.HonorLedger;
import com.predator.common.gameplay.hunt.YautjaHonor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /avp_predator honor} — a player's own honor, until the faction work gives it a screen.
 * <p>
 * {@code /avp_predator debug honor <player> set <amount> | reset | rescan} — for testing the unlock without earning 150
 * honor the slow way. {@code rescan} re-runs the retroactive credit (it only ever pays for advancements not yet
 * credited).
 */
public final class PredatorHonorCommand {

    private PredatorHonorCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("honor").executes(PredatorHonorCommand::showOwn);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createDebug() {
        return Commands.literal("honor")
            .then(
                Commands.argument("player", EntityArgument.player())
                    .then(
                        Commands.literal("set")
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer())
                                    .executes(PredatorHonorCommand::set)
                            )
                    )
                    .then(Commands.literal("reset").executes(PredatorHonorCommand::reset))
                    .then(Commands.literal("rescan").executes(PredatorHonorCommand::rescan))
            );
    }

    private static int showOwn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var entry = HonorLedger.get(player.server).entry(player.getUUID());
        var threshold = YautjaHonorConfig.unlockThreshold();

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Honor: " + entry.honor() + " / " + threshold
                        + (entry.worthy() ? " — you are worthy of the hunt." : " — not yet worthy.")
                ),
                false
            );

        return entry.honor();
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        var amount = IntegerArgumentType.getInteger(context, "amount");
        var ledger = HonorLedger.get(player.server);
        var becameWorthy = ledger.setHonor(player.getUUID(), amount, YautjaHonorConfig.unlockThreshold());

        report(context, player, ledger, becameWorthy);

        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        HonorLedger.get(player.server).reset(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Cleared all honor for " + name(player) + "."), true);

        return 1;
    }

    private static int rescan(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        YautjaHonor.scanExisting(player);
        report(context, player, HonorLedger.get(player.server), false);

        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> context, ServerPlayer player, HonorLedger ledger, boolean becameWorthy) {
        var entry = ledger.entry(player.getUUID());

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    name(player) + " — honor " + entry.honor() + " / " + YautjaHonorConfig.unlockThreshold()
                        + (entry.worthy() ? ", worthy" : ", not worthy") + (becameWorthy ? " (just became worthy)" : "")
                ),
                true
            );
    }

    private static String name(ServerPlayer player) {
        return player.getName().getString();
    }
}
