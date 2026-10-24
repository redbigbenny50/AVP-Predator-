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

                    // Oct 17 - [stated] "the predator will attack chained and captured queens this would go against its
                    // honor code. if a queen is bound its not a worthy hunt." He set bound as ALL FOUR chains, and a
                    // bound queen that strikes the yautja may be fought, like a facehugger. "Queen" means empress and
                    // harbinger too - see isFullyBound.
                    if (isFullyBound(potentialTarget)) {
                        yield yautja.getLastAttacker() != null && yautja.getLastAttacker().is(potentialTarget);
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

    /** Per creature type: [getBindManager, isFullyBound], or an empty array for a type that cannot be chained. */
    private static final java.util.Map<Class<?>, java.lang.reflect.Method[]> BIND_LOOKUP = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.lang.reflect.Method[] NOT_BINDABLE = new java.lang.reflect.Method[0];

    /**
     * {@return whether this avp_alien creature is held by all four capture chains}
     * <p>
     * Calls avp_alien's own {@code getBindManager().isFullyBound()} - the exact check avp_alien uses to call a queen
     * captured (AlienPredicates, OvipositorManager) - so the two mods cannot disagree. Verified in the 0.2.8 MDK.
     * <p>
     * Oct 17 - [stated] "when i say queen i mean empress too and harbinger". In avp_alien 0.2.8 only the QUEEN can be
     * chained (CaptureChainItem binds a Queen and nothing else), so today only she is ever "bound". This asks ANY
     * creature that has a bind manager rather than the Queen class, so an empress or harbinger made chainable later is
     * respected with no change here.
     * <p>
     * ⚠ Read by name, looked up once per creature type - predator needs no avp_alien to build or run. ⚠ Any failure
     * reads as "not bound": a missing or renamed method must never make a yautja spare everything.
     */
    private static boolean isFullyBound(LivingEntity entity) {
        var methods = BIND_LOOKUP.computeIfAbsent(entity.getClass(), type -> {
            try {
                var getBindManager = type.getMethod("getBindManager");
                return new java.lang.reflect.Method[] { getBindManager, getBindManager.getReturnType().getMethod("isFullyBound") };
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return NOT_BINDABLE;
            }
        });

        if (methods.length == 0) {
            return false;
        }

        try {
            var manager = methods[0].invoke(entity);
            return manager != null && Boolean.TRUE.equals(methods[1].invoke(manager));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private YautjaPredicates() {
        throw new UnsupportedOperationException();
    }
}
