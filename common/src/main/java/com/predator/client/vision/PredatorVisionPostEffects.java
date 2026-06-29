package com.predator.client.vision;

import com.blib.api.client.mod.v1.BLibClientMod;
import com.blib.api.client.shader.v1.BLibPostEffectInput;
import com.blib.api.client.shader.v1.BLibPostEffectSpec;
import com.blib.api.client.shader.v1.BLibPostEffectUniform;
import com.predator.Predator;
import com.predator.client.compatibility.PredatorClientCompatibility;
import com.predator.common.gameplay.component.PredatorVisionMode;

/**
 * Predator-owned vision post-effect. The shader handles every non-regular vision mode (thermal, electromagnetic, …) via
 * an integer mode-id uniform; the effect runs whenever a non-regular mode is active <em>or</em> while a
 * {@link PredatorVisionTransition} is in flight (so the sweeping erosion wipe stays visible during a transition back to
 * REGULAR even after the helmet has already updated).
 * <p>
 * Mode-uniform contract: the shader receives {@link PredatorVisionMode} ordinal values for {@code oldMode} and
 * {@code newMode}. When they match, the shader skips the wipe band and renders the active mode uniformly.
 */
public final class PredatorVisionPostEffects {

    private PredatorVisionPostEffects() {
        throw new UnsupportedOperationException();
    }

    public static void register(BLibClientMod mod) {
        mod.postEffects()
            .register(
                BLibPostEffectSpec.builder(
                    Predator.MOD.resources().createLocation("vision"),
                    Predator.MOD.resources().createLocation("blib_post/vision")
                )
                    .withInput(BLibPostEffectInput.COLOR_TEXTURE)
                    .withInput(BLibPostEffectInput.ENTITY_MASK)
                    .withInput(BLibPostEffectInput.ENTITY_LIGHTMAP)
                    .withInput(BLibPostEffectInput.ENTITY_DRAW_DATA)
                    .withInput(BLibPostEffectInput.ENTITY_SPECULAR)
                    .withInput(BLibPostEffectInput.ENTITY_MATERIAL_ID)
                    .withUniform(new BLibPostEffectUniform.Float1("wipeLineX", PredatorVisionPostEffects::wipeLineX))
                    .withUniform(new BLibPostEffectUniform.Int1("oldMode", PredatorVisionPostEffects::oldMode))
                    .withUniform(new BLibPostEffectUniform.Int1("newMode", PredatorVisionPostEffects::newMode))
                    .enabledWhen(PredatorVisionPostEffects::shouldRun)
                    .priority(100)
                    .build()
            );
    }

    private static boolean shouldRun() {
        if (PredatorClientCompatibility.areVisionsDisabledBySodium()) {
            return false;
        }

        return PredatorVisionAccessor.currentVisionMode() != PredatorVisionMode.REGULAR
            || PredatorVisionTransition.isActive();
    }

    private static float wipeLineX() {
        return PredatorVisionTransition.isActive() ? PredatorVisionTransition.progress() : 1.0F;
    }

    private static int oldMode() {
        if (PredatorVisionTransition.isActive()) {
            return PredatorVisionTransition.oldMode().ordinal();
        }

        return PredatorVisionAccessor.currentVisionMode().ordinal();
    }

    private static int newMode() {
        if (PredatorVisionTransition.isActive()) {
            return PredatorVisionTransition.newMode().ordinal();
        }

        return PredatorVisionAccessor.currentVisionMode().ordinal();
    }
}
