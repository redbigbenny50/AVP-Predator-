package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Carries an entity's remaining mud duration to every client tracking it. Zero means "no mud".
 * <p>
 * This packet exists because vanilla will not carry it: mob effects are never broadcast to trackers, so without this
 * the thermal cloak is checking an empty map on the observer's side and mud hides nothing.
 * <p>
 * Sent on a {@link com.predator.common.gameplay.effect.PredatorMud#SYNC_INTERVAL_TICKS} cadence rather than every tick
 * — the client stores an expiry game-time and derives remaining duration on demand, so a second of drift is invisible
 * on an opacity ramp and costs one packet per muddy entity per second instead of twenty.
 */
public record S2CMudStatePayload(
    int entityId,
    int remainingTicks
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("mud_state");

    public static final Type<S2CMudStatePayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CMudStatePayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.VAR_INT,
        S2CMudStatePayload::entityId,
        StreamCodecs.VAR_INT,
        S2CMudStatePayload::remainingTicks,
        S2CMudStatePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
