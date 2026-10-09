package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** A disarm attempt on the placed gauntlet at {@code blockPos} (packed) with the four-digit {@code code}. */
public record C2SDisarmGauntletPayload(
    long blockPos,
    int code
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("disarm_gauntlet");

    public static final Type<C2SDisarmGauntletPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SDisarmGauntletPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.LONG,
        C2SDisarmGauntletPayload::blockPos,
        StreamCodecs.VAR_INT,
        C2SDisarmGauntletPayload::code,
        C2SDisarmGauntletPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
