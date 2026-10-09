package com.predator.neoforge;

import com.predator.Predator;
import com.predator.common.gameplay.item.MudBucketInteractions;
import com.predator.common.gameplay.menu.GauntletMenu;
import com.predator.common.registry.init.PredatorMenuTypes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(Predator.MOD_ID)
public class PredatorNeoForge {

    public PredatorNeoForge() {
        // ⚠⚠ THE MenuType IS BUILT HERE, NOT IN :common. Its constructor is private in vanilla and its
        // MenuSupplier is package-private, and the common module compiles against UNPATCHED vanilla — an access
        // widener or transformer declared by this mod does not reach it. Proven the hard way: both files were
        // added and :common:compileJava failed identically. This module has the access, so this is where it goes.
        // ⚠ BEFORE Predator.initialize(), which is what flushes the registry.
        PredatorMenuTypes.registerGauntlet(() -> new MenuType<>(GauntletMenu::new, FeatureFlagSet.of()));

        // The creative-only yautja debug inventory — same reason it lives here: MenuType's constructor is not
        // reachable from :common. ⚠ Also BEFORE Predator.initialize().
        PredatorMenuTypes.registerYautjaInventory(
            () -> new MenuType<>(com.predator.common.gameplay.debug.yautja.YautjaInventoryMenu::new, FeatureFlagSet.of())
        );

        // A skinned corpse bigger than a six-row chest — same reason as the two above. ⚠ Also BEFORE
        // Predator.initialize().
        PredatorMenuTypes.registerCorpseScroll(
            () -> new MenuType<>(com.predator.common.gameplay.menu.CorpseScrollMenu::new, FeatureFlagSet.of())
        );

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
