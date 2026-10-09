package com.predator.common.gameplay.hunt;

import com.predator.common.gameplay.block.SkinnedCorpseBlock;
import com.predator.common.gameplay.block.entity.SkinnedCorpseBlockEntity;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.registry.init.PredatorBlocks;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Skinned corpses: where they go, when a kill earns one, and the capture that moves a victim's drops into it.
 * <h2>When a yautja kill leaves a corpse</h2> [stated] "this is left when a predator kills a humanoid npc so a marine
 * colonist other human like creatures and the items they would drop go inside here. ... its only if they kill the npc
 * outside of the players view. ... the illusion we are trying to convey is that the predator killed and skinned these
 * people its not going to do that while the player is actively fighting or nearby."
 * <ul>
 * <li>The killer is a yautja (directly, or by its projectile — {@link DamageSource#getEntity} is the owner).</li>
 * <li>The victim is in {@code #avp_predator:skinnable} and is not a baby.</li>
 * <li>Nobody is watching — see {@link #isObserved}.</li>
 * <li>There is somewhere to put it — see {@link #findPlacement}. No spot, no corpse: the drops fall normally.</li>
 * </ul>
 * <h2>The capture</h2> Drops are taken at the one place every route converges: {@code ServerLevel.addFreshEntity}. Loot
 * tables and equipment go through {@code spawnAtLocation}; avp_human's marine adds its inventory with
 * {@code addFreshEntity} DIRECTLY; NeoForge collects everything and re-adds it after {@code LivingDropsEvent}. All
 * three end in {@code addFreshEntity}, inside {@code dropAllDeathLoot}. The capture is armed at the head of
 * {@code dropAllDeathLoot} and closed at its return; only {@code ItemEntity}s are taken, so experience still drops.
 * <p>
 * ⚠ A corpse never replaces anything: it only goes into air or replaceable plants, so it is not gated on
 * {@code mobGriefing}.
 */
public final class SkinnedCorpses {

    /** Any player closer than this is "nearby" whether or not they are looking. */
    public static final double NEARBY_RADIUS = 24.0;

    /** Beyond this nobody can see the kill, line of sight or not. */
    public static final double SIGHT_RADIUS = 128.0;

    /** Half-angle of a player's view cone, in degrees. A little wider than a default FOV so edge-of-screen counts. */
    public static final double VIEW_HALF_ANGLE_DEGREES = 60.0;

    /** How far from the kill a corpse may be placed. */
    public static final int SEARCH_RADIUS = 3;

    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    private SkinnedCorpses() {}

    /** Where a corpse will go, and in which pose. */
    public record Placement(
        BlockPos pos,
        boolean hanging,
        boolean floating
    ) {

        public Placement(BlockPos pos, boolean hanging) {
            this(pos, hanging, false);
        }
    }

    /**
     * An armed capture. {@code wholeDeath} marks a player's death, bracketed from the start of {@code die} to its end
     * rather than only around the vanilla drops — so mods that drop their own slots from their death handlers are
     * caught too. {@code trophyOnly} catches nothing: a grave mod is installed and owns the items.
     */
    private record Capture(
        LivingEntity victim,
        @Nullable Placement placement,
        List<ItemStack> items,
        boolean wholeDeath,
        boolean trophyOnly
    ) {}

    /** Item drops further than this from the victim are not part of its death. */
    private static final double CAPTURE_RADIUS = 16.0;

    /**
     * Grave and corpse mods. When one is installed it owns a dying player's items; the Hunter still leaves the skinned
     * corpse, empty, as its trophy. ⚠ Ids as those mods publish them; any mod not listed is still safe — see
     * {@link #tryCapture}, which TAKES items rather than copying them.
     */
    private static final List<String> GRAVE_MODS = List.of("corpse", "gravestone", "yigd", "universal_graves", "forgottengraves");

    private static boolean graveModInstalled() {
        for (var id : GRAVE_MODS) {
            if (com.blib.api.BLibAPI.isModLoaded(id)) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- the player's own death (pass 3c)

    /**
     * Head of the player's {@code die}: if their own Hunter killed them, everything they drop from now until the end of
     * the death goes into a skinned corpse instead. [stated] "any kind of inventory on the player has to be stored" —
     * vanilla's inventory, armour and off hand, and whatever accessory, curio, trinket or backpack mods drop.
     */
    public static void beginPlayerDeath(ServerLevel level, net.minecraft.server.level.ServerPlayer player, DamageSource source) {
        if (!HuntDirector.isOwnHunterKill(level, player, source)) {
            return;
        }

        CAPTURE.set(new Capture(player, null, new ArrayList<>(), true, graveModInstalled()));
    }

    /** End of the player's {@code die}: lay the corpse out where they fell. */
    public static void finishPlayerDeath(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        var capture = CAPTURE.get();

        if (capture == null || !capture.wholeDeath() || capture.victim() != player) {
            return;
        }

        CAPTURE.remove();

        // ⚠ A mod may cancel a death (a totem-style save). Then nothing died, nothing dropped, and no corpse is left.
        if (player.getHealth() > 0.0F) {
            for (var stack : capture.items()) {
                Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), stack);
            }

            return;
        }

        var placement = findPlacement(level, player.blockPosition(), level.random);

        if (placement.isEmpty() || !place(level, placement.get(), capture.items(), level.random)) {
            for (var stack : capture.items()) {
                Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), stack);
            }
        }
    }

    // ---------------------------------------------------------------- the kill hook (called from the mixins)

    /** Head of {@code dropAllDeathLoot}: arm the capture if this death earns a corpse. */
    public static void beginDeathDrops(ServerLevel level, LivingEntity victim, DamageSource source) {
        // ⚠ A player's whole-death capture is already armed around this — leave it alone.
        var existing = CAPTURE.get();

        if (existing != null && existing.wholeDeath()) {
            return;
        }

        CAPTURE.remove();

        if (!isSkinnableKill(victim, source) || isObserved(level, victim, source.getEntity())) {
            return;
        }

        findPlacement(level, victim.blockPosition(), level.random)
            .ifPresent(placement -> CAPTURE.set(new Capture(victim, placement, new ArrayList<>(), false, false)));
    }

    /**
     * {@code addFreshEntity}: while a capture is armed, an item entity becomes corpse contents instead of a drop.
     *
     * @return true if the item was taken
     */
    public static boolean tryCapture(Entity entity) {
        var capture = CAPTURE.get();

        if (capture == null || capture.trophyOnly() || !(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) {
            return false;
        }

        if (item.distanceToSqr(capture.victim()) > CAPTURE_RADIUS * CAPTURE_RADIUS) {
            return false;
        }

        var stack = item.getItem();

        if (!stack.isEmpty()) {
            capture.items().add(stack.copy());
        }

        return true;
    }

    /** Return of {@code dropAllDeathLoot}: close the capture and lay the body out. */
    public static void finishDeathDrops(ServerLevel level, LivingEntity victim) {
        var capture = CAPTURE.get();

        // ⚠ A player's whole-death capture is finished at the end of die, not here.
        if (capture != null && capture.wholeDeath()) {
            return;
        }

        CAPTURE.remove();

        if (capture == null || capture.victim() != victim || capture.placement() == null) {
            return;
        }

        if (!place(level, capture.placement(), capture.items(), level.random)) {
            // The spot was taken between the head and the return — give the drops back to the ground.
            for (var stack : capture.items()) {
                Containers.dropItemStack(level, victim.getX(), victim.getY(), victim.getZ(), stack);
            }
        }
    }

    // ---------------------------------------------------------------- rules

    public static boolean isSkinnableKill(LivingEntity victim, DamageSource source) {
        return source.getEntity() instanceof Yautja
            && victim.getType().is(PredatorEntityTypeTags.SKINNABLE)
            && !victim.isBaby();
    }

    /**
     * Is any player watching? [stated] "simulated battles or when the player isnt looking directly at the predator or
     * nearby."
     * <ul>
     * <li>A player within {@link #NEARBY_RADIUS} of the victim counts as watching, eyes or not.</li>
     * <li>Further out, up to {@link #SIGHT_RADIUS}: watching if they have clear line of sight to the victim OR the
     * killer, AND that one is inside their view cone.</li>
     * </ul>
     * Spectators never count.
     */
    public static boolean isObserved(ServerLevel level, LivingEntity victim, @Nullable Entity killer) {
        var cosLimit = Math.cos(Math.toRadians(VIEW_HALF_ANGLE_DEGREES));

        for (var player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }

            var distance = player.distanceTo(victim);

            if (distance <= NEARBY_RADIUS) {
                return true;
            }

            if (distance > SIGHT_RADIUS) {
                continue;
            }

            if (seesInCone(player, victim, cosLimit) || (killer instanceof LivingEntity living && seesInCone(player, living, cosLimit))) {
                return true;
            }
        }

        return false;
    }

    private static boolean seesInCone(LivingEntity viewer, LivingEntity target, double cosLimit) {
        var look = viewer.getViewVector(1.0F);
        var toTarget = target.getBoundingBox().getCenter().subtract(viewer.getEyePosition());
        var length = toTarget.length();

        if (length < 1.0E-4) {
            return true;
        }

        return look.dot(toTarget.scale(1.0 / length)) >= cosLimit && viewer.hasLineOfSight(target);
    }

    // ---------------------------------------------------------------- placement

    /**
     * Finds a spot near {@code origin}. Hanging is tried first everywhere in range — [stated] "hanging if possible if
     * not then its on the ground" — and the nearest valid spot of the chosen kind wins.
     */
    public static Optional<Placement> findPlacement(ServerLevel level, BlockPos origin, RandomSource random) {
        var near = findPlacement(level, origin, SEARCH_RADIUS, -2, 3);

        // ⚠ Over water there is nothing to stand a body on within three blocks — water is never a free spot and has no
        // sturdy top — so a kill over a lake or river left no corpse at all ([tester] "when above water preds dont
        // create a corpse for the loot"). Widened to the nearest bank, with more depth for a body that sank. Only open
        // water with no bank in reach still drops the loot loose.
        if (near.isPresent()) {
            return near;
        }

        var bank = findPlacement(level, origin, WIDE_SEARCH_RADIUS, -WIDE_SEARCH_DEPTH, 4);

        // [stated] the order: hanging, then the bank, "and then the water float if both fail".
        return bank.isPresent() ? bank : findFloat(level, origin);
    }

    /** How far up a body that died underwater may rise to reach the surface. */
    public static final int FLOAT_RISE = 16;

    /**
     * The nearest water surface to {@code origin}: a water SOURCE with open air above it, within {@link #SEARCH_RADIUS}
     * across and from {@link #WIDE_SEARCH_DEPTH} below to {@link #FLOAT_RISE} above. ⚠ Sources only — waterlogging a
     * flowing block would turn it into a new source and change how the water runs.
     */
    private static Optional<Placement> findFloat(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (var dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (var dy = -WIDE_SEARCH_DEPTH; dy <= FLOAT_RISE; dy++) {
                    var pos = origin.offset(dx, dy, dz);
                    var distance = pos.distSqr(origin);

                    if (distance < bestDistance && isFloatSpot(level, pos)) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }

        return best == null ? Optional.empty() : Optional.of(new Placement(best, false, true));
    }

    private static boolean isFloatSpot(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isInWorldBounds(pos.above())) {
            return false;
        }

        var state = level.getBlockState(pos);
        var above = level.getBlockState(pos.above());

        return state.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)
            && state.canBeReplaced()
            && above.getFluidState().isEmpty()
            && (above.isAir() || above.canBeReplaced());
    }

    /** How far the search widens when nothing is within {@link #SEARCH_RADIUS} — enough to reach a bank. */
    public static final int WIDE_SEARCH_RADIUS = 10;

    /** How far down the widened search looks. */
    public static final int WIDE_SEARCH_DEPTH = 6;

    private static Optional<Placement> findPlacement(ServerLevel level, BlockPos origin, int radius, int minDy, int maxDy) {
        BlockPos bestHang = null;
        BlockPos bestGround = null;
        var bestHangDistance = Double.MAX_VALUE;
        var bestGroundDistance = Double.MAX_VALUE;

        for (var dy = minDy; dy <= maxDy; dy++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    var pos = origin.offset(dx, dy, dz);
                    var distance = pos.distSqr(origin);

                    if (!isFree(level, pos)) {
                        continue;
                    }

                    if (distance < bestHangDistance && isFree(level, pos.below()) && SkinnedCorpseBlock.canHangAt(level, pos)) {
                        bestHang = pos.immutable();
                        bestHangDistance = distance;
                    }

                    if (distance < bestGroundDistance && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        bestGround = pos.immutable();
                        bestGroundDistance = distance;
                    }
                }
            }
        }

        if (bestHang != null) {
            return Optional.of(new Placement(bestHang, true));
        }

        return bestGround == null ? Optional.empty() : Optional.of(new Placement(bestGround, false));
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    /**
     * Puts a corpse down and fills it.
     *
     * @return false if the spot is no longer free; nothing was placed and the caller keeps the items
     */
    public static boolean place(ServerLevel level, Placement placement, List<ItemStack> contents, RandomSource random) {
        var pos = placement.pos();

        if (placement.floating() ? !isFloatSpot(level, pos) : !isFree(level, pos)) {
            return false;
        }

        var orientation = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        var state = PredatorBlocks.SKINNED_CORPSE.get()
            .defaultBlockState()
            .setValue(SkinnedCorpseBlock.HANGING, placement.hanging())
            .setValue(SkinnedCorpseBlock.ORIENTATION, orientation)
            .setValue(SkinnedCorpseBlock.FLOATING, placement.floating())
            .setValue(SkinnedCorpseBlock.WATERLOGGED, placement.floating());

        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }

        // [stated] the flesh-tearing sound is for "breaking or placing the corpse".
        // 0.8 pitch: the file is authored for vanilla's block-break pitch (see SkinnedCorpseSoundType).
        level.playSound(null, pos, PredatorSoundEvents.CORPSE_BREAK.get(), SoundSource.BLOCKS, 1.0F, 0.8F);

        if (level.getBlockEntity(pos) instanceof SkinnedCorpseBlockEntity corpse) {
            var center = Vec3.atCenterOf(pos);

            for (var leftover : corpse.fill(contents)) {
                Containers.dropItemStack(level, center.x, center.y, center.z, leftover);
            }
        }

        return true;
    }

    // ---------------------------------------------------------------- the Hunter's calling card (phase 1)

    /**
     * What the phase-1 corpse carries. [stated] "the body will have an iron sword inside as well as dog tags with a
     * random name and military rank up to captain." The tags need avp_human; without it the sword goes in alone.
     */
    public static List<ItemStack> callingCardContents(RandomSource random) {
        var contents = new ArrayList<ItemStack>();
        contents.add(new ItemStack(Items.IRON_SWORD));
        DogTags.create(random).ifPresent(contents::add);

        return contents;
    }

    /**
     * Leaves the Hunter's calling card near {@code near}.
     *
     * @return where it went, or empty if there was nowhere to put it
     */
    public static Optional<BlockPos> placeCallingCard(ServerLevel level, BlockPos near) {
        return placeCallingCard(level, findPlacement(level, near, level.random));
    }

    /** Nearest-to-the-bed is too tidy: the card goes this far from it — close enough to be found on waking. */
    public static final int BED_CARD_MIN_DISTANCE = 3;

    public static final int BED_CARD_MAX_DISTANCE = 7;

    /**
     * Leaves the calling card somewhere around a bed, not beside it — [tester] "corpses / calling cards should be
     * placed in the vicinity of a bed rather than next to it". A random spot {@link #BED_CARD_MIN_DISTANCE} to
     * {@link #BED_CARD_MAX_DISTANCE} blocks away, preferring one that can be seen from the bed, and hanging over lying,
     * as everywhere else. Nowhere in that ring: as close to the bed as there is room, as before.
     */
    public static Optional<BlockPos> placeCallingCardNearBed(ServerLevel level, BlockPos bed) {
        var random = level.random;
        var from = Vec3.atCenterOf(bed).add(0.0, 0.6, 0.0);
        var hangSeen = new ArrayList<BlockPos>();
        var groundSeen = new ArrayList<BlockPos>();
        var hangHidden = new ArrayList<BlockPos>();
        var groundHidden = new ArrayList<BlockPos>();
        var minSqr = BED_CARD_MIN_DISTANCE * BED_CARD_MIN_DISTANCE;
        var maxSqr = BED_CARD_MAX_DISTANCE * BED_CARD_MAX_DISTANCE;

        for (var dy = -2; dy <= 3; dy++) {
            for (var dx = -BED_CARD_MAX_DISTANCE; dx <= BED_CARD_MAX_DISTANCE; dx++) {
                for (var dz = -BED_CARD_MAX_DISTANCE; dz <= BED_CARD_MAX_DISTANCE; dz++) {
                    var flat = dx * dx + dz * dz;

                    if (flat < minSqr || flat > maxSqr) {
                        continue;
                    }

                    var pos = bed.offset(dx, dy, dz);

                    if (!isFree(level, pos)) {
                        continue;
                    }

                    var hang = isFree(level, pos.below()) && SkinnedCorpseBlock.canHangAt(level, pos);
                    var ground = !hang && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);

                    if (!hang && !ground) {
                        continue;
                    }

                    var seen = level.clip(
                        new net.minecraft.world.level.ClipContext(
                            from,
                            Vec3.atCenterOf(pos),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            net.minecraft.world.phys.shapes.CollisionContext.empty()
                        )
                    ).getType() == net.minecraft.world.phys.HitResult.Type.MISS;

                    (hang ? (seen ? hangSeen : hangHidden) : (seen ? groundSeen : groundHidden)).add(pos.immutable());
                }
            }
        }

        for (var pool : List.of(hangSeen, groundSeen, hangHidden, groundHidden)) {
            if (!pool.isEmpty()) {
                var pos = pool.get(random.nextInt(pool.size()));
                var placement = new Placement(pos, pool == hangSeen || pool == hangHidden);

                return placeCallingCard(level, Optional.of(placement));
            }
        }

        return placeCallingCard(level, bed);
    }

    private static Optional<BlockPos> placeCallingCard(ServerLevel level, Optional<Placement> placement) {
        if (placement.isEmpty() || !place(level, placement.get(), callingCardContents(level.random), level.random)) {
            return Optional.empty();
        }

        return Optional.of(placement.get().pos());
    }
}
