package com.predator.common.gameplay.whip;

import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The grapple hook: flies out, bites something, then holds while the pull runs.
 * <h2>The one flag that decides everything</h2> Chain of Souls' trick, kept: {@code ANCHORED_ENTITY} being set means "I
 * have a mob" and the mob comes to you; unset means "I have a block" and you go to it. [stated] "enemies would be
 * pulled toward you ... when used on blocks it would pull you toward it ... if it could also lift you up."
 * <p>
 * ⚠ Extends {@code ThrowableItemProjectile}, NOT {@code Projectile}: vanilla's {@code Projectile} constructor is
 * package-private and only looks accessible through NeoForge's access transformer, so extending it compiles in a
 * NeoForge harness and fails in {@code :common}. Every other projectile in this mod extends the throwable for the same
 * reason. Gravity is zeroed below; once anchored it stops dead and holds position so the cord has something to draw to.
 */
public class WhipHookEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> ANCHORED =
        SynchedEntityData.defineId(WhipHookEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> ANCHORED_ENTITY =
        SynchedEntityData.defineId(WhipHookEntity.class, EntityDataSerializers.INT);

    /** Whether the hooked mob is too heavy to yank, so the player is reeled toward IT instead. */
    private static final EntityDataAccessor<Boolean> TOO_HEAVY =
        SynchedEntityData.defineId(WhipHookEntity.class, EntityDataSerializers.BOOLEAN);

    private ItemStack weapon = ItemStack.EMPTY;

    private Vec3 origin = Vec3.ZERO;

    public WhipHookEntity(EntityType<? extends WhipHookEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected @NotNull net.minecraft.world.item.Item getDefaultItem() {
        return com.predator.common.registry.init.item.PredatorItems.WHIP.get();
    }

    /** ⚠ The hook flies flat: it is a thrown line, not a lobbed object. */
    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    public WhipHookEntity(Level level, LivingEntity owner, ItemStack weapon) {
        this(PredatorEntityTypes.WHIP_HOOK.get(), level);
        this.weapon = weapon.copy();
        setOwner(owner);
        setPos(WhipCord.handOrigin(owner, 1.0F));
        origin = position();
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, WhipTuning.HOOK_SPEED, 0.0F);
    }

    public boolean isAnchored() {
        return entityData.get(ANCHORED);
    }

    /** {@return the mob this hook has, or null if it is holding a block (or nothing yet)} */
    public @Nullable LivingEntity anchoredEntity() {
        var id = entityData.get(ANCHORED_ENTITY);

        return id != 0 && level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();

        var owner = getOwner();

        if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
            if (!level().isClientSide) {
                discard();
            }

            return;
        }

        // ⚠ Client-side the owner may not have resolved on the very first tick; do not discard over it.
        if (isAnchored()) {
            var hooked = anchoredEntity();

            if (hooked != null) {
                if (!hooked.isAlive()) {
                    discard();
                    return;
                }

                // Ride the mob so the cord stays attached to it while it is dragged in.
                setPos(hooked.getX(), hooked.getY() + hooked.getBbHeight() * 0.5D, hooked.getZ());
            }

            setDeltaMovement(Vec3.ZERO);

            if (living.distanceToSqr(this) > WhipTuning.GRAPPLE_RANGE * WhipTuning.GRAPPLE_RANGE) {
                discard();
            }

            return;
        }

        // ⚠⚠ NO HIT SCAN AND NO MOVE HERE. super.tick() (ThrowableProjectile) already sweeps for a hit, dispatches
        // onHitBlock/onHitEntity and moves the entity. Doing it again ran the hook at double speed and hit twice.
        //
        // 🚨🚨 AND THE RANGE CHECK IS SERVER-ONLY. `origin` is assigned in the SERVER constructor; on the client the
        // entity is built through the (type, level) factory, so origin stays (0,0,0) — and "further than 32 blocks
        // from the world origin" is true almost everywhere, so THE CLIENT DISCARDED THE HOOK ON ITS FIRST TICK.
        // The server kept reeling and the client had no entity to draw a cord from: [stated] "grapple has no chain
        // at all", while the pull itself worked.
        if (!level().isClientSide && origin.distanceToSqr(position()) > WhipTuning.GRAPPLE_RANGE * WhipTuning.GRAPPLE_RANGE) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        if (level().isClientSide || !(result.getEntity() instanceof LivingEntity victim) || !(getOwner() instanceof LivingEntity owner)) {
            return;
        }

        var source = owner instanceof Player player
            ? level().damageSources().playerAttack(player)
            : level().damageSources().mobAttack(owner);
        var damage = WhipTuning.HOOK_DAMAGE;

        if (level() instanceof ServerLevel serverLevel) {
            damage += EnchantmentHelper.modifyDamage(serverLevel, weapon, victim, source, 0.0F);
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, victim, source, weapon);
        }

        victim.hurt(source, damage);
        anchorTo(victim);
        sparks(position(), 10);
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.9F, 1.2F);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        if (level().isClientSide) {
            return;
        }

        setPos(Vec3.atCenterOf(result.getBlockPos()).add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.5D)));
        entityData.set(ANCHORED, true);
        entityData.set(ANCHORED_ENTITY, 0);
        sparks(position(), 8);
        // ⚠ No bite sound: GRAPPLE_CHAIN_DEPLOY is already playing and covers the whole journey.
    }

    /**
     * ⚠ HEAVY THINGS PULL YOU. A queen or a harbinger will not be dragged anywhere; rather than the hook simply
     * failing, the anchor holds and the reel treats it like a block — you get pulled toward IT. A hazard, not a bug.
     */
    private void anchorTo(LivingEntity victim) {
        var resistance = victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        entityData.set(ANCHORED, true);
        entityData.set(ANCHORED_ENTITY, victim.getId());
        entityData.set(TOO_HEAVY, resistance >= WhipTuning.YANK_RESIST_THRESHOLD);
    }

    /** {@return whether the hooked mob is too heavy to yank, so the player is reeled in instead} */
    public boolean isTooHeavy() {
        return entityData.get(TOO_HEAVY);
    }

    private void sparks(Vec3 at, int count) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, count, 0.15, 0.15, 0.15, 0.1);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count / 2, 0.1, 0.1, 0.1, 0.04);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // ⚠⚠ SUPER FIRST, ALWAYS. ThrowableItemProjectile defines its own synched value (the item stack, id 8);
        // overriding this without calling super left that one undefined and vanilla refused to build the entity —
        // "has not defined synched data value 8" — so the hook never spawned and only the lash's sparks showed.
        super.defineSynchedData(builder);

        builder.define(ANCHORED, false);
        builder.define(ANCHORED_ENTITY, 0);
        builder.define(TOO_HEAVY, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }
}
