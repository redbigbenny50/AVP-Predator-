package com.predator.client.vision;

import com.predator.client.compatibility.PredatorClientCompatibility;
import com.predator.common.gameplay.component.PredatorVisionMode;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.item.PredatorArmorItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Client-side helper that resolves the local player's currently-active vision mode by reading the predator helmet stack
 * the player is wearing. Returns {@link PredatorVisionMode#REGULAR} whenever the player is not wearing a predator
 * helmet, so taking the helmet off automatically disables any vision-driven post-effect.
 */
public final class PredatorVisionAccessor {

    private PredatorVisionAccessor() {
        throw new UnsupportedOperationException();
    }

    public static PredatorVisionMode currentVisionMode() {
        if (PredatorClientCompatibility.areVisionsDisabledBySodium()) {
            return PredatorVisionMode.REGULAR;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return PredatorVisionMode.REGULAR;
        }

        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);

        if (!helmet.is(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get())) {
            return PredatorVisionMode.REGULAR;
        }

        return helmet.getOrDefault(PredatorDataComponents.VISION_MODE.get(), PredatorVisionMode.REGULAR);
    }

    /**
     * @return true if any non-regular vision mode is the active mode on the equipped predator helmet — i.e., the
     *         framework's MRT-aware mixins should run. Returns false off-helmet (and during REGULAR).
     */
    public static boolean isAnyVisionActive() {
        return currentVisionMode() != PredatorVisionMode.REGULAR;
    }
}
