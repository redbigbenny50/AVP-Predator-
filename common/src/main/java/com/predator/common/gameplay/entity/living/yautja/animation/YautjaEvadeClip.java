package com.predator.common.gameplay.entity.living.yautja.animation;

/**
 * Every evasive move the yautja plays, one entry per clip in yautja_jungle.animation.json, and how long each one owns
 * the body.
 * <p>
 * Oct 16 - [stated], on the clips that had sat unused: "evade roll yes we want to keep that"; "dodge backwards ...
 * shoots the yautja backwards ... this animation going backwards for its duration probably at 80% speed"; "dodge left
 * and dodge right are meant to avoid range and melee attacks like the roll does"; "the dodge forward is a lunge
 * attack"; and dodge.attack "is a duplicate of left", so it is deliberately absent.
 * <h2>⚠ The hold length is the clip length at its speed, on BOTH sides</h2> The server holds the body for
 * {@link #ticks()} (Yautja.startEvade), and the client sets the same hold when it sees the clip start, so its
 * locomotion clip cannot cut the evade short. The roll used to be cut off by the jump clip the tick it left the ground,
 * because the client never knew how long a dodge lasted.
 * <h2>⚠ None of these clips move the body</h2> Their root bone only bobs up and down, so every one of them is moved by
 * code (YautjaDodge, YautjaFallBackGoal, YautjaLungeGoal, YautjaSkirmish), timed to the clip's own frames.
 */
public enum YautjaEvadeClip {

    /** Explosions, an inbound rocket, a net. Unchanged. */
    ROLL("evade.roll", 1.0F, 1.0F),

    /** Sidestep a melee or ranged hit, or the ranged weave's hop, to the yautja's own left. */
    LEFT("dodge.left", 0.875F, 1.0F),

    /** The same, to its right. */
    RIGHT("dodge.right", 0.875F, 1.0F),

    /** The fall-back dash away from a crowd. [stated] "probably at 80% speed". */
    BACK("dodge.back", 0.91667F, 0.8F),

    /** The lunge: a gap-closer onto a target 5-10 blocks away, striking on arrival. */
    FORWARD("dodge.forward", 1.0F, 1.0F);

    private final String clipName;

    private final float clipSeconds;

    private final float speed;

    YautjaEvadeClip(String clipName, float clipSeconds, float speed) {
        this.clipName = clipName;
        this.clipSeconds = clipSeconds;
        this.speed = speed;
    }

    public String clipName() {
        return clipName;
    }

    /** {@return the playback speed, 1 being as authored} */
    public float speed() {
        return speed;
    }

    /** {@return how many ticks the clip runs at its speed, rounded up so the hold never ends a frame early} */
    public int ticks() {
        return (int) Math.ceil(clipSeconds / speed * 20.0F);
    }

    /**
     * {@return the tick, counted from the clip's start, at which a moment in the clip as AUTHORED is reached} So a
     * frame read off the animation file (in seconds at speed 1) can be used directly, whatever speed it plays at.
     */
    public int tickAt(float authoredSeconds) {
        return Math.round(authoredSeconds / speed * 20.0F);
    }

    /** {@return the clip for a byte that came over the network, or null for "none" and anything out of range} */
    public static YautjaEvadeClip byId(byte id) {
        var values = values();
        return id > 0 && id <= values.length ? values[id - 1] : null;
    }

    /** {@return this clip's network id; 0 is reserved for "none"} */
    public byte id() {
        return (byte) (ordinal() + 1);
    }
}
