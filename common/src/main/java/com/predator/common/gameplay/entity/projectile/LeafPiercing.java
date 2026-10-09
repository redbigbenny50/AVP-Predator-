package com.predator.common.gameplay.entity.projectile;

/**
 * A projectile that passes through leaves instead of stopping in a canopy.
 * <h2>Why this exists as a marker rather than a list in the mixin</h2> ⚠⚠ THE PLASMA CASTER "MISSING BADLY" AT TREED
 * PREY WAS THE BOLT HITTING THE CANOPY. The leaves pass-through was written for the yautja and then extended to the
 * smart disc, and the disc is exactly the weapon that was reported working against prey in a tree while the caster was
 * not — the difference between them was this and nothing else. A bolt that detonates on the underside of the foliage
 * looks, from the ground, like a shot that went nowhere near.
 * <p>
 * Implementing an interface means a new predator projectile opts in at its own declaration, where the author is
 * thinking about it, rather than by remembering to edit a mixin in another package.
 */
public interface LeafPiercing {}
