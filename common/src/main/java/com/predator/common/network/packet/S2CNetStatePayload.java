package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Broadcast to every client tracking {@code entityId} whenever that entity's cloak field goes up or down, and sent
 * directly to a client that has just started tracking an already-netted entity.
 * <p>
 * This is the data path a cloak needs and the vision never did: the vision runs on the wearer's own screen, but a cloak
 * has to be absent from <em>everyone else's</em>. Mob effects cannot carry this — vanilla only sends effect packets to
 * an entity's passengers.
 * <p>
 * Water/rain is deliberately not in this payload: both sides can read {@code isInWaterOrRain()} off the entity, so
 * putting it on the wire would just be a second source of truth that can disagree.
 */
/**
 * Tells a client that a mob is caught in a net, so it can draw the overlay.
 * <p>
 * ⚠ An explicit payload rather than synched entity data: a net catches ANY mob, including ones from mods that know
 * nothing about this one, so there is no entity class to add an accessor to. Same shape avp_alien uses for its capture
 * hold.
 */
public record S2CNetStatePayload(
    int entityId,
    boolean netted
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("net_state");

    public static final Type<S2CNetStatePayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CNetStatePayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.VAR_INT,
        S2CNetStatePayload::entityId,
        StreamCodecs.BOOLEAN,
        S2CNetStatePayload::netted,
        S2CNetStatePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
