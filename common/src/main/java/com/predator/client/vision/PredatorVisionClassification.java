package com.predator.client.vision;

import com.predator.common.gameplay.component.PredatorVisionMode;
import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Classifies an entity for the vision post-effect, per-mode. The mixin uses this to decide which background-flag lanes
 * to push (BLib lane A = "background under oldMode", lane B = "background under newMode") plus whether to push the
 * per-bone-light context.
 * <p>
 * During a {@link PredatorVisionTransition} the two modes can differ — left of the wipe line the shader applies newMode
 * coloring, right of the line it applies oldMode coloring — and an entity can be visible under one but not the other.
 * The two lanes carry the per-mode decision down to the shader so each side picks the correct foreground/background
 * routing without the union-and-pick compromise that the old single-flag scheme required.
 * <p>
 * Lives outside {@code com.predator.mixin.*} because mixin packages can't contain inner classes that the mixin code
 * itself references — mixin owns those packages and blocks direct class loading.
 */
public final class PredatorVisionClassification {

    public enum Result {
        /**
         * The mode does not classify this entity in either direction (e.g. the mode is
         * {@link PredatorVisionMode#REGULAR}).
         */
        NONE,
        /** The mode treats this entity as a foreground / highlighted entity. */
        VISIBLE,
        /** The mode treats this entity as background — render through the mode's world-coloring branch. */
        BACKGROUND,
    }

    /**
     * Per-mode classification for a single entity. {@code underOld} corresponds to the shader's {@code oldMode} (right
     * of the wipe line during a transition); {@code underNew} corresponds to {@code newMode} (left of the line).
     * Outside a transition the two values are equal.
     */
    public record Classification(
        Result underOld,
        Result underNew
    ) {

        public boolean anyVisible() {
            return underOld == Result.VISIBLE || underNew == Result.VISIBLE;
        }

        public boolean isBackgroundUnderOld() {
            return underOld == Result.BACKGROUND;
        }

        public boolean isBackgroundUnderNew() {
            return underNew == Result.BACKGROUND;
        }

        /** {@code true} when neither mode actively classifies this entity (both REGULAR — no vision running). */
        public boolean isInactive() {
            return underOld == Result.NONE && underNew == Result.NONE;
        }
    }

    private PredatorVisionClassification() {
        throw new UnsupportedOperationException();
    }

    /**
     * Classify {@code entity} under the currently-active old/new modes.
     * <p>
     * Outside a transition both halves resolve to the helmet's current mode (so the two lanes carry identical values).
     * Inside a transition the halves use {@link PredatorVisionTransition#oldMode()} and
     * {@link PredatorVisionTransition#newMode()} respectively — matching the {@code oldMode}/{@code newMode} uniforms
     * the shader sees from {@link PredatorVisionPostEffects}.
     */
    public static Classification classify(LivingEntity entity) {
        var modes = activeModes();
        return new Classification(
            classifyUnder(modes.old, entity),
            classifyUnder(modes.next, entity)
        );
    }

    /**
     * Classification for entities that are always background under any active vision mode — non-living entities (item
     * entities, projectiles, minecarts, paintings, …) and block entities (chests, signs, banners, …). They have no
     * visibility tag, so under a non-REGULAR mode they're BACKGROUND; under REGULAR they're NONE.
     */
    public static Classification nonLivingClassification() {
        var modes = activeModes();
        return new Classification(
            modes.old == PredatorVisionMode.REGULAR ? Result.NONE : Result.BACKGROUND,
            modes.next == PredatorVisionMode.REGULAR ? Result.NONE : Result.BACKGROUND
        );
    }

    /**
     * @return the {@link PredatorVisionMode} that classifies {@code entity} as visible (used to look up that mode's
     *         hot-tag for the per-bone-light floor), or {@code null} if no active mode considers it visible. Prefers
     *         {@code newMode} when both modes consider the entity visible, since that's the mode the player is
     *         transitioning into.
     */
    public static @Nullable PredatorVisionMode visibleMode(LivingEntity entity) {
        var modes = activeModes();

        if (isVisibleUnder(modes.next, entity)) {
            return modes.next;
        }

        if (modes.old != modes.next && isVisibleUnder(modes.old, entity)) {
            return modes.old;
        }

        return null;
    }

    private record ModePair(
        PredatorVisionMode old,
        PredatorVisionMode next
    ) {}

    private static ModePair activeModes() {
        if (PredatorVisionTransition.isActive()) {
            return new ModePair(PredatorVisionTransition.oldMode(), PredatorVisionTransition.newMode());
        }

        var current = PredatorVisionAccessor.currentVisionMode();
        return new ModePair(current, current);
    }

    private static Result classifyUnder(PredatorVisionMode mode, LivingEntity entity) {
        if (mode == null || mode == PredatorVisionMode.REGULAR) {
            return Result.NONE;
        }

        if (!isVisibleUnder(mode, entity)) {
            return Result.BACKGROUND;
        }

        // Mud cloak: a thermal-only effect that insulates the entity's heat signature, so a thermal-tagged
        // mob covered in mud reads as cold world (background) instead of as foreground. Other vision modes
        // are unaffected — an em-tagged mob covered in mud still shows up under EM.
        if (mode == PredatorVisionMode.THERMAL && entity.hasEffect(PredatorMobEffects.getMudHolder())) {
            return Result.BACKGROUND;
        }

        return Result.VISIBLE;
    }

    private static boolean isVisibleUnder(PredatorVisionMode mode, LivingEntity entity) {
        var visibleTag = mode.visibleTag();
        return visibleTag != null && entity.getType().is(visibleTag);
    }
}
