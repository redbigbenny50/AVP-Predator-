package com.predator.client;

import com.alien.client.render.entity.head.EntityHeadDataCache;
import com.alien.client.render.entity.parasite.attachment.ParasiteHeadAttachmentOffsetDataCache;
import com.blib.api.client.animation.v1.identity.AzIdentityRegistry;
import com.blib.api.client.mod.v1.BLibClientMod;
import com.predator.Predator;
import com.predator.client.cloak.PredatorCloakClientState;
import com.predator.client.effect.PredatorMudClientState;
import com.predator.client.input.keybind.PredatorKeybindingRegistry;
import com.predator.client.network.PredatorClientPacketHandlerRegistry;
import com.predator.client.render.armor.JunglePredatorArmorRenderer;
import com.predator.client.render.block.GauntletBlockRenderer;
import com.predator.client.render.block.TripMineRenderer;
import com.predator.client.render.entity.CombiStickRenderer;
import com.predator.client.render.entity.FirePelletRenderer;
import com.predator.client.render.entity.PlasmaBoltArrowRenderer;
import com.predator.client.render.entity.PlasmaBoltRenderer;
import com.predator.client.render.entity.PlasmaCloudRenderer;
import com.predator.client.render.entity.VeritaniumDartRenderer;
import com.predator.client.render.entity.YautjaRenderer;
import com.predator.client.render.item.BattleaxeItemRenderer;
import com.predator.client.render.item.CombiStickItemRenderer;
import com.predator.client.render.item.GauntletItemRenderer;
import com.predator.client.render.item.SpinningItemRenderer;
import com.predator.client.render.item.TripMineItemRenderer;
import com.predator.client.render.whip.WhipHookRenderer;
import com.predator.client.render.whip.WhipLashRenderer;
import com.predator.client.vision.PredatorVisionPostEffects;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorBlockItems;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.compatibility.avp_alien.AVPAlien;
import com.predator.compatibility.avp_alien.PredatorEntityHeadData;
import com.predator.compatibility.avp_alien.PredatorParasiteAttachmentOffsetData;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import java.util.List;

public class PredatorClient {

    public static final BLibClientMod MOD = BLibClientMod.createFor(Predator.MOD);

    /** Yautja blood in the bottle, #20FF20 — [stated] "go with bright". Fully opaque (1.21 item tints are ARGB). */
    public static final int YAUTJA_BLOOD_COLOR = 0xFF20FF20;

    /**
     * Client-setup work a LOADER module needs to run.
     * <p>
     * ⚠⚠ TWO CONSTRAINTS MEET HERE. Some client calls — {@code ItemProperties.register} for the whip's "extended"
     * sprite — are private in vanilla and only callable from the NeoForge module, where the access transformer opens
     * them. But BLib refuses event registration outside the mod's initialization window
     * ({@code BLibModInitializationException: Attempted to register an event outside of mod's initialization window}),
     * and a loader module's constructor is already past it by the time {@link #initialize()} returns.
     * <p>
     * So the loader module ADDS a task here and {@link #initialize()} — which runs inside the window — registers the
     * one callback that drains them. Add to this before or after calling initialize(); it is read at setup.
     */
    public static final java.util.List<Runnable> LOADER_CLIENT_SETUP = new java.util.ArrayList<>();

    public static void initialize() {
        MOD.initialize(PredatorClient::runInitialization);
    }

    private static void runInitialization() {
        registerArmorRenderers();
        registerBlockEntityRenderers();
        registerEntityRenderers();

        // [stated] "use the normal potion texture with the hex code of the predator blood color": vanilla's potion
        // model, its liquid layer (layer 0) tinted bright green, #20FF20, chosen against Metamorphosis's #8BF200 so
        // the two read apart. The bottle layer (1) is left as it is.
        MOD.registries()
            .registerItemColor(
                (itemStack, layer) -> layer == 0 ? YAUTJA_BLOOD_COLOR : -1,
                java.util.List.of(com.predator.common.registry.init.item.PredatorItems.YAUTJA_BLOOD_BOTTLE)
            );

        // Yautja blood is a flat cut-out decal; the gaps between the drops must be see-through.
        MOD.registries()
            .registerBlockRenderLayer(
                com.predator.common.registry.init.PredatorBlocks.YAUTJA_BLOOD,
                net.minecraft.client.renderer.RenderType.cutout()
            );
        registerItemRenderers();

        // ⚠ BLib owns the menu-screen binding on both loaders, so this is the one place it can be done without a
        // loader-specific hook.
        // ⚠⚠ MOVED TO THE LOADER CLIENT ENTRYPOINTS. registerMenuScreen names MenuScreens.ScreenConstructor in
        // its signature, and that type is package-private in the unpatched vanilla the :common module compiles
        // against — an access widener declared in this mod does not reach common. Both loader client classes
        // call it instead, where the access exists.
        PredatorVisionPostEffects.register(MOD);
        PredatorKeybindingRegistry.initialize();

        // Installs the client-side cloak lookup into the common facade, so common code can ask "is this cloaked?"
        // without importing a client class — the reference that would silently kill runDatagen.
        PredatorCloakClientState.install();
        PredatorMudClientState.install();
        PredatorClientPacketHandlerRegistry.initialize();

        // ⚠⚠ INSIDE onClientSetup, NOT beside it. runInitialization runs during FMLConstructModEvent, where
        // BLibHolder.get() throws "Trying to access unbound value" — registerAll() QUEUES registration, it does
        // not bind the holders. The setup callback fires once the registries are populated.
        //
        // ⚠⚠ AND THIS CALL IS WHY THE GAUNTLET ARM RENDERS AT ALL. Traced through BLib:
        // ItemStackMixin_AzItemStackIdentityRegistry sets the AZ_ID component ONLY when hasIdentity(item)
        // AzIdentifiableItemStackAnimatorCache stores and looks up an animator BY that AZ_ID
        // Without it no stack gets an AZ_ID, the cache never hits, and AzProvider builds a NEW ANIMATOR EVERY
        // FRAME — measured as a different animator id per frame with playing=false forever. BLib only swaps an
        // arm bone for the player skin while a track IS playing.
        //
        // ⚠ Registration is OPT-IN and nothing warns when it is missing. Any future item with its own animator
        // must be added here.
        // ⚠ Client-only is correct: identity exists purely to key the client-side animator cache.
        // Whatever the loader module handed us (see LOADER_CLIENT_SETUP), run inside the window BLib requires.
        MOD.events().onClientSetup().register(() -> LOADER_CLIENT_SETUP.forEach(Runnable::run));

        MOD.events()
            .onClientSetup()
            .register(
                // ⚠ HAND_CASTER: without an AZ_ID its animator is rebuilt every frame and no clip can establish itself
                // (the combi stick's lesson) — its idle and fire clips would never have played. BATTLEAXE: the
                // main-hand
                // equip fix recognises "the same axe" by its AZ_ID.
                () -> AzIdentityRegistry.register(
                    PredatorItems.GAUNTLET.get(),
                    PredatorItems.COMBI_STICK.get(),
                    PredatorItems.HAND_CASTER.get(),
                    PredatorItems.BATTLEAXE.get()
                )
            );

        if (AVPAlien.MOD.isLoaded()) {
            MOD.events().onClientSetup().register(() -> {
                registerEntityHeadData();
                registerParasiteHeadAttachmentOffsetData();
            });
        }
    }

    private static void registerArmorRenderers() {
        MOD.registries()
            .registerArmorRenderer(
                JunglePredatorArmorRenderer::new,
                List.of(
                    PredatorArmorItems.JUNGLE_PREDATOR_HELMET,
                    PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE,
                    PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS,
                    PredatorArmorItems.JUNGLE_PREDATOR_BOOTS
                )
            );
    }

    private static void registerBlockEntityRenderers() {
        MOD.registries()
            .registerBlockEntityRenderer(
                PredatorBlockEntityTypes.TRIP_MINE,
                (BlockEntityRendererProvider.Context rendererDispatcherIn) -> new TripMineRenderer()
            );
        MOD.registries()
            .registerBlockEntityRenderer(
                PredatorBlockEntityTypes.GAUNTLET,
                (BlockEntityRendererProvider.Context rendererDispatcherIn) -> new GauntletBlockRenderer()
            );
        MOD.registries()
            .registerBlockEntityRenderer(
                PredatorBlockEntityTypes.SKINNED_CORPSE,
                (BlockEntityRendererProvider.Context rendererDispatcherIn) -> new com.predator.client.render.block.SkinnedCorpseRenderer()
            );
    }

    private static void registerEntityHeadData() {
        EntityHeadDataCache.put(PredatorEntityTypes.YAUTJA, PredatorEntityHeadData.YAUTJA);
    }

    /**
     * Size of a thrown or stuck grenade, as a share of an ordinary item. ⚠ Changes the world model only, not the held
     * item.
     */
    private static final float GRENADE_RENDER_SCALE = 0.5F;

    private static void registerEntityRenderers() {
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.PLASMA_BOLT, PlasmaBoltRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.VERITANIUM_DART, VeritaniumDartRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.FIRE_PELLET, FirePelletRenderer::new);
        // [stated] the thrown grenade drew far too big at full item size — "about 60% ... maybe even 50%". Vanilla's
        // thrown-item renderer takes a scale; the one-argument form is 1.0.
        MOD.registries()
            .registerEntityRenderer(
                PredatorEntityTypes.YAUTJA_GRENADE,
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, GRENADE_RENDER_SCALE, false)
            );
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.PLASMA_CLOUD, PlasmaCloudRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.PLASMA_BOLT_ARROW, PlasmaBoltArrowRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.WHIP_LASH, WhipLashRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.WHIP_HOOK, WhipHookRenderer::new);

        // ⚠ The "cord is out" predicate the whip's item model switches on — the same trick Chain of Souls uses to
        // swap to an empty-handle sprite. Registered in client setup so the model has it before any model is baked.

        // ⚠⚠ NO "extended" MODEL PREDICATE HERE, DELIBERATELY. ItemProperties.register is PRIVATE in vanilla — both
        // overloads — and only looks callable through NeoForge's access transformer, so it compiles in a NeoForge
        // harness and fails in :common. Registering it needs loader-specific code (NeoForge can call ItemProperties
        // directly; Fabric needs its own model-predicate registry), which is a separate change. Until then the whip
        // always shows its coiled sprite and textures/item/whip_extended.png is simply unused — WhipItem.isExtended
        // is still there and still correct, so wiring it up later is one call per loader.
        // ⚠ VERTICAL. [stated] Sep 25: the ordinary shuriken flies upright, spinning like a wheel; the PLASMA one
        // stays flat. Everything else on this list keeps the flat frisbee spin.
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.SHURIKEN, SpinningItemRenderer::vertical);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.SMART_DISC, SpinningItemRenderer::new);
        // [stated] "the plasma shuriken is horizonal like the smart disc" — the same flat renderer.
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.PLASMA_SHURIKEN, SpinningItemRenderer::new);
        MOD.registries()
            .registerEntityRenderer(
                PredatorEntityTypes.VERITANIUM_ARROW,
                com.predator.client.render.entity.VeritaniumArrowRenderer::new
            );
        // ⚠ The NET and the thrown COMBI_STICK reuse the spinning item renderer — both are objects tumbling through
        // the air, and neither needs its own model in flight.
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.NET, SpinningItemRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.COMBI_STICK, CombiStickRenderer::new);
        MOD.registries().registerEntityRenderer(PredatorEntityTypes.YAUTJA, YautjaRenderer::new);
    }

    private static void registerParasiteHeadAttachmentOffsetData() {
        ParasiteHeadAttachmentOffsetDataCache.put(PredatorEntityTypes.YAUTJA, PredatorParasiteAttachmentOffsetData.YAUTJA);
    }

    private static void registerItemRenderers() {
        MOD.registries().registerItemRenderer(PredatorBlockItems.TRIP_MINE_BLOCK, name -> TripMineItemRenderer::new);
        MOD.registries().registerItemRenderer(PredatorItems.COMBI_STICK, name -> CombiStickItemRenderer::new);
        MOD.registries().registerItemRenderer(PredatorItems.BATTLEAXE, name -> BattleaxeItemRenderer::new);
        MOD.registries()
            .registerItemRenderer(PredatorItems.HAND_CASTER, name -> com.predator.client.render.item.HandCasterItemRenderer::new);
        MOD.registries().registerItemRenderer(PredatorItems.GAUNTLET, name -> GauntletItemRenderer::new);
    }
}
