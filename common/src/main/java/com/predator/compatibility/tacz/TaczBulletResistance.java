package com.predator.compatibility.tacz;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * TACZ's side of the gun balancing on yautja — the same numbers avp_alien uses on xenomorphs, so a TACZ gun hits both
 * species alike. The budget, the floor and the game rule are shared in
 * {@link com.predator.compatibility.guns.GunDamageParity}.
 * <h2>The multiplier</h2> avp_human's median 20.0 dps against TACZ's own median (its gun data, shotgun damage split
 * across pellets as TACZ itself does): 0.242. Copied from avp_alien's {@code TaczBulletResistance}, not re-derived: it
 * compares two gun mods, so it does not depend on what is being shot. ⚠ If one is ever changed, change the other, or
 * the same gun would hit xenomorphs and yautja differently.
 * <h2>Identified by damage type</h2> TACZ's bullets carry its own {@code tacz:bullets} damage-type tag. Referenced by
 * id, so there is no dependency on TACZ: without it the tag is simply empty and nothing matches.
 */
public final class TaczBulletResistance {

    private static final TagKey<DamageType> TACZ_BULLETS =
        TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("tacz", "bullets"));

    public static final float DPS_PARITY_MULTIPLIER = 0.242F;

    public static boolean isTaczBullet(DamageSource damageSource) {
        return damageSource.is(TACZ_BULLETS);
    }

    private TaczBulletResistance() {}
}
