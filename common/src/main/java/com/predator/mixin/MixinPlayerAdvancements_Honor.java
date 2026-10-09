package com.predator.mixin;

import com.predator.common.gameplay.hunt.YautjaHonor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yautja honor is paid when an advancement COMPLETES. {@code award} runs once per criterion; it returns true when it
 * changed something, and the advancement is complete when its progress reads done afterwards — that pair is checked
 * here, so a multi-criterion advancement pays exactly once, on its last criterion.
 */
@Mixin(PlayerAdvancements.class)
public abstract class MixinPlayerAdvancements_Honor {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void avp_predator$creditHonor(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone()) {
            YautjaHonor.onAdvancementCompleted(player, advancement.id());
        }
    }
}
