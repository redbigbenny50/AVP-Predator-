package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Broadcast to every client tracking {@code entityId} when that player starts or stops winding up a hand-caster shot.
 * <h2>⚠⚠ WHY THIS HAS TO BE ON THE WIRE AT ALL</h2> The charge began life as a private static boolean on the CLIENT,
 * which is fine for the one thing it was first used for - the local player's own charge sparks. The third-person arm
 * pose is different: {@code HumanoidModel.setupAnim} runs for EVERY player being rendered, so a global boolean would
 * raise the arm of every predator on screen the moment the local player pressed fire, and would never raise anyone
 * else's. Per-player state is the only correct shape, and the server is the only place that knows it for everybody.
 * <p>
 * Held items are already synced by vanilla, so the two-handed and bow poses need nothing like this - it is only the
 * charge, which exists purely as a transient input state, that has to be told to other clients.
 * </p>
 */
public record S2CCasterChargePayload(
    int entityId,
    boolean charging
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("caster_charge");

    public static final Type<S2CCasterChargePayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CCasterChargePayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.VAR_INT,
        S2CCasterChargePayload::entityId,
        StreamCodecs.BOOLEAN,
        S2CCasterChargePayload::charging,
        S2CCasterChargePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
