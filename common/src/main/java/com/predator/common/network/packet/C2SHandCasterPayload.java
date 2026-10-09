package com.predator.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import com.predator.PredatorResources;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * The hand caster's trigger: pressed, released, or abandoned — and, on release, where the barrel's tip really was on
 * the shooter's screen, so the bolt leaves from the barrel they see. The server checks that point
 * (HandCasterItem.release).
 */
public record C2SHandCasterPayload(
    int action,
    boolean hasTip,
    double tipX,
    double tipY,
    double tipZ
) implements CustomPacketPayload {

    public static final int PRESS = 0;

    public static final int RELEASE = 1;

    public static final int CANCEL = 2;

    public static final ResourceLocation PAYLOAD_ID = PredatorResources.location("hand_caster");

    public static final Type<C2SHandCasterPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SHandCasterPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.VAR_INT,
        C2SHandCasterPayload::action,
        StreamCodecs.BOOLEAN,
        C2SHandCasterPayload::hasTip,
        StreamCodecs.DOUBLE,
        C2SHandCasterPayload::tipX,
        StreamCodecs.DOUBLE,
        C2SHandCasterPayload::tipY,
        StreamCodecs.DOUBLE,
        C2SHandCasterPayload::tipZ,
        C2SHandCasterPayload::new
    );

    public static C2SHandCasterPayload of(int action) {
        return new C2SHandCasterPayload(action, false, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
