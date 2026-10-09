package com.predator.mixin;

import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Oct 7 - [stated] "make sure the predator mask and the ape suit mask are ignored by the gigeresque face huggers."
 * <p>
 * Gigeresque 0.8.16 decides whether a facehugger may take a host in ONE place: {@code GigEntityUtils.faceHuggerTest},
 * which the facehugger's own targeting, the egg's hatch-and-leap and its shared helpers all call (read from the jar).
 * When it would say yes and the target is wearing a helmet in {@link PredatorItemTags#FACEHUGGER_PROOF_HELMETS} (the
 * yautja bio-mask), the answer becomes no - the facehugger ignores that host and goes for someone else.
 * </p>
 * <p>
 * ⚠ {@code @Pseudo}: Gigeresque is optional. Without it the target class does not exist and this mixin is skipped.
 * Targeted by NAME with remap off (the method is Gigeresque's own, not Minecraft's, and has one overload); the handler
 * names only Minecraft types, which the Fabric build remaps with the rest of this mod. Same pattern as the Point Blank
 * hook. The other AVP mods carry their own copy for their own masks; several mods injecting here is fine.
 * </p>
 */
@Pseudo
@Mixin(targets = "mods.cybercat.gigeresque.common.util.GigEntityUtils", remap = false)
public abstract class MixinGigEntityUtils_FacehuggerProofMask {

    @Inject(method = "faceHuggerTest", at = @At("RETURN"), cancellable = true, remap = false)
    private static void avp_predator$ignoreMaskedHost(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && target.getItemBySlot(EquipmentSlot.HEAD).is(PredatorItemTags.FACEHUGGER_PROOF_HELMETS)) {
            cir.setReturnValue(false);
        }
    }
}
