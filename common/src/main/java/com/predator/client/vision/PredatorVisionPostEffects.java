package com.predator.client.vision;

import com.blib.api.client.mod.v1.BLibClientMod;
import com.blib.api.client.shader.v1.BLibPostEffectInput;
import com.blib.api.client.shader.v1.BLibPostEffectSpec;
import com.blib.api.client.shader.v1.BLibPostEffectUniform;
import com.predator.Predator;
import com.predator.client.compatibility.PredatorClientCompatibility;
import com.predator.common.gameplay.component.PredatorVisionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FogType;

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

    /**
     * How far a block heat source reaches, in blocks. Beyond this it contributes nothing.
     * <p>
     * ⚠ Keep below {@code PredatorHeatSourceScanner.RADIUS} or sources will pop in at the edge of the scan window
     * instead of fading in.
     * <p>
     * ⚠⚠ Remember a pool is MANY sources combined by max, so the visible glow is the union of all their radii, not one
     * radius. A value that looks reasonable against a single torch spreads alarmingly across a lava lake.
     */
    private static final float HEAT_SOURCE_RANGE = 6.0F;

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
                    .withInput(BLibPostEffectInput.DEPTH_TEXTURE)
                    .withUniform(new BLibPostEffectUniform.Float1("wipeLineX", PredatorVisionPostEffects::wipeLineX))
                    .withUniform(new BLibPostEffectUniform.Int1("oldMode", PredatorVisionPostEffects::oldMode))
                    .withUniform(new BLibPostEffectUniform.Int1("newMode", PredatorVisionPostEffects::newMode))
                    .withUniform(
                        new BLibPostEffectUniform.Float4Array(
                            "heatSources",
                            PredatorHeatSourceScanner::packedSources,
                            PredatorHeatSourceScanner.MAX_SOURCES
                        )
                    )
                    .withUniform(new BLibPostEffectUniform.Int1("heatSourceCount", PredatorHeatSourceScanner::sourceCount))
                    .withUniform(new BLibPostEffectUniform.Float1("heatSourceRange", () -> HEAT_SOURCE_RANGE))
                    .withUniform(new BLibPostEffectUniform.Int1("heatDebugView", PredatorVisionPostEffects::heatDebugView))
                    .withUniform(new BLibPostEffectUniform.Int1("cameraSubmerged", PredatorVisionPostEffects::cameraSubmerged))
                    .withUniform(new BLibPostEffectUniform.Float1("rainLevel", PredatorVisionPostEffects::rainLevel))
                    .enabledWhen(PredatorVisionPostEffects::shouldRun)
                    .priority(100)
                    .build()
            );
    }

    /** Shares the scanner's debug flag: one switch turns on both the log and the on-screen diagnostic. */
    private static int heatDebugView() {
        return "true".equalsIgnoreCase(System.getProperty("avp_predator.heatDebug")) ? 1 : 0;
    }

    /**
     * 1 while the view is underwater. The sky heat path goes cold rather than showing daylight green.
     * <p>
     * TWO SOURCES, OR-ED, AND THE SECOND ONE IS WHAT REMOVED THE FLASH ON ENTERING WATER. They disagree for up to a
     * full tick because they are sampled from different clocks:
     * <ul>
     * <li>{@code camera.getFluidInCamera()} is computed per FRAME from the INTERPOLATED camera position.</li>
     * <li>{@code player.isEyeInFluid(WATER)} is a cached set refreshed once per TICK, from the ticked eye position --
     * and it is the condition vanilla itself uses to draw the full-screen underwater overlay.</li>
     * </ul>
     * Descending through the surface, the ticked eye goes under first, so vanilla starts drawing the overlay while the
     * interpolated camera is still above the waterline. For those few frames the camera term was still 0, the sky path
     * ran normally, and the view read as open sky -- red wherever the celestial recolour reached, since the sun is
     * still drawn underwater. One frame or three, depending on framerate.
     * <p>
     * OR-ing means whichever clock notices the water first wins, so there is no window where one says water and the
     * other does not. It biases toward COLD during the disagreement, which is the correct way round: half a tick of
     * cold sky on the way out is invisible, half a tick of hot sky on the way in is a flash.
     * <p>
     * The eye term is gated on first person and non-spectator to match exactly when vanilla draws the overlay -- in
     * third person the camera can sit well clear of a submerged player, and that camera is the one whose view is being
     * coloured.
     */
    private static int cameraSubmerged() {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();

        if (camera.getFluidInCamera() == FogType.WATER) {
            return 1;
        }

        var player = minecraft.player;

        // ⚠ getCameraType() is null-checked here, NOT just at the other call site. This method runs while the world is
        // rendering its first frames, and an unguarded dereference at that point takes the process down through the
        // driver — Windows reports STATUS_STACK_BUFFER_OVERRUN (exit -1073740791), which writes NEITHER a Minecraft
        // crash report NOR an hs_err_pid log. That silence is why this took so long to find.
        var cameraType = minecraft.options.getCameraType();

        if (player == null || player.isSpectator() || cameraType == null || !cameraType.isFirstPerson()) {
            return 0;
        }

        return player.isEyeInFluid(FluidTags.WATER) ? 1 : 0;
    }

    /** 0 clear, 1 full downpour. Thunder counts as rain here — the sky is just as covered either way. */
    private static float rainLevel() {
        var level = Minecraft.getInstance().level;

        return level == null ? 0.0F : level.getRainLevel(1.0F);
    }

    private static boolean shouldRun() {
        if (PredatorClientCompatibility.areVisionsDisabledBySodium()) {
            return false;
        }

        // ⭐ THIRD PERSON STANDS THE VISION DOWN, AND THE REASON IS THE FICTION, NOT THE RENDERING.
        // [stated] "if im looking through a mask how can i be looking at myself?" — the modes are what the helmet's
        // optics show, so a camera floating behind the player is not looking through them at all. Detached views also
        // put the wearer in frame, which the vision then classifies and heat-colours: the one thing a first-person
        // helmet display can never do.
        //
        // ⚠ Held items are already routed to the cold background branch so they cannot give away the player's
        // position; this closes the same gap for the player's own body.
        var cameraType = Minecraft.getInstance().options.getCameraType();

        if (cameraType != null && !cameraType.isFirstPerson()) {
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
