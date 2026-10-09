package com.predator.mixin;

import com.predator.client.input.NetStruggleInputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the mash input for the net struggle.
 * <p>
 * ⚠ Same injection point avp_alien uses for its own struggle — HEAD of {@code handleKeybinds}. Two mods injecting the
 * same method is fine; each reads the keys and decides independently whether it applies, and only one of them can be
 * true at a time (you cannot be netted and carried off at once).
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_NetStruggle {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void avp_predator$handleNetStruggleInput(CallbackInfo callback) {
        NetStruggleInputHandler.handle((Minecraft) (Object) this);
    }
}
