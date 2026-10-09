package com.predator.common.gameplay.hunt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The freeze grenade's patch of ice — [stated] "a lingering-potion-sized area of fire or ice blocks (freeze with
 * particle effects) lasting about 10 s".
 * <ul>
 * <li>The top surface of the ground in the radius turns to ice, and water sources freeze over.</li>
 * <li>While it lasts, snowflakes drift over it and anything standing in it that can freeze is kept freezing (vanilla's
 * freeze, the powder-snow chill and its damage).</li>
 * <li>When it ends, every block goes back to exactly what it was.</li>
 * </ul>
 * <h2>⚠ The original blocks are SAVED with the world</h2> A patch whose ten seconds straddle a server stop, or whose
 * chunk unloads before it thaws, must still give the grass, sand or stone back. So every changed block is kept here
 * with its original state, per dimension, in saved data, and is only restored when its chunk is loaded — and only if it
 * is still the ice it was turned into (a player who mined it has already changed it).
 * <p>
 * ⚠ PACKED ice, not plain ice: plain ice melts into WATER under light on a random tick, and a melted block would no
 * longer be the ice this restores — the water would stay and the ground would be gone.
 * <p>
 * Anything in it worn down to 10%, and any passive mob, freezes SOLID — the shared rule, through FreezeBridge.
 */
public final class FreezePatches extends SavedData {

    private static final String FILE_ID = "avp_predator_freeze_patches";

    private record Thaw(
        BlockPos pos,
        BlockState original,
        long at
    ) {}

    private record Patch(
        BlockPos centre,
        int radius,
        long until
    ) {}

    private final List<Thaw> thaws = new ArrayList<>();

    private final List<Patch> patches = new ArrayList<>();

    public static FreezePatches get(ServerLevel level) {
        return level.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(FreezePatches::new, FreezePatches::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                FILE_ID
            );
    }

    /** Lays a patch of ice around the blast. */
    public static void freeze(ServerLevel level, BlockPos centre, int radius, int ticks) {
        var data = get(level);
        var until = level.getGameTime() + ticks;
        var ice = Blocks.PACKED_ICE.defaultBlockState();

        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }

                for (var dy = 2; dy >= -3; dy--) {
                    var pos = centre.offset(dx, dy, dz);
                    var state = level.getBlockState(pos);

                    if (state.isAir() || state.is(Blocks.PACKED_ICE)) {
                        continue;
                    }

                    var water = state.is(Blocks.WATER) && state.getFluidState().isSource();
                    var surface = state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity()
                        && state.getDestroySpeed(level, pos) >= 0.0F && level.getBlockState(pos.above()).isAir();

                    if (water || surface) {
                        data.thaws.add(new Thaw(pos.immutable(), state, until));
                        level.setBlockAndUpdate(pos, ice);
                    }

                    // Only the top: the first block found in this column is the surface. ⚠ A surface with a flower or
                    // grass on it is left alone (the air check above) — turning the block under a plant to ice would
                    // pop the plant off for good.
                    break;
                }
            }
        }

        data.patches.add(new Patch(centre.immutable(), radius, until));
        data.setDirty();
    }

    /** Registered on post-level-tick. */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = get(serverLevel);

        if (data.thaws.isEmpty() && data.patches.isEmpty()) {
            return;
        }

        var now = serverLevel.getGameTime();
        var changed = data.thaws.removeIf(thaw -> {
            if (now < thaw.at() || !serverLevel.isLoaded(thaw.pos())) {
                return false;
            }

            if (serverLevel.getBlockState(thaw.pos()).is(Blocks.PACKED_ICE)) {
                serverLevel.setBlockAndUpdate(thaw.pos(), thaw.original());
            }

            return true;
        });

        changed |= data.patches.removeIf(patch -> now >= patch.until());

        for (var patch : data.patches) {
            if (!serverLevel.isLoaded(patch.centre())) {
                continue;
            }

            if (now % 5 == 0) {
                serverLevel.sendParticles(
                    ParticleTypes.SNOWFLAKE,
                    patch.centre().getX() + 0.5,
                    patch.centre().getY() + 1.0,
                    patch.centre().getZ() + 0.5,
                    12,
                    patch.radius() * 0.6,
                    0.6,
                    patch.radius() * 0.6,
                    0.01
                );
            }

            var box = new AABB(patch.centre()).inflate(patch.radius(), 2.0, patch.radius());

            for (var living : serverLevel.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::canFreeze)) {
                // Held at fully frozen while it stands in the patch; vanilla thaws it off again once it leaves.
                living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 20));

                // Freezing solid, the shared rule: worn down to 10% by the chill, or any passive mob.
                if (now % 10 == 0) {
                    com.predator.common.gameplay.freeze.FreezeBridge.tryFreezeSolid(living);
                }
            }
        }

        if (changed) {
            data.setDirty();
        }
    }

    public static FreezePatches load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new FreezePatches();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);

        for (var entry : tag.getList("thaws", Tag.TAG_COMPOUND)) {
            var compound = (CompoundTag) entry;
            data.thaws.add(
                new Thaw(
                    BlockPos.of(compound.getLong("pos")),
                    NbtUtils.readBlockState(blocks, compound.getCompound("state")),
                    compound.getLong("at")
                )
            );
        }

        for (var entry : tag.getList("patches", Tag.TAG_COMPOUND)) {
            var compound = (CompoundTag) entry;
            data.patches.add(new Patch(BlockPos.of(compound.getLong("centre")), compound.getInt("radius"), compound.getLong("until")));
        }

        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        var thawList = new ListTag();

        for (var thaw : thaws) {
            var compound = new CompoundTag();
            compound.putLong("pos", thaw.pos().asLong());
            compound.put("state", NbtUtils.writeBlockState(thaw.original()));
            compound.putLong("at", thaw.at());
            thawList.add(compound);
        }

        var patchList = new ListTag();

        for (var patch : patches) {
            var compound = new CompoundTag();
            compound.putLong("centre", patch.centre().asLong());
            compound.putInt("radius", patch.radius());
            compound.putLong("until", patch.until());
            patchList.add(compound);
        }

        tag.put("thaws", thawList);
        tag.put("patches", patchList);

        return tag;
    }
}
