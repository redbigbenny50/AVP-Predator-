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
public class C2SToggleCloakPayload implements CustomPacketPayload {

    public static final C2SToggleCloakPayload INSTANCE = new C2SToggleCloakPayload();

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("toggle_cloak");

    public static final Type<C2SToggleCloakPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SToggleCloakPayload> CODEC = StreamCodec.unit(INSTANCE);

    private C2SToggleCloakPayload() {}

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
