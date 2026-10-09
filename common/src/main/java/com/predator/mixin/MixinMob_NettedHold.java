package com.predator.mixin;

import com.predator.common.gameplay.net.NetEscape;
import com.predator.common.gameplay.net.NettedMob;
import com.predator.common.gameplay.net.PredatorNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Everything a netted mob is and does, carried on the mob itself.
 * <h2>⚠⚠ WHY NOT setNoAi</h2> {@code setNoAi(true)} makes {@code isEffectiveAi()} false, and vanilla's
 * {@code LivingEntity.aiStep} then ZEROES deltaMovement every tick for such a mob — so it could not be shoved and no
 * struggle could survive. avp_alien solved the same problem for incapacitated royals by cancelling
 * {@code serverAiStep}, which suspends sensing, targeting, attacks and item use while leaving the body in vanilla's
 * normal movement. This is that pattern.
 * <h2>⚠⚠ WHY THE STATE LIVES HERE</h2> [stated] "make sure it persists as well. we dont want them despawning or
 * unnetting" and "make it efficient because its possible some players might net a large amount of things and just store
 * them." Saved in the mob's own NBT and driven from the mob's own tick: it survives unload and restart, and a hundred
 * netted mobs cost no more than a hundred ordinary ones — there is no global list to scan.
 */
@Mixin(Mob.class)
public abstract class MixinMob_NettedHold implements NettedMob {

    @Unique
    private boolean avp_predator$netted;

    @Unique
    private boolean avp_predator$captured;

    @Unique
    private int avp_predator$failedRolls;

    @Unique
    private long avp_predator$nextRollTime;

    @Override
    public boolean avp_predator$isNetted() {
        return avp_predator$netted;
    }

    @Override
    public void avp_predator$setNetted(boolean netted) {
        this.avp_predator$netted = netted;
    }

    @Override
    public boolean avp_predator$isCaptured() {
        return avp_predator$captured;
    }

    @Override
    public void avp_predator$setCaptured(boolean captured) {
        this.avp_predator$captured = captured;
    }

    @Override
    public int avp_predator$getFailedRolls() {
        return avp_predator$failedRolls;
    }

    @Override
    public void avp_predator$setFailedRolls(int rolls) {
        this.avp_predator$failedRolls = rolls;
    }

    @Override
    public long avp_predator$getNextRollTime() {
        return avp_predator$nextRollTime;
    }

    @Override
    public void avp_predator$setNextRollTime(long time) {
        this.avp_predator$nextRollTime = time;
    }

    /** Sensing, targeting, attacks and item use, all suspended — but the body still moves and can be pushed. */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void avp_predator$nettedMobsDoNotAct(CallbackInfo callback) {
        if (avp_predator$netted) {
            callback.cancel();
        }
    }

    /**
     * The whole per-mob cost of being netted: clamp the walk inputs, and on the roll tick either struggle-and-roll or,
     * once captured, give a much weaker twitch. ⚠ Two field reads for a mob that is not netted.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void avp_predator$tickNet(CallbackInfo callback) {
        if (!avp_predator$netted) {
            return;
        }

        var self = (Mob) (Object) this;

        self.xxa = 0.0F;
        self.zza = 0.0F;
        self.setSpeed(0.0F);

        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var now = serverLevel.getGameTime();

        if (now < avp_predator$nextRollTime) {
            return;
        }

        avp_predator$nextRollTime = now + NetEscape.ROLL_INTERVAL_TICKS;
        PredatorNet.onStruggleTick(self);
    }

    /** ⚠ A netted mob can be led even if vanilla would refuse it — that is how a captured one is hauled home. */
    @Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
    private void avp_predator$nettedCanBeLed(CallbackInfoReturnable<Boolean> callback) {
        if (avp_predator$netted) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void avp_predator$saveNet(CompoundTag tag, CallbackInfo callback) {
        if (avp_predator$netted) {
            tag.putBoolean("AvpNetted", true);
            tag.putBoolean("AvpNetCaptured", avp_predator$captured);
            tag.putInt("AvpNetFailedRolls", avp_predator$failedRolls);
            tag.putLong("AvpNetNextRoll", avp_predator$nextRollTime);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void avp_predator$loadNet(CompoundTag tag, CallbackInfo callback) {
        avp_predator$netted = tag.getBoolean("AvpNetted");
        avp_predator$captured = tag.getBoolean("AvpNetCaptured");
        avp_predator$failedRolls = tag.getInt("AvpNetFailedRolls");
        avp_predator$nextRollTime = tag.getLong("AvpNetNextRoll");
    }
}
