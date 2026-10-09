package com.predator.common.gameplay.hunt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where each player's hunt stands, saved with the world. One hunt per player at a time — [stated] "hunts cant stack one
 * must complete to finish it".
 * <p>
 * Also keeps a small map of where the player spends their time (8x8-block cells, visit counts), which is how the Hunter
 * knows where to leave its calling card — [stated] "outside the players door or enterence or a place the player spends
 * alot of time around".
 */
public final class HuntLedger extends SavedData {

    private static final String FILE_ID = "avp_predator_hunts";

    /** Most cells remembered per player; the least-visited is dropped to make room. */
    private static final int MAX_CELLS = 48;

    /** When any cell passes this, every count is halved, so old haunts fade and a new home takes over. */
    private static final int DECAY_AT = 4000;

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** The hunt's progress. Pass 3 adds the attack, escape and return after {@link #AWAITING_ATTACK}. */
    public enum Stage {

        /** No hunt. */
        NONE,
        /** The Hunter's Moon has risen for this player. Ends at the next sunrise they are present for. */
        MOON,
        /** Phase 1, the day of being watched: clicks, glimpses, the calling card. */
        PHASE_ONE,
        /** Phase 1 is over; the Hunter attacks on the next night the player is present for. */
        AWAITING_ATTACK,
        /** Phase 2: the Hunter is out tonight. */
        ATTACK,
        /** The Hunter withdrew (or escaped) alive; it returns on the next night, armour restored and stronger. */
        AWAITING_RETURN,
        /** Phase 3: the return. */
        RETURN_ATTACK,
        /** Phase 2, wounded: it fled and waits at the end of its blood trail for up to 5 minutes. */
        WAITING,
        /** The player followed the trail: the fight is finished there, with no second escape. */
        FINAL;

        static Stage byName(String name) {
            for (var stage : values()) {
                if (stage.name().equals(name)) {
                    return stage;
                }
            }

            return NONE;
        }
    }

    public static final class Entry {

        Stage stage = Stage.NONE;

        long moonDay;

        boolean sleptOnMoon;

        boolean callingCardPlaced;

        /** Not saved: a fresh session simply rolls a new delay. */
        long nextClickTick;

        long nextGlimpseTick;

        final Map<Long, int[]> visits = new HashMap<>();

        /** The Hunter currently out for this player, or null. */
        @Nullable
        UUID hunterId;

        /** The tier rolled for this hunt; the return keeps it. */
        String hunterTier = "";

        /** Hunts this player has faced (a Hunter actually sent). The first is always a Youngblood. */
        int huntsFaced;

        /**
         * The tier ladder: percent chance of each tier, Youngblood..Clan Leader, always summing to 100. See
         * {@link HunterLadder}.
         */
        final int[] ladder = { 100, 0, 0, 0, 0 };

        /** How many rungs have joined the roll (1 = Youngblood only). */
        int unlockedRungs = 1;

        /** Not saved: ticks the expected Hunter has been missing from the loaded world. */
        int hunterMissingTicks;

        /** Not saved: how long the Hunter has failed to get any closer to a player it cannot see. */
        int stallTicks;

        /** Not saved: the closest it has come during the current stall. */
        double stallBestDistance = Double.MAX_VALUE;

        /** When a wounded Hunter stops waiting at the end of its trail (game time). Saved. */
        long waitUntil;

        /** Not saved: when sending a Hunter may next be tried, after a try that found no arrival spot. */
        long nextSendTick;

        /** Not saved: when the Hunter may throw its next wall-breaching grenade. */
        long nextBreachTick;

        /** Not saved: it has already warped in tonight. Reset when a Hunter is sent. */
        boolean warpedTonight;

        /**
         * Not saved: a warp under way — the clicks have started; it appears {@code WARP_DELAY} ticks after this. -1
         * when none.
         */
        long warpStartTick = -1;

        /** Not saved: where the pending warp lands. */
        net.minecraft.world.phys.@Nullable Vec3 warpSpot;

        public Stage stage() {
            return stage;
        }

        public int huntsFaced() {
            return huntsFaced;
        }

        public boolean callingCardPlaced() {
            return callingCardPlaced;
        }

        /** {@return the centre of the most-visited cell, at the height last seen there, or null if none yet} */
        public @Nullable BlockPos favouriteSpot() {
            Map.Entry<Long, int[]> best = null;

            for (var visit : visits.entrySet()) {
                if (best == null || visit.getValue()[0] > best.getValue()[0]) {
                    best = visit;
                }
            }

            if (best == null) {
                return null;
            }

            var key = best.getKey();
            var cellX = (int) (key >> 32);
            var cellZ = (int) (long) key;

            return new BlockPos((cellX << 3) + 4, best.getValue()[1], (cellZ << 3) + 4);
        }
    }

    public static HuntLedger get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(HuntLedger::new, HuntLedger::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                FILE_ID
            );
    }

    public Entry entry(UUID player) {
        return entries.computeIfAbsent(player, ignored -> new Entry());
    }

    public void setStage(Entry entry, Stage stage) {
        entry.stage = stage;
        setDirty();
    }

    void beginMoon(Entry entry, long day) {
        entry.stage = Stage.MOON;
        entry.moonDay = day;
        entry.sleptOnMoon = false;
        entry.callingCardPlaced = false;
        setDirty();
    }

    void markSlept(Entry entry) {
        entry.sleptOnMoon = true;
        setDirty();
    }

    /** Debug: phase 1 again, calling card not yet left. */
    public void resetCallingCard(Entry entry) {
        entry.callingCardPlaced = false;
        setDirty();
    }

    /** Marks a Hunter as sent for this player. */
    void sendHunter(Entry entry, UUID hunterId, String tier, Stage stage) {
        entry.hunterId = hunterId;
        entry.hunterTier = tier;
        entry.stage = stage;
        entry.hunterMissingTicks = 0;
        entry.stallTicks = 0;
        entry.stallBestDistance = Double.MAX_VALUE;
        entry.warpedTonight = false;
        entry.warpStartTick = -1;
        entry.warpSpot = null;
        setDirty();
    }

    /** The hunt is over, whichever way it ended. The ladder and the count of hunts faced are kept. */
    void endHunt(Entry entry) {
        entry.stage = Stage.NONE;
        entry.hunterId = null;
        entry.hunterTier = "";
        entry.callingCardPlaced = false;
        setDirty();
    }

    void markHunterGone(Entry entry, Stage stage) {
        entry.hunterId = null;
        entry.stage = stage;
        setDirty();
    }

    public void touch() {
        setDirty();
    }

    void markCallingCardPlaced(Entry entry) {
        entry.callingCardPlaced = true;
        setDirty();
    }

    /** Records one visit sample at this position. */
    void recordVisit(Entry entry, BlockPos pos) {
        var key = ((long) (pos.getX() >> 3) << 32) | ((pos.getZ() >> 3) & 0xFFFFFFFFL);
        var cell = entry.visits.get(key);

        if (cell == null) {
            if (entry.visits.size() >= MAX_CELLS) {
                Long weakest = null;
                var weakestCount = Integer.MAX_VALUE;

                for (var visit : entry.visits.entrySet()) {
                    if (visit.getValue()[0] < weakestCount) {
                        weakest = visit.getKey();
                        weakestCount = visit.getValue()[0];
                    }
                }

                entry.visits.remove(weakest);
            }

            cell = new int[] { 0, pos.getY() };
            entry.visits.put(key, cell);
        }

        cell[0]++;
        cell[1] = pos.getY();

        if (cell[0] > DECAY_AT) {
            for (var visit : entry.visits.values()) {
                visit[0] = Math.max(1, visit[0] / 2);
            }
        }

        setDirty();
    }

    public static HuntLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new HuntLedger();
        var list = tag.getList("players", Tag.TAG_COMPOUND);

        for (var i = 0; i < list.size(); i++) {
            var compound = list.getCompound(i);

            if (!compound.hasUUID("id")) {
                continue;
            }

            var entry = ledger.entry(compound.getUUID("id"));
            entry.stage = Stage.byName(compound.getString("stage"));
            entry.moonDay = compound.getLong("moon_day");
            entry.sleptOnMoon = compound.getBoolean("slept_on_moon");
            entry.callingCardPlaced = compound.getBoolean("calling_card_placed");
            entry.hunterId = compound.hasUUID("hunter_id") ? compound.getUUID("hunter_id") : null;
            entry.hunterTier = compound.getString("hunter_tier");
            entry.huntsFaced = compound.getInt("hunts_faced");
            entry.waitUntil = compound.getLong("wait_until");

            if (compound.contains("ladder")) {
                var ladder = compound.getIntArray("ladder");

                if (ladder.length == entry.ladder.length) {
                    System.arraycopy(ladder, 0, entry.ladder, 0, ladder.length);
                }

                entry.unlockedRungs = Math.max(1, Math.min(entry.ladder.length, compound.getInt("unlocked_rungs")));
            }

            var visits = compound.getList("visits", Tag.TAG_COMPOUND);

            for (var j = 0; j < visits.size(); j++) {
                var visit = visits.getCompound(j);
                entry.visits.put(visit.getLong("cell"), new int[] { visit.getInt("count"), visit.getInt("y") });
            }
        }

        return ledger;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        var list = new ListTag();

        entries.forEach((id, entry) -> {
            var compound = new CompoundTag();
            compound.putUUID("id", id);
            compound.putString("stage", entry.stage.name());
            compound.putLong("moon_day", entry.moonDay);
            compound.putBoolean("slept_on_moon", entry.sleptOnMoon);
            compound.putBoolean("calling_card_placed", entry.callingCardPlaced);

            if (entry.hunterId != null) {
                compound.putUUID("hunter_id", entry.hunterId);
            }

            compound.putString("hunter_tier", entry.hunterTier);
            compound.putInt("hunts_faced", entry.huntsFaced);
            compound.putLong("wait_until", entry.waitUntil);
            compound.putIntArray("ladder", entry.ladder);
            compound.putInt("unlocked_rungs", entry.unlockedRungs);

            var visits = new ListTag();

            entry.visits.forEach((cell, value) -> {
                var visit = new CompoundTag();
                visit.putLong("cell", cell);
                visit.putInt("count", value[0]);
                visit.putInt("y", value[1]);
                visits.add(visit);
            });

            compound.put("visits", visits);
            list.add(compound);
        });

        tag.put("players", list);

        return tag;
    }
}
