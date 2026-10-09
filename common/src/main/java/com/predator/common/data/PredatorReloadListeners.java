package com.predator.common.data;

import com.blib.api.common.registry.v1.impl.BLibReloadListenerRegistry;
import com.predator.Predator;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * avp_predator's data-pack listeners. [stated] Oct 2: the first is the plasma detonation's block conversions, the same
 * data-pack method avp_human's nuke uses.
 */
public class PredatorReloadListeners {

    private static final BLibReloadListenerRegistry REGISTRY = Predator.MOD.registries().createReloadListenerRegistry();

    public static final PreparableReloadListener PLASMA_CONVERSIONS_RELOAD_LISTENER = new PlasmaConversionsReloadListener();

    public static void initialize() {
        REGISTRY.register(PlasmaConversionsReloadListener.DIRECTORY_NAME, PLASMA_CONVERSIONS_RELOAD_LISTENER, PackType.SERVER_DATA);
    }
}
