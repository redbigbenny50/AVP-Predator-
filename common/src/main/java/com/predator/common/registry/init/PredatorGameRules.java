package com.predator.common.registry.init;

import com.blib.internal.mixin.MixinGameRulesAccessor;
import com.blib.internal.mixin.MixinGameRulesBooleanValueAccessor;
import com.predator.common.gameplay.gauntlet.destruct.GauntletDestructRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * Gamerules this mod adds.
 * <h2>Why a gamerule rather than a config option</h2> It is per-world, it survives a restart, it syncs to clients on
 * its own, and an operator can flip it mid-session without editing a file or relaunching. For something you turn on to
 * look at something and off again, that is exactly the right shape.
 * <p>
 * ⚠ Registered through BLib's mixin accessors, the same way {@code BLibGameRules} does it. Vanilla's
 * {@code GameRules.register} is private; these invokers are the established route in this ecosystem, so avp_predator
 * does not need a mixin of its own.
 * <p>
 * ⚠ Gamerule registration happens in a STATIC initialiser, which means this class must be TOUCHED during mod setup or
 * the rule silently will not exist. {@code PredatorCommands} references it, which is enough.
 */
public final class PredatorGameRules {

    /**
     * Whether yautja cloak at all.
     * <p>
     * Default true — cloaking is the predator. Turning it off is a testing and cinematography aid: it is impossible to
     * judge an animation on something you cannot see, which is the whole reason this exists.
     * <p>
     * ⚠ Scoped to the MOB. A player's cloaking device is unaffected — that is an item the player chose to use, and
     * silently disabling it through a rule named for the predator mob would be a surprise.
     */
    public static final GameRules.Key<GameRules.BooleanValue> PREDATOR_CLOAKING = MixinGameRulesAccessor
        .blib$register(
            "predatorCloaking",
            GameRules.Category.MOBS,
            MixinGameRulesBooleanValueAccessor.blib$create(true)
        );

    /**
     * {@code prednuke}: the gauntlet self-destruct system. [stated] "when its off it disables the self destruct system
     * when its on its as planned. its on by default for single player but its off by default for multiplayer."
     * <p>
     * ⚠ A game rule has ONE static default — this one is {@code true}, the single-player half. The multiplayer half is
     * {@link #applySingleplayerDefault}: on a DEDICATED server the rule is forced off the first time each world starts
     * (recorded in the destruct registry's saved data), and never touched again, so an op's
     * {@code /gamerule prednuke true} sticks across restarts.
     */
    public static final GameRules.Key<GameRules.BooleanValue> PRED_NUKE = MixinGameRulesAccessor
        .blib$register(
            "prednuke",
            GameRules.Category.MISC,
            MixinGameRulesBooleanValueAccessor.blib$create(true)
        );

    /**
     * Lets a CREATIVE player open a yautja's inventory by right-clicking it empty-handed.
     * <p>
     * ⚠⚠ DEFAULT OFF, unlike the other two. This is a development tool for checking how a yautja holds and swings a
     * given weapon — [stated] "i have no idea how it looks using the whip" — not a feature. Off by default means it can
     * ship harmlessly; flip it per world with {@code /gamerule yautjaDebugInventory true}.
     */
    public static final GameRules.Key<GameRules.BooleanValue> YAUTJA_DEBUG_INVENTORY = MixinGameRulesAccessor
        .blib$register(
            "yautjaDebugInventory",
            GameRules.Category.MISC,
            MixinGameRulesBooleanValueAccessor.blib$create(false)
        );

    /**
     * {@code gunBalancing}: whether TACZ and Point Blank gunfire is balanced against this suite's own guns. ON by
     * default. [stated] the TACZ and Point Blank balance "need to apply to this module as well along with the game rule
     * to disable them" — and, Oct 4, ONE rule shared with avp_alien rather than one per mod. ON: the parity
     * multipliers, the shared 160 dps budget and the hit floor apply to yautja (see GunDamageParity). OFF: those mods
     * deal their own unmodified damage. avp_human's guns are never touched either way.
     * <p>
     * ⚠⚠ SHARED WITH avp_alien, WHICH REGISTERS THE SAME ID. Minecraft refuses a second registration of a rule id with
     * a crash at startup, so whichever of the two mods loads first registers it and the other finds it and uses the
     * same key — see {@link #sharedBoolean}. avp_alien does exactly the same, so the order the mods load in does not
     * matter.
     */
    public static final GameRules.Key<GameRules.BooleanValue> GUN_BALANCING = sharedBoolean("gunBalancing", GameRules.Category.MOBS, true);

    /**
     * {@return the boolean rule {@code id}} — the one already registered by a sibling mod if there is one, otherwise a
     * new one registered here.
     * <p>
     * ⚠ Locked on {@code GameRules.class}, the same monitor avp_alien locks, so the look-up and the registration are
     * one step even if the two mods are set up on different threads — otherwise both could look, both find nothing, and
     * both register.
     */
    private static GameRules.Key<GameRules.BooleanValue> sharedBoolean(String id, GameRules.Category category, boolean defaultValue) {
        synchronized (GameRules.class) {
            @SuppressWarnings("unchecked")
            GameRules.Key<GameRules.BooleanValue>[] found = new GameRules.Key[1];

            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {

                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                    if (key.getId().equals(id)) {
                        found[0] = key;
                    }
                }
            });

            if (found[0] != null) {
                return found[0];
            }

            return MixinGameRulesAccessor.blib$register(id, category, MixinGameRulesBooleanValueAccessor.blib$create(defaultValue));
        }
    }

    public static boolean isYautjaDebugInventoryEnabled(Level level) {
        return level.getGameRules().getBoolean(YAUTJA_DEBUG_INVENTORY);
    }

    public static boolean isSelfDestructEnabled(Level level) {
        return level.getGameRules().getBoolean(PRED_NUKE);
    }

    /** Registered on {@code onServerStarted}; see {@link #PRED_NUKE}. */
    public static void applySingleplayerDefault(MinecraftServer server) {
        if (server.isDedicatedServer() && GauntletDestructRegistry.get(server).markPrednukeDefaulted()) {
            server.getGameRules().getRule(PRED_NUKE).set(false, server);
        }
    }

    /**
     * Does nothing except guarantee this class has been loaded, which is what runs the registration above.
     * <p>
     * ⚠ Called from {@code Predator.runInitialization}. It looks pointless and is not: without it the rule would
     * register whenever something first happened to reference the class, which is AFTER worlds are built — so it would
     * exist in code and be absent from {@code /gamerule}, silently doing nothing.
     */
    public static void touch() {
        // Intentionally empty. Loading the class is the whole point.
    }

    private PredatorGameRules() {
        throw new UnsupportedOperationException();
    }
}
