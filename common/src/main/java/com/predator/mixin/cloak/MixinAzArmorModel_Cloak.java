package com.predator.mixin.cloak;

import com.blib.api.client.render.v1.armor.model.AzArmorModel;
import com.predator.client.cloak.PredatorCloakRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Routes BLib-rendered armour through the cloak's buffer swap.
 * <p>
 * Needed because {@code AzArmorModel.renderToBuffer} does not use the buffer source it was handed — vanilla's
 * {@code Model#renderToBuffer} passes a {@code VertexConsumer} and no source, so BLib reaches for
 * {@code Minecraft.getInstance().levelRenderer.renderBuffers.bufferSource()} directly. No swap made further up the
 * render chain can reach armour without this.
 * <p>
 * ⚠ NO {@code @Shadow} HERE, deliberately. An earlier version shadowed {@code AzArmorModel}'s private
 * {@code rendererPipeline} field to reach the wearer. Shadowed fields bind against the class as loaded at startup, and
 * hot-swapping a jar underneath a running client left that binding stale — which is why the predator armour rendered as
 * magenta after a jar swap while six other armour sets on the identical BLib path were fine: they are fine because
 * nothing mixes into them. The wearer now comes from a render-thread context set by
 * {@code MixinEntityRenderDispatcher_Cloak}, which touches no BLib internals and has no binding to go stale.
 * <p>
 * {@code remap = false} — this targets a mod class, so there are no mappings to apply.
 */
@Mixin(value = AzArmorModel.class, remap = false)
public abstract class MixinAzArmorModel_Cloak {

    @ModifyVariable(method = "renderToBuffer", at = @At("STORE"), ordinal = 0, remap = false)
    private MultiBufferSource predator$routeCloakedArmour(MultiBufferSource value) {
        var wearer = PredatorCloakRendering.currentEntity();
        return wearer == null ? value : PredatorCloakRendering.wrapArmour(value, wearer);
    }
}
