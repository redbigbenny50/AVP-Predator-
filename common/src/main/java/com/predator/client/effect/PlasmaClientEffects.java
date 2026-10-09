package com.predator.client.effect;

import com.predator.common.network.packet.S2CPlasmaFlashPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The plasma detonation's screen effects: a BLUE-WHITE flash and a camera shake, both scaled by distance.
 * <p>
 * A compact cousin of avp_human's nuke effects (which also run tremors, pressure waves, and a cloud fallback). Two
 * things only: the flash is a full-screen fill that peaks instantly and fades over {@link #FLASH_TICKS}; the shake
 * nudges the player's look angles each tick with a decaying random jitter — the same mechanism the nuke uses. Someone
 * at the edge of the effect range gets a flicker, not a blind.
 * <h2>⚠ Dials are non-final statics</h2>
 */
public final class PlasmaClientEffects {

    /** Must match PlasmaDetonation.EFFECT_RANGE_SCALE — the falloff is computed against the same distance. */
    public static double EFFECT_RANGE_SCALE = 4.5D;

    public static int FLASH_TICKS = 40;

    public static int SHAKE_TICKS = 30;

    /** 0xRRGGBB of the flash. Blue-white so it reads as plasma, not the nuke's warm white. */
    public static int FLASH_COLOR = 0xDCEEFF;

    /** Max look-angle nudge per tick, degrees, at full shake. */
    public static float SHAKE_DEGREES = 1.6F;

    private static int flashTicks;

    private static float flashStrength;

    private static int shakeTicks;

    private static float shakeStrength;

    private PlasmaClientEffects() {
        throw new UnsupportedOperationException();
    }

    public static void trigger(S2CPlasmaFlashPayload payload) {
        var player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        var center = Vec3.atCenterOf(BlockPos.of(payload.centerPos()));
        var distance = player.position().distanceTo(center);
        // Same reach the server used to decide who gets the packet: 4.5x the blast radius (108 blocks for the
        // gauntlet).
        var effectRange = payload.radius() * EFFECT_RANGE_SCALE;
        var falloff = (float) Mth.clamp(1.0D - distance / effectRange, 0.0D, 1.0D);

        flashTicks = Math.max(flashTicks, FLASH_TICKS);
        flashStrength = Math.max(flashStrength, payload.flashIntensity() * (0.15F + falloff * 0.85F));

        if (payload.shakeIntensity() > 0.0F) {
            shakeTicks = Math.max(shakeTicks, SHAKE_TICKS);
            shakeStrength = Math.max(shakeStrength, payload.shakeIntensity() * falloff);
        }
    }

    /** Runs from the client tick. */
    public static void clientTick(Minecraft minecraft) {
        if (flashTicks > 0) {
            flashTicks--;
        }

        if (shakeTicks > 0) {
            shakeTicks--;

            var player = minecraft.player;

            if (player != null && shakeStrength > 0.0F && !minecraft.isPaused()) {
                var random = player.getRandom();
                var envelope = shakeStrength * (shakeTicks / (float) SHAKE_TICKS);
                var yaw = (random.nextFloat() - 0.5F) * 2.0F * SHAKE_DEGREES * envelope;
                var pitch = (random.nextFloat() - 0.5F) * 2.0F * SHAKE_DEGREES * envelope;

                // turn() takes "mouse deltas": 0.15 degrees per unit.
                player.turn(yaw / 0.15D, pitch / 0.15D);
            }
        }

        if (flashTicks <= 0) {
            flashStrength = 0.0F;
        }

        if (shakeTicks <= 0) {
            shakeStrength = 0.0F;
        }
    }

    /** Runs after the HUD. */
    public static void renderFlash(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (flashTicks <= 0 || flashStrength <= 0.0F) {
            return;
        }

        var life = Mth.clamp((flashTicks - deltaTracker.getGameTimeDeltaPartialTick(false)) / FLASH_TICKS, 0.0F, 1.0F);
        var alpha = (int) (255.0F * flashStrength * life * life);

        if (alpha <= 0) {
            return;
        }

        var window = Minecraft.getInstance().getWindow();

        graphics.fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), (alpha << 24) | FLASH_COLOR);
    }
}
