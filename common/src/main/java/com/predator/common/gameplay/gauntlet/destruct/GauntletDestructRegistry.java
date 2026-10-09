package com.predator.common.gameplay.gauntlet.destruct;

import com.predator.common.gameplay.explosion.plasma.PlasmaDetonation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every armed gauntlet in the world, by id, with its deadline and the last place anything saw it.
 * <h2>⚠⚠ THIS IS WHAT MAKES "YOU CAN'T STOP IT BY GETTING RID OF IT" TRUE</h2> The countdown is a DEADLINE on the
 * stack, not a counter, and the block, the inventory tick and the dropped-item tick only refresh "last seen here" and
 * detonate if the deadline has passed. If none of them sees it — a chest, a shulker, an unloaded chunk, a player who
 * logged out with it — this registry detonates it at its last known position when the deadline passes. Stored
 * server-wide in the overworld's saved data so it survives restarts.
 * <p>
 * [stated] "spitting out the item or dropping it into the world shouldnt stop the countdown either. if thats possible."
 */
public class GauntletDestructRegistry extends SavedData {

    private static final String NAME = "avp_predator_gauntlet_destruct";

    private static final SavedData.Factory<GauntletDestructRegistry> FACTORY = new SavedData.Factory<>(
        GauntletDestructRegistry::new,
        GauntletDestructRegistry::load,
        DataFixTypes.LEVEL
    );

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** Whether this world has had the single-player {@code prednuke} default applied. See PredatorGameRules. */
    private boolean prednukeDefaulted;

    public record Entry(
        ResourceKey<Level> dimension,
        Vec3 lastPos,
        long deadline,
        @Nullable UUID armer
    ) {}

    public static GauntletDestructRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void register(UUID id, ResourceKey<Level> dimension, Vec3 pos, long deadline, @Nullable UUID armer) {
        entries.put(id, new Entry(dimension, pos, deadline, armer));
        setDirty();
    }

    /** Refreshes the last known position. Cheap: called every tick by whatever holds the gauntlet. */
    public void touch(UUID id, ResourceKey<Level> dimension, Vec3 pos) {
        var entry = entries.get(id);

        if (entry != null && (entry.dimension() != dimension || entry.lastPos().distanceToSqr(pos) > 0.01)) {
            entries.put(id, new Entry(dimension, pos, entry.deadline(), entry.armer()));
            setDirty();
        }
    }

    public @Nullable Entry remove(UUID id) {
        var removed = entries.remove(id);

        if (removed != null) {
            setDirty();
        }

        return removed;
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    /** {@return true the FIRST time this is called for the world} — and records that it was. */
    public boolean markPrednukeDefaulted() {
        if (prednukeDefaulted) {
            return false;
        }

        prednukeDefaulted = true;
        setDirty();

        return true;
    }

    /** Rule turned off with countdowns running: every one of them is defused, nothing goes off. */
    public void defuseAll() {
        if (!entries.isEmpty()) {
            entries.clear();
            setDirty();
        }
    }

    /**
     * The backstop. Anything whose deadline passed and which no holder detonated this tick goes off at its last known
     * position. Loading the chunk is deliberate: the gauntlet is there, and it is going off.
     */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var registry = get(serverLevel.getServer());

        if (registry.entries.isEmpty()) {
            return;
        }

        if (!com.predator.common.registry.init.PredatorGameRules.isSelfDestructEnabled(serverLevel)) {
            registry.defuseAll();
            return;
        }

        var now = serverLevel.getGameTime();
        var due = new ArrayList<Map.Entry<UUID, Entry>>();

        for (var entry : registry.entries.entrySet()) {
            if (entry.getValue().dimension() == serverLevel.dimension() && now >= entry.getValue().deadline()) {
                due.add(entry);
            }
        }

        for (var entry : due) {
            registry.remove(entry.getKey());

            var pos = entry.getValue().lastPos();

            serverLevel.getChunk(BlockPos.containing(pos));
            PlasmaDetonation.detonate(serverLevel, pos, entry.getValue().armer());
        }
    }

    private static GauntletDestructRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var registry = new GauntletDestructRegistry();

        registry.prednukeDefaulted = tag.getBoolean("PrednukeDefaulted");

        for (var element : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            var compound = (CompoundTag) element;
            var dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(compound.getString("Dimension"))
            );
            var pos = new Vec3(compound.getDouble("X"), compound.getDouble("Y"), compound.getDouble("Z"));
            var armer = compound.hasUUID("Armer") ? compound.getUUID("Armer") : null;

            registry.entries.put(compound.getUUID("Id"), new Entry(dimension, pos, compound.getLong("Deadline"), armer));
        }

        return registry;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        var list = new ListTag();

        for (var entry : entries.entrySet()) {
            var compound = new CompoundTag();

            compound.putUUID("Id", entry.getKey());
            compound.putString("Dimension", entry.getValue().dimension().location().toString());
            compound.putDouble("X", entry.getValue().lastPos().x);
            compound.putDouble("Y", entry.getValue().lastPos().y);
            compound.putDouble("Z", entry.getValue().lastPos().z);
            compound.putLong("Deadline", entry.getValue().deadline());

            if (entry.getValue().armer() != null) {
                compound.putUUID("Armer", entry.getValue().armer());
            }

            list.add(compound);
        }

        tag.put("Entries", list);
        tag.putBoolean("PrednukeDefaulted", prednukeDefaulted);

        return tag;
    }
}
