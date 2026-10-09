package com.predator.common.network;

import com.blib.api.common.network.v1.PacketDirection;
import com.blib.api.common.registry.v1.impl.BLibNetworkRegistry;
import com.predator.Predator;
import com.predator.common.network.packet.C2SArmGauntletPayload;
import com.predator.common.network.packet.C2SCyclePredatorVisionPayload;
import com.predator.common.network.packet.C2SDisarmGauntletPayload;
import com.predator.common.network.packet.C2SNetStruggleMashPayload;
import com.predator.common.network.packet.C2SOpenGauntletPayload;
import com.predator.common.network.packet.C2SToggleCloakPayload;
import com.predator.common.network.packet.C2SToggleGauntletCloakPayload;
import com.predator.common.network.packet.S2CCasterChargePayload;
import com.predator.common.network.packet.S2CCloakStatePayload;
import com.predator.common.network.packet.S2CMudStatePayload;
import com.predator.common.network.packet.S2CNetStatePayload;
import com.predator.common.network.packet.S2COpenGauntletDisarmPayload;
import com.predator.common.network.packet.S2CPlasmaFlashPayload;

public class PredatorPacketDirectionRegistry {

    private static final BLibNetworkRegistry REGISTRY = Predator.MOD.registries().createNetworkRegistry();

    public static void initialize() {
        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SCyclePredatorVisionPayload.TYPE, C2SCyclePredatorVisionPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SToggleCloakPayload.TYPE, C2SToggleCloakPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2CCloakStatePayload.TYPE, S2CCloakStatePayload.CODEC)
        );

        // ⚠ ONE DIRECTION PER CALL. registerPacketDirection takes a single PacketDirection - passing two is a compile
        // error, and a MISSING registration is worse: an unregistered payload crashes Fabric at the main entrypoint,
        // which is exactly how the C2S hand-caster packet broke before.
        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2CCasterChargePayload.TYPE, S2CCasterChargePayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2CMudStatePayload.TYPE, S2CMudStatePayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2CNetStatePayload.TYPE, S2CNetStatePayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SNetStruggleMashPayload.TYPE, C2SNetStruggleMashPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SOpenGauntletPayload.TYPE, C2SOpenGauntletPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SArmGauntletPayload.TYPE, C2SArmGauntletPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SDisarmGauntletPayload.TYPE, C2SDisarmGauntletPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2COpenGauntletDisarmPayload.TYPE, S2COpenGauntletDisarmPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(S2CPlasmaFlashPayload.TYPE, S2CPlasmaFlashPayload.CODEC)
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(
                com.predator.common.network.packet.S2CDestructLaughPayload.TYPE,
                com.predator.common.network.packet.S2CDestructLaughPayload.CODEC
            )
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.S2C<>(
                com.predator.common.network.packet.S2CHunterGlimpsePayload.TYPE,
                com.predator.common.network.packet.S2CHunterGlimpsePayload.CODEC
            )
        );

        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(C2SToggleGauntletCloakPayload.TYPE, C2SToggleGauntletCloakPayload.CODEC)
        );
        // ⚠⚠ EVERY PAYLOAD NEEDS A DIRECTION HERE BEFORE ITS HANDLER IS REGISTERED. The hand caster's handler was
        // added to PredatorServerPacketHandlerRegistry without this line, and on Fabric registering a receiver for an
        // unregistered payload type is a hard error at the 'main' entrypoint - it took the whole server down, and
        // every avp_human datagen run with it (Sep 23). NeoForge is more forgiving, which is why it was not seen there.
        REGISTRY.registerPacketDirection(
            new PacketDirection.C2S<>(
                com.predator.common.network.packet.C2SHandCasterPayload.TYPE,
                com.predator.common.network.packet.C2SHandCasterPayload.CODEC
            )
        );
    }
}
