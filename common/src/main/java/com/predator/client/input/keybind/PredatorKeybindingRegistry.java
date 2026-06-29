package com.predator.client.input.keybind;

import com.blib.api.client.input.v1.model.KeyInteractType;
import com.just.core.functional.tuple.Tuple2;
import com.predator.Predator;
import com.predator.client.PredatorClient;
import com.predator.client.compatibility.PredatorClientCompatibility;
import com.predator.client.vision.PredatorVisionAccessor;
import com.predator.client.vision.PredatorVisionTransition;
import com.predator.common.network.packet.C2SCyclePredatorVisionPayload;
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
            PredatorVisionTransition.begin(current, current.cycleNext());

            Predator.MOD.networking().sendToServer(C2SCyclePredatorVisionPayload.INSTANCE);
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
