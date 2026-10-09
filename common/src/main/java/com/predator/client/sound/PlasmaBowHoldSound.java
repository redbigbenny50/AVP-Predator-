package com.predator.client.sound;

import com.predator.common.gameplay.item.bow.PlasmaBowItem;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The plasma charge humming while the bow is held at draw.
 * <h2>⚠⚠ WHY A TICKABLE INSTANCE</h2> A one-shot {@code playSound} cannot be stopped. The hold ends on a fire, on a
 * release, on switching item, on death, on the player being interrupted — and every one of those would leave the hum
 * running forever from wherever the draw happened. This instance asks the player each tick whether they are STILL
 * drawing this bow, and stops itself the moment they are not, so there is no path that leaks it.
 * <p>
 * ⚠ It follows the player too, so the hum stays on them as they move.
 */
public class PlasmaBowHoldSound extends AbstractTickableSoundInstance {

    private final Player player;

    public PlasmaBowHoldSound(Player player) {
        super(PredatorSoundEvents.PLASMA_BOW_HOLD.get(), SoundSource.PLAYERS, player.getRandom());

        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        if (!stillDrawing()) {
            stop();

            return;
        }

        x = player.getX();
        y = player.getY();
        z = player.getZ();
    }

    /** ⚠ Every condition that ends a draw, in one place — that is what makes the loop impossible to leak. */
    private boolean stillDrawing() {
        return player.isAlive()
            && !player.isRemoved()
            && player.isUsingItem()
            && player.getUseItem().getItem() instanceof PlasmaBowItem;
    }
}
