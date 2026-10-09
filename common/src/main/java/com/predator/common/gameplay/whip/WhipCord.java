package com.predator.common.gameplay.whip;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where the cord is, at any moment, for a lash in flight. ONE definition, used by the server for hit detection and by
 * the client for drawing, so the rope you see is the rope that hits.
 * <p>
 * The lash is not rope physics — it is an arc driven on a curve: out fast, snap, recoil. {@link #tipDistance} is that
 * curve, and {@link #pointAt} bends the cord slightly across the swing so it reads as a lash rather than a pole.
 */
public final class WhipCord {

    /**
     * {@return the recorded pitch, nudged up or down}
     * <p>
     * ⚠ SYMMETRIC AROUND 1.0, by subtracting two rolls rather than scaling one. {@code 1 + random * spread} only ever
     * goes UP, which drifts the whole weapon sharp; {@code random - random} is centred, so across many swings the
     * average is still exactly the pitch he recorded.
     */
    public static float variedPitch(net.minecraft.util.RandomSource random, float spread) {
        return 1.0F + (random.nextFloat() - random.nextFloat()) * spread;
    }

    /** Segments the cord is divided into, for both drawing and hit sampling. */
    public static final int SEGMENTS = 12;

    private WhipCord() {
        throw new UnsupportedOperationException();
    }

    /** {@return 0..1 along the swing} */
    public static float progress(int age, float partialTick) {
        return Mth.clamp((age + partialTick) / WhipTuning.LASH_TICKS, 0.0F, 1.0F);
    }

    /**
     * {@return how far the tip is from the hand right now, blocks} Ease-out on the way there so the tip is quickest
     * just before the snap, then a faster ease back.
     */
    public static double tipDistance(float progress) {
        var reach = WhipTuning.LASH_REACH;

        if (progress <= WhipTuning.LASH_SNAP_AT) {
            var out = progress / WhipTuning.LASH_SNAP_AT;

            return reach * (1.0D - Math.pow(1.0D - out, 3.0D));
        }

        var back = (progress - WhipTuning.LASH_SNAP_AT) / (1.0F - WhipTuning.LASH_SNAP_AT);

        // ⚠⚠ EASE INTO ZERO, NOT OUT OF FULL EXTENSION. With only three ticks of return, "1 - back^n" curves leave
        // the cord long until the very last frame and then collapse several blocks in one tick — a teleport, not a
        // recoil. (1 - back)^2 spends the recoil early, so by the final tick the cord is already short and the
        // disappearance is invisible.
        var remaining = 1.0D - back;

        return reach * remaining * remaining;
    }

    /** {@return true for the instant the tip is fully out — the crack} */
    public static boolean isSnapping(float progress) {
        return Math.abs(progress - WhipTuning.LASH_SNAP_AT) < 0.5F / WhipTuning.LASH_TICKS;
    }

    /**
     * {@return the cord's position at {@code alongCord} (0 = hand, 1 = tip)} The sideways bend peaks mid-cord and fades
     * as the whip straightens at the snap, which is what gives the lash its shape.
     */
    public static Vec3 pointAt(Vec3 origin, Vec3 direction, Vec3 side, float progress, double alongCord) {
        var distance = tipDistance(progress) * alongCord;
        var straightness = progress <= WhipTuning.LASH_SNAP_AT
            ? progress / WhipTuning.LASH_SNAP_AT
            : 1.0F;
        var bend = Math.sin(alongCord * Math.PI) * (1.0D - straightness) * WhipTuning.LASH_REACH * 0.35D;
        var sag = Math.sin(alongCord * Math.PI) * 0.25D * (1.0D - straightness);

        return origin
            .add(direction.scale(distance))
            .add(side.scale(bend))
            .subtract(0.0D, sag, 0.0D);
    }

    /** Sideways offset of the grip from the eye line, blocks. Positive is the holder's right. */
    public static double HAND_SIDE = 0.40D;

    /** How far below eye level the grip sits, blocks. */
    public static double HAND_DROP = 0.45D;

    /** How far in front of the eyes the grip sits, blocks. */
    public static double HAND_FORWARD = 0.55D;

    /**
     * {@return where the cord leaves the hand}
     * <p>
     * ⚠ THE CORD IS DRAWN IN WORLD SPACE, NOT PARENTED TO THE HAND BONE, so this is an approximation of where the grip
     * is — the three dials above line it up. Raising HAND_DROP moves the cord's root down, HAND_FORWARD pushes it away
     * from the face, HAND_SIDE moves it toward the holder's right.
     */
    public static Vec3 handOrigin(LivingEntity owner, float partialTick) {
        var eye = owner.getEyePosition(partialTick);
        var look = owner.getViewVector(partialTick);
        var side = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();

        return eye.add(side.scale(HAND_SIDE)).subtract(0.0D, HAND_DROP, 0.0D).add(look.scale(HAND_FORWARD));
    }
}
