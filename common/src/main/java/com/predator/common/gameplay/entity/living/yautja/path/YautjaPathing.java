package com.predator.common.gameplay.entity.living.yautja.path;

import com.blib.api.common.pathfinding.v1.cache.TerrainCacheRegistry;
import com.blib.api.common.pathfinding.v1.evaluator.PathBlockBreakPolicy;
import com.blib.api.common.pathfinding.v1.evaluator.PathBlockBreakingConfig;
import com.blib.api.common.pathfinding.v1.evaluator.PathCrawlConfig;
import com.blib.api.common.pathfinding.v1.evaluator.PathWaterConfig;
import com.blib.api.common.pathfinding.v1.evaluator.TerrainEvaluatorConfig;
import com.blib.api.common.pathfinding.v1.feature.PathfindingFeature;
import com.blib.api.common.pathfinding.v1.feature.PathfindingProfile;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigator;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorConfig;
import com.blib.api.common.pathfinding.v1.search.SearchConfig;
import com.blib.api.common.pathfinding.v1.terrain.TerrainClassifiers;
import com.blib.api.common.pathfinding.v1.terrain.TerrainType;
import net.minecraft.world.level.Level;

/**
 * The yautja's BLib pathfinding, in two flavours.
 * <h2>Why two navigators and not one</h2> ⚠⚠ His ruling on water is CONDITIONAL, not a single setting: "If trying to
 * stay cloaking and stalking prey it would avoid water but closing the distance or chasing something it will swim
 * through water fine. Also it would dive after anyone trying to hide underwater as well."
 * <p>
 * A cost dial cannot express that, because the same route has to be cheap during a chase and unwalkable during a stalk.
 * So there are two whole navigators with different terrain models, and {@code Yautja.getPathNavigator} picks between
 * them by whether it currently has prey. This mirrors {@code Xenomorph}, which carries a second navigator for hive
 * intruders for the same reason.
 * <ul>
 * <li>{@link #createStalkNavigator} — {@code GROUND_ONLY} with water pathing DISABLED. It will not plan a route that
 * enters water at all, so a cloaked hunter keeps its field up and its feet dry. (Water shorts the cloak out; see
 * {@code PredatorCloakManager}. Routing around it is not fussiness, it is self-preservation.)</li>
 * <li>{@link #createPursuitNavigator} — {@code GROUND_AND_WATER} at equal cost to ground, every water feature on. It
 * swims, and because {@code waterVerticalSwim} is part of that feature set, it dives after anything that thinks the
 * bottom of a lake is cover.</li>
 * </ul>
 * <h2>What it can break</h2> His ruling: glass, doors and wood, nothing else. ⚠ That CANNOT be expressed as a hardness
 * ceiling — an oak door is 3.0 and stone is only 1.5, so any ceiling loose enough to admit doors admits stone and
 * cobblestone too. The gate is therefore a {@link PathBlockBreakPolicy} over block tags, with the hardness ceiling left
 * as a cheap backstop.
 */
public final class YautjaPathing {

    /**
     * THE TOP OF THE HARDNESS WINDOW — the yautja may break anything from 0 up to this, unless the blacklist below says
     * otherwise.
     * <p>
     * [stated] "they are supposed to punch through stone its metal and industrial blocks they wouldn't be able to. I
     * think we need to set a hardness window they can break plus a black list to further refine blocks in the hardness
     * window we dont want broken."
     * <p>
     * [stated] "i would say raise it to 4.5". 4.5 takes in glass 0.3, dirt 0.5, stone / blackstone / stone bricks 1.5,
     * cobblestone / planks / logs 2.0, deepslate, ores and end stone 3.0, the deepslate building blocks (cobbled,
     * polished, bricks, tiles, chiseled, cracked) and the stonecutter 3.5, cobwebs 4.0, and all eight deepslate ores
     * 4.5. It leaves out iron blocks, iron doors and anvils 5.0, ancient debris 30 and obsidian 50. (That band was read
     * from vanilla's own block definitions, not recalled.)
     * <p>
     * ⚠ The BOTTOM of the window is BLib's own: PathBlockBreakingConfig.canBreak refuses anything below 0, which is how
     * vanilla marks bedrock, barriers and end portals unbreakable. Zero itself is allowed, so a slime block or TNT in
     * the way does not stop a hunter.
     */
    private static final float MAX_BREAKABLE_HARDNESS = 4.5F;

    /**
     * How many blocks it will chew through in a single step of a path.
     * <p>
     * 🚨 THREE, NOT TWO. Two was copied from the xenomorphs, which are shorter. BLib counts EVERY block the creature's
     * hitbox would overlap at the new position (UnifiedTerrainEvaluator), and a yautja's box is 1 wide and 2.48 tall —
     * three blocks high. So a wall of glass from floor to above its head needed three breaks, and at two the pathfinder
     * rejected it outright (BLOCK_BREAK_LIMIT_EXCEEDED): it could never plan a way through a full-height glass wall.
     */
    private static final int MAX_BLOCKS_PER_EDGE = 3;

    private static final float FLAT_COST_PER_BLOCK = 4.0F;

    private static final float COST_PER_HARDNESS = 8.0F;

    private static final float DAMAGE_PER_TICK = 50.0F;

    /** Same as the xenomorphs. A yautja will drop a long way rather than take the long way round. */
    private static final int MAX_FALL_DISTANCE = 14;

    /** In geo units, matching the entity's 0.7 x 2.48 hitbox rounded up. */
    private static final int ENTITY_WIDTH = 1;

    private static final int ENTITY_HEIGHT = 3;

    /**
     * How deep the water has to be before it counts as swimming rather than wading, as a fraction of entity height. The
     * xenomorph figure, and it reads correctly on a 2.48-block body: knee-deep is still walking.
     */
    private static final int SWIM_HEIGHT = (int) Math.ceil(ENTITY_HEIGHT * 0.4F);

    /**
     * Blocks of headroom a crawling yautja needs.
     * <p>
     * ⚠ 1, not 2. At 2 this would cover gaps it can already WALK through, and every one of them would be planned as a
     * crawl for no reason. 1 makes crawling mean the thing it should: a gap too low to stand in.
     */
    private static final int CRAWL_HEIGHT = 1;

    /**
     * How much more a metre of water costs than a metre of dry ground while stalking. High enough that it takes a very
     * long detour before wading wins, which is his "it would avoid water" — and finite, so it is never stuck. (The
     * xenomorphs use 1.5, which is a mild preference rather than an avoidance.)
     */
    private static final float STALK_WATER_COST = 8.0F;

    /**
     * The BLACKLIST half of the rule. BLib applies the hardness window itself (see MAX_BREAKABLE_HARDNESS), then asks
     * this.
     * <p>
     * ⚠⚠ STATE-ONLY ON PURPOSE. BLib runs this during PATH PLANNING, which is asynchronous — it must not touch block
     * entities or anything else off the main thread. Every test below reads only the block state.
     */
    private static final PathBlockBreakPolicy NOT_BLACKLISTED = (level, pos, state) -> !isBlacklisted(state);

    /**
     * {@return whether a yautja may break this block} — the whole rule, window AND blacklist, for code that breaks
     * blocks outside BLib's path executor (the climb breaking through a ceiling).
     */
    public static boolean canBreak(net.minecraft.world.level.block.state.BlockState state) {
        var hardness = state.getBlock().defaultDestroyTime();

        return hardness >= 0.0F && hardness <= MAX_BREAKABLE_HARDNESS && !isBlacklisted(state);
    }

    /**
     * {@return whether a block is off limits even though it sits inside the hardness window}
     * <p>
     * In order:
     * <ol>
     * <li>DOORS, TRAPDOORS AND FENCE GATES — opened, never broken. Letting it break them would quietly undo that
     * ruling: it would stop opening doors and start smashing them.</li>
     * <li>ANYTHING WITH A BLOCK ENTITY — chests, barrels, furnaces, hoppers, shulker boxes, signs, beds, spawners. A
     * chest is only 2.5, well inside the window, and breaking one SPILLS ITS CONTENTS: vanilla drops a container's
     * items from the container's own removal code, so BLib's drop-nothing break does not protect them. Checked from the
     * state alone, which is safe during async planning.</li>
     * <li>METAL, BY SOUND — [stated] "metal". Several metal blocks sit inside the window (gold and copper blocks are
     * 3.0), so the hardness cut-off alone cannot keep them out. Judging by the block's SOUND TYPE catches every block
     * that is metal — iron, gold, copper, netherite, chains, anvils, vaults — AND other mods' metal blocks, with no
     * list to maintain. It is the same test the smart disc uses to pick its metal impact sound. ⚠ LANTERNS ARE
     * DELIBERATELY LEFT OFF — [stated] "yes its metal but its a weak metal which they should be able to break". Their
     * LANTERN sound type is used only by the lantern and the soul lantern, so leaving it out frees exactly those two
     * and nothing else.</li>
     * <li>avp_human's INDUSTRIAL FAMILIES — [stated] "in avp human steel, plastic, titanium, ferroaluminum, industrial
     * glass, industrial concrete shouldnt break". Read from avp_human's OWN block tags, one per family, so a block
     * added to any of those families later is protected with no change here. Other avp_human blocks — padding, for one
     * — fall back to the ordinary rules.</li>
     * <li>THE TAG, avp_predator:yautja_unbreakable — for anything else, one datagen line each, no code.</li>
     * </ol>
     */
    public static boolean isBlacklisted(net.minecraft.world.level.block.state.BlockState state) {
        var block = state.getBlock();

        if (
            block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock
        ) {
            return true;
        }

        if (state.hasBlockEntity()) {
            return true;
        }

        if (isMetal(state.getSoundType())) {
            return true;
        }

        for (var family : INDUSTRIAL_FAMILIES) {
            if (state.is(family)) {
                return true;
            }
        }

        return state.is(com.predator.common.registry.tag.PredatorBlockTags.YAUTJA_UNBREAKABLE);
    }

    /**
     * avp_human's industrial block families, by avp_human's own tags.
     * <p>
     * ⚠⚠ BUILT FROM STRINGS, NEVER FROM avp_human's CLASSES. avp_human is an optional sibling mod: referencing its tag
     * constants would compile and then fail wherever it is absent. A tag named by string is simply EMPTY when avp_human
     * is not installed, so this list costs nothing without it. Checked against avp_human's generated tags: steel 35,
     * plastic 256, titanium 36, ferroaluminum 34, industrial concrete 64 blocks, industrial glass all 119 through its
     * two sub-tags.
     */
    private static final java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>> INDUSTRIAL_FAMILIES =
        java.util.stream.Stream.of("steel", "plastic", "titanium", "ferroaluminum", "industrial_glass", "industrial_concrete")
            .map(
                path -> net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", path)
                )
            )
            .toList();

    private static boolean isMetal(net.minecraft.world.level.block.SoundType sound) {
        return sound == net.minecraft.world.level.block.SoundType.METAL
            || sound == net.minecraft.world.level.block.SoundType.ANVIL
            || sound == net.minecraft.world.level.block.SoundType.CHAIN
            || sound == net.minecraft.world.level.block.SoundType.COPPER
            || sound == net.minecraft.world.level.block.SoundType.COPPER_BULB
            || sound == net.minecraft.world.level.block.SoundType.COPPER_GRATE
            || sound == net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK
            || sound == net.minecraft.world.level.block.SoundType.ANCIENT_DEBRIS
            || sound == net.minecraft.world.level.block.SoundType.LODESTONE
            || sound == net.minecraft.world.level.block.SoundType.VAULT
            || sound == net.minecraft.world.level.block.SoundType.TRIAL_SPAWNER
            || sound == net.minecraft.world.level.block.SoundType.HEAVY_CORE;
    }

    private static final PathBlockBreakingConfig BLOCK_BREAKING = new PathBlockBreakingConfig(
        true,
        MAX_BLOCKS_PER_EDGE,
        MAX_BREAKABLE_HARDNESS,
        FLAT_COST_PER_BLOCK,
        COST_PER_HARDNESS,
        DAMAGE_PER_TICK,
        NOT_BLACKLISTED
    );

    private YautjaPathing() {
        throw new UnsupportedOperationException();
    }

    /**
     * Cloaked and hunting: water is punitively expensive, but not impossible.
     * <p>
     * ⚠⚠ THIS USED TO USE {@code GROUND_ONLY} WITH WATER PATHING DISABLED, AND THAT COULD STRAND A YAUTJA.
     * {@code TerrainClassifiers.classifyAsGround} returns NULL for a liquid block, so under GROUND_ONLY a yautja
     * already standing in water has no valid START node — every path request fails, the wander action aborts on NO_PATH
     * forever, and the only thing left moving it is the float goal bobbing it at the surface. One knocked into a lake
     * with nothing to hunt would have stayed there.
     * <p>
     * A heavy terrain cost expresses the same intent safely: a dry detour up to {@link #STALK_WATER_COST} times longer
     * still wins, so it routes around anything it can go around, and it can still walk out of water it is already in.
     */
    public static PathNavigator createStalkNavigator(Level level, float followRange) {
        var evaluator = baseEvaluator()
            .addTerrain(TerrainType.GROUND, 1.0F)
            .addTerrain(TerrainType.WATER, STALK_WATER_COST)
            .withTerrainClassifier(TerrainClassifiers.GROUND_AND_WATER)
            .withWaterConfig(PathWaterConfig.enabled(SWIM_HEIGHT))
            .build();

        return build(level, evaluator, followRange);
    }

    /** Committed to a target: water is just more ground, and it will follow prey under the surface. */
    public static PathNavigator createPursuitNavigator(Level level, float followRange) {
        var evaluator = baseEvaluator()
            .addTerrain(TerrainType.GROUND, 1.0F)
            // ⚠ EQUAL to ground, deliberately. The xenomorphs use 1.5 to keep them on land; a yautja mid-chase has
            // already decided the water is worth it, and a malus here would make it hesitate at the shoreline.
            .addTerrain(TerrainType.WATER, 1.0F)
            .withTerrainClassifier(TerrainClassifiers.GROUND_AND_WATER)
            .withWaterConfig(PathWaterConfig.enabled(SWIM_HEIGHT))
            .build();

        return build(level, evaluator, followRange);
    }

    private static TerrainEvaluatorConfig.Builder baseEvaluator() {
        return TerrainEvaluatorConfig.builder()
            .withEntitySize(ENTITY_WIDTH, ENTITY_HEIGHT)
            // No crawling: nothing in the moveset ducks through a one-block gap, and the crawl clip is unbuilt.
            // ⚠ A yautja is 2.48 blocks tall, so a 2-block gap is impassable standing. Crawling lets it follow
            // prey through vents and crawlspaces instead of routing the long way round. The config's cost
            // malus means it still PREFERS to walk — crawling is what it does when the alternative is much
            // longer, not a habit.
            //
            // ⚠ Set on baseEvaluator, so BOTH navigators get it — stalking and pursuing alike.
            .withCrawlConfig(PathCrawlConfig.enabled(CRAWL_HEIGHT))
            .withBlockBreakingConfig(BLOCK_BREAKING)
            .withMaxFallDistance(MAX_FALL_DISTANCE)
            // ⚠⚠ FALSE, CORRECTED FROM HIS EARLIER RULING. He first said "it's a hunter, doors are nothing";
            // the lore answer is the opposite — a yautja neither opens nor breaks a door. A door STOPS it.
            // It can still hit you through one (see the melee note in Yautja.registerGoals), which is what
            // makes a door cover rather than safety.
            .withCanOpenDoors(false);
    }

    private static PathNavigator build(Level level, TerrainEvaluatorConfig evaluator, float followRange) {
        var config = PathNavigatorConfig.builder(evaluator)
            .withSearchConfig(SearchConfig.fromFollowRange(followRange))
            // ⚠⚠ LEGACY_PERMISSIVE SETS blockBreaking = FALSE, WHICH IS WHY NOTHING WAS EVER BROKEN.
            // The evaluator gate is:
            // features.blockBreaking() && config.getBlockBreakingConfig().enabled() && ...
            // so the PathBlockBreakingConfig above — the weak-blocks policy, the hardness ceiling, all of it —
            // was inert no matter what it said. The profile is the master switch and the config is only the
            // detail. Turning the feature on explicitly is the whole fix.
            .withPathfindingFeatures(
                PathfindingProfile.LEGACY_PERMISSIVE.features()
                    .with(PathfindingFeature.BLOCK_BREAKING, true)
            )
            .build();

        // ⭐ The cache is shared per level PER CLASSIFIER, so the stalk and pursuit navigators get different caches
        // (they classify differently) while every yautja in the world shares each. Building one privately would
        // reclassify the same chunks once per mob.
        return new PathNavigator(
            level,
            config,
            TerrainCacheRegistry.getOrCreate(level, evaluator.getTerrainClassifier())
        );
    }
}
