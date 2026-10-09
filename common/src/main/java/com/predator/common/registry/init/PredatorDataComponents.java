package com.predator.common.registry.init;

import com.blib.api.common.codec.v1.stream.adapter.J2MStreamCodecAdapter;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.component.PredatorVisionMode;
import com.predator.common.gameplay.item.combistick.CombiStickState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.UnaryOperator;

public class PredatorDataComponents {

    private static final BLibRegistry<DataComponentType<?>> REGISTRY =
        Predator.MOD.registries().create(BuiltInRegistries.DATA_COMPONENT_TYPE);

    public static final BLibHolder<DataComponentType<PredatorVisionMode>> VISION_MODE = create(
        "vision_mode",
        builder -> builder.persistent(PredatorVisionMode.CODEC)
            .networkSynchronized(new J2MStreamCodecAdapter<>(PredatorVisionMode.STREAM_CODEC))
            .cacheEncoding()
    );

    /** Whether the cloaking device is switched on. Stored on the stack so the state survives a relog. */
    // ---------------------------------------------------------------- gauntlet self-destruct
    // ⚠ All five ride the gauntlet STACK, synced, so the countdown follows it wherever it goes and every client
    // renders the armed lightning. See GauntletSelfDestruct.

    /** Set by the worn GUI's four 9s. */
    public static final BLibHolder<DataComponentType<Boolean>> DESTRUCT_ARMED = create(
        "destruct_armed",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
    );

    /**
     * Whether this whip was the selected item last tick.
     * <p>
     * ⚠ Stored on the STACK, not in a map. The equip sound must fire on the tick selection CHANGES, and a stack can
     * move between slots, inventories and players; anything keyed on a slot or a player would misfire the moment it was
     * picked up or dropped.
     * <p>
     * ⚠ NOT networkSynchronized — it is bookkeeping for the server's sound, and syncing it would flicker the stack's
     * component data on every hotbar change for no benefit.
     */
    /** Hand caster shots left. Absent = full (64) — so a fresh or dropped one is fully loaded. */
    public static final BLibHolder<DataComponentType<Integer>> HAND_CASTER_SHOTS = create(
        "hand_caster_shots",
        builder -> builder.persistent(com.mojang.serialization.Codec.INT)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
    );

    /** Game time the hand caster last fired — the render side plays the fire clip while it is recent. */
    public static final BLibHolder<DataComponentType<Long>> HAND_CASTER_FIRED_AT = create(
        "hand_caster_fired_at",
        builder -> builder.persistent(com.mojang.serialization.Codec.LONG)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
    );

    /** Game time the battleaxe slam's LEAP began — the pose raises the axe while airborne. Synced, not saved. */
    public static final BLibHolder<DataComponentType<Long>> BATTLEAXE_LEAP_AT = create(
        "battleaxe_leap_at",
        builder -> builder.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
    );

    /** Game time the battleaxe slam LANDED — the pose brings the axe down. Synced, not saved. */
    public static final BLibHolder<DataComponentType<Long>> BATTLEAXE_SLAM_AT = create(
        "battleaxe_slam_at",
        builder -> builder.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
    );

    public static final BLibHolder<DataComponentType<Boolean>> WHIP_WAS_SELECTED = create(
        "whip_was_selected",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
    );

    /** Whether this plasma bow was the selected item last tick — the equip sound fires on the CHANGE. */
    public static final BLibHolder<DataComponentType<Boolean>> PLASMA_BOW_WAS_SELECTED = create(
        "plasma_bow_was_selected",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
    );

    /** Whether this hand caster was the selected item last tick — the take-out / put-away sounds fire on the CHANGE. */
    public static final BLibHolder<DataComponentType<Boolean>> HAND_CASTER_WAS_SELECTED = create(
        "hand_caster_was_selected",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
    );

    /** Whether this shuriken was the selected item last tick — the equip sound fires on the CHANGE. */
    public static final BLibHolder<DataComponentType<Boolean>> SHURIKEN_WAS_SELECTED = create(
        "shuriken_was_selected",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
    );

    /** Whether this disc was the selected item last tick — the equip sound fires on the CHANGE, not while held. */
    public static final BLibHolder<DataComponentType<Boolean>> SMART_DISC_WAS_SELECTED = create(
        "smart_disc_was_selected",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
    );

    /** Absolute game time of the blast; present only once placed. */
    public static final BLibHolder<DataComponentType<Long>> DESTRUCT_DEADLINE = create(
        "destruct_deadline",
        builder -> builder.persistent(com.mojang.serialization.Codec.LONG)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
    );

    /** Registry id of this countdown. */
    public static final BLibHolder<DataComponentType<java.util.UUID>> DESTRUCT_ID = create(
        "destruct_id",
        builder -> builder.persistent(net.minecraft.core.UUIDUtil.CODEC)
            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
    );

    /** The four-distinct-digit disarm code, packed. */
    public static final BLibHolder<DataComponentType<Integer>> DESTRUCT_CODE = create(
        "destruct_code",
        builder -> builder.persistent(com.mojang.serialization.Codec.INT)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
    );

    /** Who armed it — credited with the kills. */
    public static final BLibHolder<DataComponentType<java.util.UUID>> DESTRUCT_ARMER = create(
        "destruct_armer",
        builder -> builder.persistent(net.minecraft.core.UUIDUtil.CODEC)
            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
    );

    public static final BLibHolder<DataComponentType<Boolean>> CLOAK_ACTIVE = create(
        "cloak_active",
        builder -> builder.persistent(com.mojang.serialization.Codec.BOOL)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
    );

    /**
     * Whether a combi stick is collapsed, extending, extended or collapsing.
     * <p>
     * ⚠ ON THE STACK, not on the holder: the state survives being dropped, stored, or handed to someone else. A stick
     * that remembered being extended because its last holder had it out would desync the model from the damage it
     * deals.
     */
    public static final BLibHolder<DataComponentType<CombiStickState>> COMBI_STICK_STATE = create(
        "combi_stick_state",
        builder -> builder.persistent(CombiStickState.CODEC)
            .networkSynchronized(new J2MStreamCodecAdapter<>(CombiStickState.STREAM_CODEC))
    );

    /**
     * The gauntlet's six slots.
     * <p>
     * ⚠ ON THE STACK, so a gauntlet dropped, stored or handed over keeps its magazine. Keyed on the holder it would
     * empty the moment it changed hands, and a player would lose a full load of darts to a chest.
     * <p>
     * ⚠ Vanilla's ItemContainerContents already has both codecs, so this needs no custom serialisation at all.
     */
    public static final BLibHolder<DataComponentType<ItemContainerContents>> GAUNTLET_CONTENTS = create(
        "gauntlet_contents",
        builder -> builder.persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
    );

    /**
     * Which ammunition type this gauntlet is set to fire.
     * <p>
     * ⚠ ON THE STACK, his ruling: "gauntlets remember what they are set to individually". Two gauntlets in one
     * inventory keep their own setting, which a holder-keyed field could not do.
     * <p>
     * ⚠ Stored as a STRING rather than an ordinal, so adding a type between two existing ones does not silently
     * reinterpret every saved gauntlet.
     */
    public static final BLibHolder<DataComponentType<String>> GAUNTLET_AMMO = create(
        "gauntlet_ammo",
        builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
    );

    /**
     * Game time at which the overload cooldown expires.
     * <p>
     * ⚠ Stored as LEVEL GAME TIME, not a server tick count. The server's tick counter restarts from zero every launch,
     * so a cooldown recorded against it would evaporate on relog — which was the exploit: overload the cloak, relog,
     * and walk away with it ready again. Game time is saved with the world and survives.
     */
    public static final BLibHolder<DataComponentType<Long>> CLOAK_COOLDOWN_UNTIL = create(
        "cloak_cooldown_until",
        builder -> builder.persistent(com.mojang.serialization.Codec.LONG)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
    );

    private static <T> BLibHolder<DataComponentType<T>> create(
        String id,
        UnaryOperator<DataComponentType.Builder<T>> unaryOperator
    ) {
        return REGISTRY.createHolder(id, () -> unaryOperator.apply(DataComponentType.builder()).build());
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
