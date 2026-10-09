package com.predator.common.network.packet;

import com.just.codec.stream.StreamCodec;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Sent when the player left-clicks while holding a cloaking device. Carries no payload — the server re-checks that the
 * player is actually holding one before acting, so a spoofed packet buys nothing.
 */
/**
 * One struggle input from a netted player. ⚠ Carries no data — the server already knows who is netted, and a
 * client-supplied target would let anyone free anyone.
 */
public class C2SNetStruggleMashPayload implements CustomPacketPayload {

    public static final C2SNetStruggleMashPayload INSTANCE = new C2SNetStruggleMashPayload();

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("net_struggle_mash");

    public static final Type<C2SNetStruggleMashPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SNetStruggleMashPayload> CODEC = StreamCodec.unit(INSTANCE);

    private C2SNetStruggleMashPayload() {}

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
