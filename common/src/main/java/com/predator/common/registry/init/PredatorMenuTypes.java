package com.predator.common.registry.init;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.debug.yautja.YautjaInventoryMenu;
import com.predator.common.gameplay.menu.GauntletMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Container menus.
 * <h2>⚠⚠ THIS CLASS DOES NOT CREATE THE MenuType, AND IT CANNOT</h2> {@code MenuType}'s constructor is private in
 * vanilla and its {@code MenuSupplier} is package-private. NeoForge widens the constructor; Fabric does not. Crucially
 * the {@code :common} module compiles against UNPATCHED vanilla, and an access transformer or widener declared here
 * does NOT reach it — that was proven the hard way, by adding both files and watching {@code :common:compileJava} fail
 * identically.
 * <p>
 * So the construction happens in each loader module, where the access actually exists, and is handed in through
 * {@link #registerGauntlet}. Common still owns the registry, the holder and the name, so nothing about the registration
 * is duplicated — only the one {@code new MenuType<>(...)} that common is forbidden from writing.
 * <h2>⚠ Registration order</h2> A loader must call {@link #registerGauntlet} BEFORE {@link #initialize}. Both
 * entrypoints do this in order: {@code registerGauntlet} then {@code Predator.initialize()}.
 */
public class PredatorMenuTypes {

    public static final BLibRegistry<MenuType<?>> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.MENU);

    /**
     * ⚠ Not final, and null until a loader supplies the factory. {@link GauntletMenu} reads it in its constructor,
     * which cannot run before registration completes — a menu only exists once its type is registered.
     */
    private static @Nullable BLibHolder<MenuType<GauntletMenu>> gauntlet;

    /** The creative-only yautja debug inventory. Registered by the loader, same as the gauntlet's. */
    private static @Nullable BLibHolder<MenuType<YautjaInventoryMenu>> yautjaInventory;

    private PredatorMenuTypes() {
        throw new UnsupportedOperationException();
    }

    /**
     * Called from each loader's entrypoint with a factory that builds the type.
     * <p>
     * ⚠ The factory is a plain {@code Supplier<MenuType<GauntletMenu>>} — no vanilla private type appears in this
     * signature, which is the whole reason this compiles in common.
     */
    public static void registerGauntlet(Supplier<MenuType<GauntletMenu>> factory) {
        gauntlet = REGISTRY.createHolder("gauntlet", factory);
    }

    /**
     * A skinned corpse with more than six rows (see CorpseScrollMenu). Registered by the loader, same as the others.
     */
    private static @Nullable BLibHolder<MenuType<com.predator.common.gameplay.menu.CorpseScrollMenu>> corpseScroll;

    public static void registerCorpseScroll(Supplier<MenuType<com.predator.common.gameplay.menu.CorpseScrollMenu>> factory) {
        corpseScroll = REGISTRY.createHolder("corpse_scroll", factory);
    }

    /** {@return the scrolling corpse menu type} */
    public static MenuType<com.predator.common.gameplay.menu.CorpseScrollMenu> corpseScroll() {
        if (corpseScroll == null) {
            throw new IllegalStateException(
                "Corpse scroll menu type was never registered — the loader entrypoint must call "
                    + "PredatorMenuTypes.registerCorpseScroll before Predator.initialize()"
            );
        }

        return corpseScroll.get();
    }

    public static void registerYautjaInventory(Supplier<MenuType<YautjaInventoryMenu>> factory) {
        yautjaInventory = REGISTRY.createHolder("yautja_inventory", factory);
    }

    /** {@return the debug inventory menu type} */
    public static MenuType<YautjaInventoryMenu> yautjaInventory() {
        if (yautjaInventory == null) {
            throw new IllegalStateException(
                "Yautja inventory menu type was never registered — the loader entrypoint must call "
                    + "PredatorMenuTypes.registerYautjaInventory before Predator.initialize()"
            );
        }

        return yautjaInventory.get();
    }

    /**
     * {@return the gauntlet menu type}
     *
     * @throws IllegalStateException if a loader never called {@link #registerGauntlet} — far better than a
     *                               NullPointerException from inside a menu constructor, which would point at the wrong
     *                               file entirely.
     */
    public static MenuType<GauntletMenu> gauntlet() {
        if (gauntlet == null) {
            throw new IllegalStateException(
                "Gauntlet menu type was never registered — the loader entrypoint must call "
                    + "PredatorMenuTypes.registerGauntlet before Predator.initialize()"
            );
        }

        return gauntlet.get();
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
