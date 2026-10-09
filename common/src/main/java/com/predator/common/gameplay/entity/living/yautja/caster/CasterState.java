package com.predator.common.gameplay.entity.living.yautja.caster;

/**
 * The five positions of the shoulder plasma caster, one per animation clip.
 * <p>
 * Synced as a single byte on the yautja rather than derived on each client, because the client has to know exactly when
 * to dispatch {@code caster.shoot} — a clip that plays once and must not be missed or replayed. The server owns the
 * machine; the client watches for a change and plays the matching clip.
 *
 * @see com.predator.common.gameplay.entity.living.yautja.goal.YautjaPlasmaCasterGoal
 */
public enum CasterState {

    /** Stowed on the back. {@code caster.idle}, looping. */
    STOWED,

    /** Swinging up over the shoulder. {@code caster.aim}, held on the last frame. */
    DEPLOYING,

    /** Up and tracking, charging between shots. {@code caster.ready}, looping. */
    READY,

    /** The bolt leaves on the first tick of this state. {@code caster.shoot}, once. */
    FIRING,

    /** Folding back down. {@code caster.disarm}, held on the last frame. */
    DISARMING;

    private static final CasterState[] BY_ID = values();

    public static CasterState byId(byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : STOWED;
    }

    public byte id() {
        return (byte) ordinal();
    }

    /**
     * {@return whether the caster is off the back}
     * <p>
     * This is the flag the cloak reads: a deployed caster means the yautja has deliberately shown itself, so the cloak
     * must not creep back on underneath it.
     */
    public boolean isDeployed() {
        return this != STOWED;
    }
}
