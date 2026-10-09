package com.predator.common.gameplay.entity.living.yautja.util;

import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class YautjaPredicates {

    public static boolean isThreateningTarget(@NotNull Yautja yautja, @NotNull LivingEntity potentialTarget) {
        return isValidTarget(yautja, potentialTarget);
    }

    public static boolean isValidTarget(@NotNull Yautja yautja, @NotNull LivingEntity potentialTarget) {
        // Oct 5 - the alien truce comes first: while it holds no marine is a target, armed or not, Hunter or not.
        if (YautjaAlienTruce.spares(yautja, potentialTarget)) {
            return false;
        }

        // ⚠⚠ A HUNTER'S OWN PREY, AND THE DEFENCES IT WAS SENT AT, ARE ALWAYS FAIR GAME. The honor code below only lets
        // a
        // yautja pick a fight with an ARMED player; a Hunter sent for someone fights them armed or not ([stated] an
        // unarmed player is fought "bare handed"), and the director points it at the sentry guns and guards first.
        // Without this the combat graph would drop the very target the hunt gave it.
        if (
            yautja.getHuntedPlayer() != null && !BLibEntityPredicates.isInvulnerable(potentialTarget)
                && (yautja.getHuntedPlayer().equals(potentialTarget.getUUID()) || potentialTarget == yautja.getTarget())
                && !(potentialTarget instanceof Yautja)
        ) {
            return true;
        }

        return switch (potentialTarget) {
            case Yautja yautja1 -> false;
            case Creeper creeper -> false;
            case Player player -> !BLibEntityPredicates.isInvulnerable(player)
                && (player.getMainHandItem().is(PredatorItemTags.HOSTILE_WEAPONS)
                    || com.predator.common.gameplay.entity.living.yautja.YautjaProvocation.isProvokedBy(yautja, player)
                    || (yautja.getLastAttacker() != null && yautja.getLastAttacker().is(player)));
            default -> {
                // ⚠⚠ Aug 28, his doctrine IN FULL — the honor code, not a blanket: "xenos are supposed to be a
                // worthy prey adults only not the huggers unless yautja is targeted, not the eggs, not the
                // chestbursters and not the adolescents. the only exclusion to this rule is the predalien variants."
                // Matching is by entity-id PATH SUBSTRING, dependency-free (the modCompileOnly lesson) and robust to
                // strain prefixes: irradiated_chestburster is still a chestburster, aberrant_predalien is still
                // predalien blood. Order matters — the predalien override comes FIRST, because a predalien
                // chestburster is hated for what it is, not spared for how young it is.
                var typeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(potentialTarget.getType());

                if ("avp_alien".equals(typeKey.getNamespace())) {
                    var path = typeKey.getPath();

                    if (path.contains("predalien")) {
                        yield true;
                    }

                    if (path.contains("ovomorph") || path.contains("chestburster") || path.contains("adolescent")) {
                        yield false;
                    }

                    if (path.contains("facehugger")) {
                        // Beneath notice — until it locks onto the yautja or lays a hand on it. Self-defense only.
                        yield (potentialTarget instanceof Mob hugger && hugger.getTarget() != null && hugger.getTarget().is(yautja))
                            || (yautja.getLastAttacker() != null && yautja.getLastAttacker().is(potentialTarget));
                    }

                    // Every adult caste — worthy prey on sight.
                    yield true;
                }

                if (potentialTarget.getType().is(com.predator.common.registry.tag.PredatorEntityTypeTags.HATED_ENEMIES)) {
                    yield true;
                }

                if (potentialTarget instanceof Mob || potentialTarget instanceof Monster) {
                    yield potentialTarget.getMainHandItem().is(PredatorItemTags.HOSTILE_WEAPONS)
                        || (yautja.getLastAttacker() != null && yautja.getLastAttacker().is(potentialTarget));
                }

                yield yautja.getLastAttacker() != null && yautja.getLastAttacker().is(potentialTarget);
            }
        };
    }

    private YautjaPredicates() {
        throw new UnsupportedOperationException();
    }
}
