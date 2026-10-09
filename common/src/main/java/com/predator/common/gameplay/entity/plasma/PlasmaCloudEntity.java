package com.predator.common.gameplay.entity.plasma;

import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class PlasmaCloudEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_RADIUS = SynchedEntityData.defineId(
        PlasmaCloudEntity.class,
        EntityDataSerializers.INT
    );

    private static final EntityDataAccessor<Long> DATA_SEED = SynchedEntityData.defineId(
        PlasmaCloudEntity.class,
        EntityDataSerializers.LONG
    );

    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(
        PlasmaCloudEntity.class,
        EntityDataSerializers.INT
    );

    public PlasmaCloudEntity(EntityType<? extends Entity> entityType, Level level) {
        super(entityType, level);
        noCulling = true;
        setNoGravity(true);
    }

    public PlasmaCloudEntity(Level level, double x, double y, double z) {
        super(PredatorEntityTypes.PLASMA_CLOUD.get(), level);
        noCulling = true;
        setNoGravity(true);
        setPos(x, y, z);
    }

    public void configure(int radius, long seed, int durationTicks) {
        entityData.set(DATA_RADIUS, Math.max(16, radius));
        entityData.set(DATA_SEED, seed);
        entityData.set(DATA_DURATION, Math.max(20, durationTicks));
    }

    public int getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    public long getSeed() {
        return entityData.get(DATA_SEED);
    }

    public int getDurationTicks() {
        return entityData.get(DATA_DURATION);
    }

    public float getProgress(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / Math.max(1.0F, getDurationTicks()));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        var renderDistance = Math.max(1600.0D, getRadius() * 16.0D);
        return distance <= renderDistance * renderDistance;
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        var radius = Math.max(32.0D, getRadius() * 1.75D);
        var height = Math.max(96.0D, getRadius() * 1.8D);
        return new AABB(
            getX() - radius,
            getY() - Math.max(8.0D, getRadius() * 0.12D),
            getZ() - radius,
            getX() + radius,
            getY() + height,
            getZ() + radius
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= getDurationTicks()) {
            remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 128);
        builder.define(DATA_SEED, 0L);
        builder.define(DATA_DURATION, 20 * 45);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
        configure(
            compound.contains("radius") ? compound.getInt("radius") : 128,
            compound.getLong("seed"),
            compound.contains("duration") ? compound.getInt("duration") : 20 * 45
        );
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
        compound.putInt("radius", getRadius());
        compound.putLong("seed", getSeed());
        compound.putInt("duration", getDurationTicks());
    }
}
