package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * "This self-destruct is still counting, here": sent every few ticks for each counting gauntlet to the players near it.
 * The client starts the 20-second laugh on the first one and keeps it pinned to the gauntlet; when they STOP arriving —
 * disarmed, rule switched off, detonated — it fades the laugh out. [stated] "it would fade out if its disarmed."
 *
 * @param id           the countdown's id, so two gauntlets counting at once each get their own laugh
 * @param elapsedTicks ticks since the countdown started; a client that only hears about it late does not start the
 *                     laugh from the beginning out of sync with the panels
 */
public record S2CDestructLaughPayload(
    UUID id,
    double x,
    double y,
    double z,
    int elapsedTicks
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("destruct_laugh");

    public static final Type<S2CDestructLaughPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CDestructLaughPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.UUID,
        S2CDestructLaughPayload::id,
        StreamCodecs.DOUBLE,
        S2CDestructLaughPayload::x,
        StreamCodecs.DOUBLE,
        S2CDestructLaughPayload::y,
        StreamCodecs.DOUBLE,
        S2CDestructLaughPayload::z,
        StreamCodecs.VAR_INT,
        S2CDestructLaughPayload::elapsedTicks,
        S2CDestructLaughPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
