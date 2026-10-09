package com.predator.client.render.armor;

import com.blib.api.client.model.v1.AzBakedModel;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.armor.AzArmorRenderer;
import com.blib.api.client.render.v1.armor.AzArmorRendererConfig;
import com.predator.PredatorResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Renders the jungle predator armour.
 * <h2>⚠⚠ THE WRIST-BLADE GROUP SITS ON THE MAIN-HAND ARM, AND SWAPS WITH HANDEDNESS</h2> The geo carries a blade group
 * on each arm — {@code gGauntletBladeLeft} and {@code gGauntletBladeRight} — and exactly one draws: the one on the arm
 * OPPOSITE the offhand. The offhand arm wears the gauntlet item, which draws itself on that arm in third person
 * ({@code GauntletArmLayer}), so the armour must not carry a second one there. A right-hander (offhand LEFT) shows the
 * RIGHT blade; flip the main hand in Skin Customisation and the groups swap.
 * <h2>⚠⚠ SET BOTH GROUPS ON EVERY PASS, NEVER SKIP</h2> The armour stack has no animator, so {@code provideBakedModel}
 * hands back the model SHARED by every wearer of this armour. Whatever visibility the last pass left on a bone is what
 * the next wearer inherits — the same shared-model leak that once lit every gun icon's muzzle flash. Both bones are
 * written unconditionally each pass, from the wearer being drawn right now.
 * <p>
 * ⚠ {@code preRenderEntry} runs AFTER BLib's per-slot visibility dance, so this is not undone by it, and
 * {@code setHidden} cascades to the mount and blade children, which is what we want.
 */
public class JunglePredatorArmorRenderer extends AzArmorRenderer {

    private static final String NAME = "jungle_predator";

    private static final ResourceLocation MODEL = PredatorResources.armorGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.armorTextureLocation(NAME);

    private static final String BLADE_LEFT = "gGauntletBladeLeft";

    private static final String BLADE_RIGHT = "gGauntletBladeRight";

    public JunglePredatorArmorRenderer() {
        super(
            AzArmorRendererConfig.builder(MODEL, TEXTURE)
                .setPrerenderEntry(JunglePredatorArmorRenderer::showBladeOnMainArm)
                .build()
        );
    }

    private static AzRendererPipelineContext<UUID, ItemStack> showBladeOnMainArm(
        AzRendererPipelineContext<UUID, ItemStack> context
    ) {
        AzBakedModel model = context.bakedModel();

        if (model == null) {
            return context;
        }

        // ⚠ Non-player wearers (a mob in the chestplate, an armour stand) have no handedness: treat as right-handed.
        var mainArm = context.currentEntity() instanceof Player player ? player.getMainArm() : HumanoidArm.RIGHT;

        setHidden(model, BLADE_LEFT, mainArm != HumanoidArm.LEFT);
        setHidden(model, BLADE_RIGHT, mainArm != HumanoidArm.RIGHT);

        return context;
    }

    private static void setHidden(AzBakedModel model, String boneName, boolean hidden) {
        var bone = model.getBoneOrNull(boneName);

        if (bone != null) {
            bone.setHidden(hidden);
        }
    }
}
