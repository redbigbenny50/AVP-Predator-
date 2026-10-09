package com.predator.neoforge.client;

import com.predator.Predator;
import com.predator.client.PredatorClient;
import com.predator.client.gui.MudOverlayRenderer;
import com.predator.client.screen.GauntletScreen;
import com.predator.common.registry.init.PredatorMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Predator.MOD_ID, dist = Dist.CLIENT)
public class PredatorNeoForgeClient {

    public PredatorNeoForgeClient() {
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

        NeoForge.EVENT_BUS.addListener(PredatorNeoForgeClient::onRenderGui);

        // ⚠⚠ THE WHIP'S "extended" PREDICATE LIVES HERE, NOT IN :common. ItemProperties.register is PRIVATE in
        // vanilla — BOTH overloads — and is only callable because NeoForge's access transformer opens it, the same
        // reason registerMenuScreen is pinned here. This is what swaps in whip_extended.png while the cord is out.
        //
        // 🚨🚨 HANDED OVER, NOT REGISTERED HERE. Two separate rules bite in sequence:
        // 1. Calling ItemProperties.register at construction resolves PredatorItems.WHIP.get() during
        // FMLConstructModEvent, when registerAll() has only QUEUED registration -> "Trying to access unbound
        // value: ResourceKey[minecraft:item / avp_predator:whip]".
        // 2. Registering a BLib event here to defer it fails too -> "Attempted to register an event outside of
        // mod's initialization window", because this constructor is already past that window.
        // So the task goes into PredatorClient.LOADER_CLIENT_SETUP, and initialize() — which DOES run inside the
        // window — registers the single callback that drains it. Both rules satisfied, and the loader-only call
        // stays on the loader side.
        //
        // ⚠ Fabric has no equivalent call; on that loader the whip simply keeps its coiled sprite.
        // ⚠⚠ THE BOWS' DRAW STAGES. Vanilla registers "pulling"/"pull" for ITS bow only; a custom bow shows a single
        // frame without these. Same private-in-vanilla ItemProperties call as the whip's sprite, so it rides the same
        // hand-off. ⚠ FABRIC GETS NEITHER — both bows will show only their idle sprite there until someone wires
        // Fabric's own model-predicate registry.
        PredatorClient.LOADER_CLIENT_SETUP.add(
            () -> {
                for (
                    var bow : new net.minecraft.world.item.Item[] {
                        com.predator.common.registry.init.item.PredatorItems.VERITANIUM_BOW.get(),
                        com.predator.common.registry.init.item.PredatorItems.PLASMA_BOW.get()
                    }
                ) {
                    net.minecraft.client.renderer.item.ItemProperties.register(
                        bow,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                        (stack, level, holder, seed) -> {
                            if (holder == null || holder.getUseItem() != stack) {
                                return 0.0F;
                            }

                            return (stack.getUseDuration(holder) - holder.getUseItemRemainingTicks()) / 20.0F;
                        }
                    );

                    net.minecraft.client.renderer.item.ItemProperties.register(
                        bow,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, holder, seed) -> holder != null && holder.isUsingItem() && holder.getUseItem() == stack ? 1.0F : 0.0F
                    );
                }
            }
        );

        PredatorClient.LOADER_CLIENT_SETUP.add(
            () -> net.minecraft.client.renderer.item.ItemProperties.register(
                com.predator.common.registry.init.item.PredatorItems.WHIP.get(),
                com.predator.PredatorResources.location("extended"),
                (stack, level, holder, seed) -> holder != null && com.predator.common.gameplay.whip.WhipItem.isExtended(stack, holder)
                    ? 1.0F
                    : 0.0F
            )
        );
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        MudOverlayRenderer.render(event.getGuiGraphics());
    }
}
