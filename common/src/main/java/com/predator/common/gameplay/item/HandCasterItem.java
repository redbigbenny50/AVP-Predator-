package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hand caster — the plasma caster, handheld, for players.
 * <h2>His rulings</h2>
 * <ul>
 * <li>[stated] "its only a weapon the player has but it works like the plasma caster same bolt same charge."</li>
 * <li>[stated] "the player holds down the left click button and releases to fire the plasma bolt. it fires straight
 * where the players reticle is pointing."</li>
 * <li>[stated] "it has the same charge effect the plasma caster has but it keeps sparkling with effects while you hold
 * down the button" — the caster's own charge particles, at the barrel, every tick held, and still after full
 * charge.</li>
 * <li>[stated] "it uses plasma cores as its ammunition for now give it 64 shots ... it needs to run out of ammo to
 * reload." — 64 shots; only when EMPTY does a press spend one plasma core to refill.</li>
 * <li>[stated] "when it drops it has a full charge" — a stack with no shot count reads as full (see shots()).</li>
 * </ul>
 * <h2>The charge</h2> The plasma caster's 40 ticks. Released early it still fires, weaker — the plasma bow's rule
 * [stated] earlier ("half at min draw, full at max"): half the bolt's damage at the minimum hold, full at a full
 * charge. Released before {@link #MIN_CHARGE_TICKS} it does not fire at all and spends nothing.
 * <p>
 * ⚠ Everything here runs on the SERVER. The client only reports "pressed" and "released" (C2SHandCasterPayload); the
 * server holds the charge, draws the sparks and fires, so every player sees the same thing.
 */
public class HandCasterItem extends Item {

    /** [stated] 64 shots per plasma core, "for now". */
    public static final int MAX_SHOTS = 64;

    /** The plasma caster's own charge. */
    public static final int CHARGE_TICKS = PlasmaCaster.CHARGE_TICKS;

    /** Released sooner than this, it fizzles — no shot, nothing spent. */
    public static final int MIN_CHARGE_TICKS = 8;

    /** Shortest gap between shots. */
    public static final int FIRE_COOLDOWN_TICKS = 10;

    /** How long a reload takes. */
    public static final int RELOAD_TICKS = 30;

    /** How far the reticle is followed for aiming. */
    private static final double AIM_RANGE = 64.0D;

    /** Players charging right now: player -> game time the charge began. Server only. */
    private static final Map<UUID, Long> CHARGING = new HashMap<>();

    public HandCasterItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    /** {@return shots left} ⚠ No component means FULL — so a fresh or dropped hand caster is fully loaded. */
    public static int shots(ItemStack stack) {
        return stack.getOrDefault(PredatorDataComponents.HAND_CASTER_SHOTS.get(), MAX_SHOTS);
    }

    // ---------------------------------------------------------------- what the client asks for

    /** The button went down. Empty: try to reload instead. Otherwise begin charging. */
    public static void startCharge(Player player) {
        var stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof HandCasterItem) || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }

        var level = player.level();

        if (shots(stack) <= 0) {
            reload(player, stack);

            return;
        }

        CHARGING.put(player.getUUID(), level.getGameTime());
        broadcastCharge(player, true);
        // [stated] "the charge then the hold loop" — two separate sounds, like the plasma bow's draw and hold. This is
        // the charge (2 s, the length of a full charge); the hold loop starts on each client once it has finished
        // (CasterChargeLoopSounds).
        level.playSound(null, player.blockPosition(), PredatorSoundEvents.HAND_CASTER_CHARGE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    /**
     * The furthest a reported barrel tip may sit from the shooter's eyes. The real one is well under a block and a half
     * in any view; anything further is not a barrel, and the server's own muzzle is used.
     */
    private static final double MAX_TIP_DISTANCE = 2.5D;

    /**
     * The button came up: fire, as hard as it was charged — from the barrel tip the shooter's client saw, when it sent
     * one and it is within {@link #MAX_TIP_DISTANCE} of their eyes (so a modified client cannot fire from elsewhere).
     */
    public static void release(Player player, @org.jetbrains.annotations.Nullable Vec3 reportedTip) {
        var tip = reportedTip != null && reportedTip.distanceToSqr(player.getEyePosition()) <= MAX_TIP_DISTANCE * MAX_TIP_DISTANCE
            ? reportedTip
            : null;

        releaseAt(player, tip);
    }

    private static void releaseAt(Player player, @org.jetbrains.annotations.Nullable Vec3 tip) {
        var start = CHARGING.remove(player.getUUID());

        broadcastCharge(player, false);
        var stack = player.getMainHandItem();

        if (start == null || !(stack.getItem() instanceof HandCasterItem) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        var held = level.getGameTime() - start;

        if (held < MIN_CHARGE_TICKS) {
            return;
        }

        fire(level, player, stack, Math.min(1.0F, (float) held / CHARGE_TICKS), tip);
    }

    /** Let go of the item, switched away, opened a screen: the charge is dropped, nothing fired or spent. */
    public static void cancel(Player player) {
        CHARGING.remove(player.getUUID());
        broadcastCharge(player, false);
    }

    /**
     * Tells everyone who can see this player that the caster is winding up, or has stopped.
     * <p>
     * ⭐ Drives the THIRD-PERSON arm pose. First person is untouched - the gauntlet stays visible there whether or not
     * it is charging, so you can see you are armed.
     * </p>
     * <p>
     * ⚠ Sent from the three places that write {@code CHARGING} and nowhere else, so the wire state cannot drift from
     * the server's own. It is two edges per shot, not a per-tick stream.
     * </p>
     */
    private static void broadcastCharge(Player player, boolean charging) {
        if (player.level().isClientSide) {
            return;
        }

        com.predator.Predator.MOD.networking()
            .sendToAllClientsTrackingEntity(
                player,
                new com.predator.common.network.packet.S2CCasterChargePayload(player.getId(), charging)
            );
    }

    // ---------------------------------------------------------------- the server's side of it

    /** [stated] "it needs to run out of ammo to reload" — only ever called on an EMPTY caster. */
    private static void reload(Player player, ItemStack stack) {
        var level = player.level();

        if (!player.getAbilities().instabuild) {
            var inventory = player.getInventory();
            var core = -1;

            for (var slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).is(PredatorItems.PLASMA_CORE.get())) {
                    core = slot;

                    break;
                }
            }

            if (core < 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.displayClientMessage(Component.translatable("item.avp_predator.hand_caster.no_core"), true);

                return;
            }

            inventory.getItem(core).shrink(1);
        }

        stack.set(PredatorDataComponents.HAND_CASTER_SHOTS.get(), MAX_SHOTS);
        player.getCooldowns().addCooldown(stack.getItem(), RELOAD_TICKS);
        level.playSound(null, player.blockPosition(), PredatorSoundEvents.HAND_CASTER_RELOAD.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    private static void fire(
        ServerLevel level,
        Player player,
        ItemStack stack,
        float charge,
        @org.jetbrains.annotations.Nullable Vec3 tip
    ) {
        var muzzle = tip != null ? tip : muzzlePosition(player);
        var bolt = new PlasmaBoltProjectile(level, player);

        // [stated] "same bolt" — the caster's bolt and speed. Damage scales with the charge: half at the shortest hold,
        // the caster's full bolt at a full charge.
        bolt.setDamageOverride(PlasmaCaster.BOLT_DAMAGE * (0.5F + 0.5F * charge));
        // [stated] "a large explosion effect when it hits a mob and when it hits a block have it destroy 3/4 a tnt
        // explosion" — the hand caster's bolts only; the yautja's caster still never explodes.
        bolt.setExplosive(true);
        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
        bolt.launch(aimPoint(player).subtract(muzzle), PlasmaCaster.BOLT_SPEED);
        level.addFreshEntity(bolt);
        PlasmaCaster.spawnMuzzleFlash(level, muzzle);
        level.playSound(null, player.blockPosition(), PredatorSoundEvents.HAND_CASTER_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.1F);

        if (!player.getAbilities().instabuild) {
            stack.set(PredatorDataComponents.HAND_CASTER_SHOTS.get(), shots(stack) - 1);
        }

        // The render side plays "handcaster.fire" when this is recent. ⚠ A TIME, not a counter: when the server changes
        // a
        // component the client receives a NEW ItemStack object, so a per-object "last count seen" would never see it
        // change. A recent fire time is readable from whichever object arrives.
        stack.set(PredatorDataComponents.HAND_CASTER_FIRED_AT.get(), level.getGameTime());
        player.getCooldowns().addCooldown(stack.getItem(), FIRE_COOLDOWN_TICKS);
    }

    /**
     * [stated] "it fires straight where the players reticle is pointing." The point under the crosshair — the first
     * entity or block along the look, out to {@link #AIM_RANGE} — so a bolt leaving the barrel, beside the eye, still
     * lands on what the reticle covers.
     */
    private static Vec3 aimPoint(Player player) {
        var eye = player.getEyePosition();
        var far = eye.add(player.getLookAngle().scale(AIM_RANGE));
        var block = player.pick(AIM_RANGE, 1.0F, false);
        var end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        var entity = ProjectileUtil.getEntityHitResult(
            player,
            eye,
            end,
            player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
            candidate -> !candidate.isSpectator() && candidate.isPickable(),
            eye.distanceToSqr(end)
        );

        return entity != null ? entity.getLocation() : end;
    }

    /**
     * Roughly where the barrel is as OTHER players see it — in the holder's hand, from the body's facing. Used for the
     * sparks everyone else sees, and for the bolt when the holder's client sent no tip. The holder's own sparks and
     * bolt use the exact tip from the renderer (HandCasterClient).
     */
    public static Vec3 muzzlePosition(Player player) {
        var yaw = Math.toRadians(player.yBodyRot);
        var forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        var right = new Vec3(-forward.z, 0.0D, forward.x);
        var side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;

        return player.position().add(0.0D, 0.95D, 0.0D).add(right.scale(0.36D * side)).add(forward.scale(0.55D));
    }

    /** The plasma caster's charge colour. */
    private static final org.joml.Vector3f CHARGE_BLUE = new org.joml.Vector3f(0.42F, 0.78F, 1.0F);

    /**
     * The plasma caster's charge sparks, sent to EVERY player EXCEPT the holder — the holder draws their own at the
     * true barrel tip (HandCasterClient), so the server's copy no longer lands in front of their face.
     */
    private static void sendChargeSparksToOthers(ServerLevel level, Player holder, Vec3 at, float progress) {
        var spread = 0.16D - (0.16D - 0.05D) * progress;
        var count = 1 + Math.round(progress * 2.0F);
        var dust = new net.minecraft.core.particles.DustParticleOptions(CHARGE_BLUE, 0.4F + progress * 0.2F);

        for (var viewer : level.players()) {
            if (viewer == holder) {
                continue;
            }

            level.sendParticles(viewer, dust, false, at.x, at.y, at.z, count, spread, spread, spread, 0.0D);
            level.sendParticles(
                viewer,
                net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                false,
                at.x,
                at.y,
                at.z,
                1,
                spread,
                spread,
                spread,
                0.0D
            );

            if (progress > 0.6F) {
                level.sendParticles(
                    viewer,
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    false,
                    at.x,
                    at.y,
                    at.z,
                    1,
                    0.1D,
                    0.1D,
                    0.1D,
                    0.0D
                );
            }
        }
    }

    /**
     * [stated] "it keeps sparkling with effects while you hold down the button" — the caster's own charge particles
     * every tick held, tightening as it charges, and still sparkling once full. Also drops a charge the player walked
     * away from (switched item, died).
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }

        playSelectionSound(stack, level, player, selected);

        var start = CHARGING.get(player.getUUID());

        if (start == null) {
            return;
        }

        if (!selected || !player.isAlive()) {
            cancel(player);

            return;
        }

        var progress = Math.min(1.0F, (float) (level.getGameTime() - start) / CHARGE_TICKS);

        sendChargeSparksToOthers(serverLevel, player, muzzlePosition(player), progress);
    }

    /** ⚠ Stops a caster in the hotbar announcing itself the moment a player logs in. */
    private static final int SELECTION_SOUND_GRACE_TICKS = 10;

    /**
     * [stated] the initiation sound is "the noise when you take it out and played in reverse when you put it away" —
     * the same shape as the plasma bow's equip sound, on the CHANGE of selection rather than every tick.
     */
    private static void playSelectionSound(ItemStack stack, Level level, Player player, boolean selected) {
        var wasSelected = stack.getOrDefault(PredatorDataComponents.HAND_CASTER_WAS_SELECTED.get(), false);

        if (selected == wasSelected) {
            return;
        }

        stack.set(PredatorDataComponents.HAND_CASTER_WAS_SELECTED.get(), selected);

        if (player.tickCount <= SELECTION_SOUND_GRACE_TICKS) {
            return;
        }

        level.playSound(
            null,
            player.blockPosition(),
            selected ? PredatorSoundEvents.CASTER_DEPLOY.get() : PredatorSoundEvents.CASTER_RETRACT.get(),
            SoundSource.PLAYERS,
            0.8F,
            1.0F
        );
    }

    @Override
    public void appendHoverText(
        @NotNull ItemStack stack,
        @NotNull TooltipContext context,
        @NotNull List<Component> tooltip,
        @NotNull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.avp_predator.hand_caster.shots", shots(stack), MAX_SHOTS).withStyle(ChatFormatting.GRAY));
    }
}
