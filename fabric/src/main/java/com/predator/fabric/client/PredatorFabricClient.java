package com.predator.fabric.client;

import com.predator.client.PredatorClient;
import com.predator.client.gui.MudOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class PredatorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PredatorClient.initialize();
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> MudOverlayRenderer.render(guiGraphics));
    }
}
