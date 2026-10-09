package com.predator.common.gameplay.hunt;

import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

/**
 * How the Hunter sizes up its prey. [stated] "the predator will judge you by what you have in your inventory hotbar and
 * will recheck when you move items or open your inventory. so you cant cheese it."
 * <p>
 * Read from the HOTBAR and off hand only — the nine slots and the shield hand a player can actually fight from — and
 * re-read every second by the director, so moving a weapon onto or off the bar is noticed almost at once. Stashing a
 * sword in a backpack slot does not make a player "unarmed": it only matters if they could not draw it.
 * <p>
 * Only one question is answered here — is this player armed at all? Everything finer is already handled the way the
 * yautja always fights: it answers sword with sword, axe with axe and bow with spear (Yautja.matchWeaponTo), and the
 * shoulder caster comes out only for someone HOLDING a heavy weapon ({@link PlasmaCaster#isCarryingHeavyWeapon}) —
 * [stated] "only if youre holding it not just if its in your inventory".
 */
public final class HunterJudgement {

    private HunterJudgement() {}

    /** {@return whether nothing on the player's hotbar or in their off hand is a weapon} */
    public static boolean isUnarmed(Player player) {
        var inventory = player.getInventory();

        for (var slot = 0; slot < 9; slot++) {
            if (isWeapon(inventory.getItem(slot))) {
                return false;
            }
        }

        return !isWeapon(player.getOffhandItem());
    }

    /**
     * A weapon is anything that hits harder than a fist, fires a projectile, or is one of the guns the caster answers.
     * <p>
     * ⚠ "Hits harder than a fist" is read from the item's own attack-damage modifier, so every sword, axe, mace and
     * modded blade counts without a list. Pickaxes and shovels count too — a player swinging a pickaxe is fighting. ⚠
     * avp_human's guns are matched by registry id (namespace + a gun-ish path) so there is no compile dependency on
     * avp_human, and by the caster tag, which already lists its heavy guns.
     */
    static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        var item = stack.getItem();

        if (item instanceof ProjectileWeaponItem || item instanceof TridentItem || stack.is(PredatorItemTags.CASTER_WORTHY_WEAPONS)) {
            return true;
        }

        var id = BuiltInRegistries.ITEM.getKey(item);

        if ("avp_human".equals(id.getNamespace())) {
            var path = id.getPath();

            if (
                path.contains("rifle") || path.contains("pistol") || path.contains("shotgun") || path.contains("smartgun")
                    || path.contains("painless") || path.contains("launcher") || path.contains("gun") || path.contains("revolver")
                    || path.contains("flamethrower") || path.contains("sniper")
            ) {
                return true;
            }
        }

        var hasDamage = new boolean[1];

        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.amount() > 0.0) {
                hasDamage[0] = true;
            }
        });

        return hasDamage[0];
    }
}
