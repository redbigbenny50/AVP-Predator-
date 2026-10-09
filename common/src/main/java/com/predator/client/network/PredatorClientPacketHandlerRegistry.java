package com.predator.client.network;

import com.blib.api.common.network.v1.NetworkHandler;
import com.blib.api.common.registry.v1.impl.BLibNetworkRegistry;
import com.predator.Predator;
import com.predator.client.cloak.PredatorCloakClientState;
import com.predator.client.effect.PlasmaClientEffects;
import com.predator.client.effect.PredatorMudClientState;
import com.predator.client.net.ClientNetState;
import com.predator.client.screen.GauntletDisarmScreen;
import com.predator.common.network.packet.S2CCasterChargePayload;
import com.predator.common.network.packet.S2CCloakStatePayload;
import com.predator.common.network.packet.S2CMudStatePayload;
import com.predator.common.network.packet.S2CNetStatePayload;
import com.predator.common.network.packet.S2COpenGauntletDisarmPayload;
import com.predator.common.network.packet.S2CPlasmaFlashPayload;

public class PredatorClientPacketHandlerRegistry {

    private static final BLibNetworkRegistry REGISTRY = Predator.MOD.registries().createNetworkRegistry();

    public static void initialize() {
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                com.predator.common.network.packet.S2CDestructLaughPayload.TYPE,
                com.predator.common.network.packet.S2CDestructLaughPayload.CODEC,
                (payload, player) -> com.predator.client.sound.DestructLaughSounds.onReport(payload)
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                com.predator.common.network.packet.S2CHunterGlimpsePayload.TYPE,
                com.predator.common.network.packet.S2CHunterGlimpsePayload.CODEC,
                (payload, player) -> com.predator.client.hunt.HunterGlimpseClient.onGlimpse(payload)
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CPlasmaFlashPayload.TYPE,
                S2CPlasmaFlashPayload.CODEC,
                (payload, player) -> PlasmaClientEffects.trigger(payload)
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2COpenGauntletDisarmPayload.TYPE,
                S2COpenGauntletDisarmPayload.CODEC,
                (payload, player) -> net.minecraft.client.Minecraft.getInstance()
                    .setScreen(
                        new GauntletDisarmScreen(net.minecraft.core.BlockPos.of(payload.blockPos()), payload.code(), payload.deadline())
                    )
            )
        );
        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CNetStatePayload.TYPE,
                S2CNetStatePayload.CODEC,
                (payload, player) -> ClientNetState.set(payload.entityId(), payload.netted())
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CCasterChargePayload.TYPE,
                S2CCasterChargePayload.CODEC,
                (payload, player) -> {
                    com.predator.client.handcaster.CasterChargeClientState.set(payload.entityId(), payload.charging());
                    com.predator.client.sound.CasterChargeLoopSounds.onCharge(payload.entityId(), payload.charging());
                }
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CCloakStatePayload.TYPE,
                S2CCloakStatePayload.CODEC,
                (payload, player) -> PredatorCloakClientState.set(payload.entityId(), payload.cloaked())
            )
        );

        REGISTRY.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CMudStatePayload.TYPE,
                S2CMudStatePayload.CODEC,
                (payload, player) -> PredatorMudClientState.set(
                    payload.entityId(),
                    payload.remainingTicks(),
                    player.level().getGameTime()
                )
            )
        );
    }
}
