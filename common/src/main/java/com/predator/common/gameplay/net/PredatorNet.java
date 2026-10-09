package com.predator.common.gameplay.net;

import com.predator.Predator;
import com.predator.PredatorResources;
import com.predator.common.network.packet.S2CNetStatePayload;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which mobs are currently caught in a net.
 * <h2>The net's whole job is to make a mob immobile</h2> His ruling: "the nets job is to make the mob immobile while
 * the chain attaches to the mob itself as normal." So this holds the netted set and switches the mob's AI off, and
 * deliberately does nothing about dragging.
 * <h2>⚠⚠ IT CANNOT INTERFERE WITH avp_alien'S CAPTURE CHAIN, AND IT DOES NOT</h2> Verified against the alien jar rather
 * than assumed: {@code CaptureHoldManager} moves a held mob with {@code setDeltaMovement} and
 * {@code move(MoverType.SELF, …)} and touches nothing else — no navigation, no rotation, no pose. So
 * {@code setNoAi(true)} stops the mob DECIDING to move while the chain can still PUSH it. The two systems never meet,
 * which is why neither needs to know the other exists.
 * <h2>Server map, not synched entity data</h2> ⚠ A net catches ANY mob, including ones from mods that know nothing
 * about this one, so there is no entity class to add a {@code SynchedEntityData} accessor to. A server map plus an
 * explicit payload is the shape avp_alien uses for its capture hold, and it works on everything.
 */
public final class PredatorNet {

    /** Ticks a netted mob stays caught before it works itself free. */
    public static final int ESCAPE_TICKS = 600;

    /** Entity UUID to the tick its net expires. */
    /** Ticks between struggles. */
    public static int STRUGGLE_INTERVAL_TICKS = 45;

    /** Sideways lurch of a struggle, blocks per tick. */
    public static double STRUGGLE_LURCH = 0.16D;

    /** Upward hop of a struggle when it has footing. */
    public static double STRUGGLE_HOP = 0.22D;

    /** Degrees of body twist per struggle. */
    public static float STRUGGLE_TWIST = 26.0F;

    /** How much of a struggle a CAPTURED creature manages — it has stopped fighting, it only shifts. */
    public static float CAPTURED_STRUGGLE_SCALE = 0.25F;

    /** Ticks a player is immune to the net after mashing free. */
    public static int ESCAPE_GRACE_TICKS = 20 * 90;

    /** Game time until which an entity cannot be netted again. Players only in practice. */
    private static final Map<UUID, Long> NET_IMMUNE_UNTIL = new HashMap<>();

    private static final Map<UUID, Integer> NETTED = new HashMap<>();

    /**
     * ⚠⚠ REMEMBERS WHETHER THE MOB WAS ALREADY noAi BEFORE THE NET. Blindly calling {@code setNoAi(false)} on release
     * would un-freeze an armour-stand-style build or a deliberately disabled spawner mob that the player never netted —
     * a permanent world change caused by a net thrown at something else nearby.
     */
    private static final Set<UUID> WAS_ALREADY_NO_AI = new HashSet<>();

    private static final ResourceLocation NET_SPEED_MODIFIER = PredatorResources.location("netted");

    private PredatorNet() {
        throw new UnsupportedOperationException();
    }

    /** {@return whether this entity is currently held in a net} */
    public static boolean isNetted(@Nullable Entity entity) {
        if (entity instanceof NettedMob netted) {
            return netted.avp_predator$isNetted();
        }

        // Players only. They are not Mobs, and a player's net is a short mash-out rather than a capture —
        // [stated] "players shouldnt be perma caught so the mash escape still counts."
        return entity != null && NETTED.containsKey(entity.getUUID());
    }

    /** {@return whether the net has closed for good on this creature} Players are never captured. */
    public static boolean isCaptured(@Nullable Entity entity) {
        return entity instanceof NettedMob netted && netted.avp_predator$isCaptured();
    }

    /**
     * {@return whether a net can catch this at all}
     * <p>
     * ⚠ Driven by the {@code avp_predator:net_immune} entity tag, so modpacks and sibling mods add their own bosses
     * without touching this code — avp_alien contributes its harbingers, queens and empresses from its own side, where
     * the strain tags live and stay correct as castes are added.
     */
    /** {@return whether this player is still inside the grace period from their last escape} */
    public static boolean hasEscapeGrace(Entity entity) {
        var until = NET_IMMUNE_UNTIL.get(entity.getUUID());

        if (until == null) {
            return false;
        }

        if (entity.level().getGameTime() >= until) {
            NET_IMMUNE_UNTIL.remove(entity.getUUID());

            return false;
        }

        return true;
    }

    public static boolean canBeNetted(Entity entity) {
        // ⚠⚠ LivingEntity, NOT Mob — PLAYERS ARE NOT MOBS. The first version tested instanceof Mob, which silently
        // made players uncatchable: the net flew through them and dropped on the floor with no message and nothing to
        // debug. A hunter's capture net that cannot catch the thing it hunts is the wrong shape entirely.
        return entity instanceof LivingEntity living
            && living.isAlive()
            && !living.getType().is(PredatorEntityTypeTags.NET_IMMUNE)
            && !isNetted(living)
            && !hasEscapeGrace(living);
    }

    public static void capture(LivingEntity living) {
        if (living.level().isClientSide || !canBeNetted(living)) {
            return;
        }

        // ⚠⚠ TWO DIFFERENT WAYS TO HOLD SOMETHING STILL, because a player has no AI to switch off.
        // A mob loses its brain; a player keeps theirs and loses their legs. Same outcome, and it has to be the
        // attribute for a player because setNoAi does nothing to one.
        if (living instanceof Mob mob) {
            if (mob.isNoAi()) {
                WAS_ALREADY_NO_AI.add(mob.getUUID());
            }

            // ⚠⚠ NO LONGER setNoAi. See MixinMob_NettedHold: noAi makes isEffectiveAi() false and vanilla then zeroes
            // the mob's velocity every tick, so it could not be pushed and could not be made to struggle. The hold is
            // now serverAiStep cancellation (sensing/targeting/attacks off) plus the input clamp below, exactly as
            // avp_alien holds an incapacitated royal.
            silenceMovement(mob);
            mob.getNavigation().stop();

            var netted = (NettedMob) mob;

            netted.avp_predator$setNetted(true);
            netted.avp_predator$setFailedRolls(0);
            netted.avp_predator$setNextRollTime(living.level().getGameTime() + NetEscape.ROLL_INTERVAL_TICKS);

            // [stated] "if its small you capture it no struggle, if its a passive creature ... it also is captured."
            netted.avp_predator$setCaptured(NetEscape.capturedOnContact(living));

            // ⚠ A captured creature is someone's livestock now: it must never wander off or despawn.
            mob.setPersistenceRequired();
        } else {
            holdStill(living);

            // Players stay in the timed map; everything else carries its own state.
            NETTED.put(living.getUUID(), living.tickCount + ESCAPE_TICKS);
        }

        // ⚠ Horizontal only. Zeroing Y would freeze a falling mob in mid-air; the net stops it walking, not falling.
        living.setDeltaMovement(0.0, living.getDeltaMovement().y, 0.0);
        broadcast(living, true);
    }

    /**
     * Pins a player in place with a movement-speed modifier.
     * <p>
     * ⚠ ADD_MULTIPLIED_TOTAL at -1.0, the same shape the roar stun uses: it scales speed to exactly zero whatever the
     * player's base speed and potions are. A flat subtraction would have to guess a magnitude and would leave a
     * speed-potioned player walking out of the net.
     */
    /** Stops a mob DECIDING to move, without taking it out of vanilla's movement pipeline. */
    private static void silenceMovement(Mob mob) {
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.xxa = 0.0F;
        mob.yya = 0.0F;
        mob.zza = 0.0F;
        mob.setSpeed(0.0F);
        setGoapEnabled(mob, false);
    }

    /**
     * BLib runs its GOAP agent from {@code LivingEntity.tick}, not from {@code serverAiStep}, so cancelling the latter
     * does not stop a xenomorph planning. Disabling the agent is what actually holds one still.
     */
    private static void setGoapEnabled(Mob mob, boolean enabled) {
        if (mob instanceof com.blib.api.common.goap.v1.GOAPUser<?> user) {
            var agent = user.blib$getGOAPAgentOrNull();

            if (agent != null) {
                agent.setEnabled(enabled);
            }
        }
    }

    /**
     * The struggle: a netted mob lurches every few seconds as it fights the mesh.
     * <p>
     * [stated] "is it possible to make netted entites shake a little bit every few seconds like its trying to
     * struggle?" ⚠ This only works because the hold no longer uses noAi — under noAi vanilla wiped the velocity on the
     * same tick it was written.
     */
    private static void struggle(LivingEntity living, float scale) {
        var random = living.getRandom();
        var angle = random.nextDouble() * Math.PI * 2.0;

        living.setDeltaMovement(
            Math.cos(angle) * STRUGGLE_LURCH * scale,
            living.onGround() ? STRUGGLE_HOP * scale : living.getDeltaMovement().y,
            Math.sin(angle) * STRUGGLE_LURCH
        );
        living.hurtMarked = true;
        living.setYRot(living.getYRot() + (random.nextFloat() - 0.5F) * STRUGGLE_TWIST * scale);

        if (living.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.CRIT,
                living.getX(),
                living.getY() + living.getBbHeight() * 0.5,
                living.getZ(),
                3,
                living.getBbWidth() * 0.4,
                living.getBbHeight() * 0.3,
                living.getBbWidth() * 0.4,
                0.0
            );
        }
    }

    /**
     * One struggle, called from the mob's own tick every {@link NetEscape#ROLL_INTERVAL_TICKS}.
     * <p>
     * A captured creature only twitches — [stated] "still shakes but yes much weaker". One still fighting rolls to
     * escape; a failed roll costs it blood, and three failures in a row close the net for good.
     */
    public static void onStruggleTick(Mob mob) {
        var netted = (NettedMob) mob;

        if (netted.avp_predator$isCaptured()) {
            struggle(mob, CAPTURED_STRUGGLE_SCALE);
            return;
        }

        struggle(mob, 1.0F);

        if (mob.getRandom().nextFloat() < NetEscape.escapeChance(mob)) {
            release(mob);
            mob.level()
                .playSound(
                    null,
                    mob.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LEASH_KNOT_BREAK,
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    1.0F,
                    1.0F
                );

            return;
        }

        // [stated] "if it fails a roll it should take some damage like 10% or two hearts whichever is less"
        mob.hurt(mob.damageSources().generic(), NetEscape.struggleDamage(mob));

        var failed = netted.avp_predator$getFailedRolls() + 1;

        netted.avp_predator$setFailedRolls(failed);

        if (failed >= NetEscape.ROLLS_BEFORE_CAPTURE) {
            netted.avp_predator$setCaptured(true);
            mob.level()
                .playSound(
                    null,
                    mob.blockPosition(),
                    net.minecraft.sounds.SoundEvents.LEASH_KNOT_PLACE,
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    1.0F,
                    0.6F
                );
        }
    }

    private static void holdStill(LivingEntity living) {
        var attribute = living.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute != null && attribute.getModifier(NET_SPEED_MODIFIER) == null) {
            attribute.addTransientModifier(
                new AttributeModifier(NET_SPEED_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            );
        }
    }

    /** ⚠ Removing it is unconditional — a modifier left behind would leave the player permanently unable to walk. */
    private static void letGo(LivingEntity living) {
        var attribute = living.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute != null) {
            attribute.removeModifier(NET_SPEED_MODIFIER);
        }
    }

    public static void release(LivingEntity living) {
        if (living instanceof NettedMob netted && netted.avp_predator$isNetted()) {
            netted.avp_predator$setNetted(false);
            netted.avp_predator$setCaptured(false);
            netted.avp_predator$setFailedRolls(0);
        } else if (NETTED.remove(living.getUUID()) == null) {
            return;
        } else {
            // ⚠⚠ A PLAYER WHO MASHED FREE GETS A GRACE PERIOD. Without it the hunter simply nets them again the
            // instant they are out, and mashing free means nothing — [stated] "once a player escapes a net it cant
            // be caught again for 90 seconds otherwise it would just keep happening." Mobs need no equivalent:
            // escaping a net is a rare roll, and a recaptured one keeps rolling anyway.
            NET_IMMUNE_UNTIL.put(living.getUUID(), living.level().getGameTime() + ESCAPE_GRACE_TICKS);
        }

        if (living instanceof Mob mob) {
            // Only hand the AI back if the net was the thing that took it away. ⚠ The hold itself is now
            // serverAiStep cancellation, which simply stops applying — this restores the GOAP agent and clears any
            // noAi flag left by a net from before that change.
            setGoapEnabled(mob, true);

            if (!WAS_ALREADY_NO_AI.remove(mob.getUUID())) {
                mob.setNoAi(false);
            }
        } else {
            letGo(living);
        }

        broadcast(living, false);
    }

    /**
     * Ages every net in this level and frees what has run out.
     * <p>
     * ⚠ Iterates a COPY of the key set — {@link #release} mutates the map, so a live iterator would throw the instant
     * the first net expired: a crash appearing thirty seconds after the feature looked like it worked.
     */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // ⚠⚠ BEFORE THE EMPTY CHECK, NOT AFTER IT. NetStruggle has to see a player STOP being netted so it can
        // tear its bar down — and the tick where that happens is exactly the tick the set may become empty. Put
        // this behind the early-out and a player whose net expires keeps an orphaned bar on screen forever,
        // with nothing left running to remove it.
        for (var player : serverLevel.players()) {
            NetStruggle.tickPlayer(player);
        }

        if (NETTED.isEmpty()) {
            return;
        }

        for (var id : Set.copyOf(NETTED.keySet())) {
            var entity = serverLevel.getEntity(id);

            // ⚠ Absent from THIS level only means it is in another one. Forgetting it here would free a netted mob
            // because someone ticked a different dimension.
            if (entity == null) {
                continue;
            }

            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                // ⚠⚠ GIVE THE AI BACK TO THE CORPSE. A dead mob is still an entity for its death animation, and some
                // spawn their successors from remove(): a slime copies ITS OWN noAi flag onto every small slime it
                // splits into. Forgetting a netted slime here left it noAi=true, so the babies were born frozen as
                // if netted — with no net entry to ever free them.
                if (entity instanceof Mob mob && !WAS_ALREADY_NO_AI.contains(id)) {
                    mob.setNoAi(false); // legacy: nets from before this change persisted the flag
                    setGoapEnabled(mob, true);
                }

                NETTED.remove(id);
                WAS_ALREADY_NO_AI.remove(id);

                continue;
            }

            // ⚠ MOBS ARE NOT IN THIS MAP ANY MORE — they carry their own state and tick themselves (see
            // MixinMob_NettedHold), which is what makes a pen full of captured creatures cost nothing. Only players
            // are timed out here, because only a player mashes free.
            if (living.tickCount >= NETTED.get(id)) {
                release(living);
            }
        }
    }

    /**
     * Hands a newly-arrived observer the current state.
     * <p>
     * ⚠ Without this, walking into view of an already-netted mob draws it with no net until the state next changes —
     * which for a mob nobody frees is never. The cloak needed exactly this hook for exactly this reason.
     */
    public static void onStartTracking(Entity tracked, Player observer) {
        if (observer instanceof ServerPlayer player && tracked instanceof LivingEntity living && isNetted(living)) {
            Predator.MOD.networking().sendToClient(player, new S2CNetStatePayload(living.getId(), true));
        }
    }

    /** ⚠ Sent to everyone who can SEE the mob, not just the thrower — the overlay is on the world, not on a screen. */
    private static void broadcast(LivingEntity living, boolean netted) {
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }

        for (var player : level.players()) {
            Predator.MOD.networking().sendToClient(player, new S2CNetStatePayload(living.getId(), netted));
        }
    }
}
