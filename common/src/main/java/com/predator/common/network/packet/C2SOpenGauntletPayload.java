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
 * Asks the server to open the gauntlet menu.\r\n *
 * <p>
 * \r\n * ⚠ Carries nothing. The server finds the offhand gauntlet itself — a client-supplied stack or slot index
 * would\r\n * let a player open a container they are not holding.
 */
public class C2SOpenGauntletPayload implements CustomPacketPayload {

    public static final C2SOpenGauntletPayload INSTANCE = new C2SOpenGauntletPayload();

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("open_gauntlet");

    public static final Type<C2SOpenGauntletPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SOpenGauntletPayload> CODEC = StreamCodec.unit(INSTANCE);

    private C2SOpenGauntletPayload() {}

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
