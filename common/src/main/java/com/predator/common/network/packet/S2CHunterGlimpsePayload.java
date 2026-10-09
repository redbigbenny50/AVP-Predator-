package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Phase 1 of a hunt: show the hunted player — and only them — a cloaked yautja standing at this spot, facing
 * {@code yaw}. It exists on that one client and nowhere else: nothing on the server, nothing for anyone else to see,
 * hit or be hit by. [stated] "images of a cloaked predator at the edge of your distance that fade as you get close."
 */
public record S2CHunterGlimpsePayload(
    double x,
    double y,
    double z,
    float yaw
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("hunter_glimpse");

    public static final Type<S2CHunterGlimpsePayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CHunterGlimpsePayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.DOUBLE,
        S2CHunterGlimpsePayload::x,
        StreamCodecs.DOUBLE,
        S2CHunterGlimpsePayload::y,
        StreamCodecs.DOUBLE,
        S2CHunterGlimpsePayload::z,
        StreamCodecs.FLOAT,
        S2CHunterGlimpsePayload::yaw,
        S2CHunterGlimpsePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
