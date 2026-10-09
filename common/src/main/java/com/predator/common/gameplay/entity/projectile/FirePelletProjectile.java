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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * The gauntlet's fire pellet: a ghast fireball's little cousin. Flies dead straight at full-arrow speed, then drops;
 * ignites whatever it touches.
 * <h2>His spec</h2> [stated] "you fire them out of the gauntlet like bullets and they ignite whatever it touches so
 * trees leaves mobs etc. anything thats ignitable ... surrounded in particles of orange, yellow, and white and has a
 * small trail ... flys straight for a good distance before falling to the ground. think like a fully charged arrow but
 * its a straight trajectory." Physical reference: a ghast fireball, much smaller.
 * <h2>Damage, as he settled it</h2> An IMPACT hit of {@link #IMPACT_DAMAGE} as ordinary thrown-projectile damage —
 * which is the part that still lands on a fire-resistant player, since Fire Resistance only cancels fire-type damage —
 * plus vanilla's own burning for {@link #BURN_SECONDS}. [stated] "i didnt want to make a new damage type i was saying
 * the impact would hurt fire resistant players still and lets have it burn for only 3 seconds." No custom damage type,
 * no mob effect, nothing rides the target: hit, burst, gone.
 * <h2>⚠ Dials are non-final statics</h2> A {@code static final} primitive is inlined by javac and cannot be
 * hot-swapped.
 */
public class FirePelletProjectile extends ThrowableItemProjectile {

    /** Ticks of zero-gravity flight before it starts to drop. 40 ticks at 3.0 is roughly a hundred blocks. */
    public static int STRAIGHT_TICKS = 40;

    /** Gravity once it starts to fall; vanilla's arrow uses 0.05. */
    public static double FALL_GRAVITY = 0.05;

    /** On contact, as thrown-projectile damage. A bit under the dart's 4 (base 1.5 x speed 2.6, ceiled). */
    public static float IMPACT_DAMAGE = 3.0F;

    /** Vanilla burning, which is 1 damage per second for ordinary targets. */
    public static int BURN_SECONDS = 3;

    /** Airborne lifetime cap, so a shot into the sky does not fly forever. */
    public static int MAX_FLIGHT_TICKS = 200;

    private static final Vector3f ORANGE = new Vector3f(1.0F, 0.5F, 0.1F);

    private static final Vector3f YELLOW = new Vector3f(1.0F, 0.85F, 0.2F);

    private static final Vector3f WHITE = new Vector3f(1.0F, 1.0F, 1.0F);

    public FirePelletProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public FirePelletProjectile(Level level, LivingEntity shooter) {
        super(PredatorEntityTypes.FIRE_PELLET.get(), shooter, level);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return PredatorItems.FIRE_PELLET.get();
    }

    /** ⚠ Straight first, then a normal fall. ThrowableProjectile's own 0.99 air drag still applies throughout. */
    @Override
    protected double getDefaultGravity() {
        return tickCount < STRAIGHT_TICKS ? 0.0 : FALL_GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnTrail();
            return;
        }

        if (isInWater()) {
            fizzle();
            return;
        }

        if (tickCount > MAX_FLIGHT_TICKS) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide) {
            return;
        }

        var entity = result.getEntity();

        // ⚠ TECH, not thrown: the gauntlet fires it — see YautjaTier.techDamage. A player's pellet is unchanged.
        entity.hurt(damageSources().thrown(this, getOwner()), YautjaTier.scaleTech(getOwner(), IMPACT_DAMAGE));

        // ⚠ Fire-immune mobs (blazes and kin) take the impact and nothing more.
        if (!entity.fireImmune()) {
            entity.igniteForSeconds(BURN_SECONDS);
        }

        burst();
        discard();
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);

        if (level().isClientSide) {
            return;
        }

        // ⚠ "Anything ignitable": vanilla's own rule for where fire may exist — flammable neighbour, or a solid top.
        var firePos = result.getBlockPos().relative(result.getDirection());

        if (BaseFireBlock.canBePlacedAt(level(), firePos, result.getDirection())) {
            level().setBlockAndUpdate(firePos, BaseFireBlock.getState(level(), firePos));
        }

        burst();
        discard();
    }

    /** Orange, yellow and white dust around the pellet, and a small flame trail a little way behind it. */
    private void spawnTrail() {
        var random = level().random;
        var motion = getDeltaMovement();

        for (var color : new Vector3f[] { ORANGE, YELLOW, WHITE }) {
            level().addParticle(
                new DustParticleOptions(color, 0.6F),
                getX() + (random.nextDouble() - 0.5) * 0.25,
                getY() + (random.nextDouble() - 0.5) * 0.25,
                getZ() + (random.nextDouble() - 0.5) * 0.25,
                0.0,
                0.0,
                0.0
            );
        }

        level().addParticle(
            ParticleTypes.SMALL_FLAME,
            getX() - motion.x * 0.5,
            getY() - motion.y * 0.5,
            getZ() - motion.z * 0.5,
            0.0,
            0.0,
            0.0
        );
    }

    private void burst() {
        // Both hit paths call this, so the impact is heard whether it struck a mob or a wall.
        level().playSound(null, getX(), getY(), getZ(), PredatorSoundEvents.PROJECTILE_PELLET_HIT.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 10, 0.15, 0.15, 0.15, 0.02);
            serverLevel.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void fizzle() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.01);
        }

        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.5F, 1.2F);
        discard();
    }
}
