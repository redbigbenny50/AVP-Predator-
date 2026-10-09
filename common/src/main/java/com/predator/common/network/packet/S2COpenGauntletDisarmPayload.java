package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** Opens the disarm screen for the counting gauntlet at {@code blockPos} (packed), showing its code and deadline. */
public record S2COpenGauntletDisarmPayload(
    long blockPos,
    int code,
    long deadline
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("open_gauntlet_disarm");

    public static final Type<S2COpenGauntletDisarmPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2COpenGauntletDisarmPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.LONG,
        S2COpenGauntletDisarmPayload::blockPos,
        StreamCodecs.VAR_INT,
        S2COpenGauntletDisarmPayload::code,
        StreamCodecs.LONG,
        S2COpenGauntletDisarmPayload::deadline,
        S2COpenGauntletDisarmPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
