package com.predator.common.gameplay.block.entity;

import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** The placed gauntlet's stack, synced to clients so the renderer draws the real thing, armed glow and all. */
public class GauntletBlockEntity extends BlockEntity {

    private ItemStack gauntlet = ItemStack.EMPTY;

    /**
     * The glowing marker shown while it counts (GauntletBeacon). Saved, so a reload finds it rather than adding one.
     */
    private @org.jetbrains.annotations.Nullable java.util.UUID beacon;

    public GauntletBlockEntity(BlockPos pos, BlockState state) {
        super(PredatorBlockEntityTypes.GAUNTLET.get(), pos, state);
    }

    public ItemStack getGauntlet() {
        return gauntlet;
    }

    /** Sets the stack on placement; an armed one starts counting here. */
    public void placeGauntlet(ItemStack stack) {
        this.gauntlet = stack.copy();

        if (level instanceof ServerLevel serverLevel) {
            GauntletSelfDestruct.startCountdown(serverLevel, gauntlet, Vec3.atCenterOf(worldPosition));
        }

        setChanged();
        sync();
    }

    /** Hands the stack over for pickup and forgets it, so the block's removal does not spill or destroy anything. */
    public ItemStack takeGauntlet() {
        var taken = gauntlet;

        gauntlet = ItemStack.EMPTY;
        setChanged();

        return taken;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GauntletBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || entity.gauntlet.isEmpty()) {
            return;
        }

        var before = GauntletSelfDestruct.remainingSeconds(entity.gauntlet, level.getGameTime());

        GauntletSelfDestruct.observe(serverLevel, entity.gauntlet, Vec3.atCenterOf(pos), () -> {
            entity.gauntlet = ItemStack.EMPTY;
            level.removeBlock(pos, false);
        });

        if (!entity.gauntlet.isEmpty() && before != GauntletSelfDestruct.remainingSeconds(entity.gauntlet, level.getGameTime())) {
            entity.sync();
        }

        entity.tickBeacon(serverLevel, pos);
    }

    /**
     * Counting: keep the through-walls marker on it. Not counting (disarmed, rule off, or it has just gone off and the
     * block is about to vanish): take it away. Strays are swept once a second.
     */
    private void tickBeacon(ServerLevel level, BlockPos pos) {
        if (!gauntlet.isEmpty() && GauntletSelfDestruct.isCounting(gauntlet)) {
            var previous = beacon;
            beacon = com.predator.common.gameplay.gauntlet.destruct.GauntletBeacon.ensure(level, pos, gauntlet, beacon);

            if (!java.util.Objects.equals(previous, beacon)) {
                setChanged();
            }

            if (level.getGameTime() % 20 == 0) {
                com.predator.common.gameplay.gauntlet.destruct.GauntletBeacon.clearStrays(level, pos, beacon);
            }
        } else if (beacon != null) {
            com.predator.common.gameplay.gauntlet.destruct.GauntletBeacon.remove(level, pos, beacon);
            beacon = null;
            setChanged();
        }
    }

    /**
     * The block is gone — mined, picked up, blown up, gone off — or its chunk is unloading: the marker goes with it. A
     * reload puts a fresh one back on the next tick if it is still counting.
     */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && beacon != null) {
            com.predator.common.gameplay.gauntlet.destruct.GauntletBeacon.remove(serverLevel, worldPosition, beacon);
            beacon = null;
        }

        super.setRemoved();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        if (!gauntlet.isEmpty()) {
            tag.put("Gauntlet", gauntlet.save(registries));
        }

        if (beacon != null) {
            tag.putUUID("Beacon", beacon);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        gauntlet = tag.contains("Gauntlet") ? ItemStack.parseOptional(registries, tag.getCompound("Gauntlet")) : ItemStack.EMPTY;
        beacon = tag.hasUUID("Beacon") ? tag.getUUID("Beacon") : null;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
