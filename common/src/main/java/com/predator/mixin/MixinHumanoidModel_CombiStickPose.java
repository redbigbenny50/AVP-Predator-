package com.predator.mixin;

import com.predator.client.input.CombiStickCharge;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The combi stick's arm pose: a forward THRUST on attack, and a wind-back while charging a throw.
 * <p>
 * <strong>⚠ Modelled on the spear-backport mod he supplied, which does this correctly.</strong> Its
 * {@code BipedEntityModelMixin} drives the stab from {@code attackTime} through phased {@code Mth.inverseLerp} windows
 * and aligns both arms' {@code yRot} to the body, so the thrust travels along the look direction instead of sweeping
 * across it. That alignment is the part a naive pose misses, and it is why a hand-rolled version reads as a sword
 * swing.
 * <p>
 * <strong>⚠ TAIL, not HEAD.</strong> Vanilla writes the arm every frame from its own attack animation, so a pose
 * applied at HEAD is immediately overwritten.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModel_CombiStickPose extends EntityModel<LivingEntity> {

    /**
     * Degrees the arm rebounds during the wind-up.
     * <p>
     * ⚠⚠ THIS IS THE "SPEAR GOES VERTICAL FOR A SPLIT SECOND" DIAL. The reference uses 90, reached in a single tick on
     * our swing length — the arm snaps upright, then the thrust term immediately yanks it the other way, which reads as
     * changing its mind. Their spear is a flat sprite where it barely shows; ours is a long shaft where it is glaring.
     * <p>
     * ⚠ Lowered to 35. Set it to 0 to remove the wind-up entirely; raise it back toward 90 for the reference's exact
     * motion.
     */
    private static float AVP_WINDUP_DEGREES = 35.0F;

    /**
     * How long the wind-up takes, as a fraction of the swing.
     * <p>
     * ⚠ The reference's 0.05 is about one tick — too fast to read as a motion, which is why it looked like a glitch
     * rather than a wind-up. 0.15 spreads it over three ticks so the eye follows it.
     */
    private static float AVP_WINDUP_END = 0.15F;

    /**
     * Where the thrust finishes, as a fraction of the swing.
     * <p>
     * ⚠⚠ THIS MUST MOVE WITH AVP_WINDUP_END. The thrust runs from the wind-up's end to here, so widening the wind-up
     * without widening this squeezes the thrust into a single tick and the arm snaps instead of driving. I did exactly
     * that on the first attempt: wind-up 0 to 0.15 with the thrust still ending at 0.2 left it 0.05 wide. Keep roughly
     * 0.15 between them.
     */
    private static float AVP_THRUST_END = 0.24F;

    /**
     * Degrees the arm settles back by at the end of the swing.
     * <p>
     * ⚠ Part of the balance below; changing it changes the thrust with it.
     */
    private static float AVP_SETTLE_DEGREES = 30.0F;

    /** ⚠ Wind-back window: the arm draws back over the first 30% of the swing. */
    private static final float AVP_CHARGE_WIND_BACK_END = 0.3F;

    /** ⚠ Thrust window: the fast part, 30% to 60%. Short on purpose — a stab is a jab, not a shove. */
    private static final float AVP_CHARGE_PUSH_END = 0.6F;

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Shadow
    public ModelPart body;

    @Shadow
    public ModelPart head;

    /**
     * ⚠⚠ attackTime IS NOT SHADOWED, AND THAT IS DELIBERATE. It is declared on {@link EntityModel}, not on
     * {@code HumanoidModel} — and the crash log taught me that a &#64;Shadow resolves against the TARGET CLASS ITSELF,
     * not its superclasses:
     *
     * <pre>
     *   InvalidMixinException: &#64;Shadow method addRenderableWidget ... was not located in the
     *   target class ... InventoryScreen
     * </pre>
     *
     * Shadowed FIELDS are more forgiving than methods, so this one had not thrown yet — but it was the same mistake
     * waiting. Extending {@code EntityModel} makes it inherited instead, which needs no shadow at all.
     * <p>
     * ⚠ {@code EntityModel} has a protected no-arg constructor, so this needs no constructor of its own.
     */

    /**
     * <strong>⚠⚠ CANCELS VANILLA'S ATTACK ANIMATION OUTRIGHT.</strong> This is what makes a thrust read as a thrust.
     * Previously the pose was applied at TAIL of {@code setupAnim} while vanilla's {@code setupAttackAnimation} was
     * still running underneath every frame — two animations fighting over the same arm, which is the stuttery whack.
     * The reference spear mod does the same thing: its mixin calls {@code CallbackInfo.cancel()} rather than layering
     * on top.
     * <p>
     * ⚠ Only cancelled while an EXTENDED combi stick is in hand. Every other item keeps vanilla's swing untouched.
     */
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void avp_predator$replaceSwingWithThrust(LivingEntity entity, float ageInTicks, CallbackInfo callback) {
        if (entity instanceof Player player && CombiStickItem.isExtendedInHand(player)) {
            callback.cancel();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void avp_predator$poseCombiStick(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo callback
    ) {
        if (!(entity instanceof Player player) || !CombiStickItem.isExtendedInHand(player)) {
            return;
        }

        var arm = player.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm;
        var side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        if (attackTime > 0.0F) {
            thrust(player, arm);

            return;
        }

        // ⚠ Reads vanilla's use state now, not a hand-polled left button — see CombiStickCharge.
        if (CombiStickCharge.charging(net.minecraft.client.Minecraft.getInstance().player)) {
            windBack(arm, side);
        }
    }

    /**
     * The stab — <strong>ported term for term from the reference jar's {@code BipedEntityModelMixin}</strong>, read out
     * of its bytecode rather than approximated.
     * <p>
     * <strong>⚠ My earlier version was three linear ramps and it read as a stuttery whack.</strong> The real curve is
     * three separate easings blended together:
     * <ul>
     * <li>{@code windUp} — a cosine ease over the first 5% of the swing</li>
     * <li>{@code thrust} — a SQUARED ramp from 5% to 20%, which is the sharp part</li>
     * <li>{@code settle} — an exponential in/out from 40% to the end, {@code pow(2, 20x-10)/2}</li>
     * </ul>
     * combined as {@code 90*windUp - 120*thrust + 30*settle} degrees.
     * <p>
     * <strong>⚠ Both arms' yRot are DECREMENTED by body.yRot, not assigned.</strong> That keeps whatever else has posed
     * the arm and only corrects for body rotation. Assigning, as I did before, threw that away.
     * <p>
     * <strong>⚠ The head yaw is wrapped to ±2π before use</strong>, then scaled by {@code (thrust - settle)} so the arm
     * tracks where the player looks during the thrust and releases as it settles.
     */
    private void thrust(LivingEntity entity, ModelPart arm) {
        // ⚠ Both arms get the yRot correction because it only cancels BODY rotation — it does not pose them, so
        // the off-hand keeps whatever vanilla gave it.
        rightArm.yRot -= body.yRot;
        leftArm.yRot -= body.yRot;

        // ⚠⚠ THE REFERENCE ALSO DOES `leftArm.xRot -= body.yRot` AND I PORTED IT WITHOUT QUESTIONING IT. That
        // subtracts a YAW from a PITCH — dimensionally wrong — and it swings the OFF-HAND arm backwards whenever
        // the player is turned, dragging the off-hand item with it. That is the "spear goes forward, offhand item
        // goes backwards" you saw.
        //
        // Removed. Nothing about a one-handed stab should move the other arm's pitch.

        var windUp = -(Mth.cos(
            Mth.PI * Mth.clamp(Mth.inverseLerp(attackTime, 0.0F, AVP_WINDUP_END), 0.0F, 1.0F)
        ) - 1.0F) / 2.0F;

        // ⚠ The thrust starts where the wind-up ENDS, so widening AVP_WINDUP_END pushes the thrust later rather
        // than overlapping it. Overlapping is what made the arm fight itself.
        var thrust = Mth.clamp(Mth.inverseLerp(attackTime, AVP_WINDUP_END, AVP_THRUST_END), 0.0F, 1.0F);

        thrust = thrust * thrust;

        var settle = Mth.clamp(Mth.inverseLerp(attackTime, 0.4F, 1.0F), 0.0F, 1.0F);

        if (settle < 0.5F) {
            settle = settle == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * settle - 10.0) / 2.0);
        } else {
            settle = settle == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * settle + 10.0)) / 2.0);
        }

        // ⚠⚠ THE THREE COEFFICIENTS MUST SUM TO ZERO OR THE ARM NEVER COMES HOME. At the end of the swing all
        // three easings have reached 1, so the arm's resting offset IS their sum. The reference's 90 - 120 + 30
        // balances to exactly 0 on purpose.
        //
        // When I lowered the wind-up from 90 to 35 to kill the vertical flash, I left the -120 alone and the sum
        // became -55. The arm finished 55 degrees off rest, then SNAPPED back when attackTime reset — which is the
        // "stabs, springs up, then returns" you saw. The spring was the snap.
        //
        // ⚠ So the thrust coefficient is DERIVED, never typed in: windUp + settle. Change either of those and the
        // balance holds by construction instead of by me remembering.
        var thrustDegrees = AVP_WINDUP_DEGREES + AVP_SETTLE_DEGREES;

        arm.xRot += (AVP_WINDUP_DEGREES * windUp - thrustDegrees * thrust + AVP_SETTLE_DEGREES * settle)
            * Mth.DEG_TO_RAD;

        var headYaw = head.yRot;

        while (headYaw >= Mth.TWO_PI) {
            headYaw -= Mth.TWO_PI;
        }

        while (headYaw <= -Mth.TWO_PI) {
            headYaw += Mth.TWO_PI;
        }

        arm.yRot += headYaw * (thrust - settle);
    }

    /**
     * Holding left click to charge a throw.
     * <p>
     * ⚠ His note: "when holding down the left mouse button it doesnt pull your arm to charge." It does now — the arm
     * cocks back over the shoulder and tracks where you are looking, so the throw reads as aimed.
     */
    private void windBack(ModelPart arm, float side) {
        var charge = Mth.clamp(
            CombiStickCharge.charge(net.minecraft.client.Minecraft.getInstance().player)
                / (float) com.predator.common.gameplay.item.combistick.CombiStickItem.FULL_CHARGE_TICKS,
            0.0F,
            1.0F
        );

        // ⚠ His note: the arm needs to go HIGHER, like the shuriken's throw pose. -1.1 barely lifted it off the
        // hip; -2.2 at rest brings it to shoulder height, and a full charge takes it back past vertical.
        arm.xRot = head.xRot * 0.5F - 2.2F - charge * 1.4F;
        arm.yRot = body.yRot + side * 0.35F;
        arm.zRot = side * -0.2F * charge;
    }
}
