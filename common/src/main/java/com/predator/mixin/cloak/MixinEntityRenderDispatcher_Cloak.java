package com.predator.mixin.cloak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.cloak.PredatorCloakRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the buffer source out from under a cloaked entity's entire render.
 * <p>
 * ⚠⚠ This hangs off {@link EntityRenderDispatcher}, not {@code LivingEntityRenderer}, and that is the whole point.
 * {@code AzEntityRenderer} extends {@code EntityRenderer}, <b>not</b> {@code LivingEntityRenderer} — so every mob
 * animated through BLib, the yautja included, falls straight through a {@code LivingEntityRenderer} hook. This is the
 * same gap that once left xenomorphs unclassified by the vision, and it is called out in
 * {@code MixinAzEntityRenderer_VisionClassification} for exactly that reason. The dispatcher sits above both renderer
 * families, so one hook covers players, vanilla mobs and BLib-animated mobs alike.
 * <p>
 * Swapping the argument rather than the body render type is the other half of the trick. Vanilla's invisibility only
 * nulls the body type, which is why an invisible player in armour is a walking suit of armour; every layer still draws
 * through the buffer source it was handed. Replace that one object and body, armour, held items and any sibling mod's
 * layers all follow, with nothing left visibly floating.
 * <p>
 * The handler captures the target method's <em>complete</em> argument list after the value. Mixin allows the value
 * alone or the value followed by every argument — a partial capture is rejected, which is how the first attempt at this
 * silently never bound.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher_Cloak {

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource predator$routeCloakedDraws(
        MultiBufferSource value,
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight
    ) {
        PredatorCloakRendering.beginEntity(entity);
        return PredatorCloakRendering.wrap(value, entity);
    }

    /** Clears the render-thread entity context once this entity's whole render (layers and armour included) is done. */
    @Inject(method = "render", at = @At("RETURN"))
    private void predator$clearCloakContext(
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callbackInfo
    ) {
        PredatorCloakRendering.endEntity();
    }
}
