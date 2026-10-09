package com.predator.client.handcaster;

import com.predator.Predator;
import com.predator.common.gameplay.item.HandCasterItem;
import com.predator.common.network.packet.C2SHandCasterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The hand caster's trigger, and the holder's OWN charge sparks, on the client.
 * <p>
 * [stated] "the player holds down the left click button and releases to fire" — the ATTACK key while one is held: down
 * sends PRESS, up sends RELEASE (with the barrel tip), letting go of the item / dying / opening a screen sends CANCEL.
 * <p>
 * [stated] "the particles should be where the end of the barrel is the tip of the caster barrel group." — the holder's
 * sparks are drawn HERE, at the tip HandCasterItemRenderer measures from the rendered gBarrel bone each frame, first
 * person or third. The server sends its copy of the sparks to everyone ELSE (HandCasterItem), because it can only know
 * one position and the holder's first-person barrel is somewhere no server could work out. ⚠ Vanilla's own left click
 * is off while one is held — MixinMinecraft_HandCaster.
 */
public final class HandCasterClient {

    /** The plasma caster's charge colour and sizes — the same look as its server-side sparks. */
    private static final DustParticleOptions CHARGE = new DustParticleOptions(new Vector3f(0.42F, 0.78F, 1.0F), 0.22F);

    private static final DustParticleOptions CHARGE_FULL = new DustParticleOptions(new Vector3f(0.42F, 0.78F, 1.0F), 0.32F);

    /**
     * The bright core of a full charge. ⚠ THIS REPLACES {@code ParticleTypes.END_ROD}, which is what read as white:
     * END_ROD draws a fixed white sprite and CANNOT be tinted, so no amount of colour work elsewhere would have touched
     * it. A pale-blue dust can be tinted, and sits between the ice blue and white rather than on white.
     */
    /**
     * The crackle. ⚠ ALSO replaces an untintable vanilla particle - {@code ParticleTypes.ELECTRIC_SPARK} is the other
     * white in this effect, and like END_ROD it draws a fixed sprite that ignores colour. Kept small and bright so it
     * still reads as a spark rather than another charge mote.
     */
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.62F, 0.88F, 1.0F), 0.16F);

    private static final DustParticleOptions CHARGE_CORE = new DustParticleOptions(new Vector3f(0.70F, 0.90F, 1.0F), 0.28F);

    private static final double SPREAD_START = 0.16D;

    private static final double SPREAD_END = 0.05D;

    /** A tip older than this (no frame drew the caster) is not trusted; the server's own muzzle is used instead. */
    private static boolean charging;

    /** Charging for real — pressed with shots in it and not cooling down; an empty caster's press is a reload. */
    private static boolean sparkling;

    private static int chargeTicks;

    /**
     * How far the muzzle sits from the eye, along the player's OWN look basis. ⭐ Tune these three and nothing else to
     * move the sparks: forward is toward the crosshair, side is toward the caster's arm, up is vertical.
     */
    private static final double MUZZLE_FORWARD = 0.62D;

    private static final double MUZZLE_SIDE = 0.26D;

    private static final double MUZZLE_UP = -0.08D;

    private HandCasterClient() {
        throw new UnsupportedOperationException();
    }

    public static boolean isHolding(@Nullable Player player) {
        return player != null && player.getMainHandItem().getItem() instanceof HandCasterItem;
    }

    /**
     * Kept as a no-op so {@code HandCasterItemRenderer} still compiles without change.
     * <p>
     * ⚠ The muzzle is computed from the player's own look basis now - see {@link #freshTip}. The renderer's bone matrix
     * was yaw-dependent and is deliberately no longer trusted for this. If you delete the reporting block in the
     * renderer, delete this too.
     * </p>
     */
    public static void setBarrelTip(Vec3 tip) {
        // Intentionally empty.
    }

    /**
     * Where the muzzle is in the world, right now.
     * <h2>⚠⚠ WHY THIS NO LONGER USES THE RENDERER'S BONE MATRIX</h2> The tip used to come from the barrel bone,
     * converted out of view space with {@code camera.rotation()}. That conversion is only correct at ONE yaw - it lined
     * up facing north and drifted further off screen the further you turned, because a view-space offset rotated by the
     * camera quaternion does not land where the hand actually is once yaw is involved. Same class of bug as the earlier
     * one, same symptom of "fine in one direction".
     * <p>
     * This builds the point from the player's OWN basis instead: forward is the look vector, side is perpendicular to
     * it in the horizontal plane, up is perpendicular to both. Rotating with the player is what those vectors ARE, so
     * the muzzle cannot drift with facing no matter which way you turn - it is direction-independent by construction
     * rather than by a correction that has to be right.
     * </p>
     * <p>
     * ⚠ It also works identically in first and third person, because it never touches the camera at all - and it needs
     * no per-frame report from the renderer, so it cannot go stale or be missed on a frame.
     * </p>
     */
    private static @Nullable Vec3 freshTip(Minecraft minecraft) {
        var player = minecraft.player;

        if (player == null || minecraft.level == null) {
            return null;
        }

        var look = player.getLookAngle();
        var flat = Math.sqrt(look.x * look.x + look.z * look.z);

        // Straight up or straight down leaves no horizontal direction to take a side vector from; fall back to the
        // body's facing so the muzzle stays put instead of snapping.
        var side = flat < 1.0E-4
            ? sideFromBodyYaw(player)
            : new Vec3(-look.z / flat, 0.0D, look.x / flat);

        var up = side.cross(look).normalize();
        var hand = player.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;

        return player.getEyePosition()
            .add(look.scale(MUZZLE_FORWARD))
            .add(side.scale(MUZZLE_SIDE * hand))
            .add(up.scale(MUZZLE_UP));
    }

    private static Vec3 sideFromBodyYaw(net.minecraft.world.entity.player.Player player) {
        var yaw = Math.toRadians(player.yBodyRot);

        return new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
    }

    public static void clientTick(Minecraft minecraft) {
        var player = minecraft.player;

        if (player == null) {
            charging = false;
            sparkling = false;

            return;
        }

        var holding = isHolding(player) && minecraft.screen == null && player.isAlive();
        var down = minecraft.options.keyAttack.isDown();

        if (!charging && holding && down) {
            charging = true;
            chargeTicks = 0;
            sparkling = HandCasterItem.shots(player.getMainHandItem()) > 0
                && !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
            send(C2SHandCasterPayload.of(C2SHandCasterPayload.PRESS));

            return;
        }

        if (charging && (!holding || !down)) {
            charging = false;
            sparkling = false;

            var tip = freshTip(minecraft);

            if (holding && tip != null) {
                send(new C2SHandCasterPayload(C2SHandCasterPayload.RELEASE, true, tip.x, tip.y, tip.z));
            } else {
                send(C2SHandCasterPayload.of(holding ? C2SHandCasterPayload.RELEASE : C2SHandCasterPayload.CANCEL));
            }

            return;
        }

        if (charging && sparkling) {
            chargeTicks++;
            spawnChargeSparks(minecraft, Math.min(1.0F, (float) chargeTicks / HandCasterItem.CHARGE_TICKS));
        }
    }

    /** The plasma caster's charge sparks — tightening as it charges, still sparkling once full — at the barrel tip. */
    private static void spawnChargeSparks(Minecraft minecraft, float progress) {
        var tip = freshTip(minecraft);

        if (tip == null || minecraft.level == null) {
            return;
        }

        var random = minecraft.level.getRandom();
        var spread = SPREAD_START - (SPREAD_START - SPREAD_END) * progress;
        var count = 1 + Math.round(progress * 2.0F);

        for (var i = 0; i < count; i++) {
            minecraft.level.addParticle(
                progress > 0.6F ? CHARGE_FULL : CHARGE,
                tip.x + random.nextGaussian() * spread,
                tip.y + random.nextGaussian() * spread,
                tip.z + random.nextGaussian() * spread,
                0.0D,
                0.0D,
                0.0D
            );
        }

        minecraft.level.addParticle(
            SPARK,
            tip.x + random.nextGaussian() * spread,
            tip.y + random.nextGaussian() * spread,
            tip.z + random.nextGaussian() * spread,
            0.0D,
            0.0D,
            0.0D
        );

        if (progress > 0.6F) {
            minecraft.level.addParticle(CHARGE_CORE, tip.x, tip.y, tip.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void send(C2SHandCasterPayload payload) {
        Predator.MOD.networking().sendToServer(payload);
    }
}
