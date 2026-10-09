package com.predator.mixin.cloak;

import com.predator.client.cloak.PredatorCloakRendering;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the level's entity pass so cloaked entities' replay lanes can be drawn together instead of per entity. See
 * the cross-entity batching note in {@link PredatorCloakRendering}.
 * <p>
 * The flush is anchored on the {@code "blockentities"} profiler section rather than a bytecode offset: it is the first
 * thing {@code renderLevel} does after its entity loop, it survives mapping differences between loaders, and the
 * string has been stable for many versions. If it ever stops matching, {@code endLevelFrame} notices held lanes at the
 * end of the frame and switches the cloak back to per-entity replay, logging once - nothing goes missing for more than
 * that one frame.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_CloakBatch {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void predator$beginCloakBatch(CallbackInfo callbackInfo) {
        PredatorCloakRendering.beginLevelFrame();
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            args = "ldc=blockentities"
        )
    )
    private void predator$flushCloakBatch(CallbackInfo callbackInfo) {
        PredatorCloakRendering.flushDeferredReplays();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void predator$endCloakBatch(CallbackInfo callbackInfo) {
        PredatorCloakRendering.endLevelFrame();
    }
}
