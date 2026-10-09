package com.predator.mixin;

import com.predator.client.render.layer.GauntletArmLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link GauntletArmLayer} to every player renderer.
 * <h2>⚠⚠ EXTENDS, DOES NOT SHADOW</h2> {@code addLayer} is declared on {@code LivingEntityRenderer}, not on
 * {@code PlayerRenderer}, and {@code @Shadow} resolves against the target class ITSELF — shadowing an inherited method
 * fails at load with {@code InvalidMixinException}. Extending the parent makes the method inherited instead. The
 * constructor below is required by javac, not by mixin; mixin never merges constructors.
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer_GauntletArmLayer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private MixinPlayerRenderer_GauntletArmLayer(
        EntityRendererProvider.Context context,
        PlayerModel<AbstractClientPlayer> model,
        float shadowRadius
    ) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void avp_predator$addGauntletArmLayer(
        EntityRendererProvider.Context context,
        boolean slim,
        CallbackInfo callback
    ) {
        this.addLayer(new GauntletArmLayer(this));
    }
}
