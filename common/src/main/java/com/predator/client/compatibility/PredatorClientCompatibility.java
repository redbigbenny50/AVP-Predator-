package com.predator.client.compatibility;

import com.blib.api.BLibAPI;
import com.predator.common.gameplay.component.PredatorVisionMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Client-side compatibility helpers for the predator module.
 * <p>
 * ⭐ The Sodium block on predator visions is LIFTED — see {@link #areVisionsDisabledBySodium()}.
 * <p>
 * Mod presence is resolved through {@code BLibAPI.isModLoaded}, which is loader-agnostic and reflection-free. An
 * earlier version routed through Architectury's {@code Platform} (not a declared dependency, so it threw
 * {@link NoClassDefFoundError} the first time a vision post effect evaluated {@code shouldRun()}), and the version
 * after that used reflection against both loaders to dodge the problem. Neither is needed: BLib is a declared
 * dependency of this module and already answers the question.
 * </p>
 */
public final class PredatorClientCompatibility {

    private static final Component SODIUM_VISION_INCOMPATIBLE_MESSAGE = Component.literal(
        "Predator visions are currently incompatible with Sodium and will work with it soon in a future update."
    );

    private static Boolean sodiumLoadedCache;

    private PredatorClientCompatibility() {
        throw new UnsupportedOperationException();
    }

    public static boolean isSodiumLoaded() {
        Boolean cached = sodiumLoadedCache;

        if (cached == null) {
            cached = detectModLoaded("sodium");
            sodiumLoadedCache = cached;
        }

        return cached;
    }

    /**
     * ⭐⭐ THE SODIUM BLOCK IS LIFTED — visions now run with Sodium installed.
     * <p>
     * This returned {@code isSodiumLoaded()}, which hard-disabled predator visions at THREE points:
     * {@code PredatorKeybindingRegistry} refused the keybind, {@code PredatorVisionPostEffects} never ran the effect,
     * and {@code PredatorVisionAccessor} short-circuited. It was a placeholder for work that has since been done in
     * BLib.
     * </p>
     * <p>
     * ⭐ WHY IT IS SAFE NOW: {@code BLibSodiumCompat} documents that Sodium alone is NOT a reason to shut the
     * post-effect pipeline down — Sodium ships its own chunk shaders, so the six vanilla terrain shaders never compile
     * and terrain never writes the auxiliary attachments, which is why ENTITIES read correctly and the world did not.
     * BLib now enables a depth-gated terrain mask fixup to compensate. Only Iris/Oculus disables the pipeline outright.
     * </p>
     * <p>
     * ⚠ TO PUT THE BLOCK BACK, return {@code isSodiumLoaded()} again — nothing else needs changing. The message and the
     * detection are both still here for exactly that reason.
     * </p>
     * <p>
     * ⚠ IF THE VISION LOOKS WRONG RATHER THAN ABSENT — entities tinted correctly but terrain flat or unlit — that is
     * the terrain mask fixup, not this flag. It can be turned off with {@code -Dblib.terrainMaskFixup=false} to
     * confirm.
     * </p>
     */
    public static boolean areVisionsDisabledBySodium() {
        return false;
    }

    /**
     * TRUE WHILE A SHADER PACK IS RENDERING THE WORLD, in which case the vision CYCLES BUT DOES NOT DISPLAY.
     * <p>
     * BLib stands its whole post-effect pipeline down when Iris/Oculus owns the pipeline, so the erosion wipe and the
     * false-colour pass both go missing while the keybind, the sound and the helmet's stored mode all keep working
     * normally. From the wearer's side that is indistinguishable from a broken keybind.
     */
    public static boolean isVisionDisplaySuppressedByShaderPack() {
        // ⭐ THE VISION NOW DISPLAYS UNDER A SHADER PACK, so the interim notice must stop firing — an action bar
        // insisting the mode "is not displayed" while it is plainly on screen is worse than no message at all.
        //
        // ⚠ KEPT RATHER THAN DELETED, and deliberately: the message and its caller are one line from being useful
        // again if a future pack or loader turns out to defeat the classification pass. Return
        // BLibPostEffectFramework.isShaderModActive() here to restore it.
        return false;
    }

    /**
     * Tells the wearer WHICH MODE THEY JUST SELECTED and why nothing happened on screen.
     * <p>
     * <b>This exists because the failure is silent and reads as a bug.</b> [stated] "still no visor sweep or change on
     * screen i dont know what mode im in" — and that was from the person who WROTE the stand-down, with a diagnostic
     * log open. A player with neither will report it as broken every time.
     * <p>
     * Sent to the ACTION BAR rather than chat: the keybind cycles, so a chat line per press would become spam, and the
     * mode name is glanceable status rather than something worth keeping in the log.
     */
    public static void sendShaderPackVisionSuppressedMessage(Player player, PredatorVisionMode mode) {
        player.displayClientMessage(
            Component.literal("Vision: " + displayName(mode) + " — not displayed while a shader pack is active."),
            true
        );
    }

    /** {@code ELECTROMAGNETIC} reads as shouting in a status line; {@code Electromagnetic} does not. */
    private static String displayName(PredatorVisionMode mode) {
        var raw = mode.name().toLowerCase(Locale.ROOT);

        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public static void sendSodiumVisionIncompatibleMessage(Player player) {
        player.displayClientMessage(SODIUM_VISION_INCOMPATIBLE_MESSAGE, false);
    }

    /**
     * ⚠⚠ WAS REFLECTIVE, DELIBERATELY IS NOT ANY MORE. This used to do {@code Class.forName} + {@code getMethod} +
     * {@code invoke} against BOTH Fabric Loader and NeoForge's ModList to avoid a compile-time dependency on either.
     * <p>
     * ⭐ {@code BLibAPI.isModLoaded} does the same job through BLib's loader service — already a hard dependency of this
     * module, already loader-agnostic, and with NO reflection. Dynamic class lookup by string name plus reflective
     * method invocation is exactly the shape automated jar scanners flag, and CurseForge's published process decompiles
     * and statically analyses class code. BLib had one such method and it cost days in manual review; there is no
     * reason for this module to carry three more.
     * </p>
     * <p>
     * ⚠ The original {@code NoClassDefFoundError} this reflection was written to avoid — from routing through
     * Architectury's {@code Platform}, which is not a declared dependency — cannot recur here: BLib IS declared.
     * </p>
     */
    private static boolean detectModLoaded(String modId) {
        return BLibAPI.isModLoaded(modId);
    }
}
