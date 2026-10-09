package com.predator.common.network;

import com.blib.api.common.network.v1.NetworkHandler;
import com.blib.api.common.registry.v1.impl.BLibNetworkRegistry;
import com.predator.Predator;
import com.predator.common.network.packet.C2SArmGauntletPayload;
import com.predator.common.network.packet.C2SCyclePredatorVisionPayload;
import com.predator.common.network.packet.C2SDisarmGauntletPayload;
import com.predator.common.network.packet.C2SNetStruggleMashPayload;
import com.predator.common.network.packet.C2SOpenGauntletPayload;
import com.predator.common.network.packet.C2SToggleCloakPayload;
import com.predator.common.network.packet.C2SToggleGauntletCloakPayload;

public class PredatorServerPacketHandlerRegistry {

    private static final BLibNetworkRegistry REGISTRY = Predator.MOD.registries().createNetworkRegistry();

    public static void initialize() {
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                com.predator.common.network.packet.C2SHandCasterPayload.TYPE,
                com.predator.common.network.packet.C2SHandCasterPayload.CODEC,
                PredatorServerListener::handleHandCasterPayload
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SArmGauntletPayload.TYPE,
                C2SArmGauntletPayload.CODEC,
                PredatorServerListener::handleArmGauntletPayload
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SDisarmGauntletPayload.TYPE,
                C2SDisarmGauntletPayload.CODEC,
                PredatorServerListener::handleDisarmGauntletPayload
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SOpenGauntletPayload.TYPE,
                C2SOpenGauntletPayload.CODEC,
                PredatorServerListener::handleOpenGauntletPayload
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SToggleGauntletCloakPayload.TYPE,
                C2SToggleGauntletCloakPayload.CODEC,
                PredatorServerListener::handleToggleGauntletCloakPayload
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SNetStruggleMashPayload.TYPE,
                C2SNetStruggleMashPayload.CODEC,
                PredatorServerListener::handleNetStruggleMashPayload
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SCyclePredatorVisionPayload.TYPE,
                C2SCyclePredatorVisionPayload.CODEC,
                PredatorServerListener::handleCyclePredatorVisionPayload
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SToggleCloakPayload.TYPE,
                C2SToggleCloakPayload.CODEC,
                PredatorServerListener::handleToggleCloakPayload
            )
        );
    }
}
