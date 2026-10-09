package com.predator.mixin;

import com.predator.common.gameplay.hunt.SkinnedCorpses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets a player's WHOLE death, so a player killed by their own Hunter is left as a skinned corpse holding
 * everything they dropped — [stated] "any kind of inventory on the player has to be stored".
 * <p>
 * ⚠ The whole of {@code die}, not just {@code dropAllDeathLoot}: accessory and backpack mods drop their own slots from
 * their death handlers, some of which run outside the vanilla drop step (NeoForge's death event fires at the head of
 * {@code die}). Head to return catches all of them, on both loaders.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_HunterCorpse {

    @Inject(method = "die", at = @At("HEAD"))
    private void avp_predator$beginHunterCorpse(DamageSource source, CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        SkinnedCorpses.beginPlayerDeath(player.serverLevel(), player, source);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void avp_predator$finishHunterCorpse(DamageSource source, CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        SkinnedCorpses.finishPlayerDeath(player.serverLevel(), player);
    }
}
