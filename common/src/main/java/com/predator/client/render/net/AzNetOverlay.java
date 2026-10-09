package com.predator.client.render.net;

import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.predator.PredatorResources;
import com.predator.client.net.ClientNetState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * The capture-net overlay for BLib-rendered geometry. Same texture, same render type and same UV-density rule as
 * {@code NetOverlayLayer}; the difference is only how the geometry is re-drawn — through the pipeline's own
 * {@code reRender}, the way {@code AzAutoGlowingLayer} draws its glow.
 * <p>
 * The netted entity is the animatable itself for an entity renderer, and the WEARER ({@code currentEntity}) for an
 * armour renderer — so a netted yautja's armour is netted with it.
 */
public final class AzNetOverlay {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/entity/net_overlay.png");

    private AzNetOverlay() {
        throw new UnsupportedOperationException();
    }

    public static <K, T> void render(AzRendererPipelineContext<K, T> context) {
        var target = context.animatable() instanceof Entity entity ? entity : context.currentEntity();

        // ⚠ First line is a set lookup. This runs for every Az render in the game.
        if (target == null || !ClientNetState.isNetted(target.getId())) {
            return;
        }

        var pipeline = context.rendererPipeline();
        var baseTexture = pipeline.config().textureLocation(context.currentEntity(), context.animatable());
        var scale = NetTextureScale.forEntity(baseTexture, Function.identity());
        var type = RenderType.armorCutoutNoCull(TEXTURE);
        var scaling = scale != 1.0F;

        if (scaling) {
            RenderSystem.setTextureMatrix(new Matrix4f().scale(scale, scale, 1.0F));
        }

        // ⚠⚠ SAVE AND RESTORE THE CONTEXT'S RENDER TYPE. BLib draws held items DURING the model pass, per bone, from
        // whatever render type the context carries — and the context outlives the frame. Leaving the net type on it
        // meant the next frame's held item was drawn with the net texture: [tester] "the item they are holding
        // getting the charged creeper effect". Same discipline as BLib's own AzArmorTrimLayer.
        var previousType = context.renderType();
        var previousConsumer = context.vertexConsumer();
        var previousSkipFlatCubes = context.skipFlatCubes();

        // [stated] Sep 22: "with the net it seems to apply itself to the spine quads on the xeno ... can the quads
        // (planes) be excluded from the net texture." The mesh is drawn over solid cubes only; a xenomorph's spines,
        // fins and dorsal tubes are single planes and stay bare - a net wraps a body, it does not hang off every spike.
        context.setRenderType(type);
        context.setVertexConsumer(context.multiBufferSource().getBuffer(type));
        context.setSkipFlatCubes(true);
        pipeline.reRender(context);
        context.setSkipFlatCubes(previousSkipFlatCubes);
        context.setRenderType(previousType);
        context.setVertexConsumer(previousConsumer);

        if (scaling) {
            if (context.multiBufferSource() instanceof MultiBufferSource.BufferSource source) {
                source.endBatch(type);
            }

            RenderSystem.resetTextureMatrix();
        }
    }
}
