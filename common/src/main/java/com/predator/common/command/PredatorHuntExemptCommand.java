package com.predator.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.predator.common.gameplay.hunt.HuntDirector;
import com.predator.common.gameplay.hunt.HuntLedger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * {@code /avp_predator hunt exempt add <players> | remove <players> | list} - operators only.
 * <p>
 * Oct 16 - [stated] "a command ops can use to exempt a player from hunts". An exempted player never gets a Hunter's
 * Moon, and exempting one mid-hunt ends it at once. Honor still accrues, so removing the exemption puts them back in
 * line for the next full moon.
 * <p>
 * ⚠ Players are named like {@code /op} and {@code /whitelist} name them (a game profile, not an entity), so an offline
 * player can be exempted before they next log in.
 */
public final class PredatorHuntExemptCommand {

    private PredatorHuntExemptCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("exempt")
            .then(
                Commands.literal("add")
                    .then(
                        Commands.argument("players", GameProfileArgument.gameProfile())
                            .executes(context -> set(context, true))
                    )
            )
            .then(
                Commands.literal("remove")
                    .then(
                        Commands.argument("players", GameProfileArgument.gameProfile())
                            .executes(context -> set(context, false))
                    )
            )
            .then(Commands.literal("list").executes(PredatorHuntExemptCommand::list));
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean exempt) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "players");
        var source = context.getSource();
        var changed = 0;

        for (var profile : profiles) {
            if (HuntDirector.setExempt(source.getServer(), profile.getId(), exempt)) {
                changed++;
                var name = profile.getName();
                source.sendSuccess(
                    () -> Component.literal(exempt ? name + " is now exempt from the hunts." : name + " can be hunted again."),
                    true
                );
            } else {
                var name = profile.getName();
                source.sendFailure(
                    Component.literal(exempt ? name + " is already exempt." : name + " was not exempt.")
                );
            }
        }

        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var server = source.getServer();
        var exempt = HuntLedger.get(server).exemptPlayers();

        if (exempt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nobody is exempt from the hunts."), false);
            return 0;
        }

        var names = new StringBuilder();

        for (UUID id : exempt) {
            if (!names.isEmpty()) {
                names.append(", ");
            }

            // ⚠ The profile cache can be absent (or not know a long-gone player); fall back to the raw id.
            var cache = server.getProfileCache();
            names.append(cache == null ? id.toString() : cache.get(id).map(GameProfile::getName).orElse(id.toString()));
        }

        var text = "Exempt from the hunts (" + exempt.size() + "): " + names;
        source.sendSuccess(() -> Component.literal(text), false);

        return exempt.size();
    }
}
