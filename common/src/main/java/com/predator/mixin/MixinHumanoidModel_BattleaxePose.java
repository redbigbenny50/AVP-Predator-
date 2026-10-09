package com.predator.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both hands on the battleaxe, in third person.
 * <p>
 * [stated] "in 3rd person it will look weird if it looks like its being used with one arm."
 * <h2>How</h2> At the TAIL of setupAnim — after vanilla has posed the main arm, including its swing — the OFF arm is
 * set to follow it, turned inward so the two hands meet on the haft. Out of a swing, both arms take a two-handed carry.
 * So the swing still comes from vanilla's own timing, and the second arm simply rides with it.
 * <p>
 * ⚠ PLAYERS ONLY. HumanoidModel also draws zombies and skeletons that might pick one up; a yautja uses its own
 * animations and never reaches this.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModel_BattleaxePose {

    /** Carry pose: how far forward both arms reach, radians (negative is forward). */
    private static final float AVP_CARRY_PITCH = -0.85F;

    /** How far the main arm turns inward toward the haft. */
    private static final float AVP_MAIN_INWARD = 0.30F;

    /** How far the off arm reaches across to the haft — further than the main, since it crosses the body. */
    private static final float AVP_OFF_INWARD = 0.55F;

    /** The slam, RAISED: arms up and back over the head. */
    private static final float AVP_SLAM_RAISED_PITCH = -2.9F;

    /**
     * The slam, STRUCK: [stated] "the axe needs to rotate downward maybe 45 degrees when you land" — the carry pitch
     * plus 45 degrees, so the head reaches the ground in front instead of stopping at the player's middle.
     */
    private static final float AVP_SLAM_STRIKE_PITCH = AVP_CARRY_PITCH + (float) Math.toRadians(45.0D);

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void avp_predator$twoHandBattleaxe(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo callback
    ) {
        // ⚠ TAG, NOT instanceof. Was `instanceof BattleaxeItem`; the combistick and anything added later need the same
        // two-handed carry, and a datapack can extend the tag without touching code.
        if (
            !(entity instanceof Player player)
                || !player.getMainHandItem().is(com.predator.common.registry.tag.PredatorItemTags.TWO_HANDED)
        ) {
            return;
        }

        var rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        var main = rightHanded ? rightArm : leftArm;
        var off = rightHanded ? leftArm : rightArm;

        // ⚠ Inward is NEGATIVE yRot for the right arm and POSITIVE for the left — vanilla's crossbow hold uses the
        // same convention. The sign follows the arm, not the handedness, which is why it is derived per arm here.
        var mainInward = rightHanded ? -AVP_MAIN_INWARD : AVP_MAIN_INWARD;
        var offInward = rightHanded ? AVP_OFF_INWARD : -AVP_OFF_INWARD;

        // The slam overrides everything else, vanilla's swing included, for as long as it runs.
        var slam = com.predator.common.gameplay.item.battleaxe.BattleaxeSlamPose.of(
            player.getMainHandItem(),
            player.level().getGameTime() + (ageInTicks - net.minecraft.util.Mth.floor(ageInTicks)),
            player
        );

        if (slam.active()) {
            main.xRot = AVP_CARRY_PITCH
                + slam.raise() * (AVP_SLAM_RAISED_PITCH - AVP_CARRY_PITCH)
                + slam.strike() * (AVP_SLAM_STRIKE_PITCH - AVP_CARRY_PITCH);
            main.yRot = mainInward;
            main.zRot = 0.0F;
        } else if (player.attackAnim <= 0.0F) {
            // Out of a swing: both hands carrying the axe across the body.
            main.xRot = AVP_CARRY_PITCH;
            main.yRot = mainInward;
            main.zRot = 0.0F;
        }

        // The off arm follows the main arm — through a swing too — reaching across to the haft.
        off.xRot = main.xRot;
        off.yRot = main.yRot - mainInward + offInward;
        off.zRot = -main.zRot;
    }
}
