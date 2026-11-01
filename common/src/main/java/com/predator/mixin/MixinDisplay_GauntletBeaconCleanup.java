package com.predator.mixin;

import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import com.predator.common.gameplay.gauntlet.destruct.GauntletBeacon;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Field report Oct 30: "i got a wrist gauntlet that went boom and isnt going away ... its not doing anything anymore
 * its just sort of stuck there" - a glowing red gauntlet left floating where a self-destruct went off.
 * <p>
 * That is the countdown BEACON (a vanilla item display, {@link GauntletBeacon}). The block entity removes it when the
 * gauntlet stops counting or the block goes - but only if the display is loaded at that moment. When the gauntlet goes
 * off through the BACKSTOP (nobody near, so the block was not ticking), {@code GauntletDestructRegistry} force-loads
 * the chunk and detonates; a freshly loaded chunk's entities arrive a little after its blocks, so the block entity's
 * clean-up (on the blast, or on its first tick when a player comes back) finds no display to remove, and the display
 * then loads on its own with nothing left to remove it. It is a plain vanilla display, so nothing else ever would.
 * </p>
 * <p>
 * Fix: the beacon polices itself. Every half second a display carrying the beacon tag checks that a COUNTING gauntlet
 * still sits on its block (or the block below - the earliest markers stood a block high) and removes itself if not.
 * This also clears beacons already stranded in worlds: they go the first time their chunk loads with this fix in. Only
 * displays with the tag are touched, so a player's own /summon displays are never affected.
 * </p>
 */
@Mixin(Display.class)
public abstract class MixinDisplay_GauntletBeaconCleanup {

    private static final int CHECK_INTERVAL = 10;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void avp_predator$dropStrayGauntletBeacon(CallbackInfo callback) {
        var self = (Display) (Object) this;

        if (!(self instanceof Display.ItemDisplay) || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (self.tickCount % CHECK_INTERVAL != 0 || !self.getTags().contains(GauntletBeacon.TAG)) {
            return;
        }

        var pos = self.blockPosition();
        if (isCountingGauntlet(level, pos) || isCountingGauntlet(level, pos.below())) {
            return;
        }

        self.discard();
        callback.cancel();
    }

    private static boolean isCountingGauntlet(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GauntletBlockEntity gauntlet
            && !gauntlet.getGauntlet().isEmpty()
            && GauntletSelfDestruct.isCounting(gauntlet.getGauntlet());
    }
}
