package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.net.PredatorNet;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * The net fired from a yautja's gauntlet.
 * <h2>What it does and, more importantly, what it does not</h2> Catching a mob makes it immobile and nothing else. It
 * does NOT drag, tether or move anything — avp_alien's capture chain already does all of that, and his ruling is that
 * the two stay separate: "the nets job is to make the mob immobile while the chain attaches to the mob itself as
 * normal."
 * <h2>⚠ Discards on every terminal path</h2> Nothing in the throwable hierarchy discards a projectile on impact —
 * {@code ThrowableProjectile} does not override {@code onHit}, and {@code Projectile.onHitBlock} only runs the block's
 * own interaction. A handler that forgets this leaves the net flying, striking again every tick. That mistake shipped
 * once already, as a trail of shuriken across a whole field.
 */
public class NetProjectile extends ThrowableItemProjectile implements LeafPiercing {

    public NetProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public NetProjectile(Level level, LivingEntity thrower) {
        super(PredatorEntityTypes.NET.get(), thrower, level);

        this.recoverable = !(thrower instanceof com.predator.common.gameplay.entity.living.yautja.Yautja);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return PredatorItems.NET.get();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide) {
            return;
        }

        if (result.getEntity() instanceof Mob mob && PredatorNet.canBeNetted(mob)) {
            PredatorNet.capture(mob);
            splat(PredatorSoundEvents.PROJECTILE_NET_CATCH.get());
        } else {
            // ⚠ A refused target still drops the net rather than deleting it. Bouncing off a wither should cost the
            // player a net to pick up, not an item that quietly ceased to exist.
            dropAsItem();
        }

        discard();
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);

        if (!level().isClientSide) {
            dropAsItem();
            splat(SoundEvents.LEASH_KNOT_BREAK);
        }

        discard();
    }

    /** ⚠ Server-side spawn so everyone nearby sees it, not just the thrower. */
    private void splat(net.minecraft.sounds.SoundEvent sound) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 12, 0.25, 0.25, 0.25, 0.0);
            serverLevel.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
    }

    private void dropAsItem() {
        // ⚠⚠ A YAUTJA'S NET IS NOT RECOVERED. It used to come back as an item on every capture and every miss — and a
        // yautja's nets are infinite, so each one it threw was a free net. [stated] "same for darts and nets."
        if (!recoverable) {
            return;
        }

        spawnAtLocation(new ItemStack(PredatorItems.NET.get()));
    }

    /** False for a net thrown by a yautja. ⚠ Saved, so a net mid-flight across a reload keeps its origin. */
    private boolean recoverable = true;

    private static final String RECOVERABLE_TAG = "Recoverable";

    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(RECOVERABLE_TAG, recoverable);
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // ⚠ Absent on a net saved before this existed — default to recoverable, which is what it always was.
        this.recoverable = !tag.contains(RECOVERABLE_TAG) || tag.getBoolean(RECOVERABLE_TAG);
    }
}
