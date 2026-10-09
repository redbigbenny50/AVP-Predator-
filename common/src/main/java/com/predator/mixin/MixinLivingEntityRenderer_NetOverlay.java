package com.predator.mixin;

import com.predator.client.render.layer.NetOverlayLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives EVERY living-entity renderer the net overlay layer.
 * <h2>⚠⚠ THE CONSTRUCTOR IS THE POINT</h2> Injecting at the TAIL of {@code LivingEntityRenderer}'s constructor means
 * every renderer ever built gets the layer — vanilla mobs, this mod's, and mobs from mods that do not exist yet. There
 * is no registry of renderers to walk and no list to keep up to date, which is the only way "catch any mob from any
 * mod" is achievable at all.
 * <p>
 * The approach is Gigeresque's, and the 1.21.1 signatures were checked against it: {@code LivingEntityRenderer(Context,
 * M, float)} and {@code protected final addLayer(RenderLayer)} both exist unchanged.
 * <p>
 * ⚠ The layer itself costs nothing when nothing is netted — its first line is a set lookup that returns immediately.
 * Adding it to every renderer in the game is only acceptable BECAUSE that early-out is the common case.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer_NetOverlay<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected abstract boolean addLayer(RenderLayer<T, M> layer);

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("TAIL"))
    private void avp_predator$addNetOverlay(
        EntityRendererProvider.Context context,
        M model,
        float shadowRadius,
        CallbackInfo callback
    ) {
        this.addLayer(new NetOverlayLayer<>((RenderLayerParent<T, M>) this));
    }
}
