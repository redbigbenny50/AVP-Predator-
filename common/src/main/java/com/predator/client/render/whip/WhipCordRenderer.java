package com.predator.client.render.whip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.predator.PredatorResources;
import com.predator.common.gameplay.whip.WhipCord;
import com.predator.common.gameplay.whip.WhipTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a rope between two points as a strip of quads with {@code entity/whip_cord.png} tiled along it — no model, so
 * the cord can be any length and any shape. Two crossed quads per segment, so it reads as a cord from every angle.
 * <h2>The taper</h2> [stated] "if you can have the tip taper up on the hit that would be good." Width follows
 * {@link #widthAt}: thin at the hand, thinner toward the tip, then flaring at the instant of the crack.
 */
public final class WhipCordRenderer {

    public static final ResourceLocation CORD_TEXTURE = PredatorResources.location("textures/entity/whip_cord.png");

    /**
     * Half-width of the QUAD at the hand, blocks. ⚠ The cord you SEE is this times the fraction of the texture's width
     * the cord occupies: whip_cord.png is 14 px of 32, so visible = 2 x 0.21 x 0.4375 = 0.1837 blocks, exactly a
     * vanilla chain. Repaint the cord narrower and it thins; raise this to compensate.
     */
    public static float BASE_WIDTH = 0.21F;

    /** Multiplier at the tip when NOT snapping — a whip thins toward its end. */
    public static float TIP_TAPER = 0.55F;

    /** First-person: how far right of centre the grip sits on the near plane. Negative for a left-handed holder. */
    public static double FIRST_PERSON_SIDE = 0.34D;

    /** First-person: how far below centre, on the near plane. */
    public static double FIRST_PERSON_DROP = -0.30D;

    /**
     * First-person: how far in front of the eyes the grip sits, blocks.
     * <p>
     * ⚠ This is what closes the gap at the spool: vanilla's line anchors at the ROD TIP, far up the screen, where the
     * rod's length hides any error, while the whip's spool sits at the grip. Smaller brings the cord's root closer to
     * your face and to the item.
     */
    public static double FIRST_PERSON_FORWARD = 0.30D;

    /** How far the grip drives FORWARD at the peak of the swing, blocks. */
    public static double SWING_REACH = 0.22D;

    /** How far it rises at the peak of the swing, blocks. */
    public static double SWING_LIFT = 0.12D;

    /** How far it crosses toward centre at the peak of the swing, blocks. */
    public static double SWING_SIDE = 0.10D;

    /** How far to mirror the anchor across the body for the gauntlet arm, blocks. */
    public static double WRIST_MIRROR = 0.34D;

    /** How much closer to the body the wrist sits than the grip, blocks. */
    public static double WRIST_PULL_BACK = 0.10D;

    /** Third-person: how far out from the body's centre line the hand is, blocks. */
    public static double THIRD_PERSON_REACH = 0.35D;

    /** Third-person: how far in front of the body, blocks. */
    public static double THIRD_PERSON_FORWARD = 0.8D;

    /** Third-person: how far below eye level, blocks. */
    public static double THIRD_PERSON_DROP = 0.45D;

    /** Extra drop while crouching, as vanilla's rod uses. */
    public static float CROUCH_DROP = -0.1875F;

    private static final float DEGREES_TO_RADIANS = 0.017453292F;

    private WhipCordRenderer() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return where the gauntlet's chain leaves the WRIST}
     * <p>
     * ⚠⚠ THE OPPOSITE ARM. {@code GauntletItem.arm} puts the gauntlet on the arm opposite the main hand, so a
     * right-hander wears it on the left — the hand anchor would draw the chain from the wrong side of the body. The
     * offsets are otherwise the hand's, pulled in tighter because a wrist launcher sits closer to the body than a held
     * grip does.
     */
    public static Vec3 wristAnchor(LivingEntity owner, float partialTick) {
        var hand = handAnchor(owner, partialTick);
        var look = owner.getViewVector(partialTick);
        var right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));

        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();

        // Mirror across the body's centre line and pull it back toward the wrist.
        var mirrored = hand.subtract(right.scale(2.0D * WRIST_MIRROR));

        return mirrored.subtract(look.scale(WRIST_PULL_BACK));
    }

    /**
     * {@return where the cord should LEAVE THE HAND on screen}
     * <p>
     * ⚠⚠ THIS IS VANILLA'S FISHING-ROD ANCHOR, NOT A WORLD-SPACE GUESS. In first person the held item is drawn in VIEW
     * space — it is not really out in the world where the eye-offset approximation put it, which is why the cord
     * appeared to start in mid-air a metre in front of the camera rather than at the grip. Vanilla solves this for the
     * fishing line by projecting a point on the CAMERA'S NEAR PLANE and swinging it with the attack animation; this is
     * the same treatment. Third person uses the shoulder offset instead, as vanilla also does.
     * <p>
     * ⚠ The SERVER still uses {@link WhipCord#handOrigin} for hit detection. The two differ by a few centimetres at the
     * grip and converge along the cord, which is the end that decides what gets hit.
     */
    public static Vec3 handAnchor(LivingEntity owner, float partialTick) {
        var minecraft = Minecraft.getInstance();

        if (owner == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            // 🚨🚨 BUILT FROM THE PLAYER'S OWN AXES, NOT THE CAMERA'S NEAR PLANE. Vanilla's rod swings its anchor with
            // yRot(swing * 0.5) / xRot(-swing * 0.7), and those rotate a WORLD-SPACE vector about the WORLD ORIGIN —
            // so how far the root moves depends on WHICH WAY YOU ARE FACING. [stated] "when i face north it puts the
            // chain all the way above me the other directions seem fine." A rod gets away with that flourish because
            // its line starts metres off at the tip; a whip's cord starts at the grip, where it is glaring.
            //
            // right/up/forward all come from the look vector, so this lands in the same spot at every yaw.
            var look = owner.getViewVector(partialTick);
            var right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));

            right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();

            var up = right.cross(look).normalize();
            var handed = owner.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;

            // ⚠⚠ THE ROOT RIDES THE SWING — IN THE PLAYER'S OWN FRAME. The held item is animated during an attack
            // (it dips and drives forward), but a fixed offset from the eye does not move with it, so the cord sat
            // still while the whip swung: "its not aligned with it". Vanilla does this with yRot/xRot on a
            // world-space vector, which is exactly what broke when facing north — expressing the same motion along
            // right/up/forward keeps it identical at every yaw.
            var swing = Mth.sin(Mth.sqrt(owner.getAttackAnim(partialTick)) * (float) Math.PI);

            return owner.getEyePosition(partialTick)
                .add(right.scale(FIRST_PERSON_SIDE * handed - swing * SWING_SIDE * handed))
                .add(up.scale(FIRST_PERSON_DROP + swing * SWING_LIFT))
                .add(look.scale(FIRST_PERSON_FORWARD + swing * SWING_REACH));
        }

        // ⚠⚠ THIRD PERSON IS VANILLA'S FISHING-ROD OFFSET, SIGN FOR SIGN. Mine had the same shape but the ARM SIGN
        // INVERTED — a right-handed player got the cord off their LEFT — and it ignored both entity scale and the
        // crouch drop, so it floated wide and low. [stated] "3rd party is not aligned right at all".
        var bodyYaw = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * DEGREES_TO_RADIANS;
        var sin = Mth.sin(bodyYaw);
        var cos = Mth.cos(bodyYaw);
        var scale = owner.getScale();
        var side = (owner.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D) * THIRD_PERSON_REACH * scale;
        var forward = THIRD_PERSON_FORWARD * scale;
        var crouch = owner.isCrouching() ? CROUCH_DROP : 0.0F;

        return owner.getEyePosition(partialTick)
            .add(
                -cos * side - sin * forward,
                crouch - THIRD_PERSON_DROP * scale,
                -sin * side + cos * forward
            );
    }

    /** {@return the half-width at {@code alongCord} (0 = hand, 1 = tip)}, with {@code flare} 0..1 for the crack. */
    public static float widthAt(double alongCord, float flare) {
        var taper = Mth.lerp((float) alongCord, 1.0F, TIP_TAPER);
        var crack = 1.0F + (WhipTuning.TIP_FLARE - 1.0F) * flare * (float) Math.pow(alongCord, 3.0D);

        return BASE_WIDTH * taper * crack;
    }

    /**
     * Draws the cord through {@code points}, which are ABSOLUTE world positions; the pose stack is expected to be at
     * the entity's origin, so each point is offset by {@code origin}.
     */
    public static void draw(
        PoseStack poseStack,
        MultiBufferSource buffers,
        Vec3 origin,
        Vec3[] points,
        int packedLight,
        float flare
    ) {
        if (points.length < 2) {
            return;
        }

        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CORD_TEXTURE));
        var pose = poseStack.last();

        for (var i = 0; i < points.length - 1; i++) {
            var from = points[i].subtract(origin);
            var to = points[i + 1].subtract(origin);
            var along = i / (double) (points.length - 1);
            var alongNext = (i + 1) / (double) (points.length - 1);
            var width = widthAt(along, flare);
            var widthNext = widthAt(alongNext, flare);
            var direction = to.subtract(from);

            if (direction.lengthSqr() < 1.0E-8D) {
                continue;
            }

            var forward = direction.normalize();
            var sideA = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
            var side = sideA.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : sideA.normalize();
            var up = forward.cross(side).normalize();
            var v0 = (float) (along * points.length);
            var v1 = (float) (alongNext * points.length);

            quad(vertices, pose, from, to, side, width, widthNext, v0, v1, packedLight);
            quad(vertices, pose, from, to, up, width, widthNext, v0, v1, packedLight);
        }
    }

    private static void quad(
        VertexConsumer vertices,
        PoseStack.Pose pose,
        Vec3 from,
        Vec3 to,
        Vec3 axis,
        float width,
        float widthNext,
        float v0,
        float v1,
        int packedLight
    ) {
        var matrix = pose.pose();
        var normal = pose.normal();

        vertex(vertices, matrix, normal, from.add(axis.scale(-width)), 0.0F, v0, packedLight);
        vertex(vertices, matrix, normal, from.add(axis.scale(width)), 1.0F, v0, packedLight);
        vertex(vertices, matrix, normal, to.add(axis.scale(widthNext)), 1.0F, v1, packedLight);
        vertex(vertices, matrix, normal, to.add(axis.scale(-widthNext)), 0.0F, v1, packedLight);
    }

    private static void vertex(
        VertexConsumer vertices,
        Matrix4f matrix,
        Matrix3f normal,
        Vec3 position,
        float u,
        float v,
        int packedLight
    ) {
        vertices.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
            .setColor(1.0F, 1.0F, 1.0F, 1.0F)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(0.0F, 1.0F, 0.0F);
    }
}
