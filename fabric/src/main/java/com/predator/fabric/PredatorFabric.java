package com.predator.fabric;

import com.predator.Predator;
import com.predator.common.gameplay.item.MudBucketInteractions;
import com.predator.common.gameplay.menu.GauntletMenu;
import com.predator.common.registry.init.PredatorMenuTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public class PredatorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
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
        UseBlockCallback.EVENT.register(MudBucketInteractions::fillMudBucket);
    }
}
