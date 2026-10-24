package com.predator.common.gameplay.item;

import com.predator.common.registry.init.PredatorTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.jetbrains.annotations.NotNull;

/**
 * The plasma sword.
 * <ul>
 * <li>[stated] "the plasma sword deals more damage than the regular sword" — 11 against the veritanium sword's 9, on
 * the same veritanium tier: 1 (hand) + 5 (tier) + 5.</li>
 * <li>[stated] "it also sets things on fire" — every landed hit sets the target alight.</li>
 * <li>[stated] "plasma sword lights blocks by hitting it right click has no function." — hitting a flammable block
 * (wood, leaves, wool — anything lava could ignite) sets fire on the face you struck. See igniteBlock.</li>
 * </ul>
 * ⚠⚠ THE BLOCK HALF CANNOT LIVE ON THE ITEM. I checked the 1.21.1 jar: the server does NOT call Item.canAttackBlock
 * when a player starts hitting a block, so no item hook sees that moment. It is caught where the server DOES see it —
 * ServerPlayerGameMode.handleBlockBreakAction, whose start-hitting action carries the struck FACE, exactly what placing
 * the fire needs (MixinServerPlayerGameMode_PlasmaSword).
 */
public class PlasmaSwordItem extends SwordItem {

    /** 5 on top of the veritanium tier's 5 and the hand's 1: 11. */
    public static final int ATTACK_DAMAGE_BONUS = 5;

    public static final float ATTACK_SPEED = -2.4F;

    public static final float IGNITE_SECONDS = 4.0F;

    public PlasmaSwordItem(Properties properties) {
        super(
            PredatorTiers.VERITANIUM,
            properties.attributes(SwordItem.createAttributes(PredatorTiers.VERITANIUM, ATTACK_DAMAGE_BONUS, ATTACK_SPEED)).fireResistant()
        );
    }

    /** ⚠ Stops a sword already in the hand announcing itself the moment a player logs in. */
    private static final int SELECTION_SOUND_GRACE_TICKS = 10;

    /**
     * Player -> the last game tick a plasma sword was in their main hand. Server side.
     * <p>
     * ⚠ Oct 14 - WEAK keys on the player OBJECT, not a map by UUID: the UUID map never forgot anyone (a player who
     * logged out holding the sword stayed in it for the life of the server). A player who leaves or respawns is a new
     * object, so the old entry is collected on its own; the respawned player's first draw still ignites after the grace
     * ticks.
     * <p>
     * ⚠⚠ DELIBERATELY NOT A DATA COMPONENT, unlike the older weapons' WAS_SELECTED flags. Writing a component changes
     * the held stack, and the client's ItemInHandRenderer lowers and re-raises the hand whenever the held stack stops
     * matching - so a flag written just after the switch can dip the sword a second time mid-draw. The battleaxe is
     * protected from that by MixinItemInHandRenderer_MainHandKeep; the sword is not, and this needs nothing synced.
     */
    private static final java.util.Map<Player, Long> HELD_AT = new java.util.WeakHashMap<>();

    /**
     * Oct 10 - his ignite sound on taking the sword out, his shut-off sound on putting it away.
     * <p>
     * Switching straight from one plasma sword to another plays neither: the blade never went out. A sword DROPPED from
     * the hand plays no shut-off (nothing ticks it any more); the next draw still ignites, because the stale entry is
     * more than a tick old.
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        // ⚠⚠ "selected" IS NOT "IN THE MAIN HAND". Inventory.tick (1.21.1 bytecode) passes selected = (index == hotbar
        // slot) for EVERY compartment - main inventory, armour AND offhand - so with hotbar slot 0 chosen, the OFFHAND
        // item and the boots are also told they are selected. Identity against the main hand is the real test.
        var held = selected && player.getMainHandItem() == stack;
        var now = level.getGameTime();

        if (held) {
            var previous = HELD_AT.put(player, now);

            if ((previous == null || previous < now - 1) && player.tickCount > SELECTION_SOUND_GRACE_TICKS) {
                playEquip(player, true);
            }

            return;
        }

        var previous = HELD_AT.get(player);

        // ⚠ Exactly last tick, and nothing plasma in the hand now - every unselected sword in the inventory runs this,
        // and only the switch away from a held one counts.
        if (previous != null && previous == now - 1 && !(player.getMainHandItem().getItem() instanceof PlasmaSwordItem)) {
            HELD_AT.remove(player);
            playEquip(player, false);
        }
    }

    /** Ignite ({@code drawn}) or shut off. Shared with the yautja, which plays these when it swaps weapons. */
    public static void playEquip(LivingEntity user, boolean drawn) {
        if (user.level().isClientSide || user.isSilent()) {
            return;
        }

        user.level()
            .playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                drawn
                    ? com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_EQUIP.get()
                    : com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_PUTAWAY.get(),
                user.getSoundSource(),
                1.0F,
                0.975F + user.getRandom().nextFloat() * 0.05F
            );
    }

    /**
     * Oct 11 - ticks from the start of each of his swing files to its loudest point, measured at 25 ms: attack 1 builds
     * slowly to 0.35 s, attack 2 is up almost at once and peaks around 0.20 s.
     */
    public static int ATTACK_1_PEAK_TICKS = 7;

    public static int ATTACK_2_PEAK_TICKS = 4;

    /**
     * A yautja swinging the sword: either of his two swings, timed so its peak lands on the weapon clip's impact frame
     * (attack.melee.weapon connects on tick 10 at the yautja's heavy-attack speed, so attack 1 starts on tick 3 and
     * attack 2 on tick 6). Called from YautjaCombat when an armed step starts.
     */
    public static void scheduleYautjaSwing(com.predator.common.gameplay.entity.living.yautja.Yautja yautja, int impactTicks) {
        var first = yautja.getRandom().nextBoolean();

        com.predator.common.gameplay.item.battleaxe.BattleaxeSounds.schedule(
            yautja,
            first
                ? com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_ATTACK_1.get()
                : com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_ATTACK_2.get(),
            impactTicks,
            first ? ATTACK_1_PEAK_TICKS : ATTACK_2_PEAK_TICKS
        );
    }

    /**
     * Oct 11 - either of his two swings on every connecting hit. ⚠ Hits only, like the battleaxe and like vanilla's own
     * swing sounds - the server sees nothing for a miss but an arm-swing packet that also fires every tick of mining.
     */
    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        target.igniteForSeconds(IGNITE_SECONDS);

        if (attacker instanceof Player && !attacker.level().isClientSide) {
            com.predator.common.gameplay.item.battleaxe.BattleaxeSounds.play(
                attacker,
                attacker.getRandom().nextBoolean()
                    ? com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_ATTACK_1.get()
                    : com.predator.common.registry.init.PredatorSoundEvents.PLASMA_SWORD_ATTACK_2.get(),
                com.predator.common.gameplay.item.battleaxe.BattleaxeSounds.varied(attacker)
            );
        }

        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Sets fire on the struck face of a flammable block. ⚠ Guards: the player must be able to interact with that block
     * and position (reach, spawn protection, adventure mode) — this runs before vanilla's own checks on the action.
     */
    public static void igniteBlock(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        if (player.isSpectator() || !player.canInteractWithBlock(pos, 1.0D) || !level.mayInteract(player, pos)) {
            return;
        }

        if (!level.getBlockState(pos).ignitedByLava()) {
            return;
        }

        var firePos = pos.relative(face);

        if (!BaseFireBlock.canBePlacedAt(level, firePos, face)) {
            return;
        }

        level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
        level.playSound(null, firePos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
    }
}
