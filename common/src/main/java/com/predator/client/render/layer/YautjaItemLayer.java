package com.predator.client.render.layer;

import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzBlockAndItemLayer;
import com.mojang.math.Axis;
import com.predator.client.cloak.PredatorCloakRendering;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class YautjaItemLayer extends AzBlockAndItemLayer<UUID, Yautja> {

    /**
     * The bone the main-hand item is drawn on.
     * <p>
     * ⚠⚠ {@code gLeftHoldWeapon}, AND THAT IS NOT A TYPO. Verified against the game rather than the bone names: with
     * {@code gRightHoldWeapon} the weapon appeared in the yautja's LEFT hand on screen. In this model the bones named
     * "Right" sit at NEGATIVE x and render on the entity's left, the opposite of vanilla's humanoid convention. Trust
     * the screenshot, not the label.
     * <p>
     * ⚠ It was {@code "rightHand_Item"} before that — a bone that has never existed in this geo — so a held item did
     * not render at all for the entire life of the mod.
     * <p>
     * ⚠ WORTH KNOWING: {@code attack.melee.weapon} swings {@code gLeftArm} hardest (135 degrees against the right's
     * 70), so the weapon clip and the weapon hand now agree. The blade clips, stab and slash, lead with
     * {@code gRightArm} — the other arm — exactly as they should.
     */
    private static final String WEAPON_HAND = "gLeftHoldWeapon";

    // ---------------------------------------------------------------------------------------------------------------
    // The battleaxe grip.
    //
    // [stated] "it has a group on the axe called grab here and it goes into the left hand of the yautja in the
    // lefthandhold group." — and, after the first attempt, "looks like your holding it in the wrong spot ... its the
    // complete opposite of the grabhere group location."
    //
    // 🚨🚨 WHY THE FIRST ATTEMPT HELD IT BY THE HEAD. It translated by the negative grab pivot and nothing else, but
    // between this layer and the drawn model the item passes through THREE more transforms, none of which it undid:
    // 1. the item model's thirdperson_righthand DISPLAY transform (and it was the COMBI STICK's, copied across —
    // scale 0.75, shifted), applied by vanilla's renderStatic;
    // 2. vanilla's translate(-0.5, -0.5, -0.5) centring for a builtin/entity item;
    // 3. BLib's translate(+0.5, 0, +0.5) in AzItemRendererPipeline.preRender (no y term, because useNewOffset).
    // Modelled numerically, that put the point 19 px up the haft on the hand — inside the axe head. Exactly the
    // screenshot. The offset below undoes all three, so the grab point lands on the bone origin: checked numerically
    // to land at (0, 0, 0), and to stay there at any BATTLEAXE_SCALE.
    //
    // ⚠ BLib bakes a geo pivot with X NEGATED (AzBuiltinBakedModelFactory: updatePivot(-pivot.x, ...)), so the
    // grab's X is mirrored here. It is a tenth of a pixel on this model, but it is not left as a hidden error.
    // ⚠⚠ DISPLAY_Y_PX MUST MATCH models/item/battleaxe.json's thirdperson_righthand translation. Change one, change
    // both — that coupling is the whole reason the first attempt failed.
    // ---------------------------------------------------------------------------------------------------------------

    /** gGrabHere's pivot in the geo, in pixels, exactly as authored. */
    private static final float GRAB_X_PX = 0.1F;

    private static final float GRAB_Y_PX = 0.5F;

    private static final float GRAB_Z_PX = -0.1F;

    /**
     * The item model's thirdperson_righthand translation Y, pixels. ⚠ Keep in step with battleaxe.json.
     * <p>
     * Was -3.75. Raised to +7.5 for the PLAYER's grip — [stated] "the 3rdparty player is holding the battleaxe too
     * high. it needs to go up further" — which put the grab point 11.25 px below the player's hand. Changed HERE in the
     * same step, so the yautja, which undoes this translation, holds it exactly where it did before.
     */
    private static final float DISPLAY_Y_PX = 7.5F;

    /** Net Y shift of vanilla's -0.5 centring plus BLib's useNewOffset (which adds nothing on Y), blocks. */
    private static final float PIPELINE_Y = -0.5F;

    /** Turns the haft to lie along the hand. Same family as the flat weapons' 270 so it starts from a known pose. */
    public static float BATTLEAXE_ROT_X = 270.0F;

    public static float BATTLEAXE_ROT_Y = 0.0F;

    public static float BATTLEAXE_ROT_Z = 0.0F;

    /** Extra nudge AFTER the grab alignment, in blocks, for fine adjustment. Zero means "grab point on the bone". */
    public static float BATTLEAXE_NUDGE_X = 0.0F;

    public static float BATTLEAXE_NUDGE_Y = 0.0F;

    public static float BATTLEAXE_NUDGE_Z = 0.0F;

    /** Size. The alignment is scale-invariant, so changing this does not move the grab point. */
    public static float BATTLEAXE_SCALE = 1.0F;

    private static void alignBattleaxe(AzRendererPipelineContext<UUID, Yautja> context) {
        var pose = context.poseStack();

        pose.mulPose(Axis.XP.rotationDegrees(BATTLEAXE_ROT_X));
        pose.mulPose(Axis.YP.rotationDegrees(BATTLEAXE_ROT_Y));
        pose.mulPose(Axis.ZP.rotationDegrees(BATTLEAXE_ROT_Z));
        pose.scale(BATTLEAXE_SCALE, BATTLEAXE_SCALE, BATTLEAXE_SCALE);

        // The grab point as BLib bakes it — X mirrored — in blocks.
        var grabX = -GRAB_X_PX / 16.0F;
        var grabY = GRAB_Y_PX / 16.0F;
        var grabZ = GRAB_Z_PX / 16.0F;

        // Undo the display translation, the pipeline centring and the grab offset, so the grab lands on the bone.
        pose.translate(-grabX, -(DISPLAY_Y_PX / 16.0F + PIPELINE_Y + grabY), -grabZ);
        pose.translate(BATTLEAXE_NUDGE_X, BATTLEAXE_NUDGE_Y, BATTLEAXE_NUDGE_Z);
    }

    @Override
    public ItemStack itemStackForBone(AzBone bone, Yautja animatable) {
        return switch (bone.getName()) {
            case WEAPON_HAND -> animatable.getItemBySlot(EquipmentSlot.MAINHAND);
            default -> null;
        };
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(AzBone bone, ItemStack stack, Yautja animatable) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    protected void renderItemForBone(
        AzRendererPipelineContext<UUID, Yautja> context,
        AzBone bone,
        ItemStack itemStack,
        Yautja animatable
    ) {
        if (itemStack.getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            alignBattleaxe(context);
        } else {
            context.poseStack().mulPose(Axis.XP.rotationDegrees(270));
            context.poseStack().mulPose(Axis.YP.rotationDegrees(0));
            context.poseStack().mulPose(Axis.ZP.rotationDegrees(0f));
            context.poseStack().translate(0.0D, 0.1D, -0.1D);
        }

        // ⚠⚠ MARKS THE DRAW SO THE CLOAK SKIPS IT. PredatorCloakRendering wraps the buffer for the entire
        // entity render, so the shorting-out crackle was being inherited by the sword or axe in its hand.
        // ⚠ try/finally, not a plain pair of calls: if the item render throws, an un-cleared flag would leave
        // every subsequent entity uncloakable for the rest of the session.
        PredatorCloakRendering.beginHeldItem();

        try {
            super.renderItemForBone(context, bone, itemStack, animatable);
        } finally {
            PredatorCloakRendering.endHeldItem();
        }
    }
}
