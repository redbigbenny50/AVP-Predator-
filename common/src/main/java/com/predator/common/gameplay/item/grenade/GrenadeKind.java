package com.predator.common.gameplay.item.grenade;

/**
 * The five yautja grenades — his Oct 3 spec.
 * <ul>
 * <li>{@link #EXPLOSIVE} — a TNT-sized blast.</li>
 * <li>{@link #FIRE} — half the blast, and a patch of fire that burns and spreads like any fire.</li>
 * <li>{@link #STICKY} — sticks to any block face or to a mob, beeps, and goes off three seconds later with a TNT-sized
 * blast.</li>
 * <li>{@link #FREEZE} — half the blast, and a patch of ice for about ten seconds that freezes what stands in it.</li>
 * <li>{@link #IRRADIATED} — avp_human's irradiated grenade: a much larger blast and a radiation cloud left behind.</li>
 * </ul>
 */
public enum GrenadeKind {

    EXPLOSIVE("explosive"),
    FIRE("fire"),
    STICKY("sticky"),
    FREEZE("freeze"),
    IRRADIATED("irradiated");

    private final String id;

    GrenadeKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GrenadeKind byId(String id) {
        for (var kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }

        return EXPLOSIVE;
    }
}
