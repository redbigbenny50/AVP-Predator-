package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.hunt.FreezePatches;
import com.predator.common.gameplay.item.grenade.GrenadeKind;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * A thrown yautja grenade (see {@link GrenadeKind} for the five).
 * <ul>
 * <li>Every kind but sticky goes off where it lands — on the first block or mob it hits.</li>
 * <li>Sticky catches on any block face or on a mob and rides it, beeps (the trip mine's three-second timer beep) and
 * goes off three seconds later.</li>
 * <li>A {@link #setBreach breach} grenade — the Hunter's, thrown at a wall it cannot get through — also takes out every
 * block within {@link #BREACH_RADIUS} that the yautja's break blacklist protects (metal, avp_human's industrial
 * families, the {@code yautja_unbreakable} tag), whatever its blast resistance. Truly unbreakable blocks (bedrock,
 * barriers) are never touched.</li>
 * </ul>
 * ⚠ Removal is explicit: {@code Projectile.onHit} only dispatches, it never discards, so every path that ends the
 * grenade calls {@link #discard} itself.
 */
public class YautjaGrenadeProjectile extends ThrowableItemProjectile {

    /** TNT. */
    public static float EXPLOSIVE_POWER = 4.0F;

    /** [stated] fire and freeze are "half as strong". */
    public static float HALF_POWER = 2.0F;

    /** avp_human's irradiated grenade. */
    public static float IRRADIATED_POWER = 9.0F;

    /** Sticky: three seconds from catching to going off — the length of the timer beep. */
    public static int STICKY_FUSE_TICKS = 60;

    /** Radius of the fire patch and the ice patch. */
    public static int PATCH_RADIUS = 3;

    /** The ice lasts about ten seconds. */
    public static int ICE_TICKS = 200;

    /** The radiation cloud: radius and life (15 s, shrinking to nothing). */
    public static float CLOUD_RADIUS = 8.0F;

    public static int CLOUD_TICKS = 300;

    public static int BREACH_RADIUS = 2;

    /** A grenade that never lands goes off anyway after this. */
    public static int MAX_FLIGHT_TICKS = 200;

    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(
        YautjaGrenadeProjectile.class,
        EntityDataSerializers.BOOLEAN
    );

    private GrenadeKind kind = GrenadeKind.EXPLOSIVE;

    private boolean breach;

    private int fuse = -1;

    private int stuckToId = -1;

    private Vec3 stuckOffset = Vec3.ZERO;

    public YautjaGrenadeProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public YautjaGrenadeProjectile(Level level, LivingEntity thrower, GrenadeKind kind) {
        super(PredatorEntityTypes.YAUTJA_GRENADE.get(), thrower, level);
        this.kind = kind;
    }

    public void setBreach(boolean breach) {
        this.breach = breach;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUCK, false);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        // 🚨🚨 kind IS NULL HERE THE FIRST TIME. Vanilla calls getDefaultItem from ThrowableItemProjectile's
        // defineSynchedData, which runs inside the Entity CONSTRUCTOR — before this class's field initialiser has set
        // kind to EXPLOSIVE. Every grenade, thrown by a player or a yautja, crashed the server on the spot with a
        // NullPointerException (Oct 4 crash report: a Hunter's first throw). The item shown is replaced by setItem
        // straight after construction anyway; this only has to not crash.
        if (kind == null) {
            return PredatorItems.PRED_GRENADE_EXPLOSIVE.get();
        }

        return switch (kind) {
            case FIRE -> PredatorItems.PRED_GRENADE_FIRE.get();
            case STICKY -> PredatorItems.PRED_GRENADE_STICKY.get();
            case FREEZE -> PredatorItems.PRED_GRENADE_FREEZE.get();
            case IRRADIATED -> PredatorItems.PRED_GRENADE_IRRADIATED.get();
            default -> PredatorItems.PRED_GRENADE_EXPLOSIVE.get();
        };
    }

    @Override
    public void tick() {
        if (entityData.get(STUCK)) {
            tickStuck();
            return;
        }

        super.tick();

        if (!level().isClientSide && tickCount > MAX_FLIGHT_TICKS) {
            detonate();
        }
    }

    /** Stuck: no flight, no gravity. Ride the mob it caught, count the fuse down, go off. */
    private void tickStuck() {
        setDeltaMovement(Vec3.ZERO);

        if (level().isClientSide) {
            if (tickCount % 4 == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(), 0.0, 0.02, 0.0);
            }

            return;
        }

        if (stuckToId >= 0) {
            var host = level().getEntity(stuckToId);

            if (host != null && host.isAlive()) {
                setPos(host.position().add(stuckOffset));
            } else {
                // What it was stuck to is gone; it drops where it was and still goes off on time.
                stuckToId = -1;
            }
        }

        if (--fuse <= 0) {
            detonate();
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult hit) {
        super.onHitBlock(hit);

        if (level().isClientSide || isRemoved()) {
            return;
        }

        if (kind == GrenadeKind.STICKY) {
            // Nudged just off the face it hit, so it sits on the surface rather than inside the block.
            var face = hit.getDirection();
            var at = hit.getLocation().add(face.getStepX() * 0.05, face.getStepY() * 0.05, face.getStepZ() * 0.05);

            stick(at, -1, Vec3.ZERO);
            return;
        }

        setPos(hit.getLocation());
        detonate();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        super.onHitEntity(hit);

        if (level().isClientSide || isRemoved()) {
            return;
        }

        var target = hit.getEntity();

        if (kind == GrenadeKind.STICKY) {
            stick(position(), target.getId(), position().subtract(target.position()));
            return;
        }

        detonate();
    }

    private void stick(Vec3 at, int hostId, Vec3 offset) {
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        stuckToId = hostId;
        stuckOffset = offset;
        fuse = STICKY_FUSE_TICKS;
        entityData.set(STUCK, true);
        level().playSound(null, BlockPos.containing(at), PredatorSoundEvents.EXPLOSIVE_TIMER_BEEP.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** Goes off: the blast, then what the kind adds, then the breach if it is one. */
    private void detonate() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }

        discard();

        var at = position();

        switch (kind) {
            case FIRE -> {
                level.explode(this, at.x, at.y, at.z, HALF_POWER, true, Level.ExplosionInteraction.TNT);
                firePatch(level, BlockPos.containing(at));
            }
            case FREEZE -> {
                level.explode(this, at.x, at.y, at.z, HALF_POWER, Level.ExplosionInteraction.TNT);
                FreezePatches.freeze(level, BlockPos.containing(at), PATCH_RADIUS, ICE_TICKS);

                // The blast itself: whatever it left at 10% or less, and any passive mob caught in it, freezes solid.
                for (var caught : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PATCH_RADIUS + 1.0))) {
                    com.predator.common.gameplay.freeze.FreezeBridge.tryFreezeSolid(caught);
                }
            }
            case IRRADIATED -> {
                level.explode(this, at.x, at.y, at.z, IRRADIATED_POWER, Level.ExplosionInteraction.BLOCK);
                radiationCloud(level, at);
            }
            default -> level.explode(this, at.x, at.y, at.z, EXPLOSIVE_POWER, Level.ExplosionInteraction.TNT);
        }

        if (breach) {
            breach(level, BlockPos.containing(at));
        }
    }

    /**
     * [stated] the fire "is allowed to ignite/spread like normal fire" — it is ordinary fire, placed where fire can
     * sit.
     */
    private void firePatch(ServerLevel level, BlockPos centre) {
        var random = level.getRandom();

        for (
            var pos : BlockPos.betweenClosed(centre.offset(-PATCH_RADIUS, -2, -PATCH_RADIUS), centre.offset(PATCH_RADIUS, 2, PATCH_RADIUS))
        ) {
            if (pos.distSqr(centre) > PATCH_RADIUS * PATCH_RADIUS || random.nextFloat() > 0.6F) {
                continue;
            }

            if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, net.minecraft.core.Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
    }

    /**
     * avp_human's radiation cloud. ⚠ No compile dependency: the effect is looked up by id, so without avp_human the
     * cloud poisons instead (the irradiated grenade can only be crafted at all with a mod that provides uranium).
     */
    private void radiationCloud(ServerLevel level, Vec3 at) {
        var cloud = new AreaEffectCloud(level, at.x, at.y, at.z);

        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_TICKS);
        cloud.setRadiusPerTick(-CLOUD_RADIUS / CLOUD_TICKS);
        cloud.setParticle(ParticleTypes.ASH);

        if (getOwner() instanceof LivingEntity owner) {
            cloud.setOwner(owner);
        }

        var radiation = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("avp_human", "radiation"));

        if (radiation.isPresent()) {
            // Amplifier 1 and 2.5 minutes: the dose avp_human's own irradiated grenade cloud hands out.
            cloud.addEffect(new MobEffectInstance(radiation.get(), 3000, 1));
        } else {
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        }

        level.addFreshEntity(cloud);
    }

    /**
     * The Hunter's wall breach: every blacklisted block near the blast goes, whatever its blast resistance. [stated]
     * "make sure the items on the blacklist it cant break like the metal blocks can infact blow up".
     */
    private void breach(ServerLevel level, BlockPos centre) {
        for (
            var pos : BlockPos.betweenClosed(
                centre.offset(-BREACH_RADIUS, -BREACH_RADIUS, -BREACH_RADIUS),
                centre.offset(BREACH_RADIUS, BREACH_RADIUS, BREACH_RADIUS)
            )
        ) {
            if (pos.distSqr(centre) > BREACH_RADIUS * BREACH_RADIUS) {
                continue;
            }

            var state = level.getBlockState(pos);

            if (
                !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F
                    && com.predator.common.gameplay.entity.living.yautja.path.YautjaPathing.isBlacklisted(state)
            ) {
                level.destroyBlock(pos, true, this);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Kind", kind.id());
        tag.putBoolean("Breach", breach);
        tag.putBoolean("Stuck", entityData.get(STUCK));
        tag.putInt("Fuse", fuse);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kind = GrenadeKind.byId(tag.getString("Kind"));
        breach = tag.getBoolean("Breach");
        entityData.set(STUCK, tag.getBoolean("Stuck"));
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;

        // A grenade stuck to a mob is re-attached to nothing after a reload: it stays where it is and goes off on time.
        stuckToId = -1;
    }
}
