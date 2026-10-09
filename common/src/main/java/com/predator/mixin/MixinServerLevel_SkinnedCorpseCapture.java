package com.predator.mixin;

import com.predator.common.gameplay.hunt.SkinnedCorpses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a skinned-corpse capture is armed (see {@link SkinnedCorpses}), dropped items go into the corpse instead of
 * into the world. Outside a capture this is a single ThreadLocal read and returns.
 * <p>
 * ⚠ This is the one point every death-drop route reaches: {@code spawnAtLocation}, avp_human's marine inventory (which
 * calls {@code addFreshEntity} directly), and NeoForge's post-event re-add all end here.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel_SkinnedCorpseCapture {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void avp_predator$captureCorpseDrop(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (SkinnedCorpses.tryCapture(entity)) {
            callback.setReturnValue(true);
        }
    }
}
