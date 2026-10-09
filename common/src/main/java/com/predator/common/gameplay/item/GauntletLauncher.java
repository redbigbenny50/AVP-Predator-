package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.projectile.FirePelletProjectile;
import com.predator.common.gameplay.entity.projectile.NetProjectile;
import com.predator.common.gameplay.entity.projectile.VeritaniumDartProjectile;
import com.predator.common.gameplay.item.gauntlet.GauntletAmmo;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.gameplay.whip.WhipGrapple;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Firing whatever is loaded in a player's gauntlet.
 * <h2>His spec</h2> "the predator mob can just use the aim and fire animation for the wrist to launch nets and darts
 * but the player can also use a gauntlet."
 * <h2>⚠ It fires the FIRST loaded slot, left to right</h2> Not a selected slot, and not a random one. The five slots
 * read as a magazine strip in the GUI, so emptying them in order is what the artwork already implies — and it means a
 * player loads darts behind a net and knows exactly what comes out next.
 */
public final class GauntletLauncher {

    /** Ticks between shots. Matches the yautja's own wrist cooldown so neither side out-guns the other. */
    public static final int COOLDOWN_TICKS = 20;

    /** ⚠ "Like bullets" — the pellet fires at its own, faster cadence. Ticks. */
    public static int PELLET_COOLDOWN_TICKS = 6;

    /** A fully charged arrow is 3.0. */
    public static float PELLET_SPEED = 3.0F;

    private static final float DART_SPEED = 2.6F;

    private static final float NET_SPEED = 1.4F;

    /**
     * Where a round leaves the gauntlet, relative to the eye, in blocks. Vanilla's projectile constructor spawns at the
     * eye, so a dart fired from the offhand crossed the camera's near plane for a frame — the "flash out of my face". ⚠
     * Non-final so they can be tuned in a hot-swap. SIDE is toward the gauntlet arm (negative = the player's left for a
     * right-hander; the sign is applied per arm below), DOWN is below eye level, FORWARD is ahead of the eye.
     */
    private static float MUZZLE_SIDE = 0.4F;

    private static float MUZZLE_DOWN = 0.6F;

    private static float MUZZLE_FORWARD = 0.5F;

    private GauntletLauncher() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return whether anything was fired}
     * <p>
     * ⚠⚠ FIRES THE SELECTED TYPE, NOT THE FIRST LOADED SLOT. The first version emptied slots left to right, which was
     * my guess before he specified cycling — with shift+right-click choosing a type, firing something else would make
     * the selection meaningless.
     */
    public static boolean fire(Level level, Player player, ItemStack gauntlet) {
        if (level.isClientSide) {
            return false;
        }

        var ammo = GauntletItem.selected(gauntlet);
        var contents = new GauntletContents(gauntlet);

        // ⚠ Anything with no item (a future device entry) fails here rather than firing nothing silently.
        if (ammo.itemPath() == null) {
            deny(level, player, ammo);

            return false;
        }

        var slot = contents.firstSlotOf(ammo);

        if (slot < 0) {
            deny(level, player, ammo);

            return false;
        }

        if (!launch(level, player, ammo)) {
            return false;
        }

        // ⚠⚠ NON-CONSUMABLE TYPES ARE NOT SPENT. The chain whip is a device loaded into the gauntlet, not a round:
        // firing it must leave the magazine exactly as it was. [stated] "its a gauntlet item not consumed."
        if (!ammo.consumable()) {
            player.getCooldowns().addCooldown(gauntlet.getItem(), COOLDOWN_TICKS);

            return true;
        }

        var round = contents.getItem(slot);

        round.shrink(1);
        contents.setItem(slot, round.isEmpty() ? ItemStack.EMPTY : round);

        player.getCooldowns().addCooldown(gauntlet.getItem(), ammo == GauntletAmmo.FIRE_PELLET ? PELLET_COOLDOWN_TICKS : COOLDOWN_TICKS);

        return true;
    }

    /**
     * Moves a freshly built projectile from the eye to the gauntlet's wrist. {@code shootFromRotation} only sets the
     * velocity, so this can run after it. The offset is built from the player's own look basis so it stays on the
     * gauntlet whichever way they turn or tilt: {@code right = look x up}, and the gauntlet arm is the OPPOSITE of the
     * main arm, so a right-hander's gauntlet is on the LEFT.
     */
    private static void placeAtMuzzle(Player player, Projectile projectile) {
        var look = player.getViewVector(1.0F);
        var up = player.getUpVector(1.0F);
        var right = look.cross(up).normalize();
        var toGauntlet = GauntletItem.arm(player) == HumanoidArm.LEFT ? -1.0 : 1.0;
        var muzzle = player.getEyePosition()
            .add(look.scale(MUZZLE_FORWARD))
            .add(right.scale(MUZZLE_SIDE * toGauntlet))
            .subtract(new Vec3(0.0, MUZZLE_DOWN, 0.0));

        projectile.setPos(muzzle.x, muzzle.y, muzzle.z);
    }

    /**
     * ⚠ Says WHY, rather than clicking. An empty gauntlet that only makes a noise is indistinguishable from a broken
     * one, and the player has no way to see the magazine without opening the GUI.
     */
    private static void deny(Level level, Player player, com.predator.common.gameplay.item.gauntlet.GauntletAmmo ammo) {
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable(
                ammo.missingKey(),
                net.minecraft.network.chat.Component.translatable(ammo.nameKey())
            ),
            true
        );

        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            PredatorSoundEvents.GAUNTLET_EMPTY.get(),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
    }

    private static boolean launch(Level level, Player player, com.predator.common.gameplay.item.gauntlet.GauntletAmmo ammo) {
        return switch (ammo) {
            case DART -> {
                var dart = new VeritaniumDartProjectile(level, player);

                dart.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, DART_SPEED, 1.0F);
                placeAtMuzzle(player, dart);
                level.addFreshEntity(dart);
                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    PredatorSoundEvents.GAUNTLET_DART.get(),
                    SoundSource.PLAYERS,
                    0.7F,
                    1.6F
                );

                yield true;
            }
            case FIRE_PELLET -> {
                var pellet = new FirePelletProjectile(level, player);

                pellet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, PELLET_SPEED, 0.0F);
                placeAtMuzzle(player, pellet);
                level.addFreshEntity(pellet);
                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    PredatorSoundEvents.GAUNTLET_PELLET.get(),
                    SoundSource.PLAYERS,
                    0.6F,
                    1.4F
                );

                yield true;
            }
            case CHAIN_WHIP -> {
                // ⚠ A TOGGLE, not a shot. Firing while already hooked lets go — the second-click release the whip
                // used to own, now that the grapple lives on the gauntlet.
                if (WhipGrapple.isGrappling(player)) {
                    WhipGrapple.release(player);

                    yield true;
                }

                yield WhipGrapple.fire(level, player);
            }
            case PLASMA_SHURIKEN -> {
                var shuriken = new com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile(level, player);

                shuriken.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile.LAUNCH_SPEED,
                    0.0F
                );
                placeAtMuzzle(player, shuriken);
                // ⚠ After aiming: it locks the ONE target nearest where the player is looking.
                shuriken.lockTarget();
                level.addFreshEntity(shuriken);
                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    PredatorSoundEvents.SMART_DISC_THROW.get(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.3F
                );

                yield true;
            }
            case NET -> {
                var net = new NetProjectile(level, player);

                net.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, NET_SPEED, 1.0F);
                placeAtMuzzle(player, net);
                level.addFreshEntity(net);
                level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    PredatorSoundEvents.GAUNTLET_NET.get(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.0F
                );

                yield true;
            }
        };
    }

    /** {@return whether this player is holding a gauntlet, in either hand} */
    public static ItemStack heldGauntlet(Player player) {
        if (player.getMainHandItem().is(PredatorItems.GAUNTLET.get())) {
            return player.getMainHandItem();
        }

        if (player.getOffhandItem().is(PredatorItems.GAUNTLET.get())) {
            return player.getOffhandItem();
        }

        return ItemStack.EMPTY;
    }
}
