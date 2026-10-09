package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * The worn gauntlet's arming panels. {@code mode}: {@link #PROBE} on the first panel click (asks whether the system is
 * enabled, so the "disabled" message appears the moment you start), {@link #ARM} when all four read 9, {@link #CANCEL}
 * when a panel is blanked while armed.
 */
public record C2SArmGauntletPayload(int mode) implements CustomPacketPayload {

    public static final int CANCEL = 0;

    public static final int ARM = 1;

    public static final int PROBE = 2;

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("arm_gauntlet");

    public static final Type<C2SArmGauntletPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SArmGauntletPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.VAR_INT,
        C2SArmGauntletPayload::mode,
        C2SArmGauntletPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
