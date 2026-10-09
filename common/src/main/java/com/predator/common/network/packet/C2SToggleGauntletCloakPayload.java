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
 * Asks the server to toggle the cloak seated in the gauntlet.\r\n *
 * <p>
 * \r\n * ⚠ Carries nothing, and the server re-checks that a cloaking device is actually in the housing. Trusting
 * the\r\n * client here would make the cloak free.
 */
public class C2SToggleGauntletCloakPayload implements CustomPacketPayload {

    public static final C2SToggleGauntletCloakPayload INSTANCE = new C2SToggleGauntletCloakPayload();

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("toggle_gauntlet_cloak");

    public static final Type<C2SToggleGauntletCloakPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SToggleGauntletCloakPayload> CODEC = StreamCodec.unit(INSTANCE);

    private C2SToggleGauntletCloakPayload() {}

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
