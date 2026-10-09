package com.predator.fabric.client;

import com.predator.client.PredatorClient;
import com.predator.client.gui.MudOverlayRenderer;
import com.predator.client.screen.GauntletScreen;
import com.predator.common.registry.init.PredatorMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class PredatorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PredatorClient.initialize();
        // ⚠⚠ SCREEN BINDING LIVES HERE for the same reason — registerMenuScreen names
        // MenuScreens.ScreenConstructor, package-private in the vanilla :common compiles against.
        PredatorClient.MOD.registries().registerMenuScreen(PredatorMenuTypes::gauntlet, GauntletScreen::new);
        PredatorClient.MOD.registries()
            .registerMenuScreen(
                PredatorMenuTypes::yautjaInventory,
                com.predator.client.screen.debug.YautjaInventoryScreen::new
            );
        PredatorClient.MOD.registries()
            .registerMenuScreen(PredatorMenuTypes::corpseScroll, com.predator.client.screen.CorpseScrollScreen::new);

        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> MudOverlayRenderer.render(guiGraphics));
    }
}
