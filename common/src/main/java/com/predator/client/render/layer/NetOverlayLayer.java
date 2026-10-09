package com.predator.client.render.layer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.PredatorResources;
import com.predator.client.net.ClientNetState;
import com.predator.client.render.net.NetTextureScale;
import com.predator.client.render.net.VillagerHatMeta;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Draws the net shrink-wrapped over any mob caught in one, at a consistent mesh size.
 * <h2>His call: it clings to the body, it is not a cage</h2> The layer re-renders the mob's OWN model with the net
 * texture, so the mesh follows the silhouette exactly and animates with the mob for free, because it IS the mob's
 * model. A netted mob that struggles, struggles inside it.
 * <h2>⚠⚠ THE UV SCALE IS WHAT MAKES IT LOOK THE SAME ON EVERYTHING</h2> Re-rendering the mob's model means sampling the
 * net through THAT MOB'S UVs, so the net is stretched across whatever sheet it uses and lands at
 * {@code 512 / sheetSize} cells per block — an 8-cell mesh on a 64px player against a 2-cell one on a 256px dragon.
 * Multiplying the UVs by {@code sheetSize / 64} cancels it exactly, because vanilla is a consistent 16 texels per block
 * on every mob measured, making sheet size the only variable.
 * <h2>Why it flushes its own batch</h2> ⚠ The texture matrix is GLOBAL GL state applied at DRAW time, not at the time
 * vertices are queued. A {@code MultiBufferSource} batches by render type and flushes later, so setting the matrix and
 * walking away would apply it to whatever happened to be drawn at flush — possibly nothing of ours, possibly everything
 * else. Ending this one batch inside the matrix is what ties the two together.
 * <p>
 * ⚠ And the matrix is ALWAYS reset. Leaving it set would scale the UVs of every entity drawn afterwards in the frame —
 * a bug that would look like the whole world's textures had broken, with nothing pointing back at a net.
 */
public class NetOverlayLayer<T extends Entity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/entity/net_overlay.png");

    public NetOverlayLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffers,
        int packedLight,
        @NotNull T entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        // ⚠ First line is a set lookup that returns immediately. This layer is on EVERY living renderer in the game,
        // so the not-netted case has to cost nothing.
        if (!ClientNetState.isNetted(entity.getId())) {
            return;
        }

        var scale = NetTextureScale.forEntity(entity, this::textureFor);
        // ⚠⚠ armorCutoutNoCull, NOT entityCutoutNoCull. This is the render type vanilla uses to draw ARMOUR over
        // skin without z-fighting, and it is the same problem: VIEW_OFFSET_Z_LAYERING nudges the geometry toward
        // the camera so it always wins the depth test, and EQUAL_DEPTH_TEST draws it exactly on the surface.
        var type = RenderType.armorCutoutNoCull(TEXTURE);
        var scaling = scale != 1.0F;

        if (scaling) {
            RenderSystem.setTextureMatrix(new Matrix4f().scale(scale, scale, 1.0F));
        }

        poseStack.pushPose();

        // ⚠⚠ NO INFLATE. It used to scale the whole model by 1.04, and a SCALE moves a face proportionally to its
        // distance from the model ORIGIN — so the head top (y~8) gained 0.32 units while an arm's top face (y~2
        // from its pivot) gained only 0.08. That is not enough separation to win the depth test, which is why the
        // TOPS of arms and small mobs like the chicken showed no net while outer sides and heads did.
        //
        // EQUAL_DEPTH_TEST requires the geometry to be COPLANAR with the surface it covers, and the layering state
        // handles the separation instead. Constant coverage on every face regardless of where it sits.

        // ⚠ The parent model is already posed for this frame by the renderer that owns it, so the net inherits the
        // mob's current animation with no work.
        // ⚠⚠ A VILLAGER'S HAT IS GEOMETRY EVEN WHEN NOTHING IS DRAWN ON IT. Villagers and zombie villagers render
        // "hat" and "hat_rim" every frame; the TEXTURES decide whether anything shows (an unemployed plains villager's
        // hat region is transparent). The net texture has no transparent region, so those parts came out as a giant
        // mesh headdress with a brim. Ask vanilla's own hat metadata whether the hat is real, and hide the parts for
        // this pass only when it is not. ⚠ Villagers ONLY: for illagers, witches and everything else the part's
        // visible flag already says whether vanilla draws it, and the net follows that as it always did.
        var hidden = entity instanceof net.minecraft.world.entity.npc.VillagerDataHolder && !VillagerHatMeta.hasHat(entity)
            ? hideHatParts(getParentModel())
            : java.util.List.<ModelPart>of();

        getParentModel().renderToBuffer(poseStack, buffers.getBuffer(type), packedLight, OverlayTexture.NO_OVERLAY, -1);
        restoreHatParts(hidden);

        poseStack.popPose();

        if (scaling) {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch(type);
            }

            RenderSystem.resetTextureMatrix();
        }
    }

    /** ⚠ {@code getTextureLocation} is protected on the layer, so it is reached through a method reference. */
    private ResourceLocation textureFor(T entity) {
        return getTextureLocation(entity);
    }

    /** Sets {@code hat} / {@code hat_rim} (under {@code head}) invisible and returns the parts that were visible. */
    private static java.util.List<ModelPart> hideHatParts(EntityModel<?> model) {
        if (!(model instanceof HierarchicalModel<?> hierarchical) || !hierarchical.root().hasChild("head")) {
            return java.util.List.of();
        }

        var head = hierarchical.root().getChild("head");
        var hidden = new java.util.ArrayList<ModelPart>(2);

        if (head.hasChild("hat")) {
            var hat = head.getChild("hat");

            hideIfVisible(hat, hidden);

            if (hat.hasChild("hat_rim")) {
                hideIfVisible(hat.getChild("hat_rim"), hidden);
            }
        }

        return hidden;
    }

    private static void hideIfVisible(ModelPart part, java.util.List<ModelPart> hidden) {
        if (part.visible) {
            part.visible = false;
            hidden.add(part);
        }
    }

    private static void restoreHatParts(java.util.List<ModelPart> hidden) {
        for (var part : hidden) {
            part.visible = true;
        }
    }
}
