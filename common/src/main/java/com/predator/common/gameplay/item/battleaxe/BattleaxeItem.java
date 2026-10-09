package com.predator.common.gameplay.item.battleaxe;

import com.predator.common.registry.init.PredatorTiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The yautja battleaxe: a two-handed axe that hits harder than any vanilla axe and slams the ground on right click.
 * <p>
 * [stated] "more damage than an axe and its as slow as an axe", "the slam is right click with a long cooldown".
 * <h2>Numbers</h2> 16 damage at 0.85 swings a second.
 * <ul>
 * <li>[stated] "raise the damage from 12 to 16 and keep it at that so its not quite 1.5x damage but its stronger than
 * the normal damage would be." Every hit already LOOKS and SOUNDS like a crit (see hurtEnemy); this is the weight
 * behind it, short of a true crit's 18.</li>
 * <li>[stated] "veritanium is better than netherite." So it is built on {@code PredatorTiers.VERITANIUM} now, not
 * netherite: 2640 uses instead of 2031, repaired with veritanium shards. The veritanium axe on the same tier hits for
 * 12 — the battleaxe used to merely match it.</li>
 * <li>[stated] "slow down by another 15% the swing speed for a player." 1.0 swings a second became 0.85 (a swing every
 * 23.5 ticks), slower than the veritanium axe's 0.90. Damage per second still rises, 12 to 13.6.</li>
 * </ul>
 * ⚠ A YAUTJA holding it gains the same +4: a mob's held weapon adds to its attack damage exactly as a player's does,
 * and its slam scales from that too. Its own swing rate is set separately, in YautjaCombat.
 */
public class BattleaxeItem extends AxeItem {

    /** Attack damage added on top of the tier's. 1 (hand) + 5 (veritanium tier) + 10 = 16. */
    public static final float ATTACK_DAMAGE_BONUS = 10.0F;

    /** 0.85 swings a second — 15% slower than an axe's 1.0, and slower than the veritanium axe's 0.90. */
    public static final float ATTACK_SPEED = -3.15F;

    /** [stated] "a long cooldown". 15 seconds. */
    public static int SLAM_COOLDOWN_TICKS = 300;

    public BattleaxeItem() {
        super(
            PredatorTiers.VERITANIUM,
            new Properties().attributes(createAttributes(PredatorTiers.VERITANIUM, ATTACK_DAMAGE_BONUS, ATTACK_SPEED)).fireResistant()
        );
    }

    /**
     * The slam.
     * <p>
     * ⚠ Right click belongs to the slam, which is why the battleaxe is in BLOCKS_GAUNTLET_FIRE — [stated] "when equiped
     * the gauntlet cant be fired".
     */
    /**
     * The slam — now a LEAP, then the slam as you land.
     * <p>
     * [stated] "for the special attack with rightclick you should also jump with the slam too like the pred does." The
     * yautja's slam clip lifts it 21 px — about 1.3 blocks — by 0.17 s and brings the axe down at 0.54 s. A launch of
     * {@link #LEAP_VELOCITY} peaks at 1.37 blocks and lands after 12 ticks (0.6 s), so the player's slam arrives with
     * the same weight and the same beat.
     * <p>
     * ⚠ The jump is applied on BOTH sides, as vanilla's riptide trident does: a player's movement is driven by their
     * own client, so a server-only push would arrive late and rubber-band. The SLAM itself is server-only, on landing.
     * ⚠ Right click belongs to the slam, which is why the battleaxe is in BLOCKS_GAUNTLET_FIRE.
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // ⚠ From the ground only. Already airborne, there is nothing to leap FROM, and a mid-air right click that
        // stacked a second launch would let a player climb by spamming it.
        if (!player.onGround()) {
            return InteractionResultHolder.fail(stack);
        }

        var motion = player.getDeltaMovement();

        player.setDeltaMovement(motion.x, LEAP_VELOCITY, motion.z);
        player.hasImpulse = true;

        // The pose raises the axe from this moment (BattleaxeSlamPose). Stamped on both sides so the swinger's own view
        // responds at once; the server's copy is the one every other client receives.
        stack.set(com.predator.common.registry.init.PredatorDataComponents.BATTLEAXE_LEAP_AT.get(), level.getGameTime());

        if (!level.isClientSide) {
            PENDING_SLAMS.put(player.getUUID(), player.tickCount);
        }

        player.getCooldowns().addCooldown(this, SLAM_COOLDOWN_TICKS);

        // ⚠⚠ CONSUME, NOT SUCCESS. [stated] "you swing then jump then swing again which then does the smash." Vanilla
        // swings the arm on any SUCCESSFUL use (InteractionResult.shouldSwing is SUCCESS only — checked in the jar);
        // that
        // was the first swing. The motion is the slam pose now: raise while airborne, chop on landing.
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Lands the slam when the leap comes down.
     * <p>
     * ⚠ inventoryTick runs every tick for every stack a player carries, which is why the slam is keyed to the PLAYER
     * and checked against the tick the leap began, not simply fired whenever this stack is ticked.
     */
    @Override
    public void inventoryTick(
        @NotNull ItemStack stack,
        @NotNull Level level,
        @NotNull net.minecraft.world.entity.Entity entity,
        int slot,
        boolean selected
    ) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player) || !selected) {
            return;
        }

        var startedAt = PENDING_SLAMS.get(player.getUUID());

        if (startedAt == null) {
            return;
        }

        // ⚠ No fall damage from the leap itself — it is a weapon move, not a fall.
        player.fallDistance = 0.0F;

        var airborne = player.tickCount - startedAt;

        // ⚠ Give it a couple of ticks to leave the ground, or the tick it launched would count as a landing.
        if (airborne >= MIN_AIRBORNE_TICKS && player.onGround()) {
            PENDING_SLAMS.remove(player.getUUID());
            BattleaxeSlam.perform(serverLevel, player, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            // ⚠ No player.swing here any more — that was the second swing. The landing is stamped instead, and the pose
            // drives the axe down from raised (BattleaxeSlamPose).
            stack.set(com.predator.common.registry.init.PredatorDataComponents.BATTLEAXE_SLAM_AT.get(), level.getGameTime());

            return;
        }

        // ⚠ A leap that never comes down in time — off a cliff, into water — is abandoned rather than kept waiting, so
        // it cannot become a long fall-damage immunity.
        if (airborne > MAX_AIRBORNE_TICKS) {
            PENDING_SLAMS.remove(player.getUUID());
        }
    }

    /**
     * Makes every landed swing a CRITICAL hit to look at and to hear.
     * <p>
     * [stated] "since its a two handed weapon and does more damage have each hit count as a critical hit. like when you
     * jump and hit something with a sword and you get the particles and the harder hitting sound. have the axe do that
     * but with each time it hits."
     * <p>
     * ⚠ VERIFIED WHERE THIS RUNS: Player.attack calls hurtEnemy only inside the branch where the hit actually landed,
     * after its own crit handling — so this fires once per connecting swing and never for a miss. ⚠ Player.crit is
     * vanilla's own crit effect: it broadcasts the crit-sparks packet to everyone nearby, so it looks exactly like a
     * jump crit. ⚠ SKIPPED WHEN VANILLA ALREADY CRIT — falling onto the target — or it would spark and sound twice. ⚠
     * LOOK AND SOUND ONLY. A vanilla crit also multiplies damage by 1.5, which would take this from 12 to 18 a hit;
     * that has not been added.
     */
    @Override
    public boolean hurtEnemy(
        @NotNull ItemStack stack,
        @NotNull net.minecraft.world.entity.LivingEntity target,
        @NotNull net.minecraft.world.entity.LivingEntity attacker
    ) {
        var hit = super.hurtEnemy(stack, target, attacker);

        if (attacker instanceof Player player && !player.level().isClientSide && !(player.fallDistance > 0.0F && !player.onGround())) {
            player.crit(target);
            player.level()
                .playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    player.getSoundSource(),
                    1.0F,
                    1.0F
                );
        }

        return hit;
    }

    /** Upward launch of the leap. 1.37 blocks high, 12 ticks in the air — matched to the yautja's slam clip. */
    public static double LEAP_VELOCITY = 0.44D;

    private static final int MIN_AIRBORNE_TICKS = 3;

    /** Two seconds. A leap still in the air after this is not landing on anything worth slamming. */
    private static final int MAX_AIRBORNE_TICKS = 40;

    /** Leaps in progress, server side: player -> the tick it launched. */
    private static final java.util.Map<java.util.UUID, Integer> PENDING_SLAMS = new java.util.HashMap<>();

    /**
     * ⚠⚠ NO LOG STRIPPING. AxeItem's right click on a block strips wood; here right click is the SLAM, and a battleaxe
     * that quietly stripped the log you were standing next to instead of slamming would read as broken. PASS hands the
     * click on to {@link #use}.
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        return InteractionResult.PASS;
    }
}
