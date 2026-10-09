package com.predator.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.predator.common.gameplay.hunt.HuntDirector;
import com.predator.common.gameplay.hunt.HuntLedger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/**
 * {@code /avp_predator debug hunt <player> moon | phase1 | stop | click | glimpse | attack | return | warp | escape | status}
 * — drives a hunt by hand, so it can be tested without waiting for a full moon.
 * <ul>
 * <li>{@code moon} — the Hunter's Moon rises for them now (then sleep, or {@code /time set day}, for phase 1).</li>
 * <li>{@code phase1} — straight into phase 1, calling card not yet placed.</li>
 * <li>{@code stop} — ends their hunt.</li>
 * <li>{@code click} — one click, to hear it.</li>
 * <li>{@code glimpse} — one glimpse ahead of them, to see it.</li>
 * <li>{@code attack} — the Hunter arrives now (rolled on their ladder), whatever the time.</li>
 * <li>{@code return} — the phase-3 return now: same tier, +10%.</li>
 * <li>{@code warp} — the Hunter that is out warps in behind them now, skipping the 60-second wait.</li>
 * <li>{@code escape} — the phase-2 Hunter drops to 10% and flees now: smoke bomb, blood trail, the 5-minute wait.</li>
 * </ul>
 */
public final class PredatorHuntCommand {

    private PredatorHuntCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> createDebug() {
        return Commands.literal("hunt")
            .then(
                Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("moon").executes(context -> {
                        HuntDirector.forceMoon(EntityArgument.getPlayer(context, "player"));
                        return status(context);
                    }))
                    .then(Commands.literal("phase1").executes(context -> setStage(context, HuntLedger.Stage.PHASE_ONE)))
                    .then(Commands.literal("stop").executes(context -> {
                        HuntDirector.stopHunt(EntityArgument.getPlayer(context, "player"));
                        return status(context);
                    }))
                    .then(Commands.literal("attack").executes(context -> {
                        HuntDirector.forceAttack(EntityArgument.getPlayer(context, "player"), false);
                        return status(context);
                    }))
                    .then(Commands.literal("escape").executes(context -> {
                        var player = EntityArgument.getPlayer(context, "player");
                        var fled = HuntDirector.forceEscape(player);
                        context.getSource()
                            .sendSuccess(
                                () -> Component.literal(fled ? "The Hunter is wounded and flees." : "No phase-2 Hunter out to flee."),
                                true
                            );
                        return fled ? 1 : 0;
                    }))
                    .then(Commands.literal("warp").executes(context -> {
                        var player = EntityArgument.getPlayer(context, "player");
                        var warped = HuntDirector.forceWarp(player);
                        context.getSource()
                            .sendSuccess(
                                () -> Component.literal(warped ? "The Hunter warped in." : "No Hunter out, or no spot to warp to."),
                                true
                            );
                        return warped ? 1 : 0;
                    }))
                    .then(Commands.literal("return").executes(context -> {
                        HuntDirector.forceAttack(EntityArgument.getPlayer(context, "player"), true);
                        return status(context);
                    }))
                    .then(Commands.literal("click").executes(context -> {
                        var player = EntityArgument.getPlayer(context, "player");
                        HuntDirector.playClick(player, player.serverLevel());
                        return 1;
                    }))
                    .then(Commands.literal("glimpse").executes(context -> {
                        var player = EntityArgument.getPlayer(context, "player");
                        HuntDirector.showGlimpse(player, player.serverLevel());
                        return 1;
                    }))
                    .then(Commands.literal("status").executes(PredatorHuntCommand::status))
            );
    }

    private static int setStage(CommandContext<CommandSourceStack> context, HuntLedger.Stage stage) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        var ledger = HuntLedger.get(player.server);
        var entry = ledger.entry(player.getUUID());

        if (stage == HuntLedger.Stage.PHASE_ONE) {
            ledger.resetCallingCard(entry);
        }

        ledger.setStage(entry, stage);

        return status(context);
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        var entry = HuntLedger.get(player.server).entry(player.getUUID());
        var home = entry.favouriteSpot();

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    player.getName().getString() + " — hunt stage " + entry.stage() + ", calling card "
                        + (entry.callingCardPlaced() ? "placed" : "not placed") + ", home "
                        + (home == null ? "unknown" : home.getX() + " " + home.getY() + " " + home.getZ())
                        + ", hunts faced " + entry.huntsFaced() + ", ladder " + com.predator.common.gameplay.hunt.HunterLadder.describe(
                            entry
                        )
                ),
                true
            );

        return 1;
    }
}
