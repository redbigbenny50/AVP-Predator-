package com.predator.neoforge;

import com.predator.Predator;
import com.predator.common.gameplay.item.MudBucketInteractions;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(Predator.MOD_ID)
public class PredatorNeoForge {

    public PredatorNeoForge() {
        Predator.initialize();
        NeoForge.EVENT_BUS.addListener(PredatorNeoForge::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var result = MudBucketInteractions.fillMudBucket(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
