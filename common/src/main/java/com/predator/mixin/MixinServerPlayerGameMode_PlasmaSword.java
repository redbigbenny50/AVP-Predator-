package com.predator.mixin;

import com.predator.common.gameplay.item.PlasmaSwordItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [stated] "plasma sword lights blocks by hitting it". ⚠ Here because the server does NOT call Item.canAttackBlock when
 * a player starts hitting a block (checked in the 1.21.1 jar). This is the moment it does see — and the action carries
 * the struck FACE, which is where the fire goes. The guards live in PlasmaSwordItem.igniteBlock.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode_PlasmaSword {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void avp_predator$plasmaSwordIgnites(
        BlockPos pos,
        ServerboundPlayerActionPacket.Action action,
        Direction face,
        int maxBuildHeight,
        int sequence,
        CallbackInfo ci
    ) {
        if (
            action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && player.getMainHandItem()
                .getItem() instanceof PlasmaSwordItem
        ) {
            PlasmaSwordItem.igniteBlock(level, player, pos, face);
        }
    }
}
