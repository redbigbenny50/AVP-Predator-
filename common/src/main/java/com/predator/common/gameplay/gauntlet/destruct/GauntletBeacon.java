package com.predator.common.gameplay.gauntlet.destruct;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Makes a counting self-destruct gauntlet findable through walls, like a spectral-arrow hit. [stated] "maybe let the
 * player see it like spectral arrows highlight it through walls just to be extra sure they can find it."
 * <ul>
 * <li><b>Dropped</b> — the item entity itself glows (MixinItemEntity_GauntletCountdown).</li>
 * <li><b>Carried</b> — the player carrying it glows (GauntletItem).</li>
 * <li><b>Placed</b> — a block cannot glow, so this puts a glowing red item display of the gauntlet on it for as long as
 * it counts, and takes it away the moment it stops — disarmed, broken, picked up, gone off, or the rule turned
 * off.</li>
 * </ul>
 * <h2>⚠ Display setters are private in vanilla</h2> The display is built from saved data instead (the same keys a
 * {@code /summon} uses): the item, the glow colour, full brightness, glowing. It carries a scoreboard tag so a stray
 * one — left behind if its chunk unloaded out of order — is recognised and removed.
 */
public final class GauntletBeacon {

    public static final String TAG = "avp_predator.gauntlet_beacon";

    /** Bright warning red. */
    private static final int GLOW_COLOUR = 0xFF2020;

    private GauntletBeacon() {}

    /** {@return the beacon's id} — the existing one if it is still there, otherwise a new one placed on the block. */
    public static @Nullable UUID ensure(ServerLevel level, BlockPos pos, ItemStack gauntlet, @Nullable UUID current) {
        var anchor = anchor(pos);

        if (current != null && level.getEntity(current) instanceof Display.ItemDisplay existing) {
            // A marker from before the height fix sits a block too high — moved down onto the gauntlet.
            if (existing.position().distanceToSqr(anchor) > 1.0E-4) {
                existing.setPos(anchor.x, anchor.y, anchor.z);
            }

            return current;
        }

        var display = EntityType.ITEM_DISPLAY.create(level);

        if (display == null) {
            return null;
        }

        var tag = new CompoundTag();
        tag.put("item", gauntlet.copyWithCount(1).save(level.registryAccess()));
        tag.putInt("glow_color_override", GLOW_COLOUR);
        tag.putBoolean("NoGravity", true);

        var brightness = new CompoundTag();
        brightness.putInt("sky", 15);
        brightness.putInt("block", 15);
        tag.put("brightness", brightness);

        display.load(tag);

        display.setPos(anchor.x, anchor.y, anchor.z);
        display.addTag(TAG);
        display.setGlowingTag(true);
        level.addFreshEntity(display);

        return display.getUUID();
    }

    /**
     * {@return where the marker stands} — the block's BOTTOM centre, not its middle. 🚨 BLib's item renderer draws a
     * model from its feet upward (AzItemRendererPipeline translates +0.5 / +0.51 / +0.5 after vanilla's centring -0.5),
     * so a display standing at the block's middle drew the gauntlet's outline in the top half of the block and the half
     * above it ([tester] "the red marker isnt on the actual gauntlet"). Standing at the floor, the outline sits on the
     * block.
     */
    private static Vec3 anchor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** Removes this beacon, and any stray one on the block that is not it. */
    public static void remove(ServerLevel level, BlockPos pos, @Nullable UUID current) {
        if (current != null && level.getEntity(current) instanceof Display.ItemDisplay display) {
            display.discard();
        }

        clearStrays(level, pos, null);
    }

    /** Removes beacons on this block other than {@code keep} — strays from an earlier load. */
    public static void clearStrays(ServerLevel level, BlockPos pos, @Nullable UUID keep) {
        for (var display : level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(0.5))) {
            if (display.getTags().contains(TAG) && !display.getUUID().equals(keep)) {
                display.discard();
            }
        }
    }
}
