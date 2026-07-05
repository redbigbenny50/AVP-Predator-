package com.predator.common.gameplay.item;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public class MudBucketInteractions {

    public static InteractionResult fillMudBucket(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        var itemStack = player.getItemInHand(hand);
        var blockPos = hitResult.getBlockPos();

        if (!itemStack.is(Items.BUCKET) || !level.getBlockState(blockPos).is(Blocks.MUD)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            var mudBucket = new ItemStack(PredatorItems.MUD_BUCKET.get());
            level.destroyBlock(blockPos, false, player);
            player.setItemInHand(hand, ItemUtils.createFilledResult(itemStack, player, mudBucket));
            player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, mudBucket);
            }
            level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
        }

        player.playSound(SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private MudBucketInteractions() {}
}
