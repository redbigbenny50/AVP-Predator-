package com.predator.common.gameplay.block.entity;

import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.predator.common.property.PredatorProperties;
import com.predator.common.property.PredatorPropertyAccess;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The proximity mine's brain.
 * <h2>His spec (Sep 8)</h2> [stated] "we want them to go off if anything wanders within a 3 block radius of them ... we
 * want it to be able to reach and damage someone who triggers it at the very edge." Then: [stated] "tnt strength, lower
 * that timer to 3 seconds and it ignores the person who placed it mob included ... make it any movement no reason it
 * shouldnt blow up a wandering mob."
 * <h2>The owner</h2> Whoever placed it — recorded from {@code setPlacedBy} for a player, or via {@link #setOwner} for a
 * mob that lays one — neither arms it by walking near it nor takes its blast; everyone and everything else does.
 * <h2>"Any movement"</h2> Any entity except the owner, creative/spectator players, dropped items and XP orbs. That
 * includes projectiles: shoot a mine to set it off.
 * <h2>⚠⚠ WHY IT NEVER WENT OFF BEFORE — TWO SEPARATE BUGS</h2>
 * <ol>
 * <li>The trigger filter admitted players and anything tagged {@code predators}, and NOTHING ELSE. A cow, a zombie or a
 * xenomorph walking over it was not a trigger.</li>
 * <li>Once armed it counted down {@code 100} ticks and only while something was STILL in range, resetting the instant
 * the area emptied. Walk through and it never fires. Now: once armed it fires after {@link #FUSE_TICKS}
 * regardless.</li>
 * </ol>
 * <h2>Explosion</h2> {@link #EXPLOSION_POWER} is TNT's 4 (creeper 3, charged creeper 6). Damage reaches out to twice
 * the power, so 8 blocks — a target at the 3-block trigger edge takes roughly 14 hearts unarmoured. TNT block
 * interaction, as before.
 * <h2>⚠ The radius is a real sphere from the block centre</h2> The old check was a cube ({@code AABB.inflate}); a
 * corner of a 3-cube is 5.2 blocks out. The box is the coarse query; distance decides.
 */
public class TripMineBlockEntity extends BlockEntity {

    /** Ticks from trigger to detonation. [stated] three seconds. Non-final so it can be tuned in a hot-swap. */
    public static int FUSE_TICKS = 60;

    /** TNT. */
    public static float EXPLOSION_POWER = 4.0F;

    private boolean triggered;

    private int fuse;

    private @Nullable UUID owner;

    /**
     * Placed by a MOB (a yautja), not a player. Decides two things: its blast respects mobGriefing, and it expires.
     */
    private boolean ownerIsMob;

    /** Game time a mob-placed mine disarms itself at; 0 = never (every player mine). */
    private long expiresAt;

    public TripMineBlockEntity(BlockPos pos, BlockState blockState) {
        super(PredatorBlockEntityTypes.TRIP_MINE.get(), pos, blockState);
    }

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, TripMineBlockEntity mine) {
        if (level.isClientSide) {
            return;
        }

        // ⚠ A yautja's mine that has sat untriggered for its lifetime disarms — removed, dropping nothing. [agreed] so
        // a
        // hunt that ended long ago does not leave a base booby-trapped for good. Never once armed: a lit fuse finishes.
        if (!mine.triggered && mine.expiresAt > 0L && level.getGameTime() >= mine.expiresAt) {
            level.removeBlock(blockPos, false);

            return;
        }

        if (!mine.triggered) {
            if (mine.anythingInRange(level, blockPos)) {
                mine.arm(level, blockPos);
            }

            return;
        }

        // ⚠ Armed. The fuse runs whether or not the trigger is still standing there.
        mine.fuse--;

        if (mine.fuse <= 0) {
            mine.detonate(level, blockPos);
        }
    }

    private boolean anythingInRange(Level level, BlockPos blockPos) {
        var range = PredatorPropertyAccess.INSTANCE.getOrThrow(PredatorProperties.Blocks.TripMine.RANGE);
        var centre = Vec3.atCenterOf(blockPos);
        var candidates = level.getEntities((Entity) null, new AABB(blockPos).inflate(range), this::isTrigger);

        for (var entity : candidates) {
            if (entity.position().closerThan(centre, range)) {
                return true;
            }
        }

        return false;
    }

    private boolean isTrigger(Entity entity) {
        if (!entity.isAlive() || isOwner(entity)) {
            return false;
        }

        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return false;
        }

        return !(entity instanceof Player player) || !BLibEntityPredicates.isInvulnerable(player);
    }

    private boolean isOwner(Entity entity) {
        return owner != null && owner.equals(entity.getUUID());
    }

    /**
     * Records who laid the mine. Called from {@code TripMineBlock.setPlacedBy}; a mob that lays one should call it too.
     */
    public void setOwner(@Nullable LivingEntity placer) {
        this.owner = placer == null ? null : placer.getUUID();
        this.ownerIsMob = placer != null && !(placer instanceof Player);
        setChanged();
    }

    /** A mob-placed mine disarms itself at {@code gameTime}. */
    public void setExpiresAt(long gameTime) {
        this.expiresAt = gameTime;
        setChanged();
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return isOwner(entity);
    }

    private void arm(Level level, BlockPos blockPos) {
        triggered = true;
        fuse = FUSE_TICKS;
        setChanged();

        // [stated] his beep "for the proximity mine": 3.0 s long, timed so its final tone ends on FUSE_TICKS (60).
        level.playSound(null, blockPos, PredatorSoundEvents.EXPLOSIVE_TIMER_BEEP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void detonate(Level level, BlockPos blockPos) {
        var centre = Vec3.atCenterOf(blockPos);

        // ⚠ Remove the block FIRST so the explosion cannot re-tick this entity or drop the mine as loot.
        level.removeBlock(blockPos, false);

        // ⚠ The owner is exempt from the blast itself, not just from arming it. Vanilla has no exclude list, but the
        // damage calculator is consulted per entity.
        var spareOwner = new ExplosionDamageCalculator() {

            @Override
            public boolean shouldDamageEntity(@NotNull net.minecraft.world.level.Explosion explosion, @NotNull Entity entity) {
                return !isOwner(entity);
            }
        };

        // ⚠⚠ A MOB'S MINE RESPECTS mobGriefing. [stated] "yes respect the mob griefing rule". Vanilla's MOB explosion
        // mode
        // checks the gamerule and breaks nothing when it is off (read from Level.explode in the 1.21.1 jar); TNT mode
        // never checks it. A PLAYER's mine stays TNT — a player placing an explosive is not mob griefing.
        var interaction = ownerIsMob ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.TNT;

        // [stated] "if anyone dies to a trip mine" the yautja laughs. The blast has no source entity, so the deaths it
        // causes are matched back to it by place and time.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.predator.common.gameplay.entity.living.yautja.YautjaTaunts.recordMineBlast(serverLevel, centre, owner, ownerIsMob);
        }

        level.explode(null, null, spareOwner, centre.x, centre.y, centre.z, EXPLOSION_POWER, false, interaction);
    }

    public boolean isTriggered() {
        return triggered;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Triggered", triggered);
        tag.putInt("Fuse", fuse);

        if (owner != null) {
            tag.putUUID("Owner", owner);
        }

        tag.putBoolean("OwnerIsMob", ownerIsMob);
        tag.putLong("ExpiresAt", expiresAt);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        triggered = tag.getBoolean("Triggered");
        fuse = tag.getInt("Fuse");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // Absent on mines placed before this existed — they read as a player's, never expire: exactly as before.
        ownerIsMob = tag.getBoolean("OwnerIsMob");
        expiresAt = tag.getLong("ExpiresAt");
    }
}
