package com.predator.common.gameplay.item;

import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class MudBucketItem extends SolidBucketItem {

    public static final int MUD_EFFECT_DURATION_TICKS = 2400;

    public MudBucketItem() {
        super(Blocks.MUD, SoundEvents.MUD_PLACE, new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        var itemStack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(PredatorMobEffects.getMudHolder(), MUD_EFFECT_DURATION_TICKS));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        player.playSound(SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
        return InteractionResultHolder.sidedSuccess(BucketItem.getEmptySuccessItem(itemStack, player), level.isClientSide());
    }
}
