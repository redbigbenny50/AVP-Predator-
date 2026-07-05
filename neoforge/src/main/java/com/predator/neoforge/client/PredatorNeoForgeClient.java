package com.predator.neoforge.client;

import com.predator.Predator;
import com.predator.client.PredatorClient;
import com.predator.client.gui.MudOverlayRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Predator.MOD_ID, dist = Dist.CLIENT)
public class PredatorNeoForgeClient {

    public PredatorNeoForgeClient() {
        PredatorClient.initialize();
        NeoForge.EVENT_BUS.addListener(PredatorNeoForgeClient::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        MudOverlayRenderer.render(event.getGuiGraphics());
    }
}
