package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** One packet per nearby player at detonation: where, how big, how hard to flash and shake. */
public record S2CPlasmaFlashPayload(
    long centerPos,
    int radius,
    float flashIntensity,
    float shakeIntensity
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("plasma_flash");

    public static final Type<S2CPlasmaFlashPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CPlasmaFlashPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.LONG,
        S2CPlasmaFlashPayload::centerPos,
        StreamCodecs.VAR_INT,
        S2CPlasmaFlashPayload::radius,
        StreamCodecs.FLOAT,
        S2CPlasmaFlashPayload::flashIntensity,
        StreamCodecs.FLOAT,
        S2CPlasmaFlashPayload::shakeIntensity,
        S2CPlasmaFlashPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
