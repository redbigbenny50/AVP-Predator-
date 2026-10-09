package com.predator.common.gameplay.item.combistick;

import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.schema.StreamCodecSchema;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

/**
 * Whether a combi stick is collapsed, extending, extended or collapsing.
 * <h2>Why a state rather than a boolean</h2> ⚠ The open and close clips are 0.25s and 0.17s of actual motion —
 * telescoping segments sliding out of one another. A boolean would snap between collapsed and extended and throw both
 * away. The two transient states exist so the animation has somewhere to live and so an attack cannot start
 * mid-extension.
 */
public enum CombiStickState {

    /** Folded to a baton. The only state it can be put away in. */
    COLLAPSED,

    /** ⚠ Playing {@code combi.open}. Attacks are refused here — the blades are not out yet. */
    OPENING,

    /** Extended and holding {@code combi.loop}. */
    EXTENDED,

    /** Playing {@code combi.close}. */
    CLOSING;

    public boolean canAttack() {
        return this == EXTENDED;
    }

    /**
     * ⚠ Codecs copied from {@code PredatorVisionMode} — the mod already stores an enum in a data component and this is
     * that pattern, not a new one. String on disk so a reordering of the enum does not silently reinterpret saved
     * sticks; ordinal on the wire because both sides are the same build.
     */
    public static final Codec<CombiStickState> CODEC = Codec.STRING.xmap(
        CombiStickState::valueOf,
        CombiStickState::name
    );

    public static final StreamCodec<CombiStickState> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull <T> CombiStickState decode(@NotNull StreamCodecSchema<T> schema, @NotNull T input) {
            var ordinal = schema.readVarInt(input);
            var values = CombiStickState.values();

            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Invalid CombiStickState ordinal: " + ordinal);
            }

            return values[ordinal];
        }

        @Override
        public <T> void encode(@NotNull StreamCodecSchema<T> schema, @NotNull T input, @NotNull CombiStickState value) {
            schema.writeVarInt(input, value.ordinal());
        }
    };
}
