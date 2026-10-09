package com.predator.common.gameplay.entity.projectile;

import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The plasma shuriken — a hybrid of the smart disc and the shuriken, fired from the gauntlet.
 * <h2>His rulings</h2>
 * <ul>
 * <li>[stated] "fires from the gauntlet and homes in towards a target."</li>
 * <li>[stated] "unlike the smart disc it only locks onto one target it doesnt bounce if the target ducks behind
 * something then it hits that one thing and returns or breaks." — ONE target, locked at launch; whatever it strikes
 * first, target or cover, is its only hit.</li>
 * <li>[stated] "it also ignites the target on fire if it hits something igniteable it will set fire to it."</li>
 * <li>[stated] "it returns to its user but it has a 20% chance to break. if it breaks it drops two veritanium shards."
 * — since lowered to 10% ([stated] "please change it from 20% breaking to 10%") — [stated] "shurikens 20% break is when
 * it makes a hit".</li>
 * <li>[stated] "the plasma shuriken is horizonal like the smart disc" — drawn by the same flat
 * SpinningItemRenderer.</li>
 * <li>[stated] "when it flies add red particles too it like the plasma arrow. you should see it coming." — the plasma
 * bolt's own red (0.94, 0.16, 0.16), every tick, out and back.</li>
 * </ul>
 * ⚠ Homeward it passes through everything: it can no longer hit entities, and block hits are ignored, so it never
 * stalls against a wall between it and its owner. ⚠ The gauntlet SPENDS one to fire it (a consumable ammo); a return
 * puts it back in the owner's inventory. A creative player's gauntlet does not spend one, so nothing is handed back to
 * them.
 */
public class PlasmaShurikenProjectile extends ThrowableItemProjectile {

    public static float LAUNCH_SPEED = 1.6F;

    public static float DAMAGE = 6.0F;

    /**
     * 10% on a hit — [stated] "i had the plasma shuriken break 6 times in a row please change it from 20% breaking to
     * 10%" (was 20%). ⚠ Rolled exactly ONCE per throw: afterHit runs on the first hit only, server-side, and a
     * returning shuriken can hit nothing. In CREATIVE a successful return hands nothing back (creative never spent
     * one), so it simply vanishes as it arrives — a real break is the item-break sound AND two veritanium shards on the
     * ground.
     */
    public static float BREAK_CHANCE = 0.1F;

    /** [stated] two veritanium shards when it breaks. */
    public static int SHARDS_ON_BREAK = 2;

    public static float IGNITE_SECONDS = 5.0F;

    /** How far it looks for something to lock onto, and how tight a cone around the aim. */
    private static final double LOCK_RANGE = 32.0D;

    private static final double LOCK_CONE_COS = Math.cos(Math.toRadians(20.0D));

    /** How hard it bends toward the target each tick (0 = straight, 1 = snap). */
    private static final double HOMING_TURN = 0.22D;

    private static final double RETURN_SPEED = 1.1D;

    /** Never reaching anything, it gives up and comes home after this. */
    private static final int MAX_OUTBOUND_TICKS = 80;

    /** The plasma bolt's own red. */
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.94F, 0.16F, 0.16F), 1.0F);

    private @Nullable UUID targetId;

    private boolean returning;

    public PlasmaShurikenProjectile(EntityType<? extends PlasmaShurikenProjectile> type, Level level) {
        super(type, level);
    }

    public PlasmaShurikenProjectile(Level level, LivingEntity owner) {
        super(PredatorEntityTypes.PLASMA_SHURIKEN.get(), owner, level);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return PredatorItems.PLASMA_SHURIKEN.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    /**
     * Locks the ONE target it will chase: the living thing nearest the aim, within {@link #LOCK_RANGE} and a 20-degree
     * cone of the owner's look. None there, and it simply flies straight. Call after it has been aimed.
     */
    public void lockTarget() {
        if (!(getOwner() instanceof LivingEntity owner)) {
            return;
        }

        var eye = owner.getEyePosition();
        var look = owner.getLookAngle();
        var best = -1.0D;

        for (var candidate : level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(LOCK_RANGE))) {
            if (candidate == owner || !candidate.isAlive() || candidate.isAlliedTo(owner) || candidate.isSpectator()) {
                continue;
            }

            var toCandidate = candidate.getBoundingBox().getCenter().subtract(eye);
            var distance = toCandidate.length();

            if (distance > LOCK_RANGE || distance < 1.0E-3D) {
                continue;
            }

            var alignment = toCandidate.scale(1.0D / distance).dot(look);

            if (alignment >= LOCK_CONE_COS && alignment > best && owner.hasLineOfSight(candidate)) {
                best = alignment;
                targetId = candidate.getUUID();
            }
        }
    }

    /** Locks onto a KNOWN target — a yautja aims at its own target rather than along its look. */
    public void lockOn(LivingEntity target) {
        targetId = target.getUUID();
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            level().addParticle(TRAIL, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);

            return;
        }

        if (returning) {
            steerHome();

            return;
        }

        if (tickCount > MAX_OUTBOUND_TICKS) {
            startReturn();

            return;
        }

        if (
            targetId != null && level() instanceof ServerLevel serverLevel && serverLevel.getEntity(targetId) instanceof LivingEntity target
                && target.isAlive()
        ) {
            var speed = Math.max(getDeltaMovement().length(), LAUNCH_SPEED);
            var toTarget = target.getBoundingBox().getCenter().subtract(position()).normalize();
            var heading = getDeltaMovement().normalize().lerp(toTarget, HOMING_TURN).normalize();

            setDeltaMovement(heading.scale(speed));
        }
    }

    /** ⚠ Homeward it hits nothing. */
    @Override
    protected boolean canHitEntity(@NotNull Entity entity) {
        return !returning && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide || returning) {
            return;
        }

        var victim = result.getEntity();

        // Tech scaling by the thrower's tier for a yautja; a player's is unchanged (YautjaTier.scaleTech).
        victim.hurt(
            damageSources().thrown(this, getOwner()),
            com.predator.common.gameplay.entity.living.yautja.YautjaTier.scaleTech(getOwner(), DAMAGE)
        );
        victim.igniteForSeconds(IGNITE_SECONDS);
        afterHit();
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);

        if (level().isClientSide || returning) {
            return;
        }

        // [stated] "if it hits something igniteable it will set fire to it" — on the face it struck.
        var pos = result.getBlockPos();
        var firePos = pos.relative(result.getDirection());

        if (level().getBlockState(pos).ignitedByLava() && BaseFireBlock.canBePlacedAt(level(), firePos, result.getDirection())) {
            level().setBlock(firePos, BaseFireBlock.getState(level(), firePos), 11);
        }

        afterHit();
    }

    /** [stated] One hit, then 20%: it breaks into two shards. Otherwise it comes home. */
    private void afterHit() {
        if (random.nextFloat() < BREAK_CHANCE) {
            level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, getSoundSource(), 0.8F, 0.9F);

            // ⚠ A YAUTJA'S never drops shards — the same rule as its ordinary shuriken (its ammunition is never spent,
            // so its breaks cannot be a free shard farm). A player's drops the two.
            var shards = getOwner() instanceof Player ? SHARDS_ON_BREAK : 0;

            for (var shard = 0; shard < shards; shard++) {
                level().addFreshEntity(
                    new ItemEntity(level(), getX(), getY(), getZ(), new ItemStack(PredatorItems.VERITANIUM_SHARD.get()))
                );
            }

            discard();

            return;
        }

        startReturn();
    }

    private void startReturn() {
        returning = true;
        steerHome();
    }

    private void steerHome() {
        var owner = getOwner();

        if (!(owner instanceof LivingEntity living) || !living.isAlive() || owner.level() != level()) {
            // No one to come home to: it drops where it is.
            spawnAtLocation(getItem().copyWithCount(1));
            discard();

            return;
        }

        var toOwner = living.getEyePosition().subtract(position());

        if (toOwner.length() < 1.5D) {
            catchBy(living);

            return;
        }

        setDeltaMovement(toOwner.normalize().scale(RETURN_SPEED));
    }

    private void catchBy(LivingEntity owner) {
        level().playSound(null, owner.blockPosition(), PredatorSoundEvents.SMART_DISC_RETURN.get(), getSoundSource(), 1.0F, 1.1F);

        if (owner instanceof Player player && !player.getAbilities().instabuild) {
            var stack = getItem().copyWithCount(1);

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        discard();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Returning", returning);

        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        returning = tag.getBoolean("Returning");
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }
}
