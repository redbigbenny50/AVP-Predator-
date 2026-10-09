package com.predator.common.gameplay.item.battleaxe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The battleaxe's ground slam, shared by the player and the yautja so both hit exactly the same way.
 * <p>
 * [stated] "a ground slam with the axes that has alot of particles fly up of dirt and stone and it pushes enemies back
 * around the yaujta in a circle", "4 block seems fine with a strong knockback and a boosted attack".
 */
public final class BattleaxeSlam {

    /** [stated] 4 blocks. */
    public static double RADIUS = 4.0D;

    /** Horizontal shove at the centre, falling off to the rim. */
    public static double KNOCKBACK = 1.6D;

    /** Upward lift, so the shove reads as a blast rather than a push. */
    public static double LIFT = 0.45D;

    /** The slam's damage relative to one swing — [stated] "a boosted attack". */
    public static float DAMAGE_MULTIPLIER = 1.4F;

    private BattleaxeSlam() {
        throw new UnsupportedOperationException();
    }

    /** Performs the slam centred on {@code user}. {@code swingDamage} is one ordinary swing's damage. */
    public static void perform(ServerLevel level, LivingEntity user, float swingDamage) {
        var centre = user.position();
        var damage = swingDamage * DAMAGE_MULTIPLIER;
        var box = new AABB(centre, centre).inflate(RADIUS, 2.0D, RADIUS);

        for (var victim : level.getEntitiesOfClass(LivingEntity.class, box, living -> living != user && living.isAlive())) {
            var offset = victim.position().subtract(centre);
            var distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);

            if (distance > RADIUS) {
                continue;
            }

            victim.hurt(user.damageSources().mobAttack(user), damage);

            // ⚠ Outward from the slam, strongest at the centre — a ring blast, not a single-direction shove.
            // A victim standing exactly on the user gets pushed along the user's facing so it still moves.
            var away = distance < 1.0E-3D
                ? user.getLookAngle().multiply(1, 0, 1).normalize()
                : new Vec3(offset.x / distance, 0, offset.z / distance);
            var strength = KNOCKBACK * (1.0D - 0.5D * (distance / RADIUS));

            // ⚠ AFTER the hurt, or vanilla's own hit knockback overwrites it.
            victim.setDeltaMovement(away.x * strength, LIFT, away.z * strength);
            victim.hurtMarked = true;
        }

        burst(level, user);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9F, 0.7F);
    }

    /**
     * Throws up the ground the user is standing on, in a ring.
     * <p>
     * ⚠ THE PARTICLES ARE THE BLOCK BENEATH EACH POINT — [stated] "dirt and stone". A slam on grass throws grass and
     * dirt, on stone throws stone, on sand throws sand, without a list of blocks to keep in sync.
     */
    private static void burst(ServerLevel level, LivingEntity user) {
        var centre = user.position();

        for (var ring = 1; ring <= (int) RADIUS; ring++) {
            var points = 10 + ring * 6;

            for (var i = 0; i < points; i++) {
                var angle = (Math.PI * 2 * i) / points;
                var x = centre.x + Math.cos(angle) * ring;
                var z = centre.z + Math.sin(angle) * ring;
                var ground = BlockPos.containing(x, centre.y - 0.5D, z);
                var state = level.getBlockState(ground);

                if (state.isAir()) {
                    continue;
                }

                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, centre.y + 0.1D, z, 6, 0.2D, 0.1D, 0.2D, 0.35D);
            }
        }
    }
}
