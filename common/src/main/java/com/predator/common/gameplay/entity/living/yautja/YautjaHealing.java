package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
 * <li>Regeneration II potion — 450 ticks at 1 per 25 = 18 of 20 = <b>90%</b>, over 22.5 s.</li>
 * <li>Enchanted golden apple — Regeneration II 400 ticks = 16 of 20 = <b>80%</b> over 20 s; Absorption IV 2400 ticks =
 * 16 of 20 = <b>80%</b> absorption for 2 minutes; plus Resistance I and Fire Resistance for 5 minutes each, which are
 * not health-based and are applied exactly as vanilla gives them.</li>
 * </ul>
 * ⚠ Healing over time is done here rather than through vanilla's Regeneration effect, which heals a FIXED 1 point per
 * interval whatever the maximum — it cannot be scaled.
 */
public final class YautjaHealing {

    /** [stated] 30 seconds between uses. */
    public static final int COOLDOWN_TICKS = 20 * 30;

    /** The same threshold the mask break uses: at or below half. */
    public static final float HEAL_BELOW = 0.5F;

    private static final float HEALING_II_SHARE = 8.0F / 20.0F;

    private static final float REGEN_POTION_SHARE = 18.0F / 20.0F;

    private static final int REGEN_POTION_TICKS = 450;

    private static final float APPLE_REGEN_SHARE = 16.0F / 20.0F;

    private static final int APPLE_REGEN_TICKS = 400;

    private static final float APPLE_ABSORPTION_SHARE = 16.0F / 20.0F;

    private static final int APPLE_ABSORPTION_TICKS = 2400;

    private static final int APPLE_BUFF_TICKS = 6000;

    /**
     * ⚠ Vanilla CAPS absorption with the max-absorption attribute (Absorption IV raises it by 16). A scaled amount
     * would be clipped straight back to 16 without raising the cap for as long as the apple's absorption lasts.
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

        if (used.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            yautja.startRegeneration(max * APPLE_REGEN_SHARE, APPLE_REGEN_TICKS);
            grantAbsorption(yautja, max * APPLE_ABSORPTION_SHARE, APPLE_ABSORPTION_TICKS);
            yautja.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, APPLE_BUFF_TICKS, 0));
            yautja.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, APPLE_BUFF_TICKS, 0));
            level.playSound(null, yautja.blockPosition(), SoundEvents.GENERIC_EAT, yautja.getSoundSource(), 1.0F, 0.8F);
            level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, used),
                yautja.getX(),
                yautja.getEyeY() - 0.2D,
                yautja.getZ(),
                12,
                0.25D,
                0.15D,
                0.25D,
                0.05D
            );
        } else {
            var contents = used.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);

            if (contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING)) {
                yautja.heal(max * HEALING_II_SHARE);
            } else {
                yautja.startRegeneration(max * REGEN_POTION_SHARE, REGEN_POTION_TICKS);
            }

            level.playSound(null, yautja.blockPosition(), SoundEvents.GENERIC_DRINK, yautja.getSoundSource(), 1.0F, 0.8F);
        }

        level.sendParticles(ParticleTypes.HEART, yautja.getX(), yautja.getEyeY() + 0.4D, yautja.getZ(), 4, 0.4D, 0.2D, 0.4D, 0.0D);
        yautja.setNextHealTick(yautja.tickCount + COOLDOWN_TICKS);

        return true;
    }

    /** Scaled absorption, with the cap raised to hold it for as long as it lasts. */
    private static void grantAbsorption(Yautja yautja, float amount, int ticks) {
        var cap = yautja.getAttribute(Attributes.MAX_ABSORPTION);

        if (cap == null) {
            return;
        }

        cap.removeModifier(ABSORPTION_CAP_ID);
        cap.addTransientModifier(new AttributeModifier(ABSORPTION_CAP_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        yautja.setAbsorptionAmount(Math.max(yautja.getAbsorptionAmount(), amount));
        yautja.setAbsorptionUntilTick(yautja.tickCount + ticks);
    }

    /** Server tick: regeneration over time, and the absorption ending on schedule. */
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
