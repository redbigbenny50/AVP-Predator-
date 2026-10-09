package com.predator.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.predator.common.gameplay.hunt.SkinnedCorpses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /avp_predator debug corpse} — leaves the Hunter's calling card (iron sword + dog tags) next to the caller, so
 * the corpse, its pose rules and its contents can be checked without staging a hunt.
 */
public final class PredatorCorpseDebugCommand {

    private PredatorCorpseDebugCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("corpse").executes(PredatorCorpseDebugCommand::placeCorpse);
    }

    private static int placeCorpse(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var level = source.getLevel();
        var placed = SkinnedCorpses.placeCallingCard(level, BlockPos.containing(source.getPosition()));

        if (placed.isEmpty()) {
            source.sendFailure(Component.literal("Nowhere to put a corpse within 3 blocks."));

            return 0;
        }

        var pos = placed.get();
        source.sendSuccess(
            () -> Component.literal("Skinned corpse placed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "."),
            true
        );

        return 1;
    }
}
