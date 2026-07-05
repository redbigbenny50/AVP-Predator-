package com.predator.fabric;

import com.predator.Predator;
import com.predator.common.gameplay.item.MudBucketInteractions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class PredatorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Predator.initialize();
        UseBlockCallback.EVENT.register(MudBucketInteractions::fillMudBucket);
    }
}
