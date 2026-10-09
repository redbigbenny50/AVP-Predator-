package com.predator.common.gameplay.entity.living.yautja.animation;

/**
 * The one-shot attack clips, and the combo order they play in.
 * <h2>⚠⚠ Why these need syncing at all when the locomotion states do not</h2> Az commands dispatch CLIENT-side, but
 * every attack decision — the GOAP melee action, the two throws — happens on the SERVER. A locomotion clip can be
 * derived on the client because the evidence is observable (speed, swimming, airtime); "he just stabbed" is not
 * observable, it is an event. So the server stamps the event into synched data and the client edge-detects it.
 * <p>
 * ⚠ Which is why {@code Yautja} pairs this with a SEQUENCE counter. Two identical stabs in a row would otherwise set
 * the same byte twice, the client would see no change, and the second stab would play nothing.
 */
public enum YautjaAttackAnimation {

    /** Nothing playing. The initial value, never dispatched. */
    NONE("", 0.0F),

    /**
     * {@code attack.melee.slash} — his "diagonal slash attack".
     * <p>
     * The workhorse of the combo. Two of these lead into a stab so the melee is not the same swipe repeated.
     */
    SLASH("attack.melee.slash", 1.0F),

    /**
     * {@code attack.melee.stab} — right arm, wrist blades driven in.
     * <p>
     * His spec: "it would have some armor piercing power like 10%". The finisher of the combo, and the only step that
     * cares what the target is wearing.
     */
    STAB("attack.melee.stab", 1.0F),

    /**
     * {@code attack.melee.bare} — his "opposite arm punching normal".
     * <p>
     * Deliberately the weakest step. It is the beat between combos, not a third blade attack.
     */
    BARE("attack.melee.bare", 0.75F),

    /**
     * {@code attack.melee.weapon} — a swing with a sword or axe in hand.
     * <p>
     * ⚠ Replaces the WHOLE combo rather than joining it. The slash, stab and bare clips animate bare forearms and wrist
     * blades; a yautja holding an axe is doing something different with its arms, so mixing them would swing the weapon
     * through a blade pose. Armed melee is its own single clip.
     */
    WEAPON("attack.melee.weapon", 1.0F),

    /** {@code attack.throw.shuriken} — reaching into the belt and throwing. 0.75s. */
    THROW_SHURIKEN("attack.throw.shuriken", 0.0F),

    /** {@code attack.throw.smartdisc} — the same reach, thrown flat. 0.65s. */
    THROW_SMARTDISC("attack.throw.smartdisc", 0.0F),

    /** Combi stick sweep. ⚠ Two of these then a stab, mirroring the bare combo — his "swipe swipe stab". */
    SPEAR_SWIPE("attack.spear.swipe", 1.0F),

    /**
     * Combi stick thrust.
     * <p>
     * ⚠ The knockback is what makes it the COMBO FINISHER rather than just a third swing: it ends the exchange by
     * putting the target where the yautja wants them, which is the whole point of a spear over a blade.
     */
    SPEAR_STAB("attack.spear.stab", 1.15F),

    SPEAR_THROW("attack.spear.throw", 1.0F),

    /** ⚠ Zero damage — these are the gauntlet firing, and whatever it launches carries the damage itself. */
    WRIST_AIM("attack.wrist.aim", 0.0F),

    WRIST_FIRE("attack.wrist.fire", 0.0F),

    /**
     * The battleaxe's three swings, in the combo order he set: horizontal, left diagonal, right diagonal.
     * <p>
     * ⚠ Their multiplier is 1.0 against the yautja's ATTACK_DAMAGE — the axe's extra weight is already in the yautja's
     * weapon damage, so multiplying again here would count it twice.
     */
    BATTLEAXE_HORIZONTAL("attack.battleaxe.horizontal", 1.0F),

    BATTLEAXE_LEFT("attack.battleaxe.leftdiagonal", 1.0F),

    BATTLEAXE_RIGHT("attack.battleaxe.rightdiagonal", 1.0F),

    /** The ground slam. Its damage is applied by BattleaxeSlam, not by this multiplier. */
    BATTLEAXE_SLAM("specialattack.battleaxe.downwardslam", 0.0F);

    /**
     * The melee combo, in order, cycling.
     * <p>
     * ⚠ Slash, slash, STAB, bare — his "a combo of slashes and then a stab would look good so its not the same
     * swiping". The stab landing third is what gives the sequence a shape: two setup blows, the armour-piercing
     * finisher, then the off-hand punch as a beat before it starts again.
     */
    private static final YautjaAttackAnimation[] MELEE_COMBO = { SLASH, SLASH, STAB, BARE };

    /**
     * The combi stick combo — his "swipe swipe stab".
     * <p>
     * ⚠ Three steps where the bare combo has four. A spear has no off-hand punch to fall back on, and ending on the
     * knockback stab means every cycle finishes by resetting the distance — which is what a spear is FOR.
     */
    private static final YautjaAttackAnimation[] SPEAR_COMBO = { SPEAR_SWIPE, SPEAR_SWIPE, SPEAR_STAB };

    private static final YautjaAttackAnimation[] BY_ID = values();

    private final String clip;

    private final float damageMultiplier;

    YautjaAttackAnimation(String clip, float damageMultiplier) {
        this.clip = clip;
        this.damageMultiplier = damageMultiplier;
    }

    public static YautjaAttackAnimation meleeStep(int step) {
        return MELEE_COMBO[Math.floorMod(step, MELEE_COMBO.length)];
    }

    private static final YautjaAttackAnimation[] BATTLEAXE_COMBO = {
        BATTLEAXE_HORIZONTAL,
        BATTLEAXE_LEFT,
        BATTLEAXE_RIGHT
    };

    /** {@return the battleaxe swing for this step of the combo} */
    public static YautjaAttackAnimation battleaxeStep(int step) {
        return BATTLEAXE_COMBO[Math.floorMod(step, BATTLEAXE_COMBO.length)];
    }

    public static YautjaAttackAnimation spearStep(int step) {
        return SPEAR_COMBO[Math.floorMod(step, SPEAR_COMBO.length)];
    }

    public static YautjaAttackAnimation byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }

    public String clip() {
        return clip;
    }

    /** Scales the attack-damage attribute for this step. */
    public float damageMultiplier() {
        return damageMultiplier;
    }

    /** {@return the fraction of the target's armour this step ignores} */
    public float armourPiercing() {
        return this == STAB ? 0.10F : 0.0F;
    }

    /**
     * {@return the knockback this attack applies, in vanilla knockback units}
     * <p>
     * ⚠ Only the spear stab has any. His ruling — it is what separates the finisher from a third swing, and giving the
     * swipe some too would let a yautja shove a target out of its own reach on every hit.
     */
    /**
     * Playback speed for this clip.
     * <p>
     * [stated] "i think perhaps the yautja attacks in general should be slower ... maybe to 60% speed or 65. some can
     * be faster like the combos but i think they happen so fast you actually dont get to see them." COMBO steps (the
     * punch combo, the spear swipe) 0.80 BATTLEAXE swings 0.85 — a combo, and the yautja swings it faster than a player
     * HEAVY single blows (weapon swing, throws, the slam) 0.65 SPEAR STAB and THROW 0.50 — [stated] earlier, "slowed
     * down 50%" wrist aim / fire 1.00 — quick gauntlet shots, not swings ⚠ Slowing a clip MOVES its impact frame, which
     * is why {@link #impactTicks()} is derived from this value.
     */
    public float playbackSpeed() {
        return switch (this) {
            case SPEAR_STAB, SPEAR_THROW -> SPEAR_SPEED;
            case BARE, SLASH, STAB, SPEAR_SWIPE -> COMBO_SPEED;
            case BATTLEAXE_HORIZONTAL, BATTLEAXE_LEFT, BATTLEAXE_RIGHT -> BATTLEAXE_SPEED;
            case WEAPON, THROW_SHURIKEN, THROW_SMARTDISC, BATTLEAXE_SLAM -> HEAVY_SPEED;
            default -> 1.0F;
        };
    }

    /**
     * Ticks from the start of the clip to the moment it CONNECTS — or lets go — at its playback speed.
     * <p>
     * 🚨 EVERY VALUE IS READ FROM THE CLIP'S OWN KEYFRAMES, at the arm's fastest movement toward the target — the end
     * of the wind-up. (Seconds at normal speed; 20 ticks is one second.) attack.melee.bare 0.25 wind-up to 0.15, swing
     * lands 0.25 attack.melee.slash 0.30 right arm swings from 0.25, sweeps by 0.35 attack.melee.stab 0.25 right arm
     * thrusts at 0.25 attack.melee.weapon 0.32 🚨 WAS 0.25, WHICH WAS EARLY. [stated] "for the veritanium axe and sword
     * it seems like the enemies die before the actual hit from the weapon". Traced through the bone chain (gBody >
     * gLeftArm > gLeftForearm > gLeftHoldWeapon): at 0.25 s the blade is only ~5 px in front of the body, still coming
     * round the side; it arrives in front at 0.32 s and is fully across at 0.35 s. The raw keyframes agree — the arm's
     * sweep across the front runs 0.25 to 0.35 s. attack.spear.swipe 0.45 overhead by 0.35, sweeps through 0.45
     * attack.spear.stab 0.42 winds back to 0.17, thrusts 0.42 attack.spear.throw 0.75 cocked overhead 0.5-0.6, released
     * 0.75 attack.battleaxe.horizontal 0.46 raised 0.17-0.33, sweeps across to 0.58 attack.battleaxe.leftdiagonal 0.44
     * raised to 0.33, cuts down to 0.54 attack.battleaxe.rightdiagonal 0.30 raised 0.17, cuts at 0.33
     * specialattack.battleaxe.downwardslam 0.54 overhead at 0.42, SLAMS DOWN 0.54 attack.throw.shuriken 0.48 wound
     * overhead 0.35, released by 0.50 attack.throw.smartdisc 0.40 wound to 0.35, released by 0.45 Zero means frame 0 —
     * only the wrist shots, which are instant by nature.
     */
    public int impactTicks() {
        var seconds = switch (this) {
            case BARE, STAB -> 0.25F;
            case WEAPON -> 0.32F;
            case SLASH -> 0.30F;
            case SPEAR_SWIPE -> 0.45F;
            case SPEAR_STAB -> 0.42F;
            case SPEAR_THROW -> 0.75F;
            case BATTLEAXE_HORIZONTAL -> 0.46F;
            case BATTLEAXE_LEFT -> 0.44F;
            case BATTLEAXE_RIGHT -> 0.30F;
            case BATTLEAXE_SLAM -> 0.54F;
            case THROW_SHURIKEN -> 0.48F;
            case THROW_SMARTDISC -> 0.40F;
            default -> 0.0F;
        };

        return Math.round(seconds * 20.0F / playbackSpeed());
    }

    private static final float SPEAR_SPEED = 0.5F;

    private static final float COMBO_SPEED = 0.8F;

    private static final float BATTLEAXE_SPEED = 0.85F;

    private static final float HEAVY_SPEED = 0.65F;

    public float knockback() {
        return this == SPEAR_STAB ? 0.9F : 0.0F;
    }

    public boolean isMelee() {
        return this == SLASH || this == STAB || this == BARE || this == SPEAR_SWIPE || this == SPEAR_STAB;
    }
}
