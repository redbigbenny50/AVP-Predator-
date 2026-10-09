package com.predator.common.gameplay.hunt;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's yautja honor, saved with the world (on the overworld's data storage, so it is one ledger per world
 * whatever dimension the player is in).
 * <p>
 * Per player: the honor total, which advancements have already paid out (so an advancement pays once, ever — even if it
 * is revoked and re-earned), and whether they have become WORTHY. Worthiness is latched the moment honor first reaches
 * the threshold: lowering the threshold later can make more players worthy, raising it never un-marks one.
 * <p>
 * ⚠ Honor is stored as a plain signed int so the faction work can spend it, or take it away for dishonour, without a
 * data migration.
 */
public final class HonorLedger extends SavedData {

    private static final String FILE_ID = "avp_predator_honor";

    private static final String NBT_PLAYERS = "players";

    private static final String NBT_ID = "id";

    private static final String NBT_HONOR = "honor";

    private static final String NBT_CREDITED = "credited";

    private static final String NBT_WORTHY = "worthy";

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** One player's standing. */
    public static final class Entry {

        private int honor;

        private boolean worthy;

        private final Set<String> credited = new HashSet<>();

        public int honor() {
            return honor;
        }

        public boolean worthy() {
            return worthy;
        }

        public boolean hasCredited(String advancement) {
            return credited.contains(advancement);
        }
    }

    /**
     * ⚠ The three-argument factory, as {@code PlasmaDetonationSavedData} explains: the two-argument form is a NeoForge
     * patch that does not exist in the vanilla jar {@code :common} compiles against, and would crash on Fabric.
     */
    public static HonorLedger get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(HonorLedger::new, HonorLedger::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                FILE_ID
            );
    }

    public Entry entry(UUID player) {
        return entries.computeIfAbsent(player, ignored -> new Entry());
    }

    /**
     * Pays an advancement's honor out once.
     *
     * @return true if this call made the player worthy (they crossed the threshold just now)
     */
    public boolean credit(UUID player, String advancement, int honor, int threshold) {
        var entry = entry(player);

        if (!entry.credited.add(advancement)) {
            return false;
        }

        entry.honor += honor;
        setDirty();

        return latchWorthy(entry, threshold);
    }

    /** Debug: sets a player's honor outright. {@return true if that made them worthy} */
    public boolean setHonor(UUID player, int honor, int threshold) {
        var entry = entry(player);
        entry.honor = honor;
        setDirty();

        return latchWorthy(entry, threshold);
    }

    /** Debug: wipes a player's standing entirely, credited advancements included. */
    public void reset(UUID player) {
        entries.remove(player);
        setDirty();
    }

    private boolean latchWorthy(Entry entry, int threshold) {
        if (!entry.worthy && entry.honor >= threshold) {
            entry.worthy = true;
            setDirty();

            return true;
        }

        return false;
    }

    public static HonorLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new HonorLedger();
        var list = tag.getList(NBT_PLAYERS, Tag.TAG_COMPOUND);

        for (var i = 0; i < list.size(); i++) {
            var compound = list.getCompound(i);

            if (!compound.hasUUID(NBT_ID)) {
                continue;
            }

            var entry = ledger.entry(compound.getUUID(NBT_ID));
            entry.honor = compound.getInt(NBT_HONOR);
            entry.worthy = compound.getBoolean(NBT_WORTHY);

            var credited = compound.getList(NBT_CREDITED, Tag.TAG_STRING);

            for (var j = 0; j < credited.size(); j++) {
                entry.credited.add(credited.getString(j));
            }
        }

        return ledger;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        var list = new ListTag();

        entries.forEach((id, entry) -> {
            var compound = new CompoundTag();
            compound.putUUID(NBT_ID, id);
            compound.putInt(NBT_HONOR, entry.honor);
            compound.putBoolean(NBT_WORTHY, entry.worthy);

            var credited = new ListTag();
            entry.credited.stream().sorted().forEach(advancement -> credited.add(StringTag.valueOf(advancement)));
            compound.put(NBT_CREDITED, credited);

            list.add(compound);
        });

        tag.put(NBT_PLAYERS, list);

        return tag;
    }
}
