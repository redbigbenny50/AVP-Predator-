package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A yautja healing itself once its mask is gone.
 * <h2>His rulings</h2>
 * <ul>
 * <li>[stated] "the yautja would use the items once their mask breaks and they do their roar attack ... at once under
 * 50% health and after it does the roar attack following its mask breaking."</li>
 * <li>[stated] "After it heals back over 50% it doesnt regain its mask that stays broken." — nothing here touches the
 * helmet; the mask break is one-way already.</li>
 * <li>[stated] "the healing items have a 30s cooldown before a yautja will use them again."</li>
 * <li>[stated] "for the healing yes scale it" — see below.</li>
 * </ul>
 * <h2>⚠⚠ SCALED TO SHARE OF HEALTH, NOT VANILLA'S FLAT NUMBERS</h2> Vanilla's numbers are sized for a 20-health player;
 * a Blooded has 150, so used as-is Healing II restored 5% and two items could not lift it back over half. Each item
 * restores the same SHARE of the yautja's health it gives a player. Read from the 1.21.1 jar:
 * <ul>
 * <li>Healing II — instant heal 8 of 20 = <b>40%</b>, at once.</li>
 * </ul>
 * [stated] Oct 25 - "i said remove regen items from pred you kept the golden apples remove those too please": no
 * regeneration of any kind - neither the Regeneration II potion nor the enchanted golden apple is a healing item now.
 * Healing II is the only one. An item a data pack adds to the healing tag heals the same instant 40%.
 * ⚠ Healing over time is done here rather than through vanilla's Regeneration effect, which heals a FIXED 1 point per
 * interval whatever the maximum — it cannot be scaled.
 */
public final class YautjaHealing {

    /** [stated] 30 seconds between uses. */
    public static final int COOLDOWN_TICKS = 20 * 30;

    /** The same threshold the mask break uses: at or below half. */
    public static final float HEAL_BELOW = 0.5F;

    private static final float HEALING_II_SHARE = 8.0F / 20.0F;

    /**
     * The absorption-cap modifier the golden apple used to add. Apples no longer heal, but a yautja saved mid-buff in an
     * older version still carries it, so {@link #tick} keeps taking it off on schedule.
     */
    private static final ResourceLocation ABSORPTION_CAP_ID = ResourceLocation.fromNamespaceAndPath(
        "avp_predator",
        "yautja_apple_absorption"
    );

    private YautjaHealing() {
        throw new UnsupportedOperationException();
    }

    /** {@return whether it is ready to heal now} Mask gone, roar over, under half, off cooldown, carrying something. */
    public static boolean wantsToHeal(Yautja yautja) {
        return !yautja.hasMask()
            && !yautja.isRoaring()
            && yautja.getHealth() < yautja.getMaxHealth() * HEAL_BELOW
            && yautja.tickCount >= yautja.getNextHealTick()
            && yautja.findHealingSlot() >= 0;
    }

    /** Uses ONE healing item from the rack or the hand and starts its effect. {@return whether one was used} */
    public static boolean useOne(ServerLevel level, Yautja yautja) {
        var slot = yautja.findHealingSlot();

        if (slot < 0) {
            return false;
        }

        var stack = slot == Yautja.HAND_HEALING_SLOT ? yautja.getMainHandItem() : yautja.getInventory().getItemStack(slot);
        var used = stack.copyWithCount(1);
        var max = yautja.getMaxHealth();

        // ⚠ CONSUMED. His loadout is two per yautja; unlike ammunition, healing is spent.
        stack.shrink(1);

        if (slot != Yautja.HAND_HEALING_SLOT) {
            yautja.getInventory().setItemStack(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        // [stated] Oct 24/25 - no regeneration items at all: Healing II (or anything a data pack adds to the healing
        // tag) heals its share at once.
        yautja.heal(max * HEALING_II_SHARE);
        level.playSound(
            null,
            yautja.blockPosition(),
            used.is(Items.POTION) ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT,
            yautja.getSoundSource(),
            1.0F,
            0.8F
        );

        level.sendParticles(ParticleTypes.HEART, yautja.getX(), yautja.getEyeY() + 0.4D, yautja.getZ(), 4, 0.4D, 0.2D, 0.4D, 0.0D);
        yautja.setNextHealTick(yautja.tickCount + COOLDOWN_TICKS);

        return true;
    }

    /**
     * Server tick: finishes any heal-over-time or apple absorption a yautja was saved with in an older version. Nothing
     * starts either any more.
     */
    public static void tick(Yautja yautja) {
        yautja.tickRegeneration();

        if (yautja.getAbsorptionUntilTick() > 0 && yautja.tickCount >= yautja.getAbsorptionUntilTick()) {
            yautja.setAbsorptionUntilTick(0);

            var cap = yautja.getAttribute(Attributes.MAX_ABSORPTION);

            if (cap != null) {
                cap.removeModifier(ABSORPTION_CAP_ID);
            }

            // ⚠ Clamp to the restored cap, exactly as vanilla does when its own absorption effect ends.
            yautja.setAbsorptionAmount(Math.min(yautja.getAbsorptionAmount(), yautja.getMaxAbsorption()));
        }
    }
}
