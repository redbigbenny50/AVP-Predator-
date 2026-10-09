package com.predator.common.gameplay.whip;

import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The lash: the cord in flight during a left-click swing.
 * <p>
 * It is an ENTITY purely so the cord exists on every client without a packet of its own and so the server has an
 * obvious place to sample hits from — it has no collision, no gravity and no hitbox worth the name, and it removes
 * itself when the swing ends.
 * <h2>Hit detection is along the CORD, not at the player</h2> Every tick it walks the same {@link WhipCord#pointAt} the
 * renderer draws and damages anything within {@link WhipTuning#LASH_HIT_RADIUS} of any sample, once per entity per
 * swing. That is what makes it a whip: a line of enemies to your side all take it, and something directly beyond the
 * tip does not.
 */
public class WhipLashEntity extends Entity {

    private static final EntityDataAccessor<Integer> OWNER_ID =
        SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);

    private final Set<Integer> alreadyHit = new HashSet<>();

    private ItemStack weapon = ItemStack.EMPTY;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public WhipLashEntity(Level level, LivingEntity owner, ItemStack weapon) {
        this(PredatorEntityTypes.WHIP_LASH.get(), level);
        this.weapon = weapon.copy();
        setPos(owner.getX(), owner.getY(), owner.getZ());
        entityData.set(OWNER_ID, owner.getId());
    }

    public @Nullable LivingEntity owner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();

        var owner = owner();

        if (owner == null || !owner.isAlive() || tickCount > WhipTuning.LASH_TICKS) {
            // ⚠ The cord is home: this is the "rewrapping" moment, and only the entity knows when it arrives.
            if (!level().isClientSide && tickCount > WhipTuning.LASH_TICKS) {
                coil();
            }

            discard();

            return;
        }

        // Ride the owner: the cord leaves the hand wherever the hand is.
        setPos(owner.getX(), owner.getY(), owner.getZ());

        var progress = WhipCord.progress(tickCount, 0.0F);

        if (level().isClientSide) {
            return;
        }

        var origin = WhipCord.handOrigin(owner, 1.0F);
        var direction = owner.getViewVector(1.0F);
        var side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();

        sampleHits(owner, origin, direction, side, progress);

        if (WhipCord.isSnapping(progress)) {
            crack(WhipCord.pointAt(origin, direction, side, progress, 1.0D));
        }
    }

    /** Walks the cord and damages whatever it touches, once each. */
    private void sampleHits(LivingEntity owner, Vec3 origin, Vec3 direction, Vec3 side, float progress) {
        for (var i = 1; i <= WhipCord.SEGMENTS; i++) {
            var along = i / (double) WhipCord.SEGMENTS;
            var point = WhipCord.pointAt(origin, direction, side, progress, along);
            var box = net.minecraft.world.phys.AABB.ofSize(
                point,
                WhipTuning.LASH_HIT_RADIUS * 2,
                WhipTuning.LASH_HIT_RADIUS * 2,
                WhipTuning.LASH_HIT_RADIUS * 2
            );

            for (var victim : level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (victim == owner || !victim.isAlive() || !alreadyHit.add(victim.getId())) {
                    continue;
                }

                hit(owner, victim, (float) along);
            }
        }
    }

    /**
     * ⚠ Uses the OWNER's attack damage source and calls doHurtTarget-style enchantment handling, so Sharpness, Fire
     * Aspect and Looting on the whip all apply — [stated] "let it be enchantable with sword fire enchantment".
     */
    private void hit(LivingEntity owner, LivingEntity victim, float alongCord) {
        var damage = WhipTuning.LASH_DAMAGE + WhipTuning.LASH_TIP_BONUS * alongCord;
        var source = owner instanceof net.minecraft.world.entity.player.Player player
            ? level().damageSources().playerAttack(player)
            : level().damageSources().mobAttack(owner);

        if (level() instanceof ServerLevel serverLevel) {
            damage += EnchantmentHelper.modifyDamage(serverLevel, weapon, victim, source, 0.0F);
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, victim, source, weapon);
        }

        victim.hurt(source, damage);
        sparks(victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), 8);
        level().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    /**
     * The crack at full extension: his sound and a burst of sparks, both at the TIP.
     * <p>
     * ⚠ Played at the tip's position, not the player's, so a whip cracked across a room sounds like it happened over
     * there. [stated] "this is when the tip hits with the particles."
     */
    private void crack(Vec3 tip) {
        level()
            .playSound(
                null,
                tip.x,
                tip.y,
                tip.z,
                PredatorSoundEvents.WHIP_CRACK.get(),
                SoundSource.PLAYERS,
                1.0F,
                WhipCord.variedPitch(random, WhipTuning.SOUND_PITCH_SPREAD)
            );
        sparks(tip, 14);
    }

    /**
     * The coil at the end of the swing.
     * <p>
     * [stated] "it plays when the whip attack ends as it its 'rewrapping' in your hand." ⚠ Fired from the entity's last
     * tick rather than the item, because only the entity knows when the cord is actually home.
     */
    private void coil() {
        var owner = owner();

        if (owner != null) {
            level().playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                PredatorSoundEvents.WHIP_EQUIP.get(),
                SoundSource.PLAYERS,
                0.8F,
                WhipCord.variedPitch(random, WhipTuning.SOUND_PITCH_SPREAD * 0.5F)
            );
        }
    }

    /** [stated] "add a particle effect orange and white like a spark." */
    private void sparks(Vec3 at, int count) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, count, 0.15, 0.15, 0.15, 0.12);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count / 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(OWNER_ID, 0);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.@NotNull CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.@NotNull CompoundTag tag) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }
}
