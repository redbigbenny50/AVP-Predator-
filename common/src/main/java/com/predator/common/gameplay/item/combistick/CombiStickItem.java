package com.predator.common.gameplay.item.combistick;

import com.predator.common.gameplay.entity.projectile.CombiStickProjectile;
import com.predator.common.registry.init.PredatorDataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The combi stick — a collapsible spear.
 * <h2>His spec</h2> "when you equip it you play the combi.open animation, when its equipped it does the combi.loop, you
 * put it away too fast for the close to be noticeable but if possible play combi.close when you put it away." Two
 * attacks, a swipe and a stab, the stab dealing knockback, combined like the punches: swipe, swipe, stab.
 * <h2>⚠⚠ THE STATE LIVES ON THE STACK, NOT ON THE HOLDER</h2> A data component rather than a field or a map: it
 * survives being dropped, put in a chest, handed to another player and picked up by a yautja. A collapsed stick that
 * "remembered" being extended because the last holder had it out would be an invisible desync between the model and the
 * damage it deals.
 * <h2>Why it extends on equip rather than on use</h2> ⚠ Right-click is the THROW, matching the trident and the
 * shuriken. If right-click also had to extend it, the first click of every fight would be wasted — so holding it is
 * what opens it, and that is also what his spec asks for.
 */
public class CombiStickItem extends Item {

    /** ⚠ Matches combi.open's 0.25s. Attacks are refused until it elapses, so the blades are out before they cut. */
    public static final int OPEN_TICKS = 5;

    /** Matches combi.close's 0.17s. */
    public static final int CLOSE_TICKS = 4;

    /** Ticks of wind-up before a throw releases, so a tap does not launch it. */
    /**
     * Minimum hold before a throw is allowed.
     * <p>
     * ⚠⚠ TEN, TO MATCH THE TRIDENT. Vanilla's trident refuses a throw under 10 ticks of draw; this was FIVE, so the
     * combi stick could be thrown twice as fast as the weapon it is modelled on — [stated] "In creative you can repeat
     * throw combi sticks faster than tridents".
     */
    private static final int THROW_WINDUP_TICKS = 10;

    /**
     * Ticks before it can be thrown again.
     * <p>
     * 🚨🚨 NOTHING EVER SET A COOLDOWN. The use() check for one has always been there, but no code path armed it, so it
     * could never fire — in creative, where the stick is not consumed, that meant unlimited throws at click speed. A
     * trident is rate-limited by losing it; this needs a real cooldown to stand in for that.
     */
    private static final int THROW_COOLDOWN_TICKS = 20;

    private static final float THROW_SPEED = 2.5F;

    /**
     * When each extending stick started, so the OPENING state can time out into EXTENDED.
     * <p>
     * ⚠ Not a data component: this is transient timing that nobody needs on disk or over the wire, and writing a
     * component every tick during the extend would sync the stack five times for nothing. ⚠ Weak keys, so a dropped or
     * destroyed stack does not pin an entry.
     */
    private static final Map<ItemStack, Integer> openedAtTick = new WeakHashMap<>();

    /** ⚠ Trident-like reach weapon: 8 total damage (7 + the player's base 1). */
    private static final float ATTACK_DAMAGE = 7.0F;

    /**
     * ⚠⚠ TAKEN FROM THE REFERENCE JAR, not from the trident. Its {@code registerSpearRaw} builds the modifier as
     * {@code attackSpeed - 4.0}, so the literals at its call sites are attacks-per-second directly:
     *
     * <pre>
     *   copper_spear    0.85/sec   modifier -3.15
     *   electrum_spear  1.05/sec   modifier -2.95
     *   dragon_spear    1.20/sec   modifier -2.80
     *   enderite_spear  1.20/sec   modifier -2.80
     * </pre>
     *
     * -3.0 puts the combi stick at 1.0/sec — the middle of their own range, a ~20 tick swing.
     * <p>
     * ⚠ The weapon_attributes jsons only say {@code "parent": "bettercombat:spear"}, so the numbers are NOT there; they
     * are in the registration code, which is where I finally read them.
     */
    private static final float ATTACK_SPEED = -3.0F;

    public CombiStickItem() {
        // ⚠⚠ THE ATTACK SPEED IS WHAT MAKES THE STAB READABLE. With no attribute at all the combi stick swung at
        // the BARE-HAND rate of 4/sec, so a whole swing lasted ~5 ticks and the ported thrust — a squared ramp
        // across 5%-20% of it — was over in about ONE tick. The animation was correct; there was no time to see it.
        //
        // ⚠ -2.9 matches the vanilla TRIDENT (1.1 attacks/sec, ~18 tick swing), which is the closest weapon in
        // reach and weight and is what the reference spear mod's own weapons sit near.
        // ⚠ Damage is set alongside it because supplying an attribute modifier list REPLACES the defaults; setting
        // speed alone would silently drop the item to 1 damage.
        super(
            new Item.Properties()
                .stacksTo(1)
                .durability(512)
                .fireResistant()
                .attributes(
                    ItemAttributeModifiers.builder()
                        .add(
                            Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE
                            ),
                            EquipmentSlotGroup.MAINHAND
                        )
                        .add(
                            Attributes.ATTACK_SPEED,
                            new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE
                            ),
                            EquipmentSlotGroup.MAINHAND
                        )
                        .build()
                )
        );
    }

    /** {@return the stick's current mechanical state, defaulting to collapsed} */
    public static CombiStickState stateOf(ItemStack stack) {
        return stack.getOrDefault(PredatorDataComponents.COMBI_STICK_STATE.get(), CombiStickState.COLLAPSED);
    }

    public static void setState(ItemStack stack, CombiStickState state) {
        stack.set(PredatorDataComponents.COMBI_STICK_STATE.get(), state);
    }

    /**
     * ⚠ Extends the moment it is held and collapses when it is not.
     * <p>
     * {@code inventoryTick} is the only hook that fires for a stack sitting in a hand, which is what "when you equip
     * it" means — there is no equip event for a hotbar slot.
     */
    /**
     * Advances the extend/retract state for a stack held by ANY living thing.
     * <p>
     * 🚨🚨 PULLED OUT OF inventoryTick SO A YAUTJA CAN CALL IT. Vanilla only ever runs {@code inventoryTick} for items
     * in a PLAYER's inventory — a mob's held item is never ticked. So a combi stick handed to a yautja stayed in
     * whatever state it arrived in, which from the creative menu is COLLAPSED, forever: the throw goal requires an
     * extended stick and quietly refused every time. [stated] "it didnt even try to throw the combi stick so it must
     * not know its also a distance weapon." It knew; the stick was simply never opened. The yautja now calls this each
     * tick for its main hand, exactly as the player's inventory does.
     */
    public static void tickState(ItemStack stack, Level level, LivingEntity holder, boolean inHand) {
        var state = stateOf(stack);

        if (inHand && state == CombiStickState.COLLAPSED) {
            setState(stack, CombiStickState.OPENING);
            openedAtTick.put(stack, holder.tickCount);
            level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.7F, 0.8F);
        } else if (inHand && state == CombiStickState.OPENING) {
            // ⚠⚠ SOMETHING HAS TO ADVANCE OPENING -> EXTENDED OR IT NEVER ATTACKS. The open clip is 5 ticks of
            // telescoping and canAttack() is false throughout, so without this the stick extends visually and
            // then stays permanently unusable — a bug that looks like the attack code is broken rather than
            // the state machine.
            var since = holder.tickCount - openedAtTick.getOrDefault(stack, holder.tickCount);

            if (since >= OPEN_TICKS) {
                setState(stack, CombiStickState.EXTENDED);
                openedAtTick.remove(stack);
            }
        } else if (!inHand && (state == CombiStickState.EXTENDED || state == CombiStickState.OPENING)) {
            // ⚠ Straight to COLLAPSED rather than through CLOSING: nobody is looking at a stack that has left the
            // hand, and leaving it mid-close would strand it in a state that only the holder's tick can advance.
            setState(stack, CombiStickState.COLLAPSED);
        }
    }

    @Override
    public void inventoryTick(
        @NotNull ItemStack stack,
        @NotNull Level level,
        @NotNull net.minecraft.world.entity.Entity entity,
        int slot,
        boolean selected
    ) {
        if (level.isClientSide || !(entity instanceof LivingEntity holder)) {
            return;
        }

        tickState(stack, level, holder, selected || holder.getOffhandItem() == stack);
    }

    /**
     * ⚠ SPEAR, so the throw gets the trident wind-up for free — the same choice the shuriken uses. The pose is what
     * tells the player a throw is charging; without it, holding right-click looks like nothing is happening.
     */
    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 72000;
    }

    /**
     * Throws the spear.
     * <p>
     * ⚠⚠ THE SLOT DOES EMPTY FOR THE FLIGHT, AND THAT IS THE LOYALTY BEHAVIOUR, NOT A DEPARTURE FROM IT. Vanilla's
     * trident does the same — {@code Inventory.removeItem} on release, and Loyalty flies it back and re-adds it. The
     * player never LOSES the spear, which is what "it has loyalty on it already" buys, but they are without it
     * mid-flight.
     * <p>
     * ⚠ Keeping the stack in hand instead would mean a spear visibly in the hand AND in the air at once, and would let
     * a player fill the sky with them. If that IS what you want, this is the method to change.
     * <p>
     * ⚠ The yautja does NOT go through here. It builds a fresh stack and never touches its hand, exactly as vanilla's
     * Drowned does with its trident.
     */
    /** Ticks of hold for a full-power throw. */
    public static final int FULL_CHARGE_TICKS = 20;

    /**
     * ⚠⚠ THE THROW IS A RIGHT-CLICK HOLD NOW. [stated] "we need to make the combistick stab with left click and throw
     * holding down right click." It was a LEFT-click hold, which needed a hand-polled input handler, a Minecraft mixin
     * and a packet, because vanilla has no left-hold API. Right click IS vanilla's use-hold, so all of that is deleted
     * and this is the whole trigger.
     * <p>
     * ⚠ THE GAUNTLET WILL NOT FIRE WHILE THIS IS HELD, and that is accepted — [stated] "which means the gauntlet wont
     * work with it anymore." The stick is also kept in BLOCKS_GAUNTLET_FIRE so the worn gauntlet never steals the click
     * on the ticks this one passes.
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        if (hand != InteractionHand.MAIN_HAND || !isExtendedInHand(player)) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        // A short wind-up so a tap does not throw — the same shape the shuriken uses.
        if (getUseDuration(stack, entity) - remaining < THROW_WINDUP_TICKS) {
            return;
        }

        // ⚠ POWER SCALES WITH THE HOLD, exactly as the old left-click path did (1.6 to 3.0) — losing that would have
        // made every throw identical and the wind-up pose a lie.
        var held = Mth.clamp(getUseDuration(stack, entity) - remaining, 0, FULL_CHARGE_TICKS);
        var power = 1.6F + 1.4F * (held / (float) FULL_CHARGE_TICKS);
        // ⚠ ARMED HERE, on a successful throw only — a refused one (too short a hold) must not lock the weapon.
        player.getCooldowns().addCooldown(stack.getItem(), THROW_COOLDOWN_TICKS);

        var spear = new CombiStickProjectile(level, player, stack.copy());

        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power, 1.0F);

        if (player.getAbilities().instabuild) {
            spear.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
        } else {
            player.getInventory().removeItem(stack);
        }

        level.addFreshEntity(spear);
        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.TRIDENT_THROW.value(),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    /**
     * {@return whether this player is holding an EXTENDED combi stick in the main hand}
     * <p>
     * ⚠ Extended only. A collapsed baton neither stabs nor throws, so the charge handler and the pose both gate on this
     * rather than on the item alone.
     */
    public static boolean isExtendedInHand(net.minecraft.world.entity.player.Player player) {
        var held = player.getMainHandItem();

        return held.getItem() instanceof CombiStickItem && stateOf(held).canAttack();
    }

    // ⚠⚠ THERE IS NO use() OVERRIDE, DELIBERATELY. His ruling: "left click stabs, holding down left click charges it
    // up for the throw, so right click never occurs." The throw used to live on right-click via startUsingItem, which
    // was wrong — it meant the spear could not be swung and thrown with the same button the rest of melee uses.
    //
    // The stab is vanilla's own attack on left-click press. The THROW is a right-click hold — vanilla's use-hold —
    // so no charge polling exists any more; CombiStickCharge reads the same state for the pose.
    // throw is a packet on release. Nothing here needs a use action, and adding one back would reintroduce the bug.
}
