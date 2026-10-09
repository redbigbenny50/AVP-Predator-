package com.predator.mixin.cloak;

import com.predator.client.cloak.PredatorSceneColor;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the scene-colour copy at the exact moment terrain is finished and entities have not started.
 * <p>
 * Anchored on the {@code "entities"} profiler constant in {@code renderLevel} rather than on a method call, because the
 * constant is stable across Sodium — which replaces the terrain rendering underneath it but leaves {@code renderLevel}
 * and its profiler sections intact.
 * <p>
 * Ordering is the whole point: a copy taken after entities would contain the cloaked body, and refracting your own
 * previous silhouette produces feedback smearing.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_SceneColorCapture {

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=entities"))
    private void predator$captureSceneColour(CallbackInfo callbackInfo) {
        PredatorSceneColor.capture();
    }

    /**
     * Closes the level-render window. Anything drawn after this — inventory previews, screen entity renders — must not
     * take the refraction path, because those use GUI matrices and screen-space sampling is meaningless there.
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void predator$endLevelRender(CallbackInfo callbackInfo) {
        PredatorSceneColor.endLevelRender();
        com.predator.client.cloak.PredatorCloakSounds.tick();
    }
}
