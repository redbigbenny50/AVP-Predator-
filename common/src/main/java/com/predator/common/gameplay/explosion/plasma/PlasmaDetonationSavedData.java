package com.predator.common.gameplay.explosion.plasma;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The carving progress of every nuclear explosion that has not finished yet, per dimension.
 * <p>
 * An in-flight explosion otherwise lives only in memory: the cursors inside the processor plus a queued task in the
 * server scheduler. Any world unload ends it mid-carve, and in singleplayer logging out IS a world unload, so a player
 * who quits partway through comes back to a half-dug crater that never completes.
 * <p>
 * Restarting the explosion from scratch is not an alternative. The terrain transform READS the block it is replacing,
 * so a second pass over ground that has already been carved transforms the results again and the crater changes
 * appearance. Only resuming from exactly where it stopped is correct.
 * <p>
 * What is stored is deliberately tiny - an id, the centre, two ints and the opaque cursor array, on the order of thirty
 * ints per explosion. The callbacks cannot be stored at all, being lambdas, so the consumer rebuilds the explosion with
 * its callbacks and then restores the cursor state into it.
 */
public class PlasmaDetonationSavedData extends SavedData {

    private static final String FILE_ID = "avp_predator_avp_predator_plasma_detonations";

    private static final String NBT_EXPLOSIONS = "Explosions";

    private static final String NBT_ID = "Id";

    private static final String NBT_CENTER_X = "CenterX";

    private static final String NBT_CENTER_Y = "CenterY";

    private static final String NBT_CENTER_Z = "CenterZ";

    private static final String NBT_RADIUS = "Radius";

    private static final String NBT_MAX_KNOCKBACK = "MaxKnockback";

    private static final String NBT_CURSOR_STATE = "CursorState";

    /**
     * Everything needed to rebuild an interrupted explosion and put it back where it stopped.
     *
     * @param cursorState opaque to this class - produced by and only meaningful to the explosion library.
     */
    public record PendingExplosion(
        UUID id,
        Vec3 center,
        int radius,
        int maxKnockback,
        int[] cursorState
    ) {}

    /**
     * The three-argument factory is deliberate, and the two-argument one must NOT be used here.
     * <p>
     * NeoForge patches {@code SavedData.Factory} with a two-argument constructor that passes a null data-fix type, and
     * patches {@code DimensionDataStorage} to tolerate that null. Vanilla has neither. This module compiles against the
     * vanilla jar, so the short form does not exist at compile time - and on Fabric, which runs unpatched vanilla, a
     * null type would dereference straight into a crash on load.
     * <p>
     * Which type is named barely matters. {@code SavedData.save} stamps the current data version into every file it
     * writes, so on read the fixer is asked to migrate from the current version to the current version and hands the
     * tag back untouched. A near-empty schema is named anyway, so nothing here would be rewritten even if that ever
     * stopped being true.
     */
    public static PlasmaDetonationSavedData get(ServerLevel level) {
        return level.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(
                    PlasmaDetonationSavedData::new,
                    PlasmaDetonationSavedData::load,
                    DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
                ),
                FILE_ID
            );
    }

    public static PlasmaDetonationSavedData load(CompoundTag compoundTag, HolderLookup.Provider registries) {
        var savedData = new PlasmaDetonationSavedData();
        var listTag = compoundTag.getList(NBT_EXPLOSIONS, Tag.TAG_COMPOUND);

        for (var i = 0; i < listTag.size(); i++) {
            var entry = listTag.getCompound(i);

            if (!entry.hasUUID(NBT_ID)) {
                continue;
            }

            var id = entry.getUUID(NBT_ID);
            var center = new Vec3(
                entry.getDouble(NBT_CENTER_X),
                entry.getDouble(NBT_CENTER_Y),
                entry.getDouble(NBT_CENTER_Z)
            );

            savedData.pendingExplosions.put(
                id,
                new PendingExplosion(
                    id,
                    center,
                    entry.getInt(NBT_RADIUS),
                    entry.getInt(NBT_MAX_KNOCKBACK),
                    entry.getIntArray(NBT_CURSOR_STATE)
                )
            );
        }

        return savedData;
    }

    private final Map<UUID, PendingExplosion> pendingExplosions = new LinkedHashMap<>();

    /**
     * Records the progress of an explosion, overwriting any earlier snapshot of the same one.
     * <p>
     * Called once per carving cycle. That is a field write and a dirty flag, not a file write - saved data only reaches
     * disk when the world saves - so the cost is negligible and at most one cycle of progress can ever be lost.
     */
    public void record(UUID id, Vec3 center, int radius, int maxKnockback, int[] cursorState) {
        pendingExplosions.put(id, new PendingExplosion(id, center, radius, maxKnockback, cursorState));
        setDirty();
    }

    public void forget(UUID id) {
        if (pendingExplosions.remove(id) != null) {
            setDirty();
        }
    }

    /**
     * A snapshot of what is outstanding, safe to iterate while resuming - which re-records into this map on its first
     * cycle.
     */
    public List<PendingExplosion> pendingExplosions() {
        return List.copyOf(pendingExplosions.values());
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider registries) {
        var listTag = new ListTag();

        for (var pendingExplosion : pendingExplosions.values()) {
            var entry = new CompoundTag();

            entry.putUUID(NBT_ID, pendingExplosion.id());
            entry.putDouble(NBT_CENTER_X, pendingExplosion.center().x());
            entry.putDouble(NBT_CENTER_Y, pendingExplosion.center().y());
            entry.putDouble(NBT_CENTER_Z, pendingExplosion.center().z());
            entry.putInt(NBT_RADIUS, pendingExplosion.radius());
            entry.putInt(NBT_MAX_KNOCKBACK, pendingExplosion.maxKnockback());
            entry.putIntArray(NBT_CURSOR_STATE, pendingExplosion.cursorState());

            listTag.add(entry);
        }

        compoundTag.put(NBT_EXPLOSIONS, listTag);

        return compoundTag;
    }
}
