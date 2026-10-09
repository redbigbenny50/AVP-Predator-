package com.predator.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.predator.common.debug.PredatorCasterTestMode;
import com.predator.common.debug.PredatorPathDebug;
import com.predator.common.debug.PredatorPathDiagnostics;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorGameRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /avp_predator debug path ...} and {@code /avp_predator debug caster ...}.
 * <p>
 * Per-player, not global: the particles are sent only to whoever turned it on, so one person can debug on a live server
 * without lighting the world up for everyone else.
 */
public final class PredatorPathDebugCommand {

    private PredatorPathDebugCommand() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@code /avp_predator debug caster on|off|status} — pins every yautja and makes the caster fire.
     * <p>
     * ⚠ Global and unsubtle by design. It is a test harness for a weapon that is otherwise very hard to stage, not a
     * gameplay toggle; leaving it on makes every yautja in the world a stationary turret.
     */
    /**
     * {@code /avp_predator debug cloak on|off|status} — a shortcut for the {@code predatorCloaking} gamerule.
     * <p>
     * ⚠ It SETS THE GAMERULE rather than keeping a flag of its own. One source of truth: the rule survives a restart,
     * syncs on its own, and shows up in {@code /gamerule} where someone looking for it would expect it. A parallel
     * static boolean would drift out of step with it the first time anyone used the vanilla command.
     */
    /**
     * {@code /avp_predator debug hunter on|off} — promotes the nearest yautja to a HUNTER.
     * <p>
     * ⚠ EXISTS BECAUSE NOTHING ELSE SETS THE FLAG YET. The tier and hunting systems are still design, so without a
     * command the 10% hunter resistance would be unreachable and therefore untested — shipped code nobody has ever seen
     * run. This is how it gets exercised until spawning knows about hunters.
     */
    /**
     * {@code /avp_predator debug tier <rank>} — sets the nearest yautja's class rank.
     * <p>
     * ⚠ Nothing spawns above BLOODED yet, so without this the other four tiers are unreachable and untestable — shipped
     * numbers nobody has seen in play.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> createTier() {
        var command = Commands.literal("tier");

        for (var tier : YautjaTier.values()) {
            command.then(Commands.literal(tier.serializedName()).executes(context -> setTier(context, tier)));
        }

        return command;
    }

    private static int setTier(CommandContext<CommandSourceStack> context, YautjaTier tier) {
        var source = context.getSource();
        var nearest = nearestYautja(source);

        if (nearest == null) {
            source.sendFailure(Component.literal("No yautja within 48 blocks."));

            return 0;
        }

        nearest.setTier(tier);

        source.sendSuccess(
            () -> Component.literal(
                "Yautja " + nearest.getId() + " is now " + tier.serializedName()
                    + " — " + (int) nearest.getMaxHealth() + " hp, armour " + (int) nearest.getArmorValue()
            ),
            true
        );

        return Command.SINGLE_SUCCESS;
    }

    /** ⚠ Shared by the tier and hunter commands so they can never disagree about which yautja they mean. */
    private static Yautja nearestYautja(CommandSourceStack source) {
        var origin = source.getPosition();

        return source.getLevel()
            .getEntitiesOfClass(Yautja.class, net.minecraft.world.phys.AABB.ofSize(origin, 48.0, 48.0, 48.0))
            .stream()
            .min((a, b) -> Double.compare(a.distanceToSqr(origin), b.distanceToSqr(origin)))
            .orElse(null);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createHunter() {
        return Commands.literal("hunter")
            .then(Commands.literal("on").executes(context -> setHunter(context, true)))
            .then(Commands.literal("off").executes(context -> setHunter(context, false)));
    }

    private static int setHunter(CommandContext<CommandSourceStack> context, boolean hunter) {
        var source = context.getSource();
        var nearest = nearestYautja(source);

        if (nearest == null) {
            source.sendFailure(Component.literal("No yautja within 48 blocks."));

            return 0;
        }

        nearest.setHunter(hunter);

        source.sendSuccess(
            () -> Component.literal(
                "Yautja " + nearest.getId() + (hunter
                    ? " is now a HUNTER — 10% extra resistance to everything."
                    : " is no longer a hunter.")
            ),
            true
        );

        return Command.SINGLE_SUCCESS;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createCloak() {
        return Commands.literal("cloak")
            .executes(PredatorPathDebugCommand::cloakStatus)
            .then(Commands.literal("on").executes(context -> setCloak(context, true)))
            .then(Commands.literal("off").executes(context -> setCloak(context, false)))
            .then(Commands.literal("status").executes(PredatorPathDebugCommand::cloakStatus));
    }

    private static int setCloak(CommandContext<CommandSourceStack> context, boolean enabled) {
        var server = context.getSource().getServer();

        server.getGameRules().getRule(PredatorGameRules.PREDATOR_CLOAKING).set(enabled, server);

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    enabled
                        ? "Yautja cloaking ON."
                        : "Yautja cloaking OFF. Already-cloaked yautja will drop their field within a second. "
                            + "Player cloaking devices are unaffected."
                ),
                true
            );

        return Command.SINGLE_SUCCESS;
    }

    private static int cloakStatus(CommandContext<CommandSourceStack> context) {
        var on = context.getSource().getServer().getGameRules().getBoolean(PredatorGameRules.PREDATOR_CLOAKING);

        context.getSource()
            .sendSuccess(
                () -> Component.literal("Yautja cloaking is " + (on ? "ON" : "OFF") + " (gamerule predatorCloaking)."),
                false
            );

        return Command.SINGLE_SUCCESS;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createCaster() {
        return Commands.literal("caster")
            .executes(PredatorPathDebugCommand::casterStatus)
            .then(Commands.literal("on").executes(context -> setCaster(context, true)))
            .then(Commands.literal("off").executes(context -> setCaster(context, false)))
            .then(Commands.literal("status").executes(PredatorPathDebugCommand::casterStatus));
    }

    private static int setCaster(CommandContext<CommandSourceStack> context, boolean enabled) {
        PredatorCasterTestMode.setEnabled(enabled);

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    enabled
                        ? "Caster test mode ON. Every yautja stands still and fires its plasma caster at you, ignoring "
                            + "the honor rule and the minimum range. Charge, cloak window and bolt are unchanged."
                        : "Caster test mode OFF. Yautja move and pick targets normally again."
                ),
                true
            );

        return Command.SINGLE_SUCCESS;
    }

    private static int casterStatus(CommandContext<CommandSourceStack> context) {
        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Caster test mode is " + (PredatorCasterTestMode.isEnabled() ? "ON" : "OFF") + "."
                ),
                false
            );

        return Command.SINGLE_SUCCESS;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("path")
            .executes(PredatorPathDebugCommand::status)
            .then(Commands.literal("on").executes(context -> set(context, true)))
            .then(Commands.literal("off").executes(context -> set(context, false)))
            .then(Commands.literal("status").executes(PredatorPathDebugCommand::status))
            .then(
                Commands.literal("diag")
                    .executes(PredatorPathDebugCommand::diagStatus)
                    .then(Commands.literal("on").executes(context -> setDiag(context, true)))
                    .then(Commands.literal("off").executes(context -> setDiag(context, false)))
            );
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean enabled) {
        var player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendFailure(Component.literal("Path debug is per-player; run it as a player."));
            return 0;
        }

        PredatorPathDebug.toggle(player, enabled);

        // ⚠ TURNS ON THE LOG TOO. Two separate toggles meant "I switched on path debug" reliably produced a
        // trail and an empty log, which is the opposite of useful when the whole point is to send the log back.
        // "path diag" still exists for the log ALONE, on a headless server where particles help nobody.
        PredatorPathDiagnostics.setEnabled(enabled);

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    enabled
                        ? "Path debug ON - particle trail AND [pathdiag] lines in the log. "
                            + "Orange = ground, blue = crawl, bubbles = water, white = next node, green = destination."
                        : "Path debug OFF - trail and log both."
                ),
                false
            );

        return Command.SINGLE_SUCCESS;
    }

    /**
     * ⚠ GLOBAL, not per-player, unlike the particle trail — it writes to the server log, which has one reader.
     */
    private static int setDiag(CommandContext<CommandSourceStack> context, boolean enabled) {
        PredatorPathDiagnostics.setEnabled(enabled);

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    enabled
                        ? "Path diagnostics ON. Writing [pathdiag] lines to the log: target changes, GOAP state, "
                            + "navigator, jumps and climbs. Reproduce the problem, then send latest.log."
                        : "Path diagnostics OFF."
                ),
                true
            );

        return Command.SINGLE_SUCCESS;
    }

    private static int diagStatus(CommandContext<CommandSourceStack> context) {
        context.getSource()
            .sendSuccess(
                () -> Component.literal("Path diagnostics are " + (PredatorPathDiagnostics.isEnabled() ? "ON" : "OFF") + "."),
                false
            );

        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();
        var watching = player != null && PredatorPathDebug.isWatching(player);

        context.getSource()
            .sendSuccess(
                () -> Component.literal(
                    "Path debug is " + (watching ? "ON" : "OFF") + " for you ("
                        + PredatorPathDebug.watcherCount() + " watching in total)."
                ),
                false
            );

        return Command.SINGLE_SUCCESS;
    }
}
