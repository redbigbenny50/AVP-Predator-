package com.predator.client.input.keybind;

import com.blib.api.client.input.v1.model.KeyInteractType;
import com.just.core.functional.tuple.Tuple2;
import com.predator.Predator;
import com.predator.client.PredatorClient;
import com.predator.client.compatibility.PredatorClientCompatibility;
import com.predator.client.screen.GauntletScreen;
import com.predator.client.vision.PredatorVisionAccessor;
import com.predator.client.vision.PredatorVisionTransition;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.network.packet.C2SCyclePredatorVisionPayload;
import com.predator.common.network.packet.C2SOpenGauntletPayload;
import com.predator.common.network.packet.C2SToggleGauntletCloakPayload;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorArmorItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class PredatorKeybindingRegistry {

    public static final Supplier<Tuple2<KeyMapping, Consumer<KeyInteractType>>> TOGGLE_VISION = register(
        "toggle_vision",
        "predator",
        GLFW.GLFW_KEY_V,
        keyType -> {
            if (keyType != KeyInteractType.PRESS) {
                return;
            }

            var player = Minecraft.getInstance().player;

            if (player == null) {
                return;
            }

            var helmet = player.getItemBySlot(EquipmentSlot.HEAD);

            if (!helmet.is(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get())) {
                return;
            }

            if (PredatorClientCompatibility.areVisionsDisabledBySodium()) {
                PredatorClientCompatibility.sendSodiumVisionIncompatibleMessage(player);
                return;
            }

            // Cooldown: ignore the keypress if a transition is still in flight. Transition duration doubles as the
            // cooldown — once the wipe finishes, the next press can fire immediately.
            if (PredatorVisionTransition.isActive()) {
                return;
            }

            // Start the local erosion-wipe immediately so the visual is responsive; the server packet runs in
            // parallel and persists the new mode on the helmet stack.
            var current = PredatorVisionAccessor.currentVisionMode();
            var next = current.cycleNext();

            PredatorVisionTransition.begin(current, next);

            // Played client-side next to the player rather than sent through the server: the wipe is already local and
            // responsive, and the sound is helmet feedback for the wearer, not something bystanders should hear.
            player.playSound(PredatorSoundEvents.VISION_SWAP.get(), 1.0F, 1.0F);

            Predator.MOD.networking().sendToServer(C2SCyclePredatorVisionPayload.INSTANCE);

            // The mode still cycles under a shader pack — only the DISPLAY is suppressed — so say which mode was
            // selected. Without this the wearer gets the swap sound and nothing else, which reads as a broken keybind
            // rather than a known limitation.
            if (PredatorClientCompatibility.isVisionDisplaySuppressedByShaderPack()) {
                PredatorClientCompatibility.sendShaderPackVisionSuppressedMessage(player, next);
            }
        }
    );

    /**
     * G — open the gauntlet inventory.
     * <p>
     * ⚠ Only when a gauntlet is actually in the OFFHAND. A key that opens nothing most of the time trains players to
     * stop pressing it, and G is a busy key across modpacks — claiming it unconditionally would be rude.
     */
    public static final Supplier<Tuple2<KeyMapping, Consumer<KeyInteractType>>> OPEN_GAUNTLET = register(
        "open_gauntlet",
        "predator",
        GLFW.GLFW_KEY_G,
        keyType -> {
            if (keyType == KeyInteractType.RELEASE) {
                GauntletScreen.clearSuppressedOpen();

                return;
            }

            // ⚠ The press that just CLOSED the screen also lands here one tick later (see GauntletScreen.keyPressed
            // for why vanilla lets it through). Swallow exactly that one.
            if (GauntletScreen.consumeSuppressedOpen()) {
                return;
            }

            var player = Minecraft.getInstance().player;

            if (player == null || GauntletItem.equipped(player).isEmpty()) {
                return;
            }

            // ⚠ Asks the SERVER to open it. Opening a container client-side gives a screen with no synced menu
            // behind it — the classic symptom being slots that look right and cannot be clicked.
            Predator.MOD.networking().sendToServer(C2SOpenGauntletPayload.INSTANCE);
        }
    );

    /**
     * C — toggle the cloak, when a cloaking device is seated in the gauntlet.
     * <p>
     * ⚠ Also gated on the gauntlet being equipped, for the same reason as G.
     */
    public static final Supplier<Tuple2<KeyMapping, Consumer<KeyInteractType>>> TOGGLE_GAUNTLET_CLOAK = register(
        "toggle_gauntlet_cloak",
        "predator",
        GLFW.GLFW_KEY_C,
        keyType -> {
            if (keyType != KeyInteractType.PRESS) {
                return;
            }

            var player = Minecraft.getInstance().player;

            if (player == null || GauntletItem.equipped(player).isEmpty()) {
                return;
            }

            Predator.MOD.networking().sendToServer(C2SToggleGauntletCloakPayload.INSTANCE);
        }
    );

    private static Supplier<Tuple2<KeyMapping, Consumer<KeyInteractType>>> register(
        String path,
        String category,
        int key,
        Consumer<KeyInteractType> onKeyMappingActivated
    ) {
        return PredatorClient.MOD.registries()
            .registerKeyMapping(
                Predator.MOD.resources().createLocation(path),
                category,
                key,
                onKeyMappingActivated
            );
    }

    public static void initialize() {}
}
