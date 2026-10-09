package com.predator.common.gameplay.entity.living.yautja;

import com.blib.api.common.codec.v1.BLibCodecs;
import com.blib.api.common.data_sync.v1.model.DataUser;
import com.blib.api.common.goap.v1.GOAPUser;
import com.blib.api.common.inventory.v1.BLibInventory;
import com.blib.api.common.inventory.v1.BLibInventoryHolder;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigator;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorApi;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import com.predator.common.debug.PredatorCasterTestMode;
import com.predator.common.debug.PredatorPathDebug;
import com.predator.common.debug.PredatorPathDiagnostics;
import com.predator.common.gameplay.debug.yautja.YautjaInventoryContainer;
import com.predator.common.gameplay.debug.yautja.YautjaInventoryMenu;
import com.predator.common.gameplay.effect.AdrenalineRushEffect;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaGOAP;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaBodyState;
import com.predator.common.gameplay.entity.living.yautja.caster.CasterState;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaBattleaxeSlamGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaBowGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaCloakGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaDartGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaFallBackGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaNetGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaPlasmaCasterGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaSpearThrowGoal;
import com.predator.common.gameplay.entity.living.yautja.goal.YautjaThrowGoal;
import com.predator.common.gameplay.entity.living.yautja.path.YautjaClimb;
import com.predator.common.gameplay.entity.living.yautja.path.YautjaJump;
import com.predator.common.gameplay.entity.living.yautja.path.YautjaPathing;
import com.predator.common.gameplay.entity.living.yautja.path.YautjaWaterExit;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import com.predator.common.gameplay.item.combistick.CombiStickState;
import com.predator.common.registry.init.PredatorGameRules;
import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Yautja extends Monster implements DataUser, GOAPUser<Yautja>, PathNavigatorUser, BLibInventoryHolder {

    /**
     * Body markings, synced so every observer draws the same yautja.
     * <p>
     * Synched data rather than a mob effect or a server-side map: vanilla broadcasts this to every tracking client
     * automatically, including on first pairing, which is exactly what a per-entity texture choice needs.
     */
    /**
     * ⚠ A SECOND, INDEPENDENT roll from {@link #DATA_VARIANT}, which is the skin. His ruling: a spawn egg can give
     * regular, brush or tiger with EITHER armour set, so all six combinations occur.
     */
    /**
     * The class rank. ⚠ Synced because the client needs it for the deflect chance and, later, a name plate or boss bar
     * — and retrofitting a synced field after those exist is worse than carrying it now.
     */
    private static final EntityDataAccessor<String> DATA_TIER =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<String> DATA_ARMOR_VARIANT =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<String> DATA_VARIANT =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.STRING);

    /**
     * Where the shoulder caster is in its cycle. Synced for the same reason the variant is: the client has to know the
     * moment it changes so it can play the matching clip, and {@code caster.shoot} plays once — a state the client
     * merely guessed at would drop shots or replay them.
     */
    private static final EntityDataAccessor<Byte> DATA_CASTER_STATE =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BYTE);

    /**
     * Where the caster is pointing, in world degrees.
     * <p>
     * Separate from the yautja's own head and body rotation on purpose — the caster tracks its own target, so these two
     * floats are the only thing that tells an observing client that the gun is aimed somewhere the hunter is not
     * looking.
     */
    private static final EntityDataAccessor<Float> DATA_CASTER_AIM_YAW =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> DATA_CASTER_AIM_PITCH =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.FLOAT);

    /**
     * Whether the yautja is currently holding a wall.
     * <p>
     * ⚠ The one piece of locomotion state that HAS to be synced. Everything else the animation selector needs — speed,
     * swimming, airtime — a client can observe for itself, but nothing observable distinguishes ascending a wall from
     * being shoved upward, and the client has to know which clip to play.
     */
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BOOLEAN);

    /**
     * The last attack animation, stamped with a sequence number.
     * <p>
     * ⚠⚠ THE SEQUENCE IS NOT DECORATION. Every attack decision happens on the SERVER while Az commands dispatch on the
     * CLIENT, so the event has to cross as synched data. Sending only the clip id would mean two identical stabs in a
     * row wrote the same byte twice — no change, no packet, and the second stab plays nothing. The low three bits carry
     * the clip, the rest a counter that makes every attack a distinct value.
     */
    private static final EntityDataAccessor<Byte> DATA_ATTACK_ANIMATION =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BYTE);

    /** Blocks per tick below which there is no reliable travel direction to read a pitch from. */
    private static final double SWIM_PITCH_MOVEMENT_FLOOR = 0.002;

    /** Straight up and straight down are both reachable; past vertical would read as a backflip. */
    private static final float SWIM_PITCH_LIMIT = 85.0F;

    /** Per-tick approach fraction. Roughly a third of a second to settle, which reads as a body, not a gimbal. */
    private static final float SWIM_PITCH_SMOOTHING = 0.18F;

    /** NBT key, so {@code /summon avp_predator:yautja_jungle ~ ~ ~ {Variant:"tiger"}} works. */
    private static final String VARIANT_TAG = "Variant";

    private static final String ARMOR_VARIANT_TAG = "ArmorVariant";

    private static final String TIER_TAG = "Tier";

    private final YautjaAnimationDispatcher animationDispatcher;

    /**
     * Client-side only: the caster state the animation track was last told about.
     * <p>
     * ⚠ Not synced and not saved. Az animation commands are dispatched from the client, so the client needs an edge
     * detector — dispatching every tick would restart {@code caster.shoot} on each frame of its own playback.
     */
    private CasterState lastDispatchedCasterState = CasterState.STOWED;

    /**
     * Client-side only: the locomotion clip the full-body track was last told about.
     * <p>
     * Starts null rather than IDLE so the very first client tick always dispatches. Without that, a yautja that spawns
     * standing still would sit in the model's bind pose until it happened to move — the T-pose bug, arrived at by an
     * edge detector that thought it had already sent the idle.
     */
    private YautjaBodyState lastDispatchedBodyState;

    /**
     * Client-side only: how steeply the body is tipped for swimming, in degrees, positive nose-down.
     * <p>
     * ⚠ SMOOTHED rather than read raw each frame. The raw travel angle comes from a one-tick position delta, which
     * jitters hard when the navigator corrects course or the entity clips a block — applied straight to the model that
     * reads as the yautja shuddering. It also has to ease back to level on leaving the water, which a raw value cannot
     * do because there is no travel direction to read once it is standing still.
     */
    private float clientSwimPitch;

    public Yautja(EntityType<? extends Yautja> entityType, Level level) {
        super(entityType, level);
        this.animationDispatcher = new YautjaAnimationDispatcher(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, YautjaVariant.NORMAL.serializedName());
        builder.define(DATA_ARMOR_VARIANT, YautjaArmorVariant.REGULAR.serializedName());
        builder.define(DATA_TIER, YautjaTier.DEFAULT.serializedName());
        builder.define(DATA_CASTER_STATE, CasterState.STOWED.id());
        builder.define(DATA_CASTER_AIM_YAW, 0.0F);
        builder.define(DATA_CASTER_AIM_PITCH, 0.0F);
        builder.define(DATA_CLIMBING, false);
        builder.define(DATA_CRAWLING, false);
        builder.define(DATA_CLIMB_VAULTING, false);
        builder.define(DATA_CLIMB_HANGING, false);
        builder.define(DATA_DODGE_SEQUENCE, (byte) 0);
        builder.define(DATA_HUNTER, false);
        builder.define(DATA_ROAR_SEQUENCE, (byte) 0);
        builder.define(DATA_ATTACK_ANIMATION, (byte) 0);
    }

    public YautjaVariant getVariant() {
        return YautjaVariant.byName(entityData.get(DATA_VARIANT));
    }

    public void setVariant(YautjaVariant variant) {
        entityData.set(DATA_VARIANT, variant.serializedName());
    }

    public YautjaArmorVariant getArmorVariant() {
        return YautjaArmorVariant.byName(entityData.get(DATA_ARMOR_VARIANT));
    }

    public void setArmorVariant(YautjaArmorVariant variant) {
        entityData.set(DATA_ARMOR_VARIANT, variant.serializedName());
    }

    public YautjaTier getTier() {
        return YautjaTier.byName(entityData.get(DATA_TIER));
    }

    /**
     * ⚠ Applies the tier's attributes immediately, and HEALS TO FULL. Raising max health leaves current health where it
     * was, so promoting a wounded yautja would otherwise produce one at 150/550 — technically correct and
     * indistinguishable from a bug.
     */
    public void setTier(YautjaTier tier) {
        entityData.set(DATA_TIER, tier.serializedName());
        applyTierAttributes();
        setHealth(getMaxHealth());
    }

    /**
     * Writes the tier's numbers onto the live attributes.
     * <p>
     * ⚠⚠ SET AT RUNTIME, NOT IN createYautjaAttributes(). That method is STATIC and builds ONE supplier shared by every
     * yautja of this type — there is no entity there to ask what tier it is. Anything per-instance has to be written
     * here, after the entity exists.
     */
    public void applyTierAttributes() {
        var tier = getTier();
        var hunter = isHunter();

        var bonus = huntReturn ? HUNT_RETURN_BONUS : 1.0;

        set(Attributes.MAX_HEALTH, tier.health(hunter) * bonus);
        set(Attributes.ARMOR, tier.armor(hunter) * bonus);
        set(Attributes.ARMOR_TOUGHNESS, tier.toughness(hunter) * bonus);
        set(Attributes.ATTACK_DAMAGE, tier.damage(hunter) * bonus);
        set(Attributes.KNOCKBACK_RESISTANCE, tier.knockbackResistance());

        if (huntedPlayer != null) {
            set(Attributes.FOLLOW_RANGE, HUNTER_FOLLOW_RANGE);
        }
    }

    private void set(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = getAttribute(attribute);

        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Pathfinding and GOAP
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Dry-ground routing, used while hunting rather than chasing.
     * <p>
     * ⚠ Water pathing is DISABLED on this one, so a stalking yautja will not plan a route that gets it wet. That is not
     * fussiness: water shorts the cloak out and starts the overload, so a cloaked hunter walking into a river gives
     * itself away. See {@link YautjaPathing}.
     */
    private PathNavigator stalkNavigator;

    /** Water is just more ground here, and it will dive after anything hiding under the surface. */
    private PathNavigator pursuitNavigator;

    private int lastMeleeAttackTick;

    /**
     * ⭐⭐ THIS METHOD IS THE SWITCH OFF VANILLA NAVIGATION. {@code NeoMoveToPosAction} chooses BLib's pathfinder purely
     * on {@code instanceof PathNavigatorUser} — there is no config flag — so implementing this interface is what routes
     * the yautja through the section-corridor planner, the water features and the terrain cache.
     * <p>
     * ⚠ Which navigator comes back is his water ruling, and it cannot be a cost dial: the same crossing must be
     * unwalkable while stalking and free while chasing, and no single number does both.
     */
    @Override
    public PathNavigatorApi getPathNavigator() {
        var pursuing = getTarget() != null;

        // ⚠ Built on FIRST USE, not in the constructor. Entities are constructed on the client too, and each
        // navigator registers a terrain-classification cache for its level — two caches per yautja that no client
        // ever paths with. Nothing calls this outside server-side GOAP, so lazily is also never.
        if (pursuing) {
            if (pursuitNavigator == null) {
                pursuitNavigator = YautjaPathing.createPursuitNavigator(level(), (float) getAttributeValue(Attributes.FOLLOW_RANGE));
            }

            return pursuitNavigator;
        }

        if (stalkNavigator == null) {
            stalkNavigator = YautjaPathing.createStalkNavigator(level(), (float) getAttributeValue(Attributes.FOLLOW_RANGE));
        }

        return stalkNavigator;
    }

    @Override
    public Graph<Yautja> blib$getGOAPGraphOrNull() {
        return YautjaGOAP.GRAPH;
    }

    @Override
    public Agent.Builder<Yautja> blib$applyGOAPAgentProperties(Agent.Builder<Yautja> builder) {
        return YautjaGOAP.applyAgentProperties(builder);
    }

    /**
     * Server tick before which the yautja counts as NOT bored, so it stands still instead of roaming.
     * <p>
     * ⚠⚠ WITHOUT THIS THE IDLE CLIP NEVER PLAYS. The boredom goal is satisfied by {@code IS_BORED} going false, and
     * IS_BORED was defined purely as "has no target" — which wandering can never change. The wander action's effect was
     * therefore unsatisfiable, the plan never completed, and the yautja roamed non-stop from spawn to death. The wander
     * action sets this on arrival, which lets the plan finish and the hunter rest.
     * <p>
     * Not saved: a yautja loading into a world has nowhere it was going, and resting is the safe default anyway.
     */
    private int restUntilTick;

    // -----------------------------------------------------------------------------------------------------------
    // Weapon rack
    // -----------------------------------------------------------------------------------------------------------

    private static final String NBT_INVENTORY = "inventory";

    /**
     * Slots on the yautja's belt. Six is enough for the whole kit — sword, axe, disc, shuriken and room to grow —
     * without pretending a hunter carries a chest.
     */
    /**
     * ⚠ EIGHT, not six. The spawn kit is five stacks, so a six-slot rack was full after a single stow and the next swap
     * would have had nowhere to put the weapon coming off the hand. Headroom is cheaper than the bug.
     */
    /** ⚠ Public: the debug inventory menu sizes itself from this. */
    /**
     * SIXTEEN — two rows of eight, [stated] as drawn in his mockup for the reworked yautja screen, with room for tribe
     * gear later. The loadout needs nine at worst (potions do not stack). The held weapon sits in the HAND, not here. ⚠
     * Old saves load safely: loading COPIES each saved stack into this inventory, so an 8-slot save fills 8 of 16.
     */
    public static final int INVENTORY_SIZE = 16;

    /** findHealingSlot's answer when the healing item is the one in the HAND rather than the rack. */
    public static final int HAND_HEALING_SLOT = -2;

    /**
     * What it is carrying but not currently holding.
     * <p>
     * ⭐ Same shape as the marine's: a {@code BLibInventory} behind {@code BLibInventoryHolder}, so anything already
     * written against that interface works on a yautja too. What it does NOT copy is the marine's menu, container and
     * screen — see {@code swapToBestWeaponFor}: this rack exists so the hunter can change weapons mid-fight, and a
     * hostile mob has no interaction that would ever open a GUI.
     */
    private final BLibInventory inventory = new BLibInventory(INVENTORY_SIZE);

    /** Server-side: whether caster test mode has this yautja pinned. Not saved — a debug state, not world state. */
    private boolean casterTestFrozen;

    public boolean isCasterTestFrozen() {
        return casterTestFrozen;
    }

    public void setCasterTestFrozen(boolean frozen) {
        this.casterTestFrozen = frozen;
    }

    /** Server-side: the target the drawn weapon was last chosen for. Not saved; a fresh fight re-picks. */
    @Nullable
    private LivingEntity lastMatchedTarget;

    /**
     * Set when a player puts something in this yautja's HAND through the debug menu; cleared when they take it out.
     * <p>
     * [stated] "if i place a weapon in its main hand it has to use that. this is essential for testing." Only the menu
     * sets or clears it — the yautja's own weapon handling never touches it — so the lock always reflects what the
     * tester chose, and a yautja whose hand was left empty swaps freely from its rack as before.
     */
    private boolean debugWeaponLocked;

    // ---------------------------------------------------------------- the hunt (see HuntDirector)

    /** The player this Hunter was sent for, or null for any ordinary yautja. Saved. */
    private @Nullable java.util.UUID huntedPlayer;

    /** Phase 3: the return, armour restored and +10% to every stat. Saved. */
    private boolean huntReturn;

    /**
     * Set by the director each second from the prey's hotbar: [stated] "if the player has no weapons it will fight them
     * bare handed putting away all its weapons including wrist blades". Not saved — it is re-judged within a second.
     */
    private boolean huntBareHanded;

    public @Nullable java.util.UUID getHuntedPlayer() {
        return huntedPlayer;
    }

    public void setHuntedPlayer(@Nullable java.util.UUID player) {
        this.huntedPlayer = player;
    }

    public boolean isHuntReturn() {
        return huntReturn;
    }

    /** Phase 3. Re-applies the tier so the +10% lands at once. */
    public void setHuntReturn(boolean huntReturn) {
        this.huntReturn = huntReturn;
        applyTierAttributes();
    }

    public boolean isHuntBareHanded() {
        return huntBareHanded;
    }

    public void setHuntBareHanded(boolean bareHanded) {
        if (this.huntBareHanded != bareHanded) {
            this.huntBareHanded = bareHanded;
            forceWeaponRepick();
        }
    }

    /** {@return whether its weapons may come out} — everything except a bare-handed fight. */
    public boolean mayUseWeapons() {
        return !huntBareHanded;
    }

    /**
     * {@return whether the shoulder caster may come out} — not in a bare-handed fight, and not once a Hunter's chest
     * piece has broken off: [stated] the chest loss "also disables the caster".
     */
    public boolean mayUseCaster() {
        return mayUseWeapons() && !(isHunter() && !hasChestPiece());
    }

    // ---------------------------------------------------------------- the wounded escape (see HuntDirector.escape)

    /** [stated] "damaged near death it escapes" — at 10% of its health. */
    public static final float ESCAPE_HEALTH_FRACTION = 0.10F;

    /** The phase-2 escape has been used (or could not be). Saved. */
    private boolean huntEscaped;

    /** Set only while a /kill-class hit is being applied, so the escape clamp never refuses one. */
    private boolean bypassingDamage;

    public boolean hasHuntEscaped() {
        return huntEscaped;
    }

    public void setHuntEscaped(boolean escaped) {
        this.huntEscaped = escaped;
    }

    /** {@return whether a hit may not take it below the escape threshold yet} — a phase-2 Hunter that has not fled. */
    private boolean holdsForEscape() {
        return huntedPlayer != null && !huntReturn && !huntEscaped && !bypassingDamage && !level().isClientSide;
    }

    /**
     * ⚠⚠ THE ESCAPE CANNOT BE SKIPPED BY ONE BIG HIT. A blow that would take a phase-2 Hunter from 30% to dead leaves
     * it at 10% instead, and it flees on its next tick. Only health LOSS is clamped — healing and spawning pass
     * straight through — and a /kill (bypasses_invulnerability) is never held back.
     */
    @Override
    public void setHealth(float health) {
        if (holdsForEscape() && health < getHealth()) {
            health = Math.max(health, getMaxHealth() * ESCAPE_HEALTH_FRACTION);
        }

        super.setHealth(health);
    }

    /** The tick its chain whip is ready again. Read by the caster, which leaves a flyer to the whip while it is. */
    private int whipReadyTick;

    public int getWhipReadyTick() {
        return whipReadyTick;
    }

    public void setWhipReadyTick(int tick) {
        this.whipReadyTick = tick;
    }

    /** The caster gate closed (a bare-handed fight, or the chest piece lost): fold it away. */
    private void putCasterAway() {
        if (getCasterState() != com.predator.common.gameplay.entity.living.yautja.caster.CasterState.STOWED) {
            setCasterState(com.predator.common.gameplay.entity.living.yautja.caster.CasterState.STOWED);
        }
    }

    /** The weapon gate closed: lower a drawn bow and let go of a chain hook. */
    private void putWeaponsAway() {
        if (isUsingItem()) {
            stopUsingItem();
        }

        // ⚠ The bow goal raises and lowers the bow through animation, not item use, and cannot lower it itself once it
        // is gated off — so it is put away here (the command is idempotent).
        getAnimationDispatcher().bowPutaway();

        com.predator.common.gameplay.whip.WhipGrapple.release(this);
    }

    /**
     * 🚨 NOTHING FROM VANILLA'S EQUIPMENT DROP — armour or weapon. Every mob drops each worn piece with an 8.5% chance
     * (more with Looting) unless told otherwise, so every ordinary yautja could drop its jungle armour — [stated]
     * "Hunters are the ONLY source of armour drops". A Hunter's one random piece is dropped by hand
     * (dropHunterArmourPiece). And the weapon in its hand would be a second weapon on top of the loot table's one —
     * [stated] "one weapon max". Every drop comes from YautjaLootTable and the Hunter's armour piece, nothing else.
     */
    @Override
    protected float getEquipmentDropChance(@NotNull EquipmentSlot slot) {
        return 0.0F;
    }

    /** Phase 3's buff: [stated] "an additional 10% to all stats", every Hunter tier. */
    public static final double HUNT_RETURN_BONUS = 1.10;

    /** A Hunter can follow its prey this far, so one sent from out of sight does not forget the player on arrival. */
    private static final double HUNTER_FOLLOW_RANGE = 64.0;

    // ---------------------------------------------------------------- a blow held for its impact frame

    private @Nullable LivingEntity pendingStrikePrey;

    private float pendingStrikeDamage;

    private YautjaAttackAnimation pendingStrikeStep = YautjaAttackAnimation.NONE;

    private int pendingStrikeTick;

    /** Holds a blow until its clip's impact frame. ⚠ A new blow replaces a pending one — only one swing at a time. */
    public void setPendingStrike(LivingEntity prey, float damage, YautjaAttackAnimation step, int tick) {
        this.pendingStrikePrey = prey;
        this.pendingStrikeDamage = damage;
        this.pendingStrikeStep = step;
        this.pendingStrikeTick = tick;
    }

    public @Nullable LivingEntity getPendingStrikePrey() {
        return pendingStrikePrey;
    }

    public float getPendingStrikeDamage() {
        return pendingStrikeDamage;
    }

    public YautjaAttackAnimation getPendingStrikeStep() {
        return pendingStrikeStep;
    }

    public int getPendingStrikeTick() {
        return pendingStrikeTick;
    }

    public void clearPendingStrike() {
        this.pendingStrikePrey = null;
        this.pendingStrikeStep = YautjaAttackAnimation.NONE;
    }

    /** Set by {@link #forceWeaponRepick()}; consumed on the next tick. */
    private boolean weaponRepickQueued;

    @Override
    public BLibInventory getInventory() {
        return inventory;
    }

    /**
     * Draws whichever weapon suits the prey, stowing whatever was in hand.
     * <p>
     * <b>His rule.</b> "if a player also fights with a sword or axe the predator will match them." So the choice is
     * made from what the TARGET is holding, not from what the yautja happens to have drawn.
     * <p>
     * <b>⚠ Matching is courtesy, not tactics.</b> A yautja meeting a swordsman with a sword is the honor code showing
     * up in the fight — it is deliberately NOT the optimal choice, and it should not be "improved" into picking
     * whatever does the most damage. An unarmed or gun-carrying target gets the blades, because there is nothing to
     * match.
     * <p>
     * ⚠ Silent when the rack does not hold the answer. A yautja that spawned without an axe faces an axeman with what
     * it has rather than conjuring one.
     */
    /**
     * Forces a fresh weapon pick on the next tick.
     * <p>
     * ⚠ Weapons are only re-matched when the TARGET CHANGES, so kit swapped into an idle yautja would sit unused until
     * something provoked it. The debug inventory calls this on close so what you put in is what it draws.
     */
    /**
     * The debug inventory hook — see YautjaInventoryMenu.
     * <p>
     * ⚠⚠ GATED THREE WAYS and it FALLS THROUGH when any gate fails, so ordinary play is untouched: creative mode, an
     * empty main hand, and the yautjaDebugInventory gamerule (default off).
     */
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (
            hand == InteractionHand.MAIN_HAND
                && player.isCreative()
                && player.getMainHandItem().isEmpty()
                && PredatorGameRules.isYautjaDebugInventoryEnabled(level())
        ) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                // ⚠ ONE ARGUMENT. The two-arg openMenu(provider, buffer) is a NeoForge extension and does not
                // exist in :common; the yautja's id reaches the client through the menu's data slot instead.
                serverPlayer.openMenu(
                    new SimpleMenuProvider(
                        (containerId, playerInventory, ignored) -> new YautjaInventoryMenu(
                            containerId,
                            playerInventory,
                            new YautjaInventoryContainer(this),
                            this
                        ),
                        getDisplayName()
                    )
                );
            }

            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /**
     * {@return this yautja's animation dispatcher}
     * <p>
     * ⚠ Public because a GOAL needs it — the bow goal drives raise/loose/lower around its shots. Everything else that
     * animates the yautja lives inside this class, so this is the first outside caller.
     */
    public YautjaAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    /**
     * {@return whether this yautja must keep what is in its hand}
     * <p>
     * ⚠ GATED ON THE GAMERULE AS WELL AS THE FLAG. [stated] "when in inventory mode with creative and the gamerule on".
     * Switch the gamerule off and every locked yautja goes back to choosing its own weapons, with the flag still
     * remembered for when it comes back on.
     * <p>
     * ⚠ The lock holds even if the hand goes EMPTY — a thrown disc in flight, the last shuriken used up. It will not
     * draw something else in its place; that is what "it has to use that" means, and a disc in flight comes back to the
     * same hand anyway. Taking the item out through the menu is what releases it.
     */
    /**
     * {@return whether this yautja carries any of {@code item}, in its rack or its hand}
     * <p>
     * [stated] "any kind of ammo the yautja spawns with they have infinite as long as the item is in the inventory". ⚠⚠
     * PRESENCE, NEVER COUNT — a single dart is as good as sixteen, and nothing a yautja fires is ever taken from it.
     * What this gates is whether it may fire that ammunition AT ALL, which it previously never checked: darts and nets
     * came out of the wrist launcher with nothing in the rack — [stated] "the yautja keeps shooting nets even though i
     * emptied its inventory".
     */
    public boolean hasAmmo(net.minecraft.world.item.Item item) {
        return getMainHandItem().is(item) || inventory.hasItem(item);
    }

    /** {@return whether it is hurt badly enough to want to break off and recover} */
    public boolean needsRecovery() {
        return getHealth() <= getMaxHealth() * RECOVERY_HEALTH_FRACTION;
    }

    /** {@return whether it carries anything it can heal with — see PredatorItemTags.YAUTJA_HEALING_ITEMS} */
    public boolean hasHealingItem() {
        if (isHealingItem(getMainHandItem())) {
            return true;
        }

        for (var slot = 0; slot < inventory.getSize(); slot++) {
            if (isHealingItem(inventory.getItemStack(slot))) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@return whether a yautja can heal itself with this stack}
     * <p>
     * [stated] "add healthpotion II, enchanted golden apple, regeneration 2 potion to the yautja healing items."
     * <p>
     * ⚠⚠ THE POTIONS CANNOT LIVE IN THE TAG. Every potion in the game is the SAME item, minecraft:potion; which potion
     * it is lives in its POTION_CONTENTS data. Tagging minecraft:potion would make a water bottle or a Potion of Poison
     * count as healing. So the tag (PredatorItemTags.YAUTJA_HEALING_ITEMS) holds real items — the enchanted golden
     * apple — and the two potions are recognised here by TYPE: Healing II and Regeneration II, drinkable only.
     * <p>
     * ⚠ The Potions constants are read HERE, when the check runs, never held in a static field: vanilla's Potions
     * registers itself the first time it is touched, and a static reference on this class could trigger that during mod
     * registration, before vanilla's registries are ready.
     */
    public static boolean isHealingItem(ItemStack stack) {
        if (stack.is(com.predator.common.registry.tag.PredatorItemTags.YAUTJA_HEALING_ITEMS)) {
            return true;
        }

        if (!stack.is(net.minecraft.world.item.Items.POTION)) {
            return false;
        }

        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);

        return contents != null
            && (contents.is(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING)
                || contents.is(net.minecraft.world.item.alchemy.Potions.STRONG_REGENERATION));
    }

    /**
     * Share of its damage a yautja deals to a PLAYER — 20% less than to anything else. Applied in one place,
     * MixinPlayer_YautjaDamageToPlayers, so it covers every weapon.
     */
    public static float DAMAGE_TO_PLAYERS_MULTIPLIER = 0.8F;

    // ---------------------------------------------------------------- trip mines (see YautjaMines)

    /** Where this yautja's mines stand, so the cap can count them and its death can clear them. Saved. */
    private final java.util.List<net.minecraft.core.BlockPos> placedMines = new java.util.ArrayList<>();

    public java.util.List<net.minecraft.core.BlockPos> getPlacedMines() {
        return placedMines;
    }

    /** Set once its carried grenade has been dropped, so a repeated die() call never drops a second. */
    private boolean grenadeDropped;

    /** Set once a dead Hunter has left its gauntlet, so a repeated die() call never leaves a second one. */
    private boolean selfDestructLeft;

    /**
     * [agreed] Its mines go with it — removed, dropping nothing, unless one is already counting down.
     * <p>
     * A HUNTER leaves its gauntlet behind with the self-destruct already counting: [stated] "when it despawns from
     * death it leaves behind the gauntlet with the self destruct armed." It is SET DOWN as a block where it fell and
     * runs the usual 20-second countdown, panels, beeps and laugh, and can be disarmed like any other. With the
     * self-destruct game rule off it is left as a plain, unarmed gauntlet block.
     */
    @Override
    public void die(net.minecraft.world.damagesource.@NotNull DamageSource damageSource) {
        super.die(damageSource);

        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            YautjaMines.disarmAll(serverLevel, this);

            if (!grenadeDropped) {
                grenadeDropped = true;
                dropCarriedGrenade();
            }

            if (isHunter() && !selfDestructLeft) {
                selfDestructLeft = true;
                leaveArmedGauntlet(serverLevel);
                dropHunterArmourPiece();

                if (huntedPlayer != null) {
                    com.predator.common.gameplay.hunt.HuntDirector.onHunterKilled(serverLevel, this, damageSource);
                }
            }
        }
    }

    /**
     * [stated] Hunters are the only source of yautja armour, and drop "a random piece regardless if it broke or not":
     * one of the four, chosen at random, whatever the fight knocked off.
     */
    private void dropHunterArmourPiece() {
        var pieces = new net.minecraft.world.item.Item[] {
            com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get(),
            com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE.get(),
            com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS.get(),
            com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_BOOTS.get()
        };

        spawnAtLocation(new ItemStack(pieces[getRandom().nextInt(pieces.length)]));
    }

    private void leaveArmedGauntlet(net.minecraft.server.level.ServerLevel level) {
        var gauntlet = new net.minecraft.world.item.ItemStack(com.predator.common.registry.init.item.PredatorItems.GAUNTLET.get());

        // ⚠ With the self-destruct rule off it is left UNARMED. An armed one would sit waiting, and the first time a
        // player placed it after the rule was turned on it would start counting under them — a bomb they never set.
        if (com.predator.common.registry.init.PredatorGameRules.isSelfDestructEnabled(level)) {
            // ⚠ Armed by hand rather than through GauntletSelfDestruct.arm, which takes a Player. These are the same
            // two components arm() sets; the armer is this Hunter, so the blast is credited to it.
            gauntlet.set(com.predator.common.registry.init.PredatorDataComponents.DESTRUCT_ARMED.get(), true);
            gauntlet.set(com.predator.common.registry.init.PredatorDataComponents.DESTRUCT_ARMER.get(), getUUID());
        }

        // [stated] "it should be placed like a block when they die not dropped". Set down exactly as a player sets one
        // down — the gauntlet block, the stack inside it — and placing an armed one is what starts the countdown
        // (GauntletBlockEntity.placeGauntlet), the panels, the beeps, the laugh and the through-walls marker.
        var spot = findGauntletSpot(level);

        if (spot != null) {
            level.setBlock(spot, com.predator.common.registry.init.PredatorBlocks.GAUNTLET_BLOCK.get().defaultBlockState(), 3);

            if (level.getBlockEntity(spot) instanceof com.predator.common.gameplay.block.entity.GauntletBlockEntity block) {
                block.placeGauntlet(gauntlet);
                level.playSound(
                    null,
                    spot,
                    net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    0.8F,
                    1.1F
                );
                return;
            }
        }

        // ⚠ Nowhere to set it down (it died in mid-air over a drop, in deep water, wedged in a tunnel): it falls as an
        // item instead, and an armed one is started here so it still counts, glows and goes off exactly the same.
        com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct.startCountdown(level, gauntlet, position());
        spawnAtLocation(gauntlet);
    }

    /** How far from where it fell the gauntlet may be set down. */
    private static final int GAUNTLET_SPOT_RADIUS = 3;

    /**
     * The nearest spot to where it died that a player could set a gauntlet down: replaceable (air, grass, water…),
     * standing on the sturdy top of a block — the same test GauntletItem.useOn makes. Searched from its feet outward, a
     * few blocks down first so a Hunter killed mid-leap leaves it on the ground below.
     */
    private @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos findGauntletSpot(net.minecraft.server.level.ServerLevel level) {
        var feet = blockPosition();
        net.minecraft.core.BlockPos best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var dy = -GAUNTLET_SPOT_RADIUS - 2; dy <= 2; dy++) {
            for (var dx = -GAUNTLET_SPOT_RADIUS; dx <= GAUNTLET_SPOT_RADIUS; dx++) {
                for (var dz = -GAUNTLET_SPOT_RADIUS; dz <= GAUNTLET_SPOT_RADIUS; dz++) {
                    var pos = feet.offset(dx, dy, dz);
                    var distance = pos.distSqr(feet);

                    if (distance >= bestDistance || !level.isInWorldBounds(pos)) {
                        continue;
                    }

                    var below = pos.below();

                    if (
                        level.getBlockState(pos).canBeReplaced() && level.getBlockState(below)
                            .isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                    ) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    // ---------------------------------------------------------------- healing (see YautjaHealing)

    private int nextHealTick;

    private float regenRemaining;

    private float regenPerTick;

    private int absorptionUntilTick;

    public int getNextHealTick() {
        return nextHealTick;
    }

    public void setNextHealTick(int tick) {
        this.nextHealTick = tick;
    }

    public int getAbsorptionUntilTick() {
        return absorptionUntilTick;
    }

    public void setAbsorptionUntilTick(int tick) {
        this.absorptionUntilTick = tick;
    }

    /** Heals {@code total} spread evenly over {@code ticks}. A second one simply adds to what is left. */
    public void startRegeneration(float total, int ticks) {
        regenRemaining += total;
        regenPerTick = Math.max(regenPerTick, total / Math.max(1, ticks));
    }

    public void tickRegeneration() {
        if (regenRemaining <= 0.0F) {
            regenPerTick = 0.0F;

            return;
        }

        var step = Math.min(regenPerTick, regenRemaining);

        regenRemaining -= step;
        heal(step);
    }

    /** {@return the rack slot of a healing item, HAND_HEALING_SLOT if it is in the hand, or -1} */
    public int findHealingSlot() {
        if (isHealingItem(getMainHandItem())) {
            return HAND_HEALING_SLOT;
        }

        for (var slot = 0; slot < inventory.getSize(); slot++) {
            if (isHealingItem(inventory.getItemStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    /** At or below this share of max health a yautja counts as needing to recover. */
    public static float RECOVERY_HEALTH_FRACTION = 0.5F;

    public boolean isWeaponLocked() {
        return debugWeaponLocked && PredatorGameRules.isYautjaDebugInventoryEnabled(level());
    }

    public void setDebugWeaponLocked(boolean locked) {
        this.debugWeaponLocked = locked;
    }

    public void forceWeaponRepick() {
        // 🚨🚨 A FLAG, NOT A NULLED CACHE. Clearing lastMatchedTarget only works if the yautja HAS a target: an idle
        // one has getTarget() == null too, so "target != lastMatchedTarget" was false and the repick never ran. That
        // is why stripping a yautja's kit changed nothing until something attacked it.
        weaponRepickQueued = true;
        lastMatchedTarget = null;
    }

    public void matchWeaponTo(@Nullable LivingEntity target) {
        if (level().isClientSide) {
            return;
        }

        // ⚠⚠ A WEAPON PLACED IN ITS HAND BY THE DEBUG MENU STAYS THERE. See isWeaponLocked.
        if (isWeaponLocked()) {
            return;
        }

        // A bare-handed hunt: whatever is in the hand goes back on the rack, and nothing comes out.
        if (huntBareHanded) {
            var held = getMainHandItem();

            if (!held.isEmpty() && inventory.addItemStack(held.copy()) instanceof BLibInventory.AddResult.Success) {
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }

            return;
        }

        // ⚠ HONOUR FIRST — sword against sword, axe against axe, the spear against tridents and bows. Only when that
        // asks for something this yautja is not carrying does it fall back to RANGE: the ranged weapon at a distance,
        // the melee one up close. With one of each in the loadout, the honour match alone would rarely have an answer,
        // and nothing else ever drew a bow out of the rack.
        var honour = weaponMatching(target);
        var wanted = honour != null && (getMainHandItem().is(honour) || inventory.hasItem(honour)) ? honour : weaponForRange(target);

        if (wanted == null || getMainHandItem().is(wanted)) {
            return;
        }

        // 🚨🚨 NO "EMPTY RACK MEANS EMPTY HANDS" RULE. There was one, and it was wrong: equipping a weapon REMOVES it
        // from the rack, so a yautja holding its only weapon has an empty rack by design. The rule put that weapon
        // straight back the next time the target changed — [stated] "it lightning fast equipped the combi stick and
        // then unequipped it and did a sideways punch". Disarming a yautja is what the debug menu's HAND SLOT is for.
        if (!inventory.hasItem(wanted)) {
            return;
        }

        // ⚠ RemoveResult is a SEALED interface — Success, Partial or InventoryEmpty — never null. A null check
        // here would always pass and the draw would proceed even when the rack refused, so the outcome is
        // matched properly instead.
        if (!(inventory.removeItem(wanted) instanceof BLibInventory.RemoveResult.Success)) {
            return;
        }

        // ⚠⚠ STOW FIRST, AND PROVE IT LANDED. setItemSlot OVERWRITES the main hand, so a stow that silently
        // failed on a full rack would destroy the weapon being put away. If it cannot be stored, the swap is
        // abandoned and the drawn weapon is handed back — a yautja keeps what it is holding rather than
        // losing it to a bookkeeping failure.
        var stowed = getMainHandItem().copy();

        if (!stowed.isEmpty() && !(inventory.addItemStack(stowed) instanceof BLibInventory.AddResult.Success)) {
            inventory.addItemStack(new ItemStack(wanted));

            return;
        }

        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wanted));
    }

    /** {@return the weapon that answers what the target is holding, or null when nothing needs matching} */
    /** Ticks between re-checking whether the fight's distance calls for the other weapon. */
    private static final int WEAPON_RANGE_CHECK_TICKS = 20;

    /** Beyond this it wants its ranged weapon, within the other its melee one; between, it keeps what it holds. */
    private static final double RANGED_FROM = 8.0D;

    private static final double MELEE_WITHIN = 5.0D;

    /** {@return the weapon the distance calls for, or null to keep the current one} */
    private @Nullable Item weaponForRange(@Nullable LivingEntity target) {
        if (target == null) {
            return null;
        }

        var distance = distanceTo(target);

        if (distance >= RANGED_FROM) {
            return firstCarried(true);
        }

        if (distance <= MELEE_WITHIN) {
            return firstCarried(false);
        }

        return null;
    }

    /** {@return the first ranged (or melee) weapon in hand or rack; a bow counts only with its arrows} */
    private @Nullable Item firstCarried(boolean ranged) {
        if (isWeapon(getMainHandItem(), ranged)) {
            return getMainHandItem().getItem();
        }

        for (var slot = 0; slot < inventory.getSize(); slot++) {
            var stack = inventory.getItemStack(slot);

            if (isWeapon(stack, ranged)) {
                return stack.getItem();
            }
        }

        return null;
    }

    private boolean isWeapon(ItemStack stack, boolean ranged) {
        if (stack.isEmpty()) {
            return false;
        }

        var item = stack.getItem();

        if (ranged) {
            // ⚠ [stated] "if the veritanium bow is chosen it also needs arrows" — no arrows, not a ranged option.
            if (item == PredatorItems.VERITANIUM_BOW.get()) {
                return hasAmmo(net.minecraft.world.item.Items.ARROW) || hasAmmo(PredatorItems.VERITANIUM_ARROW.get());
            }

            return item == PredatorItems.PLASMA_BOW.get() || item == PredatorItems.SHURIKEN.get() || item == PredatorItems.SMART_DISC.get();
        }

        return item instanceof SwordItem || item instanceof AxeItem || item instanceof CombiStickItem;
        // ⚠ The plasma sword IS a SwordItem, so it counts as melee here with no change.
    }

    private @Nullable Item weaponMatching(@Nullable LivingEntity target) {
        if (target == null) {
            return null;
        }

        var held = target.getMainHandItem().getItem();

        if (held instanceof AxeItem) {
            return PredatorItems.VERITANIUM_AXE.get();
        }

        if (held instanceof SwordItem) {
            return PredatorItems.VERITANIUM_SWORD.get();
        }

        // ⚠ A spear answers a spear. TridentItem covers vanilla's, and a player who has taken a combi stick off a
        // yautja gets met with one — which is the fight that story earns.
        if (held instanceof TridentItem || held instanceof CombiStickItem) {
            return PredatorItems.COMBI_STICK.get();
        }

        // ⚠⚠ AND IT ANSWERS A BOW. Nothing else in the rack has reach: against an archer a yautja could only close
        // the distance and take the shots. The combi stick is the one weapon it can throw back, so a ranged target
        // is exactly when it should be drawn.
        if (held instanceof BowItem || held instanceof CrossbowItem) {
            return PredatorItems.COMBI_STICK.get();
        }

        return null;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Crawling
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Whether the navigator wants this yautja on its belly.
     * <p>
     * ⚠ Synced, for the same reason climbing is: nothing observable distinguishes crawling from standing in a low
     * space, and the client has to know which clip to play and which hitbox to draw.
     */
    private static final EntityDataAccessor<Boolean> DATA_CRAWLING =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BOOLEAN);

    /**
     * True during the short hop over a one-block overhang.
     * <p>
     * ⚠ Synced only so the CLIENT can pick the fast climb clip for it — his "play the fast climb animation". The vault
     * itself is entirely server-side; without this the client would show the slow clip whenever the yautja happened to
     * have no target, which is exactly when a vault looks most like a glitch.
     */
    private static final EntityDataAccessor<Boolean> DATA_CLIMB_VAULTING =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BOOLEAN);

    /**
     * True while clinging under a one-block lip.
     * <p>
     * ⚠ Synced because it changes BOTH the hitbox and the pose. The client has to shrink the box to match, or the
     * yautja is unclickable where it is drawn, and it has to tilt the model or the head visibly sits inside stone.
     */
    private static final EntityDataAccessor<Boolean> DATA_CLIMB_HANGING =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BOOLEAN);

    /**
     * Bumped per dodge, so tracking clients play the roll. A counter, not a flag — two rolls in a row must both fire.
     */
    private static final EntityDataAccessor<Byte> DATA_DODGE_SEQUENCE =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BYTE);

    /**
     * Whether this yautja is a HUNTER — the boss-grade variant that actively hunts a player.
     * <p>
     * ⚠ Synced because the client will need it long before the hunting system lands: a boss bar, a different name
     * plate, a marker on the tracker. Cheap to carry now, awkward to retrofit later.
     * <p>
     * ⚠⚠ NOTHING SETS THIS YET. The tier system is still a design table, so every yautja spawns as a normal one and the
     * hunter resistance below is inert until something flips this. That is deliberate — the mechanism is here and
     * testable, but no spawn path pretends to know what a hunter is.
     */
    private static final EntityDataAccessor<Boolean> DATA_HUNTER =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BOOLEAN);

    /**
     * Bumped once per roar so tracking clients can play it.
     * <p>
     * ⚠ A COUNTER, not a boolean, for the same reason the attack animations use one: the server has to be able to say
     * "roar again" and a boolean that is already true is not a change, so nothing would fire.
     */
    private static final EntityDataAccessor<Byte> DATA_ROAR_SEQUENCE =
        SynchedEntityData.defineId(Yautja.class, EntityDataSerializers.BYTE);

    /**
     * Height while crawling, in blocks. Standing is 2.48.
     * <p>
     * ⚠⚠ MUST STAY UNDER THE 1-BLOCK GAP THE PATHFINDER PLANS FOR. CRAWL_HEIGHT in YautjaPathing is 1, so the planner
     * will happily route through a 1-block gap; if the hitbox does not actually fit, the yautja plans a path it then
     * cannot walk and grinds against the ceiling. avp_alien hit exactly this — its crawl hitbox and its crawl clearance
     * disagreed and xenos jammed in doorways.
     */
    private static final float CRAWL_HEIGHT = 0.9F;

    /** ⚠ Must match the entity type's sized(...) height, or the obstruction test checks the wrong block. */
    /**
     * ⚠⚠ THE CLIMB MEASURES AGAINST THIS, NOT AGAINST getBbHeight(). The hitbox shrinks when the yautja ducks or hangs,
     * and every climb check that read the LIVE height would then answer differently the instant the posture changed —
     * head blocked, duck, head no longer blocked, stand up, head blocked — oscillating every tick. A fixed reference
     * makes the geometry independent of the pose it produces.
     * <p>
     * ⚠ Must match the entity type's sized(...) height.
     */
    public static final double STANDING_HEIGHT = 2.48;

    /** Server-side: the tick the current roar ends, and the tick the next one is allowed. */
    private int roarUntilTick;

    private int nextRoarTick;

    /** Client-side: the last roar stamp played, so each roar fires exactly once. */
    private byte lastDispatchedRoar;

    /** True while the roar clip should own the body. */
    public boolean isRoaring() {
        return tickCount < roarUntilTick;
    }

    /**
     * {@return whether a block occupies the space this yautja's head is in}
     * <p>
     * ⚠ Tested at standing height, NOT at the current height. Asked at the crawl height it would answer "no" the moment
     * it ducked, the yautja would stand back up into the ceiling, and it would oscillate every tick.
     */
    private boolean isHeadObstructed() {
        // 🚨🚨 THE WHOLE STANDING BODY, NOT ONE BLOCK ABOVE THE CENTRE. [stated] "when it tries to crawl through a 1
        // block high height it seems to get up too soon and take suffocation damage." This used to test a single
        // block, at feet + 2.38, straight above the middle. That fails twice in a low tunnel: in a ONE-high gap that
        // block is ABOVE the ceiling — air, if the roof is one block thick — so it read clear and stood with its head
        // in the roof; and it looked only at the centre, so halfway out of a tunnel the back of the body was still
        // under the ceiling when it stood. This asks vanilla the real question — would the full standing hitbox
        // collide with anything — so every tunnel height, width and exit angle is covered.
        // ⚠ Deflated a hair so resting on the floor does not count as a collision.
        var halfWidth = getBbWidth() / 2.0D;
        var standing = new net.minecraft.world.phys.AABB(
            getX() - halfWidth,
            getY(),
            getZ() - halfWidth,
            getX() + halfWidth,
            getY() + STANDING_HEIGHT,
            getZ() + halfWidth
        ).deflate(1.0E-5D);

        return !level().noCollision(this, standing);
    }

    // -----------------------------------------------------------------------------------------------------------
    // Fire
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Ticks of continuous fire exposure before it takes any damage at all — his turtle-helmet grace.
     * <p>
     * A yautja steps through flame and out the other side unharmed; standing in it is what hurts.
     */
    private static final int FIRE_GRACE_TICKS = 60;

    /**
     * ⚠ His 80% resistance expressed as what SURVIVES it. Fire still kills a yautja, it just takes five times longer.
     */
    private static final float FIRE_DAMAGE_MULTIPLIER = 0.2F;

    /** Ticks without a fire hit before the grace resets. Two seconds, so walking through a fire line does not stack. */
    private static final int FIRE_EXPOSURE_RESET_TICKS = 40;

    private int fireExposureTicks;

    private int lastFireDamageTick = Integer.MIN_VALUE / 2;

    /**
     * ⚠⚠ NEVER CATCHES FIRE. {@code igniteForSeconds} is final, but it routes through this, so overriding here stops
     * every ignition source — lava, flint and steel, a fire aspect blade — without needing to know which.
     * <p>
     * ⚠ NOT {@code fireImmune()}. That would make fire damage zero as well, and he wants sustained fire to still kill:
     * "fire can still hurt it but it has to be a prolonged period". This kills only the BURNING, not the harm.
     */
    @Override
    public void setRemainingFireTicks(int ticks) {
        super.setRemainingFireTicks(0);
    }

    /**
     * Fire hurts a yautja late and lightly.
     * <p>
     * Three seconds of grace, then 20% of what the fire would otherwise do. The exposure counter resets after
     * {@value #FIRE_EXPOSURE_RESET_TICKS} ticks without a fire hit, so crossing a fire line repeatedly does not
     * accumulate toward the threshold the way standing in one does.
     * <p>
     * ⚠ Counts TICKS OF EXPOSURE, not hits. Fire ticks damage every 20 ticks while lava is every 10, so counting hits
     * would make lava reach the threshold twice as fast — which is backwards, since lava is the thing a volcanic-world
     * hunter should be most at home in.
     */
    /**
     * Caliber resistances. Thick scaly hide shrugs off small rounds; a heavy slug still tells.
     * <p>
     * ⚠ These are the SURVIVING fraction, not the resistance. small 75% resistant = 0.25 gets through.
     * <p>
     * ⚠⚠ SHELL AND "OTHER" ARE NOT IN HIS SPEC AND DEFAULT TO NO RESISTANCE. Left explicit rather than guessed:
     * shotguns are 128 raw dps and rockets 5.3, so a value invented here would quietly decide whether the shotgun is
     * the best gun in the game. Say the numbers and they go in.
     */
    private static final float SMALL_CALIBER_TAKEN = 0.25F;

    private static final float MEDIUM_CALIBER_TAKEN = 0.35F;

    private static final float HEAVY_CALIBER_TAKEN = 0.60F;

    private static final float CASELESS_CALIBER_TAKEN = 0.50F;

    /**
     * Extra reduction for drum-fed weapons, applied ON TOP of their caliber.
     * <p>
     * ⚠ 20% at his instruction. Worth knowing where that lands: Old Painless goes from 64s to 80s on a Clan Leader
     * against a sword's 83s, so it is still MARGINALLY the faster weapon. 23% is the exact tipping point and 25% would
     * put melee back on top — if the goal was "a sword should beat a minigun", this number is three points short of it.
     */
    private static final float DRUM_EXTRA_RESISTANCE = 0.20F;

    /**
     * Fraction of incoming hits the armour turns aside outright.
     * <p>
     * ⚠ Applies to EVERYTHING including melee and arrows — it is armour deflecting a blow, not a gun-specific rule.
     * Raise it per tier when the class levels land.
     */
    /**
     * ⚠ SUPERSEDED by {@link YautjaTier#deflectChance()} — kept only as the documented baseline. The live value is per
     * tier (10% at Youngblood rising to 20% at Clan Leader); a constant here would silently override the table.
     */
    private static final float BASE_DEFLECT_CHANCE = 0.10F;

    /** ⚠ 1.10, down from the 1.20 he first proposed — it tested too strong. Melee only; a bow does not get it. */
    private static final float MELEE_DAMAGE_BONUS = 1.10F;

    /**
     * ⚠⚠ THIS IS THE NUMBER THAT MAKES THE CALIBER RULE WORK AT ALL. avp_human sets
     * {@code LivingEntity.invulnerableTime = 0} immediately before its hurt call (verified in
     * EntityGunHitResultHandler), so guns bypass vanilla's hit window entirely while melee does not. That single
     * asymmetry is why Old Painless reaches 160 dps against a sword's 18.
     * <p>
     * Re-imposing even five ticks collapses every high-rate weapon to its per-hit damage and leaves slow weapons
     * untouched: Old Painless 160 -> 32 dps, smartgun 80 -> 16, sword and sniper unchanged because neither ever fired
     * faster than the window.
     * <p>
     * ⚠⚠ TWO, LOWERED FROM FIVE AT HIS TESTERS' REQUEST — five made full-auto not worth carrying. Two caps the fire
     * rate at 10 hits per second instead of 4, which roughly TRIPLES what automatic weapons do: Old Painless 32 -> 80
     * dps M56 smartgun 16 -> 40 dps
     * <p>
     * ⚠ AND IT CHANGES WHICH WEAPON IS BEST. At five ticks melee was the fastest way to kill a yautja at every tier; at
     * two, Old Painless beats a sword on a Clan Leader (64s against 83s). That may be the right call — a minigun SHOULD
     * beat a sword — but it reverses the "reward fighting close" goal the melee bonus was added for, so it is a
     * deliberate trade rather than a side effect.
     * <p>
     * ⚠ Weapons firing every 2 ticks or slower — the M4RA and F903WE — are now completely UNCAPPED, so the window no
     * longer applies to them at all. Three ticks would be the middle ground: automatics roughly double instead of
     * tripling, and melee stays ahead.
     * <p>
     * ⚠ The shotgun is unaffected by this number either way. It fires every 20 ticks, slower than any window; what
     * limits it is the separate fact that its 8 pellets all resolve in ONE tick, so only the first lands.
     */
    private static final int HURT_COOLDOWN_TICKS = 2;

    /** Server-side: the tick this yautja last actually took damage. */
    private int lastHurtTick = Integer.MIN_VALUE / 2;

    /**
     * ⚠⚠ THE TIER'S ARMOUR IS THE WHOLE ARMOUR. Vanilla adds every worn armour item's modifiers to the ARMOR and
     * ARMOR_TOUGHNESS attributes of whoever wears it, and a yautja spawns in its plates — so a Blooded read 30 on the
     * HUD (tier 11 + a 19-point set), and at 30 the vanilla formula sits on its 80% cap for anything but a heavy slug.
     * Old Painless landed 0.45 per round instead of 1.31; a tester's 13-second burst took a third of its health. The
     * plates on the mob are a costume: the class tier already prices them in. Both the absorb step and the displayed
     * value read the tier and only the tier.
     */
    @Override
    protected float getDamageAfterArmorAbsorb(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return amount;
        }

        // [stated] veritanium arrows "bypass armor defense" — a yautja's tier armour included. This override replaces
        // LivingEntity's, so MixinLivingEntity_VeritaniumArrowArmor never reaches a yautja; the check is repeated here.
        if (source.getDirectEntity() instanceof com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity) {
            return amount;
        }

        var tier = getTier();
        var hunter = isHunter();
        var defence = huntDefenceScale();

        return CombatRules.getDamageAfterAbsorb(
            this,
            amount,
            source,
            (float) (tier.armor(hunter) * defence),
            (float) (tier.toughness(hunter) * defence)
        );
    }

    @Override
    public int getArmorValue() {
        return (int) Math.round(getTier().armor(isHunter()) * huntDefenceScale());
    }

    /**
     * The mask is the yautja's breather. [stated] Sep 22: "predators with their mask on should be able to survive in
     * space without oxygen ... if a yautja loses its mask then it will suffocate." And, same day: "this goes for
     * drowning too. the predator with mask wont drown" - so vanilla drowning is refused on the same condition, and
     * {@link #canBreatheUnderwater} keeps the air bar from draining in the first place.
     * <p>
     * ⚠ THE YAUTJA MOB ONLY. [stated] "the player does not benefit from breathing from the yautja mask only the
     * predator does ... a player with the mask can [drown]." Nothing here touches the mask ITEM, and the item is in
     * none of the space mods' armor tags, so a player wearing it gets no air from it anywhere.
     * <p>
     * Both space mods suffocate a creature by dealing their own {@code <mod>:oxygen} damage type through {@code hurt}
     * (Ad Astra {@code OxygenApiImpl.entityTick}, Stellaris {@code LivingEntityMixin} after
     * {@code DimensionOxygenManager.breath} fails - read from the 1.16.26 and 1.4.25 jars). Neither offers a per-entity
     * "can breathe" hook beyond entity-type tags, and a tag cannot see the mask, so the answer is at the damage: while
     * the mask is on, oxygen damage from either mod is refused here. The moment the mask breaks ({@link #checkMask})
     * this returns false again and the yautja suffocates like anything else.
     * <p>
     * Matched by damage-type KEY, so neither mod is a compile-time dependency and an absent mod simply never matches.
     * Heat, cold, acid rain and radiation are unconditional and are handled by entity-type tags in the datagen provider
     * instead.
     */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        if (hasMask() && source.typeHolder().unwrapKey().map(MASK_BREATHING_DAMAGE_TYPES::contains).orElse(false)) {
            return true;
        }

        return super.isInvulnerableTo(source);
    }

    /**
     * The mask breathes for the yautja in water: with it on, air never drains, so drowning never starts.
     * <p>
     * ⚠ {@code canBreatheUnderwater()} is FINAL in 1.21.1 (checked, {@code LivingEntity} line 382), so the hook is one
     * step down. Vanilla writes {@code setAirSupply(decreaseAirSupply(air))}; NeoForge's {@code onLivingBreathe} takes
     * {@code air - decreaseAirSupply(air)} as the amount to consume. Returning the air unchanged makes both loaders
     * consume nothing. Once the mask is gone this defers to vanilla and the yautja drowns like anything else.
     */
    @Override
    protected int decreaseAirSupply(int currentAir) {
        return hasMask() ? currentAir : super.decreaseAirSupply(currentAir);
    }

    /** Every "no air" damage type the mask answers: vanilla drowning plus the two space mods' suffocation, by key. */
    private static final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>> MASK_BREATHING_DAMAGE_TYPES =
        java.util.Set.of(
            net.minecraft.world.damagesource.DamageTypes.DROWN,
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "oxygen")
            ),
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("stellaris", "oxygen")
            )
        );

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // Oct 8 - [stated] "if the player struck first it would keep chasing it longer": a player who strikes a
        // yautja stays its prey for a while, not just vanilla's 5-second last-attacker memory. See YautjaProvocation.
        if (!level().isClientSide && source.getEntity() instanceof Player striker && !striker.isCreative() && !striker.isSpectator()) {
            com.predator.common.gameplay.entity.living.yautja.YautjaProvocation.struckBy(this, striker);
        }

        // ⚠⚠ NOTHING HERE MAY REFUSE A KILL. /kill and the void deal damage tagged bypasses_invulnerability, and
        // vanilla marks them that way so no defence can answer them. The deflect roll below did: 12% of /kill
        // commands played the shield sound and returned false ([tester] "we hear a blocking noise"). Those sources
        // skip dodge, cooldown, deflect, caliber and the fire rule and go straight to vanilla.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            bypassingDamage = true;

            try {
                return super.hurt(source, amount);
            } finally {
                bypassingDamage = false;
            }
        }

        // Who is hitting it — for "outnumbered" (YautjaThreatAssessment). Recorded for every swing that reaches it,
        // dodged, deflected or not: an attacker is an attacker whether or not the blow landed.
        if (!level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            recentAttackers.put(attacker.getUUID(), tickCount);
        }

        // Refused outright (see isInvulnerableTo) - vanilla would refuse it inside super.hurt anyway, but not before
        // the dodge and deflect below had rolled and played their sounds for a hit that was never going to land.
        if (isInvulnerableTo(source)) {
            return false;
        }

        // ⚠ ORDER IS DELIBERATE: cooldown, then deflect, then caliber, then fire. Cheapest and most absolute first,
        // so a shot inside the window costs nothing to reject and never rolls a deflect it did not need.
        // ⚠⚠ THE DODGE IS CHECKED FIRST, BEFORE THE HURT WINDOW. An explosion is a single hit event, so the
        // window does nothing about it — the M6B rocket kills a Clan Leader in 24 seconds where a sword takes
        // 74. The dodge is the only answer this creature has to per-hit damage, and it has to see the hit
        // before anything else decides the hit does not count.
        if (!level().isClientSide && YautjaDodge.isDodgeable(source) && tryDodge(YautjaDodge.threatFrom(source, this))) {
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                // Rolled clear of the blast but still inside it: 60% absorbed, his figure.
                lastHurtTick = tickCount;

                return super.hurt(source, amount * YautjaDodge.EXPLOSION_DAMAGE_TAKEN);
            }

            // Melee is negated outright — a yautja can sidestep a sword, it cannot sidestep a blast radius.
            return false;
        }

        if (!level().isClientSide && isRapidFollowUp(source)) {
            return false;
        }

        if (!level().isClientSide && random.nextFloat() < getTier().deflectChance()) {
            playSound(SoundEvents.SHIELD_BLOCK, 0.8F, 1.4F);

            return false;
        }

        // ⚠ His 20% was too strong on test, so 10%. Applied to anything that is NOT an avp_human gun, which
        // means melee AND arrows both get it — arrows are explicitly untouched by the caliber rule, and
        // giving a bow the close-quarters bonus would be wrong, so the check is for a MELEE source rather
        // than "not a gun".
        // ⭐ THIRD-PARTY GUNFIRE (TACZ, Point Blank): scaled to match avp_human's guns, with a per-second budget and a
        // floor so every hit still visibly lands — the same balancing avp_alien gives xenomorphs, behind the same
        // gunBalancing rule. After the dodge, window and deflect, so only hits that land spend the budget. See
        // GunDamageParity. ⚠ No-op without either mod: the multiplier is 1.
        var gunParity = com.predator.compatibility.guns.GunDamageParity.damageMultiplier(this, source, amount);
        var scaled = amount * gunParity * caliberMultiplier(source) * meleeBonus(source) * hunterResistance() * adrenalineResistance();

        if (!source.is(DamageTypeTags.IS_FIRE)) {
            var taken = super.hurt(source, scaled);

            if (taken) {
                lastHurtTick = tickCount;
                recordRecentDamage(scaled);
            }

            return taken;
        }

        if (tickCount - lastFireDamageTick > FIRE_EXPOSURE_RESET_TICKS) {
            fireExposureTicks = 0;
        }

        fireExposureTicks += tickCount - lastFireDamageTick;
        lastFireDamageTick = tickCount;

        if (fireExposureTicks < FIRE_GRACE_TICKS) {
            return false;
        }

        return super.hurt(source, scaled * FIRE_DAMAGE_MULTIPLIER);
    }

    /**
     * {@return whether this hit lands inside the yautja's own hit window}
     * <p>
     * ⚠ Kept here rather than relying on {@code invulnerableTime}, because avp_human ZEROES that field before every gun
     * hit. A cooldown built on it would be reset by the very weapon it exists to limit.
     * <p>
     * ⚠ Bypassed by anything that ignores invulnerability anyway (void, /kill), so the window can never make a yautja
     * unkillable by a source that is supposed to be absolute.
     */
    /**
     * {@return the fraction of a gun's damage that gets through this hide}
     * <p>
     * ⚠⚠ IDENTIFIED BY THE AMMUNITION ITEM'S REGISTRY PATH, not by a class or a tag. avp_human is an OPTIONAL
     * dependency — {@code modCompileOnly} — so referencing its types here would not compile, and a datapack tag would
     * silently resolve to empty when the mod is absent and hand every gun full damage.
     * <p>
     * ⚠ THE TWO DRUM ITEMS RESOLVE DIFFERENTLY AND THAT IS CORRECT, NOT A TYPO. Verified against
     * {@code GunCalibers.of}: {@code drum_cartridge} is CASELESS (M56 smartgun) and {@code drum_cannister} is MEDIUM
     * (Old Painless). They are the two highest rate-of-fire weapons in the mod and they sit in different calibers,
     * which is exactly why the hurt cooldown above matters more than these numbers do.
     * <p>
     * ⚠ Arrows and melee never reach here — only a source whose direct entity carries avp_human ammunition does. His
     * ruling: "arrows dont need to get debuffed."
     */
    /**
     * {@return the reward for fighting a yautja up close}
     * <p>
     * ⚠ Keyed on {@code IS_PLAYER_ATTACK} plus a direct attacker, so it covers swords, axes and fists but NOT a bow —
     * an arrow is a player attack too, and rewarding a ranged weapon with a melee bonus would invert the whole point of
     * the rule.
     */
    /** {@return the hunter's flat extra reduction, or 1 for an ordinary yautja} */
    private float hunterResistance() {
        return isHunter() ? HUNTER_DAMAGE_TAKEN : 1.0F;
    }

    /**
     * {@return the multiplier the adrenaline rush applies to incoming damage} Attributes have no "resistance", so the
     * rush's damage reduction is applied here, alongside the hunter and caliber multipliers.
     */
    private float adrenalineResistance() {
        return hasEffect(PredatorMobEffects.getAdrenalineRushHolder())
            ? 1.0F - AdrenalineRushEffect.DAMAGE_RESISTANCE
            : 1.0F;
    }

    private float meleeBonus(DamageSource source) {
        // ⚠ A Point Blank round hurts with plain player_attack from the shooter, so to the damage source it is a sword
        // swing. Without this check every Point Blank bullet earned the close-quarters bonus.
        var melee = source.is(DamageTypeTags.IS_PLAYER_ATTACK)
            && source.getDirectEntity() == source.getEntity()
            && !com.predator.compatibility.guns.GunDamageParity.isThirdPartyGunfire(this, source);

        return melee ? MELEE_DAMAGE_BONUS : 1.0F;
    }

    /**
     * {@return whether this gun feeds from a drum}
     * <p>
     * ⚠ The two drum items resolve to DIFFERENT calibers and that is correct, not a typo — verified against
     * {@code GunCalibers.of}: the smartgun feeds {@code drum_cartridge} (caseless) and Old Painless feeds
     * {@code drum_cannister} (medium). They are the two highest rate-of-fire weapons in the mod and they sit in
     * different calibers, which is exactly why they need a rule of their own.
     */
    private static boolean isDrumFed(String gun) {
        return "old_painless".equals(gun) || "m56_smartgun".equals(gun);
    }

    private float caliberMultiplier(DamageSource source) {
        // ⚠⚠ READS THE SHOOTER'S GUN, NOT THE DAMAGE SOURCE'S WEAPON. avp_human builds its bullet damage as
        // new DamageSource(BULLET, shooter) — the two-argument constructor, with NO weapon item — so
        // getWeaponItem() comes back empty and a lookup built on it silently gives every gun full damage.
        // Verified in EntityGunHitResultHandler rather than assumed from the API.
        //
        // Hitscan resolves in the same tick it is fired, so whatever is in the shooter's hand IS the gun that fired.
        if (!(source.getEntity() instanceof LivingEntity shooter)) {
            return 1.0F;
        }

        var key = BuiltInRegistries.ITEM.getKey(shooter.getMainHandItem().getItem());

        if (!"avp_human".equals(key.getNamespace())) {
            return 1.0F;
        }

        // ⚠ Caliber per GUN, mapped from GunData's ammunition supplier. The two drum weapons resolve DIFFERENTLY
        // and that is correct, not a typo: the smartgun feeds drum_cartridge (caseless) and Old Painless feeds
        // drum_cannister (medium). They are the two highest rate-of-fire guns in the mod and they sit in different
        // calibers — which is exactly why the hurt cooldown matters more than these percentages do.
        //
        // ⚠ Shotguns and the rocket launcher are absent ON PURPOSE. He gave no value for shell or "other", and
        // shotguns are 128 raw dps, so inventing one here would quietly decide whether the shotgun is the best
        // weapon in the game. They take full damage until he says otherwise.
        var caliber = switch (key.getPath()) {
            case "m88mod4_combat_pistol", "f903we_rifle" -> SMALL_CALIBER_TAKEN;
            case "m4ra_battle_rifle", "old_painless" -> MEDIUM_CALIBER_TAKEN;
            case "m42a3_sniper_rifle" -> HEAVY_CALIBER_TAKEN;
            case "m41a_pulse_rifle", "m56_smartgun" -> CASELESS_CALIBER_TAKEN;
            default -> 1.0F;
        };

        // ⚠⚠ DRUM-FED WEAPONS TAKE A SECOND CUT ON TOP OF THEIR CALIBER, and they need one because caliber
        // cannot describe them. Old Painless shares MEDIUM with the M4RA while doing four times its damage per
        // second, and the smartgun shares CASELESS with the M41A pulse while doing nearly seven times its.
        // One number per caliber cannot serve both ends of a gap that wide; this is the correction for the
        // thing caliber genuinely does not measure, which is sustained rate of fire.
        return isDrumFed(key.getPath()) ? caliber * (1.0F - DRUM_EXTRA_RESISTANCE) : caliber;
    }

    private boolean isRapidFollowUp(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        return tickCount - lastHurtTick < HURT_COOLDOWN_TICKS;
    }

    public boolean isCrawling() {
        return entityData.get(DATA_CRAWLING);
    }

    /** ⚠ refreshDimensions on CHANGE only — it re-does collision work and must not run every tick. */
    public void setCrawling(boolean crawling) {
        if (entityData.get(DATA_CRAWLING) == crawling) {
            return;
        }

        entityData.set(DATA_CRAWLING, crawling);
        refreshDimensions();
    }

    /**
     * ⚠ Called by vanilla whenever the hitbox is rebuilt, including on the CLIENT after the synced flag arrives — which
     * is why the flag is synced rather than server-only. A client-side box that did not match would make the yautja
     * unclickable where it looks, and its shadow the wrong size.
     */
    @Override
    public @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        var dimensions = super.getDefaultDimensions(pose);

        if (isCrawling()) {
            return EntityDimensions.scalable(dimensions.width(), CRAWL_HEIGHT);
        }

        // ⚠ Hanging is checked AFTER crawling, so a yautja that is somehow both stays at the smaller box.
        return isClimbHanging() ? EntityDimensions.scalable(dimensions.width(), HANGING_HEIGHT) : dimensions;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Climbing and airtime
    // -----------------------------------------------------------------------------------------------------------

    private int climbLostContactTicks;

    /** Server tick before which it may not grab a wall again. See YautjaClimb.REATTACH_COOLDOWN_TICKS. */
    private int nextClimbAttachTick;

    public int getNextClimbAttachTick() {
        return nextClimbAttachTick;
    }

    public void setClimbAttachCooldown(int ticks) {
        this.nextClimbAttachTick = tickCount + ticks;
    }

    /** Consecutive ticks off the ground. Drives the jump clip, and is cheaper than watching velocity. */
    private int airborneTicks;

    private int ticksSinceLanding = Integer.MAX_VALUE;

    public boolean isClimbing() {
        return entityData.get(DATA_CLIMBING);
    }

    public void setClimbing(boolean climbing) {
        entityData.set(DATA_CLIMBING, climbing);

        if (!climbing) {
            climbLostContactTicks = 0;
            setClimbHanging(false);
        }
    }

    /** Server-side countdown for the overhang vault. */
    private int climbVaultTicks;

    /** Consecutive climbing ticks with no upward progress. See YautjaClimb's stall backstop. */
    private int climbStallTicks;

    /**
     * Height while hanging under a lip.
     * <p>
     * ⚠ 1.5, not the 0.9 crawl height. The point is to get the HEAD out of the block overhead, not to flatten the
     * yautja — and the tilt in the animator does the rest, so the box only has to cover a body angled back from the
     * wall rather than a standing one.
     */
    private static final float HANGING_HEIGHT = 1.5F;

    /**
     * Blocks. How far out an inbound threat is noticed — a live explosive, or another yautja's capture net.
     * <p>
     * ⚠ Covers rockets, thrown grenades, nukes and vanilla TNT — anything whose purpose is to detonate. A yautja that
     * dodged a rocket but stood in a grenade blast would read as broken, and the player has no way of knowing the two
     * are different code.
     */
    private static final double THREAT_WATCH_RADIUS = 8.0;

    private int dodgeUntilTick;

    private int nextDodgeTick;

    private byte lastDispatchedDodge;

    /**
     * What survives a hunter's extra hide, applied to EVERY damage source.
     * <p>
     * ⚠ His 10% "across the board", so it is the LAST multiplier in {@link #hurt} — after caliber, after the drum
     * penalty, after the melee bonus, after fire. Anything that reaches a hunter is reduced, including sources this mod
     * knows nothing about, which is the point of "across the board".
     * <p>
     * ⚠ Multiplicative, so it stacks predictably with the tier bonuses rather than being swallowed by them: it makes
     * every fight against a hunter exactly 11% longer whatever its tier.
     */
    private static final float HUNTER_DAMAGE_TAKEN = 0.90F;

    public boolean isHunter() {
        return entityData.get(DATA_HUNTER);
    }

    public void setHunter(boolean hunter) {
        entityData.set(DATA_HUNTER, hunter);

        // ⚠ The hunter bonus is baked into the ATTRIBUTES, not read at damage time, so promoting one has to rewrite
        // them — otherwise a Hunter would carry its 10% resistance while still having a plain tier's health.
        applyTierAttributes();
        setHealth(getMaxHealth());
    }

    public boolean isDodging() {
        return tickCount < dodgeUntilTick;
    }

    /**
     * {@return whether the roll fired}
     * <p>
     * ⚠ Server-side and cooldown-gated in one place, so the melee path and the rocket path cannot each spend the same
     * dodge.
     */
    public boolean tryDodge(net.minecraft.world.entity.Entity threat) {
        if (level().isClientSide || tickCount < nextDodgeTick || isClimbing()) {
            return false;
        }

        dodgeUntilTick = tickCount + YautjaDodge.ROLL_TICKS;
        nextDodgeTick = tickCount + YautjaDodge.COOLDOWN_TICKS;

        entityData.set(DATA_DODGE_SEQUENCE, (byte) (entityData.get(DATA_DODGE_SEQUENCE) + 1));
        YautjaDodge.roll(this, threat);

        return true;
    }

    public boolean isClimbHanging() {
        return entityData.get(DATA_CLIMB_HANGING);
    }

    public void setClimbHanging(boolean hanging) {
        if (entityData.get(DATA_CLIMB_HANGING) == hanging) {
            return;
        }

        entityData.set(DATA_CLIMB_HANGING, hanging);
        refreshDimensions();
    }

    public boolean isClimbVaulting() {
        return entityData.get(DATA_CLIMB_VAULTING);
    }

    public int getClimbStallTicks() {
        return climbStallTicks;
    }

    public void setClimbStallTicks(int ticks) {
        this.climbStallTicks = ticks;
    }

    public int getClimbVaultTicks() {
        return climbVaultTicks;
    }

    public void setClimbVaultTicks(int ticks) {
        this.climbVaultTicks = ticks;

        var vaulting = ticks > 0;

        if (entityData.get(DATA_CLIMB_VAULTING) != vaulting) {
            entityData.set(DATA_CLIMB_VAULTING, vaulting);
        }
    }

    public int getClimbLostContactTicks() {
        return climbLostContactTicks;
    }

    public void setClimbLostContactTicks(int ticks) {
        this.climbLostContactTicks = ticks;
    }

    public int getAirborneTicks() {
        return airborneTicks;
    }

    public int getTicksSinceLanding() {
        return ticksSinceLanding;
    }

    /**
     * ⚠⚠ THIS IS WHAT STOPS A CLIMB ENDING IN FALL DAMAGE, and it is the ONLY part of vanilla's climbable machinery
     * still in use. The physics are driven in {@link #travel}, because {@code handleOnClimbable} hardcodes the ascent
     * at 0.2 blocks per tick and he asked for two different climb speeds.
     */
    @Override
    public boolean onClimbable() {
        return isClimbing() || super.onClimbable();
    }

    /** Server tick before which it will not leap again, so one beside a lake does not pogo. */
    private int nextLeapTick;

    // ---------------------------------------------------------------- evasion (see YautjaFallBackGoal)

    private int nextFallBackTick;

    private int nextAdrenalineTick;

    /** How long something that hit the yautja still counts as fighting it. */
    public static final int RECENT_ATTACKER_TICKS = 100;

    /** Who hit it, and when (tick). Server side, not saved — a fight does not survive a reload. */
    private final java.util.Map<java.util.UUID, Integer> recentAttackers = new java.util.HashMap<>();

    /**
     * {@return whether {@code entity} hit this yautja in the last {@link #RECENT_ATTACKER_TICKS}} Prunes as it goes.
     */
    public boolean wasRecentlyAttackedBy(LivingEntity entity) {
        recentAttackers.values().removeIf(tick -> tickCount - tick > RECENT_ATTACKER_TICKS || tick > tickCount);

        var tick = recentAttackers.get(entity.getUUID());

        return tick != null;
    }

    /** While the tick count is below this, MOVE_TO_TARGET is suppressed so the ranged goals get their shot. */
    private int rangedHoldUntil;

    /** Damage taken inside the burst window, decayed each tick — the "losing health quickly" reading. */
    private float recentDamage;

    private int recentDamageTick = Integer.MIN_VALUE / 2;

    public int getNextFallBackTick() {
        return nextFallBackTick;
    }

    public void setFallBackCooldown(int ticks) {
        this.nextFallBackTick = tickCount + ticks;
    }

    public int getNextAdrenalineTick() {
        return nextAdrenalineTick;
    }

    public void setAdrenalineCooldown(int ticks) {
        this.nextAdrenalineTick = tickCount + ticks;
    }

    public void setRangedHoldUntil(int tick) {
        this.rangedHoldUntil = tick;
    }

    /** {@return whether the fall-back leap is holding it at range right now} Read by YautjaCombat.MOVE_TO_TARGET. */
    public boolean isHoldingRange() {
        return tickCount < rangedHoldUntil;
    }

    /** {@return damage taken within the last {@code YautjaThreatAssessment.BURST_WINDOW_TICKS} ticks} */
    public float getRecentDamage() {
        return recentDamage;
    }

    /**
     * ⚠ A WINDOW, NOT A TOTAL. Decayed every tick so chip damage never adds up to a trigger while a shotgun or a pack
     * does. Called from hurt(); the decay runs in the server tick.
     */
    private void recordRecentDamage(float amount) {
        if (tickCount - recentDamageTick > YautjaThreatAssessment.BURST_WINDOW_TICKS) {
            recentDamage = 0.0F;
        }

        recentDamage += amount;
        recentDamageTick = tickCount;
    }

    public int getNextLeapTick() {
        return nextLeapTick;
    }

    public void setLeapCooldown(int ticks) {
        this.nextLeapTick = tickCount + ticks;
    }

    public int getRestUntilTick() {
        return restUntilTick;
    }

    public void restFor(int ticks) {
        this.restUntilTick = tickCount + ticks;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Attack animations
    // -----------------------------------------------------------------------------------------------------------

    /**
     * 🚨🚨 FIVE BITS, NOT THREE. With three, only ordinals 0-7 could be sent, and the enum has had TWELVE values for a
     * while — so everything from ordinal 8 up arrived as a different clip: SPEAR_STAB played NOTHING, SPEAR_THROW
     * played a SLASH, WRIST_FIRE (the net) played a PUNCH. Five bits holds 32 clips, which covers the four battleaxe
     * steps and leaves room, and still leaves three sequence bits so the same clip can replay back to back. ⚠ If the
     * enum ever passes 32 values, this must grow again — see the assertion in playAttackAnimation.
     */
    private static final int ATTACK_CLIP_BITS = 5;

    private static final int ATTACK_CLIP_MASK = (1 << ATTACK_CLIP_BITS) - 1;

    /** Server-side: which step of the slash/slash/stab/bare combo comes next. */
    private int meleeComboStep;

    private int spearComboStep;

    /** Client-side: the combi stick state last seen, so open and close fire once on the transition. */
    private CombiStickState lastSpearState = CombiStickState.COLLAPSED;

    /** Client-side: the last stamped value played, so each attack fires exactly once. */
    private byte lastDispatchedAttack;

    /** {@return the step this swing should use, and advances the combo} */
    public YautjaAttackAnimation nextMeleeStep() {
        return YautjaAttackAnimation.meleeStep(meleeComboStep++);
    }

    /**
     * ⚠ Its OWN counter, separate from the melee combo. Sharing one would mean drawing a combi stick mid-combo started
     * the spear on step 2 — a stab with no swipes before it, which is the finisher without the setup.
     */
    /** Where the battleaxe combo is up to. */
    private int battleaxeComboStep;

    public YautjaAttackAnimation nextBattleaxeStep() {
        return YautjaAttackAnimation.battleaxeStep(battleaxeComboStep++);
    }

    public YautjaAttackAnimation nextSpearStep() {
        return YautjaAttackAnimation.spearStep(spearComboStep++);
    }

    /** Server-side. Stamps an attack for every tracking client to play once. */
    public void playAttackAnimation(YautjaAttackAnimation animation) {
        if (level().isClientSide) {
            return;
        }

        // ⚠⚠ FAIL LOUD RATHER THAN SEND THE WRONG CLIP. Overflowing this field is exactly how the spear stab, spear
        // throw and net fire silently played the wrong animations; an enum that outgrows it must be caught here.
        if (animation.ordinal() > ATTACK_CLIP_MASK) {
            throw new IllegalStateException(
                "YautjaAttackAnimation." + animation + " (ordinal " + animation.ordinal() + ") does not fit in "
                    + ATTACK_CLIP_BITS + " bits — widen ATTACK_CLIP_BITS"
            );
        }

        var previous = entityData.get(DATA_ATTACK_ANIMATION);
        var sequence = (previous >> ATTACK_CLIP_BITS) + 1;

        entityData.set(DATA_ATTACK_ANIMATION, (byte) ((sequence << ATTACK_CLIP_BITS) | animation.ordinal()));
    }

    /**
     * Plays the yautja's half of the combi stick deploy and stow.
     * <p>
     * ⚠⚠ TWO ANIMATORS, ONE EVENT. The ITEM plays {@code combi.open} on its own model — the segments telescoping —
     * while the YAUTJA plays {@code attack.spear.open} on its body. Neither knows about the other; they stay in step
     * because both are driven off the same state on the stack, which is exactly why that state lives there rather than
     * in either animator.
     * <p>
     * ⚠ Driven off a TRANSITION, not the state itself. Dispatching every tick the stick is OPENING would restart the
     * clip five times and it would never visibly progress.
     */
    private void dispatchSpearAnimations() {
        var held = getMainHandItem();
        var state = held.getItem() instanceof CombiStickItem
            ? CombiStickItem.stateOf(held)
            : CombiStickState.COLLAPSED;

        if (state == lastSpearState) {
            return;
        }

        var previous = lastSpearState;

        lastSpearState = state;

        if (state == CombiStickState.OPENING) {
            animationDispatcher.spearOpen();
        } else if (state == CombiStickState.COLLAPSED && previous != CombiStickState.COLLAPSED) {
            animationDispatcher.spearClose();
        }
    }

    private void dispatchDodgeAnimation() {
        var stamped = entityData.get(DATA_DODGE_SEQUENCE);

        if (stamped == lastDispatchedDodge) {
            return;
        }

        lastDispatchedDodge = stamped;

        if (stamped != 0) {
            animationDispatcher.dodge();
        }
    }

    private void dispatchRoarAnimation() {
        var stamped = entityData.get(DATA_ROAR_SEQUENCE);

        if (stamped == lastDispatchedRoar) {
            return;
        }

        lastDispatchedRoar = stamped;

        // ⚠ Zero is the initial value, so a yautja that has never roared does not roar the moment it loads.
        if (stamped != 0) {
            animationDispatcher.roar();
        }
    }

    private void dispatchAttackAnimations() {
        var stamped = entityData.get(DATA_ATTACK_ANIMATION);

        if (stamped == lastDispatchedAttack) {
            return;
        }

        lastDispatchedAttack = stamped;

        var animation = YautjaAttackAnimation.byId(stamped & ATTACK_CLIP_MASK);

        if (animation != YautjaAttackAnimation.NONE) {
            // ⚠ At the clip's own speed — the spear stab and throw are deliberately slowed so they can be read.
            YautjaAnimationDispatcher.playAttack(this, animation.clip(), animation.playbackSpeed());
        }
    }

    public int getLastMeleeAttackTick() {
        return lastMeleeAttackTick;
    }

    public void setLastMeleeAttackTick(int tick) {
        this.lastMeleeAttackTick = tick;
    }

    // -----------------------------------------------------------------------------------------------------------
    // Shoulder plasma caster
    // -----------------------------------------------------------------------------------------------------------

    public CasterState getCasterState() {
        return CasterState.byId(entityData.get(DATA_CASTER_STATE));
    }

    public void setCasterState(CasterState state) {
        entityData.set(DATA_CASTER_STATE, state.id());
    }

    public boolean isCasterDeployed() {
        return getCasterState().isDeployed();
    }

    public void setCasterAim(float yaw, float pitch) {
        entityData.set(DATA_CASTER_AIM_YAW, yaw);
        entityData.set(DATA_CASTER_AIM_PITCH, pitch);
    }

    public float getCasterAimYaw() {
        return entityData.get(DATA_CASTER_AIM_YAW);
    }

    public float getCasterAimPitch() {
        return entityData.get(DATA_CASTER_AIM_PITCH);
    }

    /**
     * ⚠ Aug 28 — cloak persistence. Without this, every world load stood the yautja exposed for the seconds until
     * {@code YautjaCloakGoal} re-engaged — a free spotting window on every server restart. The flag saves here and
     * re-engages in {@link #readAdditionalSaveData}; the tracking-start sync then tells clients as they come into
     * range, so a loaded yautja is cloaked before anyone ever sees it.
     */
    private static final String CLOAK_TAG = "CloakActive";

    /**
     * ⚠ Aug 28, his ruling — the honor code binds a yautja to REVEAL on physical attacks: a landed melee blow drops the
     * cloak through the same {@code onMeleeHit} window players get (2s, then it re-engages for free). Shipped alongside
     * the shader-pack particle tell so shader users are never fighting a tough mob that is invisible through the whole
     * fight — the reveal is gameplay-layer and no pack can break it.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        var landed = super.doHurtTarget(target);

        if (landed) {
            com.predator.common.gameplay.cloak.PredatorCloakManager.onMeleeHit(this);
        }

        return landed;
    }

    /**
     * ⚠ Aug 28, his ruling: "preds are rare so they should be persistant like queens." Without this they despawned like
     * common hostiles the moment the player left the chunk.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private static final String DEBUG_WEAPON_LOCK_TAG = "DebugWeaponLocked";

    private static final String PLACED_MINES_TAG = "PlacedMines";

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Hunter", isHunter());

        if (huntedPlayer != null) {
            tag.putUUID("HuntedPlayer", huntedPlayer);
        }

        tag.putBoolean("HuntReturn", huntReturn);
        tag.putBoolean("HuntEscaped", huntEscaped);
        tag.putString(VARIANT_TAG, getVariant().serializedName());
        tag.putString(ARMOR_VARIANT_TAG, getArmorVariant().serializedName());
        tag.putString(TIER_TAG, getTier().serializedName());
        tag.putBoolean(CLOAK_TAG, com.predator.common.gameplay.cloak.PredatorCloak.isCloaked(this));
        tag.put(NBT_INVENTORY, BLibInventory.CODEC.encode(BLibCodecs.Schema.NBT, inventory));
        // ⚠ Persisted so a test setup survives a reload — relogging mid-test would otherwise silently unlock it.
        tag.putBoolean(DEBUG_WEAPON_LOCK_TAG, debugWeaponLocked);

        // Its mines, so a reload does not orphan them: the cap still counts them and its death still clears them.
        tag.putLongArray(PLACED_MINES_TAG, placedMines.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // ⚠⚠ Oct 5 review - ATTRIBUTE BASES ARE SAVED, SO A NEW DEFAULT NEVER REACHES AN EXISTING MOB. LivingEntity
        // loads
        // every attribute base value from the save, and step height is one (maxUpStep reads it every tick, so it is
        // always
        // instantiated and always written). Every yautja already in a world would have kept stepping at 1.2 forever.
        // Raising a lower saved value here brings them up to the current figure; a higher one (set by a command or
        // another mod) is left alone.
        var stepHeight = getAttribute(Attributes.STEP_HEIGHT);

        if (stepHeight != null && stepHeight.getBaseValue() < STEP_HEIGHT) {
            stepHeight.setBaseValue(STEP_HEIGHT);
        }

        // ⚠ Persisted, or a hunter would demote itself to an ordinary yautja on the first chunk reload — mid-fight,
        // silently, and with its drops changing underneath the player.
        setHunter(tag.getBoolean("Hunter"));
        huntedPlayer = tag.hasUUID("HuntedPlayer") ? tag.getUUID("HuntedPlayer") : null;
        huntReturn = tag.getBoolean("HuntReturn");
        huntEscaped = tag.getBoolean("HuntEscaped");
        debugWeaponLocked = tag.getBoolean(DEBUG_WEAPON_LOCK_TAG);

        placedMines.clear();

        for (var packed : tag.getLongArray(PLACED_MINES_TAG)) {
            placedMines.add(net.minecraft.core.BlockPos.of(packed));
        }

        // Absent on a yautja saved before variants existed — byName falls back to NORMAL, which is what it looked like.
        if (tag.contains(VARIANT_TAG)) {
            setVariant(YautjaVariant.byName(tag.getString(VARIANT_TAG)));

            // ⚠ Persisted, or a yautja swaps armour set every time its chunk reloads.
            setArmorVariant(YautjaArmorVariant.byName(tag.getString(ARMOR_VARIANT_TAG)));

            // ⚠ Straight to the data accessor, NOT setTier — setTier heals to full, and a wounded yautja must
            // not come back from a chunk reload at full health. Attributes are applied by the caller instead.
            entityData.set(DATA_TIER, YautjaTier.byName(tag.getString(TIER_TAG)).serializedName());
            applyTierAttributes();
        }

        if (tag.contains(NBT_INVENTORY)) {
            // ⚠ The inventory is final and the codec hands back a NEW one, so the contents are copied across rather
            // than the field being reassigned. Anything already holding a reference to it keeps working.
            BLibInventory.CODEC.decode(BLibCodecs.Schema.NBT, tag.get(NBT_INVENTORY)).ifOk(loaded -> {
                inventory.clear();

                for (var stack : loaded.getSerializedItemStacks()) {
                    if (!stack.isEmpty()) {
                        inventory.addItemStack(stack);
                    }
                }
            });
        }

        // Absent on older saves — they load uncloaked and the goal decides, exactly as before this tag existed.
        if (tag.getBoolean(CLOAK_TAG)) {
            com.predator.common.gameplay.cloak.PredatorCloakManager.engage(this);
        }

        // The caster is deliberately NOT saved. A yautja that quit mid-engagement has no fight to come back to, and a
        // saved DEPLOYING state would restore a hunter standing with its gun up at nothing.
        setCasterState(CasterState.STOWED);
    }

    /**
     * ⚠ Raised from vanilla's 300 (15 seconds) BECAUSE the float goal above no longer surfaces it mid-chase. Gating
     * that goal without this would have introduced a drowning bug that did not exist before — a yautja that dived after
     * prey and suffocated doing it. Sixty seconds is long enough for any believable underwater pursuit and still
     * finite, so one trapped below really does die.
     */
    @Override
    public int getMaxAirSupply() {
        return 1200;
    }

    public static AttributeSupplier.Builder createYautjaAttributes() {
        var builder = Monster.createMonsterAttributes();

        // ⚠⚠ THESE ARE ONLY DEFAULTS — every one of them is overwritten per entity by applyTierAttributes().
        // This method is STATIC and builds ONE supplier shared by every yautja, so it cannot know a tier. The
        // values are BLOODED's, matching what actually spawns, so a yautja is never briefly wrong even if
        // something reads its attributes before the tier is applied.
        builder.add(Attributes.ARMOR, YautjaTier.DEFAULT.armor(false));
        builder.add(Attributes.ARMOR_TOUGHNESS, YautjaTier.DEFAULT.toughness(false));
        builder.add(Attributes.ATTACK_DAMAGE, YautjaTier.DEFAULT.damage(false));
        builder.add(Attributes.FOLLOW_RANGE, 35F);
        builder.add(Attributes.KNOCKBACK_RESISTANCE, YautjaTier.DEFAULT.knockbackResistance());
        builder.add(Attributes.MAX_HEALTH, YautjaTier.DEFAULT.health(false));
        // ⚠ His spec: a strut 15% BELOW a player's walk. This was BASE_WALK_SPEED * 1.2 — 20% ABOVE one — so
        // the yautja has been ambling at 1.41x the intended pace. The run is a modifier on top of this, not a
        // second attribute; see YautjaMovement for both figures and the animation playback that matches them.
        builder.add(Attributes.MOVEMENT_SPEED, YautjaMovement.WALK_SPEED);

        // ⚠⚠ Oct 5 - 1.5, [stated] "they should be able to step up 1.5 high steps", after "they seem to jump around
        // alot
        // going up normal one block heights". At 1.2 a block topped with a slab, or any full block climbed from a slab,
        // carpet or path block, was over the limit, and vanilla's MoveControl turns every waypoint more than one step
        // height above into a real jump. Re-checked for coupling: YautjaJump keys off gaps and YautjaClimb needs two
        // blocks of wall, so neither moves.
        // (History) 1.2, so a one-block ledge is a STRIDE rather than a hop. Vanilla mobs default to 0.6, which cannot
        // clear a full block, so every kerb and slab edge became a jump or a wall-grab — his "keeps climbing up
        // 1 block heights". The pathfinder already treated a 1-block step as walkable
        // (TerrainEvaluatorConfig.maxStepHeight defaults to 1), so only the PHYSICS disagreed.
        //
        // ⚠⚠ Safe here, but it is not safe everywhere: in avp_alien the crusher charge and the chrysalis roll both
        // derive their terrain-vs-wall line from step height, so raising it silently breaks both. The yautja has no
        // such coupling — checked: YautjaJump keys off gaps and YautjaClimb needs 2+ blocks of wall.
        builder.add(Attributes.STEP_HEIGHT, STEP_HEIGHT);

        return builder;
    }

    /**
     * How long a yautja keeps hunting a target it can no longer see.
     * <p>
     * ⚠⚠ RAISED FROM VANILLA'S 60 SO A DOOR IS COVER, NOT SAFETY. His ruling: a door STOPS a yautja, but it can still
     * attack through one. The attack half already works — {@code Mob.doHurtTarget} does not check line of sight
     * (verified), and the melee action's precondition is distance only — but the TARGET would have been forgotten after
     * three seconds unseen, so it would have wandered off mid-swing. Ten seconds means shutting a door in its face buys
     * a barrier and time, not an escape.
     * <p>
     * ⚠ This does NOT reopen the "aggroed onto nothing" bug. That was about ACQUIRING a target through walls, which
     * {@code mustSee = true} still prevents. This governs only how long an already-acquired one is remembered.
     */
    private static final int DOOR_PERSISTENCE_TICKS = 200;

    /**
     * [stated] Oct 5: "they should be able to step up 1.5 high steps". See createAttributes and readAdditionalSaveData.
     */
    public static final double STEP_HEIGHT = 1.5;

    @Override
    protected void registerGoals() {
        // ⚠⚠ GATED ON HAVING NO TARGET, AND THAT IS NOT A TWEAK. FloatGoal jumps the mob toward the surface every
        // tick it is in deep water — which is the exact opposite of his "it would dive after anyone trying to hide
        // underwater". With BLib driving vertical swim during a pursuit, the two fight and the yautja bobs instead
        // of diving. It still runs when idle, so one knocked into a lake with nothing to hunt still surfaces.
        goalSelector.addGoal(0, new FloatGoal(this) {

            @Override
            public boolean canUse() {
                return getTarget() == null && super.canUse();
            }
        });
        // ⚠⚠ THE CHASE AND THE STROLL ARE GONE FROM HERE ON PURPOSE. DelayedAttackGoal, UseItemGoal and both
        // stroll goals all steer through mob.getNavigation(), which is vanilla navigation — the thing the BLib
        // pathfinder replaces. Movement and combat are now the GOAP graph's job (YautjaGOAP), and the chase speed
        // rides on YautjaCombat.MOVE_TO_TARGET instead. Anything re-added here that calls getNavigation() will
        // fight the navigator rather than co-operate with it.

        // ⚠⚠ PRIORITY 1, ABOVE EVERY WEAPON GOAL. The fall-back leap has to interrupt whatever it is doing; it holds
        // the MOVE flag so the walk cannot fight it, and it ends its own hold as soon as the distance is bought.
        goalSelector.addGoal(1, new YautjaFallBackGoal(this));

        // ⚠ Holds no goal flags, so it never blocks the melee goal or the walk. That is what lets the caster fire on
        // its own target while the yautja is fighting something else entirely.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaPlasmaCasterGoal(this),
                this::mayUseCaster,
                this::putCasterAway
            )
        );

        // Also flagless. A dart leaving the bracer must not interrupt the swim GOAP is driving.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaDartGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ Holds no goal flags, like the other weapon goals: it must not block the walk or the melee goal.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaBowGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ No goal flags, like the other weapon goals. The slam is a situational crowd answer on its own cooldown.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaBattleaxeSlamGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ No flags, always running — healing never blocks moving or fighting. See YautjaHealing.
        goalSelector.addGoal(2, new com.predator.common.gameplay.entity.living.yautja.goal.YautjaHealGoal(this));

        // The chain whip, and the mine-and-whip combo. No flags, like every gauntlet goal. See YautjaWhipGoal.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new com.predator.common.gameplay.entity.living.yautja.goal.YautjaWhipGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // The plasma shuriken — a ranged weapon fired from the gauntlet. See YautjaPlasmaShurikenGoal.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new com.predator.common.gameplay.entity.living.yautja.goal.YautjaPlasmaShurikenGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // PASS 3 — the ambush at a hidden target's way out, occasional trail traps, and fire pellets at flammable
        // cover.
        // No flags, like every gauntlet goal. The trap goal stands down while an ambush is under way.
        var ambush = new com.predator.common.gameplay.entity.living.yautja.goal.YautjaAmbushGoal(this);

        goalSelector.addGoal(2, ambush);
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new com.predator.common.gameplay.entity.living.yautja.goal.YautjaTrapGoal(this, ambush),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new com.predator.common.gameplay.entity.living.yautja.goal.YautjaFirePelletGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ Also flagless. Throwing at prey it cannot walk to must not interrupt whatever GOAP is doing about it.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaThrowGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // Grenades from its loadout, at groups and at prey out of reach. Gated like every weapon: none in a bare-handed
        // fight. See YautjaGrenadeGoal.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGrenadeGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ Also flagless. A net is a capture tool, so it fires at prey that is getting away rather than prey it is
        // already beating — see YautjaNetGoal.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaNetGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        // ⚠ Flagless like the rest. Throwing the spear costs the yautja nothing — the projectile is a fresh stack,
        // so it stays armed, exactly as vanilla's Drowned does with its trident.
        goalSelector.addGoal(
            2,
            new com.predator.common.gameplay.entity.living.yautja.goal.YautjaGatedGoal(
                new YautjaSpearThrowGoal(this),
                this::mayUseWeapons,
                this::putWeaponsAway
            )
        );

        goalSelector.addGoal(6, new YautjaCloakGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Yautja.class));

        // ⚠ KEPT. The target selector does not move the yautja, it only decides what getTarget() returns — which is
        // what the plasma caster, the cloak goal and the pursuit-navigator switch all read. The GOAP graph senses
        // its own targets through the same YautjaPredicates gate, so the two can never disagree about the honor code.
        targetSelector.addGoal(
            2,
            new NearestAttackableTargetGoal<>(
                this,
                LivingEntity.class,
                // ⚠⚠ mustSee = TRUE. This was false, which let a yautja lock onto anything inside its follow
                // range THROUGH WALLS, UNDERGROUND OR OVERHEAD. To a player that reads as "it suddenly aggroed
                // onto nothing" — because the thing it aggroed onto is behind six blocks of stone. A hunter
                // that stalks what it cannot see is also just wrong: seeing the prey is the hunt.
                true,
                target -> YautjaPredicates.isThreateningTarget(this, target)
            ).setUnseenMemoryTicks(DOOR_PERSISTENCE_TICKS)
        );
    }

    @Override
    public void tick() {
        super.tick();

        // Oct 5 - profiler v3 laps (/blib perf "inside the tick"); free while no session runs.
        var perfLap = com.blib.api.common.perf.v1.BLibPerf.start();
        checkMask();
        checkHunterArmour();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.mask+armour", perfLap);

        // Wounded to the threshold in phase 2: it flees (once). If it may not flee now, the hold is released so the
        // fight can end normally.
        if (
            huntedPlayer != null && !huntEscaped && !level().isClientSide && level() instanceof ServerLevel huntLevel
                && getHealth() <= getMaxHealth() * ESCAPE_HEALTH_FRACTION + 0.01F
        ) {
            if (com.predator.common.gameplay.hunt.HuntDirector.mayEscape(huntLevel, this)) {
                com.predator.common.gameplay.hunt.HuntDirector.escape(huntLevel, this);
            } else {
                huntEscaped = true;
            }
        }

        // A Hunter the hunt no longer recognises (the night ended, the hunt was stopped, or a stale copy came back
        // with a reloaded chunk after a fresh one was sent) leaves rather than wandering the world.
        if (
            huntedPlayer != null && tickCount % 100 == 0 && level() instanceof ServerLevel serverLevel
                && !com.predator.common.gameplay.hunt.HuntDirector.isCurrentHunter(serverLevel, this)
        ) {
            discard();
            return;
        }

        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.hunt", perfLap);
        updateAirborneTicks();

        if (!level().isClientSide) {
            // The rush is granted here so it fires whether or not the fall-back leap does — one that is outnumbered
            // but not yet in melee still gets it. Also decays the burst-damage window.
            YautjaThreatAssessment.tickRush(this);

            if (tickCount - recentDamageTick > YautjaThreatAssessment.BURST_WINDOW_TICKS) {
                recentDamage = 0.0F;
            }

            // ⚠ Order matters: a yautja that has just grabbed a wall must not also try to leap off it. Both are
            // reactions, because BLib's pathfinder can describe neither a gap nor a vertical surface.
            // ⚠ Only on a CHANGE of target. Calling it every tick would rummage through the rack constantly, and
            // a swap mid-swing would cancel the attack animation it is in the middle of.
            var target = getTarget();

            if (weaponRepickQueued || target != lastMatchedTarget) {
                weaponRepickQueued = false;
                lastMatchedTarget = target;
                matchWeaponTo(target);
            } else if (target != null && tickCount % WEAPON_RANGE_CHECK_TICKS == 0) {
                // ⚠ Distance changes during a fight, the target does not — so the range half of the choice is
                // re-checked on a timer rather than only when the target changes.
                matchWeaponTo(target);
            }

            // ⚠ Straight from the navigator rather than a guess about headroom: BLib already decided, when it
            // planned the path, whether this stretch needs a crawl. Re-deriving it here would be a second opinion
            // that could disagree with the route the yautja is actually following.
            // ⚠⚠ THE SECOND HALF IS AN ANTI-SUFFOCATION RULE, NOT A TIDY-UP. The navigator only reports
            // shouldCrawl while it is FOLLOWING a path through a low gap; a yautja that wandered under a
            // two-block overhang with no path — or whose path just ended — stands upright at 2.48 blocks with
            // its head inside a block and quietly takes suffocation damage until something else moves it.
            // Ducking whenever the head is actually obstructed makes the posture follow reality rather than
            // intent.
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.rush+weaponMatch", perfLap);
            setCrawling(getPathNavigator().getPostureView().shouldCrawl() || isHeadObstructed());
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.posture", perfLap);

            // ⚠ PRE-EMPTIVE. Rolling only when the blast lands means always eating the reduced hit; spotting the
            // rocket in flight is what makes it a dodge rather than a damage resistance. His spec asks for
            // both, and the same cooldown covers them so it cannot do both to one rocket.
            if (!isDodging()) {
                var threat = YautjaDodge.incomingThreat(this, THREAT_WATCH_RADIUS);

                if (threat != null) {
                    tryDodge(threat);
                }
            }

            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.dodgeWatch", perfLap);
            YautjaWaterExit.tick(this);
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.waterExit", perfLap);
            YautjaClimb.tick(this);
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.climb", perfLap);
            YautjaJump.tick(this);
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.jump", perfLap);

            // A blow waiting for its clip's impact frame lands here — see YautjaCombat.tickPendingStrike.
            YautjaCombat.tickPendingStrike(this);

            // Scaled regeneration over time, and the apple's absorption ending on schedule.
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.pendingStrike", perfLap);
            YautjaHealing.tick(this);
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.healing", perfLap);

            // ⚠⚠ A MOB'S HELD ITEM IS NEVER TICKED BY VANILLA, so the combi stick's extend/retract has to be driven
            // from here — otherwise a stick given to a yautja stays collapsed and the throw goal refuses it forever.
            if (getMainHandItem().getItem() instanceof CombiStickItem) {
                CombiStickItem.tickState(getMainHandItem(), level(), this, true);
            }

            // Draws this mob's own BLib path as particles for any player who ran /avp_predator debug path on.
            // ⚠ Each mob draws itself, which is why there is no server-tick event hook and no registry of
            // navigating entities to keep in sync. Costs one empty-set test per tick when nobody is watching.
            perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.combistick", perfLap);
            PredatorPathDebug.draw(this, getPathNavigator());
            PredatorPathDiagnostics.tick(this);
            PredatorCasterTestMode.tick(this);
            com.blib.api.common.perf.v1.BLibPerf.lap(this, "yautja.debugTools", perfLap);
        }

        if (level().isClientSide) {
            updateSwimPitch();
            dispatchBodyAnimations();
            dispatchCasterAnimations();
            dispatchAttackAnimations();
            dispatchRoarAnimation();
            dispatchDodgeAnimation();
            dispatchSpearAnimations();
        }

        if (!level().isClientSide && (getVehicle() instanceof Boat || getVehicle() instanceof Minecart)) {
            stopRiding();
        }
    }

    /**
     * Counts airtime on BOTH sides.
     * <p>
     * ⚠ Not server-only and not synced: the client simulates {@code onGround} for tracked entities perfectly well, and
     * a synced counter would cost a packet per tick of every jump to tell the client something it can see.
     */
    /**
     * Airtime that counts as a real jump or fall when it ends.
     * <p>
     * ⚠⚠ Oct 5 - [stated] "they seem to jump around alot going up normal one block heights. so it makes it hard to tell
     * if they are just stepping up or jumping". The JUMP clip already waited for four airborne ticks
     * (YautjaBodyState.AIRBORNE_TICKS_FOR_JUMP), but a LANDING was any airtime at all - so the one-tick bump of an
     * ordinary step-up, or the client briefly losing onGround on uneven ground, played the land clip and read as a hop.
     * The same four ticks now gate both, so a step is a walk and only something the yautja actually jumped or fell from
     * lands.
     * </p>
     */
    private static final int MIN_AIRBORNE_TICKS_FOR_LANDING = 4;

    private void updateAirborneTicks() {
        if (onGround() || isClimbing() || isInWater()) {
            if (airborneTicks >= MIN_AIRBORNE_TICKS_FOR_LANDING) {
                ticksSinceLanding = 0;
            } else if (ticksSinceLanding < Integer.MAX_VALUE) {
                ticksSinceLanding++;
            }

            airborneTicks = 0;
            return;
        }

        airborneTicks++;
    }

    /** {@return the smoothed swim pitch in degrees, positive nose-down; zero on land} */
    public float getClientSwimPitch() {
        return clientSwimPitch;
    }

    /**
     * Eases the body pitch toward the direction of travel while swimming, and back to level otherwise.
     * <p>
     * The angle is the actual movement vector, not the look direction — a yautja diving after prey is looking at the
     * prey, and its body should follow where it is GOING. Below the movement floor the last angle is held rather than
     * reset, so treading water does not snap it flat.
     */
    private void updateSwimPitch() {
        var target = 0.0F;

        if (isSwimming()) {
            var dx = getX() - xo;
            var dy = getY() - yo;
            var dz = getZ() - zo;
            var horizontal = Math.sqrt(dx * dx + dz * dz);

            if (horizontal + Math.abs(dy) > SWIM_PITCH_MOVEMENT_FLOOR) {
                // Positive when descending, matching vanilla's xRot convention.
                target = (float) (-Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
            } else {
                target = clientSwimPitch;
            }

            target = Mth.clamp(target, -SWIM_PITCH_LIMIT, SWIM_PITCH_LIMIT);
        }

        clientSwimPitch += (target - clientSwimPitch) * SWIM_PITCH_SMOOTHING;
    }

    /**
     * Plays the locomotion clip that matches how the yautja is actually moving, once per change.
     * <p>
     * Client-side because that is where Az commands are dispatched, and because every input is already there — see
     * {@link YautjaBodyState} for why none of this needs syncing.
     */
    private void dispatchBodyAnimations() {
        var state = YautjaBodyState.select(this);

        if (state == lastDispatchedBodyState) {
            return;
        }

        lastDispatchedBodyState = state;

        switch (state) {
            case IDLE -> animationDispatcher.idle();
            case WALK -> animationDispatcher.walk();
            case RUN -> animationDispatcher.run();
            case SWIM -> animationDispatcher.swim();
            case DODGE -> { /* dispatched as an event, like the roar */ }
            case ROAR -> { /* the roar is dispatched as an event, not by locomotion state */ }
            case CRAWL -> animationDispatcher.crawl();
            case CLIMB_SLOW -> animationDispatcher.climbSlow();
            case CLIMB_FAST -> animationDispatcher.climbFast();
            // ⚠ Two idle clips; each yautja keeps to one, chosen from its id so it does not flick between them.
            case CLIMB_IDLE -> {
                if ((getId() & 1) == 0) {
                    animationDispatcher.climbIdleLeft();
                } else {
                    animationDispatcher.climbIdleRight();
                }
            }
            case JUMP -> animationDispatcher.jump();
            case LAND -> animationDispatcher.land();
        }
    }

    /**
     * Plays the caster clip that matches the synced state, once per change.
     * <p>
     * The five clips touch ONLY {@code gCasterMount}, {@code gCasterArm} and {@code gCaster} — not one body bone
     * appears in any of them — so they ride on their own animation track layered over the body and cannot fight the
     * walk, the idle or an attack.
     */
    private void dispatchCasterAnimations() {
        var state = getCasterState();

        if (state == lastDispatchedCasterState) {
            return;
        }

        lastDispatchedCasterState = state;

        switch (state) {
            case STOWED -> animationDispatcher.casterIdle();
            case DEPLOYING -> animationDispatcher.casterAim();
            case READY -> animationDispatcher.casterReady();
            case FIRING -> animationDispatcher.casterShoot();
            case DISARMING -> animationDispatcher.casterDisarm();
        }
    }

    public void checkMask() {
        if (level().isClientSide || !hasMask()) {
            return;
        }

        var overHalfHealth = getHealth() > getMaxHealth() / 2;

        if (!overHalfHealth) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

            // ⚠ Hung off the EXISTING mask-break rather than re-deriving "health below half", so the roar and
            // the mask can never disagree about when it happened. YautjaAnimator.showHelmet already hides
            // gArmorMask once hasMask() is false, so the mask is gone from the model before the clip starts.
            if (tickCount >= nextRoarTick && level() instanceof ServerLevel serverLevel) {
                roarUntilTick = tickCount + YautjaRoar.ROAR_TICKS;
                nextRoarTick = tickCount + YautjaRoar.COOLDOWN_TICKS;

                entityData.set(DATA_ROAR_SEQUENCE, (byte) (entityData.get(DATA_ROAR_SEQUENCE) + 1));
                YautjaRoar.roar(serverLevel, this);
            }
        }
    }

    // ---------------------------------------------------------------- Hunter armour stages

    /** [stated] "each loss reduces defence by 15%". */
    private static final double DEFENCE_LOST_PER_PIECE = 0.15;

    /**
     * [stated] Hunters lose armour as they are hurt: legs at 75% health, the mask at 50% (every yautja,
     * {@link #checkMask}), the chest at 25% — "Hunters only" for the legs and chest. Called every tick beside
     * checkMask. The pieces simply come off; what a dead Hunter drops is decided separately (one random piece of the
     * set).
     * <p>
     * ⚠ Gated on the stage already being reached, never re-equipping: a Hunter healed back above 75% stays without its
     * legs, the same as the mask. Phase 3's return is a fresh Hunter, so it arrives whole.
     */
    public void checkHunterArmour() {
        if (level().isClientSide || !isHunter()) {
            return;
        }

        var fraction = getHealth() / getMaxHealth();

        if (fraction <= 0.75F && !getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }

        if (fraction <= 0.25F && !getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
    }

    public boolean hasChestPiece() {
        return !getItemBySlot(EquipmentSlot.CHEST).isEmpty();
    }

    /**
     * {@return the share of its defence a Hunter keeps} — 15% gone per lost piece (legs, mask, chest); 1 for others.
     */
    private double huntDefenceScale() {
        if (!isHunter()) {
            return 1.0;
        }

        var lost = 0;

        if (getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            lost++;
        }

        if (!hasMask()) {
            lost++;
        }

        if (!hasChestPiece()) {
            lost++;
        }

        return 1.0 - DEFENCE_LOST_PER_PIECE * lost;
    }

    public boolean hasMask() {
        return getItemBySlot(EquipmentSlot.HEAD).getItem() == PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get();
    }

    @Override
    public boolean startRiding(@NotNull Entity entity, boolean force) {
        if (entity instanceof Boat || entity instanceof Minecart) {
            return false;
        }

        return super.startRiding(entity, force);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        @NotNull ServerLevelAccessor serverLevelAccessor,
        @NotNull DifficultyInstance difficultyInstance,
        @NotNull MobSpawnType mobSpawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        // ⚠ Rolled only when the variant is still the default. finalizeSpawn also runs for a spawn egg, and a
        // /summon that specified {Variant:"tiger"} has already had readAdditionalSaveData applied — re-rolling here
        // would overwrite the value the command asked for.
        if (getVariant() == YautjaVariant.NORMAL) {
            setVariant(YautjaVariant.random(random));
        }

        // ⚠⚠ ITS OWN GUARD, NOT NESTED IN THE SKIN'S. Rolling armour inside the skin check would mean a
        // /summon that specified {Variant:"tiger"} never rolled armour at all and was silently always REGULAR
        // — the two are independent choices and each defends its own explicitly-set value.
        if (getArmorVariant() == YautjaArmorVariant.REGULAR) {
            setArmorVariant(YautjaArmorVariant.random(random));
        }

        // ⚠⚠ NO RANDOM ROLL. His ruling: what spawns is TIER 2, and a spawn egg or /summon produces the same. The
        // other four tiers exist for the hunting system to place deliberately, not for the world to scatter.
        applyTierAttributes();
        setHealth(getMaxHealth());

        setItemSlot(EquipmentSlot.HEAD, new ItemStack(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get()));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE.get()));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS.get()));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(PredatorArmorItems.JUNGLE_PREDATOR_BOOTS.get()));

        // 🚨 HIS LOADOUT (Sep 22). Every yautja carries:
        // ONE melee weapon (in hand) and ONE ranged weapon (in the rack) — tek weapons count as melee or ranged, not
        // a separate category; the veritanium bow brings arrows. TWO healing items, both of one kind. Darts (for
        // swimming), nets (outnumbered / to recover), fire pellets (burn cover and wooden obstacles), trip mines and
        // the chain whip. ⚠ Ammunition is never spent — only needed (Yautja.hasAmmo); healing items ARE spent.
        var melee = MELEE_LOADOUT.get(random.nextInt(MELEE_LOADOUT.size())).get();
        var ranged = RANGED_LOADOUT.get(random.nextInt(RANGED_LOADOUT.size())).get();

        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(melee));
        inventory.addItemStack(
            ranged == PredatorItems.SHURIKEN.get() || ranged == PredatorItems.PLASMA_SHURIKEN.get()
                ? new ItemStack(ranged, 4)
                : new ItemStack(ranged)
        );

        // [stated] "The yautja have a chance to spawn with these instead of normal arrows if they have the veritanium
        // bow
        // 50/50 chance."
        if (ranged == PredatorItems.VERITANIUM_BOW.get()) {
            var arrows = random.nextBoolean() ? PredatorItems.VERITANIUM_ARROW.get() : net.minecraft.world.item.Items.ARROW;

            inventory.addItemStack(new ItemStack(arrows, 16));
        }

        for (var healing : healingLoadout()) {
            inventory.addItemStack(healing);
        }

        inventory.addItemStack(new ItemStack(PredatorItems.VERITANIUM_DART.get(), 16));
        inventory.addItemStack(new ItemStack(PredatorItems.NET.get()));
        inventory.addItemStack(new ItemStack(PredatorItems.FIRE_PELLET.get(), 8));
        inventory.addItemStack(new ItemStack(com.predator.common.registry.init.PredatorBlocks.TRIP_MINE_BLOCK.get().asItem(), 2));
        inventory.addItemStack(new ItemStack(PredatorItems.CHAIN_WHIP.get()));

        stockGrenades();

        return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, mobSpawnType, spawnGroupData);
    }

    /** [stated] the melee choices. Suppliers, so nothing touches the item registry before it exists. */
    private static final java.util.List<java.util.function.Supplier<net.minecraft.world.item.Item>> MELEE_LOADOUT = java.util.List.of(
        PredatorItems.VERITANIUM_SWORD::get,
        PredatorItems.VERITANIUM_AXE::get,
        PredatorItems.BATTLEAXE::get,
        PredatorItems.COMBI_STICK::get,
        PredatorItems.PLASMA_SWORD::get
    );

    /** [stated] the ranged choices; the veritanium bow brings arrows with it. */
    private static final java.util.List<java.util.function.Supplier<net.minecraft.world.item.Item>> RANGED_LOADOUT = java.util.List.of(
        PredatorItems.VERITANIUM_BOW::get,
        PredatorItems.PLASMA_BOW::get,
        PredatorItems.SHURIKEN::get,
        PredatorItems.SMART_DISC::get,
        // [stated] "the plasma shuriken counts as a ranged weapon even though it fires from the gauntlet." Carried in
        // the rack, fired from the wrist by YautjaPlasmaShurikenGoal — never drawn into the hand.
        PredatorItems.PLASMA_SHURIKEN::get
    );

    /**
     * [stated] "each yautja would have 2 healing items so that means 2 enchanted golden apples or 2 health potion II or
     * 2 regen potion II". ⚠ Apples stack, so they share one slot; potions do not, so they take two. ⚠ Potions resolved
     * HERE, at spawn, never in a static field — vanilla's Potions registers itself on first touch.
     */
    // ---------------------------------------------------------------- grenades (his Oct 4 ruling)

    /** An ordinary yautja carries grenades half the time. */
    public static float GRENADE_CHANCE = 0.5F;

    /** Explosives every Hunter carries for breaching walls. */
    public static int HUNTER_BREACH_GRENADES = 2;

    /** Oct 8 - sticky grenades every yautja carries regardless of the random roll. */
    public static final int ALWAYS_STICKY_GRENADES = 2;

    /**
     * Gives it its grenades, replacing any it had — called at spawn, and again by the hunt once a Hunter's tier is set,
     * since the Hunter's kit and the irradiated grenade both depend on it.
     * <ul>
     * <li>Ordinary yautja: half the time, 2-3 of one random kind.</li>
     * <li>Hunters: always — {@link #HUNTER_BREACH_GRENADES} explosives for walls, plus 2-3 of one random kind.</li>
     * <li>Irradiated is half as likely as the others and only for Elders and Clan Leaders, so a Youngblood never levels
     * a base.</li>
     * </ul>
     */
    public void stockGrenades() {
        // Slot by slot, so every grenade stack goes whatever its size.
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItemStack(slot).getItem() instanceof com.predator.common.gameplay.item.grenade.YautjaGrenadeItem) {
                inventory.setItemStack(slot, ItemStack.EMPTY);
            }
        }

        // Oct 8 - [stated] "have it where preds always have sticky grenades": two on every yautja, before the
        // random roll, so it always has the siege option (YautjaSiege) against prey it cannot climb to.
        inventory.addItemStack(
            new ItemStack(
                com.predator.common.gameplay.item.grenade.YautjaGrenadeItem
                    .forKind(com.predator.common.gameplay.item.grenade.GrenadeKind.STICKY),
                ALWAYS_STICKY_GRENADES
            )
        );

        if (isHunter()) {
            inventory.addItemStack(new ItemStack(PredatorItems.PRED_GRENADE_EXPLOSIVE.get(), HUNTER_BREACH_GRENADES));
        } else if (random.nextFloat() >= GRENADE_CHANCE) {
            return;
        }

        var kind = randomGrenadeKind();
        inventory.addItemStack(
            new ItemStack(com.predator.common.gameplay.item.grenade.YautjaGrenadeItem.forKind(kind), 2 + random.nextInt(2))
        );
    }

    private com.predator.common.gameplay.item.grenade.GrenadeKind randomGrenadeKind() {
        var irradiatedAllowed = getTier().ordinal() >= YautjaTier.ELDER.ordinal();
        // Weights: explosive, fire, sticky, freeze 2 each; irradiated 1 (and only for the top two tiers).
        var total = 8 + (irradiatedAllowed ? 1 : 0);
        var roll = random.nextInt(total);

        if (roll < 2) {
            return com.predator.common.gameplay.item.grenade.GrenadeKind.EXPLOSIVE;
        }

        if (roll < 4) {
            return com.predator.common.gameplay.item.grenade.GrenadeKind.FIRE;
        }

        if (roll < 6) {
            return com.predator.common.gameplay.item.grenade.GrenadeKind.STICKY;
        }

        if (roll < 8) {
            return com.predator.common.gameplay.item.grenade.GrenadeKind.FREEZE;
        }

        return com.predator.common.gameplay.item.grenade.GrenadeKind.IRRADIATED;
    }

    /**
     * Half the time, one or two of a grenade it was still carrying — [stated] fighting them is how the gear is had. On
     * top of the loot table's own drops.
     */
    private void dropCarriedGrenade() {
        if (random.nextBoolean()) {
            return;
        }

        for (var kind : com.predator.common.gameplay.item.grenade.GrenadeKind.values()) {
            var item = com.predator.common.gameplay.item.grenade.YautjaGrenadeItem.forKind(kind);

            if (inventory.hasItem(item)) {
                spawnAtLocation(new ItemStack(item, inventory.hasItem(item, 2) && random.nextBoolean() ? 2 : 1));
                return;
            }
        }
    }

    private java.util.List<ItemStack> healingLoadout() {
        return switch (random.nextInt(3)) {
            case 0 -> java.util.List.of(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 2));
            case 1 -> java.util.List.of(
                potion(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING),
                potion(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING)
            );
            default -> java.util.List.of(
                potion(net.minecraft.world.item.alchemy.Potions.STRONG_REGENERATION),
                potion(net.minecraft.world.item.alchemy.Potions.STRONG_REGENERATION)
            );
        };
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> type) {
        return net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, type);
    }

    public void setMoveControl(MoveControl moveControl) {
        this.moveControl = moveControl;
    }

    public void setNavigation(PathNavigation navigation) {
        this.navigation = navigation;
    }

    /**
     * ⚠⚠ THIS USED TO SWAP THE WHOLE NAVIGATION STACK. {@code YautjaNavigationManager} tore out a vanilla
     * {@code GroundPathNavigation} and put a {@code WaterBoundPathNavigation} in its place every time the yautja went
     * under, swapping the move control and an attack goal with it. None of that is needed now: BLib's pathfinder
     * classifies GROUND and WATER inside ONE navigator and hands the movement off to
     * {@code WaterPathMovementController} on its own. All that is left here is telling vanilla whether to draw and
     * treat the entity as swimming.
     */
    @Override
    public void updateSwimming() {
        if (!level().isClientSide) {
            setSwimming(isEffectiveAi() && isUnderWater());
        }
    }

    @Override
    public void travel(@NotNull Vec3 vec3) {
        // ⚠ Climbing sets its own delta every tick in YautjaClimb, so the movement system must not add to it.
        // Falling through to super here would apply gravity underneath the ascent and stall the climb.
        if (isClimbing()) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }

        if (isControlledByLocalInstance() && isUnderWater()) {
            moveRelative(0.01F, vec3);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.8));
        } else {
            super.travel(vec3);
        }
    }

    /**
     * [stated] his click recordings are "normal sounds predators make when idle or stalking": the clicking while it
     * stalks — cloaked, or with a target — and the softer idle sounds otherwise. Both pick a random variant each time
     * (three of each in sounds.json).
     */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return getTarget() != null || com.predator.common.gameplay.cloak.PredatorCloak.isCloaked(this)
            ? PredatorSoundEvents.YAUTJA_CLICK.get()
            : PredatorSoundEvents.YAUTJA_IDLE.get();
    }

    /**
     * [stated] his death recordings are "for the non hunter preds". A Hunter makes no death cry of its own: "the
     * hunters death is the self destruct laugh because when it despawns from death it leaves behind the gauntlet with
     * the self destruct armed" — the laugh comes from that gauntlet's countdown (see {@link #die}). If the
     * self-destruct game rule is off there is no countdown and so no laugh, and a Hunter falls back to the ordinary
     * death cry.
     */
    @Override
    protected @Nullable SoundEvent getDeathSound() {
        if (isHunter() && com.predator.common.registry.init.PredatorGameRules.isSelfDestructEnabled(level())) {
            return null;
        }

        return PredatorSoundEvents.YAUTJA_DEATH.get();
    }

    /** Taking damage: one of his five pain recordings, cleaned, levelled and quickened. */
    @Override
    protected @Nullable SoundEvent getHurtSound(net.minecraft.world.damagesource.@NotNull DamageSource damageSource) {
        return PredatorSoundEvents.YAUTJA_HURT.get();
    }

    /** A hunter is quiet: on average one sound every ~15 s, not vanilla's chatty 4 s. */
    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }
}
