package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * The plasma bow's bolt: a red charge that BURSTS on whatever it meets.
 * <p>
 * [stated] "it doesnt lodge the bolts into objects they burst against them and enemies in red particles" and "it flies
 * through leaves like the caster bolt does destroying them in its path".
 * <h2>⚠ An arrow that refuses to behave like one</h2> It extends {@code AbstractArrow} for the flight, draw-power
 * scaling and crit handling a bow expects — but an arrow's defining habits are exactly what he ruled out, so lodging,
 * pickup and the drop are all suppressed below. Extending the thing and then removing three behaviours is still far
 * less code than reimplementing flight.
 */
public class PlasmaBoltArrowProjectile extends AbstractArrow {

    /** Blocks per tick at a full draw. */
    public static final float LAUNCH_SPEED = 3.2F;

    /**
     * Damage before the tech multiplier. [stated] 6, "if 6 adds up to be too deadly to a player we can lower it to 4".
     */
    public static double BASE_DAMAGE = 6.0D;

    /** [stated] "Max at full charge. Half at lowest." */
    private static final float MINIMUM_DAMAGE_SCALE = 0.5F;

    /** The tier-scaled damage before the draw is applied. */
    private double chargeBaseDamage;

    private static final Vector3f BURST_COLOUR = new Vector3f(0.94F, 0.16F, 0.16F);

    public PlasmaBoltArrowProjectile(EntityType<? extends PlasmaBoltArrowProjectile> entityType, Level level) {
        super(entityType, level);
        configure(null);
    }

    public PlasmaBoltArrowProjectile(Level level, LivingEntity shooter) {
        super(PredatorEntityTypes.PLASMA_BOLT_ARROW.get(), shooter, level, new ItemStack(PredatorItems.PLASMA_BOW.get()), null);
        configure(shooter);
    }

    /**
     * Scales the bolt's damage by how far the bow was drawn.
     * <p>
     * ⚠⚠ DAMAGE, NOT SPEED. Vanilla bows scale VELOCITY with draw, which is right for an arrow and wrong for a charged
     * weapon — a half-drawn bolt drifted across the room. The bolt always flies at {@link #LAUNCH_SPEED} now, and the
     * draw decides how hard it lands: HALF damage at the minimum charge, FULL at a complete draw.
     */
    public void setChargeDamage(float power) {
        var scale = MINIMUM_DAMAGE_SCALE + (1.0F - MINIMUM_DAMAGE_SCALE) * Mth.clamp(power, 0.0F, 1.0F);

        // ⚠ Scales the REMEMBERED base, not whatever the field holds — scaling a scaled value would compound.
        setBaseDamage(chargeBaseDamage * scale);
    }

    private void configure(LivingEntity shooter) {
        // ⚠ TECH, not thrown: the bow supplies the charge, so tier here means better equipment.
        chargeBaseDamage = YautjaTier.scaleTech(shooter, (float) BASE_DAMAGE);

        setBaseDamage(chargeBaseDamage);
        setSoundEvent(SoundEvents.GENERIC_EXTINGUISH_FIRE);

        // ⚠ NEVER PICKED UP. There is no bolt item to give back.
        pickup = Pickup.DISALLOWED;
    }

    /** A bolt is energy: it does not arc. */
    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        // [stated, agreed] a true 6 before tier scaling — see hitFlat. It was hitting for ~20 at Youngblood and ~52 at
        // Clan Leader, and a player's full draw for ~20 plus the crit bonus.
        hitFlat(result);
        burst();
    }

    /**
     * Makes vanilla's arrow hit deal exactly {@code getBaseDamage()}, flat.
     * <p>
     * 🚨🚨 VANILLA MULTIPLIES AN ARROW'S DAMAGE BY ITS SPEED — AbstractArrow.onHitEntity deals ceil(speed x base). So a
     * "base" of 6 flying at 3.2 hit for ~20, and every number set on this projectile was silently ~2-3x what was meant.
     * Setting the base to (intended / current speed) for the duration of the hit makes that product come out to exactly
     * the intended damage, whatever the projectile has slowed to — then the real base is put back.
     * <p>
     * ⚠ The random crit-arrow bonus vanilla adds on top (up to half the damage again, plus two) is suppressed for the
     * hit too, so the number is the number.
     */
    private void hitFlat(EntityHitResult result) {
        var intended = getBaseDamage();
        var speed = getDeltaMovement().length();
        var crit = isCritArrow();

        setBaseDamage(speed > 1.0E-3D ? intended / speed : intended);
        setCritArrow(false);

        try {
            super.onHitEntity(result);
        } finally {
            setBaseDamage(intended);
            setCritArrow(crit);
        }
    }

    /**
     * ⚠⚠ BURNS THROUGH FOLIAGE, STOPS ON EVERYTHING ELSE. Same ruling the plasma caster follows: a leaf is destroyed
     * and the bolt CARRIES ON, so it can reach prey in a canopy while punching a hole through the branches. Dropless,
     * because dropping saplings from every shot would turn a weapon into a harvesting tool.
     */
    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        if (!level().isClientSide && level().getBlockState(result.getBlockPos()).is(BlockTags.LEAVES)) {
            level().destroyBlock(result.getBlockPos(), false);
            spawnBurstParticles(Vec3.atCenterOf(result.getBlockPos()), 4);

            return;
        }

        super.onHitBlock(result);
        burst();
    }

    /** ⚠ No lodging: an arrow would stick here and wait to be collected. This one is simply gone. */
    private void burst() {
        if (!level().isClientSide) {
            spawnBurstParticles(position(), 12);
            // [stated] "hit plays when it actually hits something and bursts into particles".
            level()
                .playSound(
                    null,
                    blockPosition(),
                    PredatorSoundEvents.PLASMA_BOW_HIT.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    com.predator.common.gameplay.whip.WhipCord.variedPitch(random, 0.12F)
                );
        }

        discard();
    }

    private void spawnBurstParticles(Vec3 at, int count) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new DustParticleOptions(BURST_COLOUR, 1.4F), at.x, at.y, at.z, count, 0.18, 0.18, 0.18, 0.02);
            serverLevel.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, count / 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** ⚠ It must never sit in the world waiting to be picked up, so a bolt that somehow lands simply expires. */
    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && inGround) {
            burst();
        }
    }
}
