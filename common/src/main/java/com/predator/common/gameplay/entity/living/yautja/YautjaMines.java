package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.gameplay.block.TripMineBlock;
import com.predator.common.gameplay.block.entity.TripMineBlockEntity;
import com.predator.common.registry.init.PredatorBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;

/**
 * Every rule for a yautja's trip mines.
 * <h2>His rulings</h2>
 * <ul>
 * <li>[agreed] Two live mines per yautja. [stated] "we are going to add a hunting type yautja what it can do is set
 * some additional mines for traps natural spawned or event yautja can have the two mine limit." Until the hunting type
 * exists, the Hunter flag stands in for it.</li>
 * <li>[agreed] A mine disarms when its yautja dies, or after {@link #LIFETIME_TICKS} untriggered, dropping
 * nothing.</li>
 * <li>[stated] its blast respects mobGriefing — done in the mine itself, which knows a mob placed it.</li>
 * </ul>
 * ⚠ Placing a mine is not SPENDING one: like all yautja ammunition it needs a trip mine in the rack, never uses it up.
 * The cap is what limits them.
 */
public final class YautjaMines {

    /** [agreed] Natural and event yautja. */
    public static int MINE_CAP = 2;

    /** The hunting type sets more. His number was "some additional" — a dial until he picks one. */
    public static int HUNTER_MINE_CAP = 4;

    /** [agreed] ~10 minutes. */
    public static long LIFETIME_TICKS = 20L * 60L * 10L;

    private YautjaMines() {
        throw new UnsupportedOperationException();
    }

    public static int capFor(Yautja yautja) {
        return yautja.isHunter() ? HUNTER_MINE_CAP : MINE_CAP;
    }

    /** {@return whether it may place another} Carrying a mine, and under its cap once dead entries are pruned. */
    public static boolean canPlace(ServerLevel level, Yautja yautja) {
        return yautja.hasAmmo(PredatorBlocks.TRIP_MINE_BLOCK.get().asItem()) && liveCount(level, yautja) < capFor(yautja);
    }

    /**
     * Places a mine at {@code pos}, mounted on the face in {@code mount}'s opposite direction (UP = on the floor).
     * {@return whether it went down} — the space must be replaceable and the mine must be able to hang there.
     */
    public static boolean place(ServerLevel level, Yautja yautja, BlockPos pos, Direction mount) {
        if (!canPlace(level, yautja) || !level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        var state = PredatorBlocks.TRIP_MINE_BLOCK.get().defaultBlockState().setValue(TripMineBlock.MOUNT, mount);

        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, 3)) {
            return false;
        }

        if (level.getBlockEntity(pos) instanceof TripMineBlockEntity mine) {
            mine.setOwner(yautja);
            mine.setExpiresAt(level.getGameTime() + LIFETIME_TICKS);
        }

        yautja.getPlacedMines().add(pos.immutable());
        level.playSound(null, pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, yautja.getSoundSource(), 0.8F, 0.8F);

        return true;
    }

    /** Places a mine on the floor it is standing on. */
    public static boolean placeAtFeet(ServerLevel level, Yautja yautja) {
        return place(level, yautja, yautja.blockPosition(), Direction.UP);
    }

    /**
     * {@return how many of its mines still stand} Forgets any that have gone off, expired or been broken.
     * <p>
     * ⚠ Only forgets a mine it can SEE is gone. A mine in an unloaded chunk is still counted — forgetting it would let
     * the yautja lay past its cap, and lose the mine from the clean-up at its death.
     */
    public static int liveCount(ServerLevel level, Yautja yautja) {
        yautja.getPlacedMines().removeIf(pos -> level.isLoaded(pos) && !isOurs(level, yautja, pos));

        return yautja.getPlacedMines().size();
    }

    /** [agreed] On its death: every mine it laid is removed, dropping nothing — unless already counting down. */
    public static void disarmAll(ServerLevel level, Yautja yautja) {
        for (var pos : yautja.getPlacedMines()) {
            if (isOurs(level, yautja, pos) && level.getBlockEntity(pos) instanceof TripMineBlockEntity mine && !mine.isTriggered()) {
                level.removeBlock(pos, false);
            }
        }

        yautja.getPlacedMines().clear();
    }

    /** ⚠ Only if its chunk is loaded — never force-loads a chunk to check on a mine. */
    private static boolean isOurs(ServerLevel level, Yautja yautja, BlockPos pos) {
        return level.isLoaded(pos)
            && level.getBlockEntity(pos) instanceof TripMineBlockEntity mine
            && mine.isOwnedBy(yautja);
    }
}
