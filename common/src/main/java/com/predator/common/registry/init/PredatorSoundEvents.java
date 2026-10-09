package com.predator.common.registry.init;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.PredatorResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public class PredatorSoundEvents {

    private static final BLibRegistry<SoundEvent> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.SOUND_EVENT);

    public static final BLibHolder<SoundEvent> ITEM_ARMOR_EQUIP_VERITANIUM = create("item.armor.equip_veritanium");

    public static final BLibHolder<SoundEvent> JUKEBOX_SOUNDS_PREDATOR_MUSIC_1 = create("jukebox_sounds.predator_music_1");

    /** Plays locally when the helmet's vision mode is cycled. Client-only — nothing on the server triggers it. */
    public static final BLibHolder<SoundEvent> VISION_SWAP = create("vision.vision_swap");

    /**
     * One beep per digit of the self-destruct countdown. ⚠ Pitched UP per column — see GauntletSelfDestruct: column 1
     * plays it at 1.0, and each column after is a step higher, so the sequence rises as the panels burn down.
     */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_COUNTDOWN = create("gauntlet.destruct_countdown");

    /** The wrist gauntlet's door opening as the GUI opens. */
    public static final BLibHolder<SoundEvent> GAUNTLET_OPEN = create("gauntlet.open");

    /** The door closing as the GUI closes. */
    public static final BLibHolder<SoundEvent> GAUNTLET_CLOSE = create("gauntlet.close");

    /**
     * The chain firing out.
     * <p>
     * ⚠⚠ DEPLOY AND RETRACT ARE SEPARATE ON PURPOSE. One clip covering the whole journey only lined up at maximum range
     * — a 10-block grapple is over in about 1.3 s and the tail played after landing. Firing the deploy on launch and
     * the retract on RELEASE makes the audio fit any length, because the second clip is triggered by the event rather
     * than by a guess at the duration.
     * <p>
     * ⚠ Named under "grapple", not "whip" — the grapple is moving to the gauntlet as its own weapon later, and these
     * sounds go with it.
     */
    public static final BLibHolder<SoundEvent> GRAPPLE_CHAIN_DEPLOY = create("grapple.chain_deploy");

    /**
     * The shuriken being wound up.
     * <p>
     * ⚠ Played at the START of the draw, not at the end of it. The clip is 1.01 s and the draw is 20 ticks — exactly
     * one second — so it covers the whole wind-up. Firing it at full charge would put a "pulling back" sound after the
     * pull had finished.
     */
    public static final BLibHolder<SoundEvent> SHURIKEN_CHARGE = create("shuriken.charge");

    /**
     * The shuriken reaching FULL charge.
     * <p>
     * ⚠ A separate clip rather than a tail on the charge sound, and that matters: a baked-in tail would play even when
     * the draw was released early, because the whole clip starts the moment the button goes down. This one fires only
     * when the charge actually completes, so it can never lie about being ready.
     */
    public static final BLibHolder<SoundEvent> SHURIKEN_CHARGE_END = create("shuriken.charge_end");

    /** The plasma bow, brought out. */
    public static final BLibHolder<SoundEvent> PLASMA_BOW_EQUIP = create("plasma_bow.equip");

    /** Drawing it back. Plays once, as the draw begins. */
    public static final BLibHolder<SoundEvent> PLASMA_BOW_DRAW = create("plasma_bow.draw");

    /**
     * The charge held at full draw, LOOPED.
     * <p>
     * ⚠⚠ THREE PITCHES FROM ONE FILE. [stated] "the loop needs different pitches if possible at least 3 its a
     * continuous plasma sound." sounds.json lists the same clip three times at 0.92, 1.00 and 1.09, and vanilla picks
     * one at random each time the loop starts — so two draws in a row hum differently without three copies of the
     * audio. ⚠ The pitch is fixed for the duration of a given hold; varying it mid-loop would warble.
     */
    public static final BLibHolder<SoundEvent> PLASMA_BOW_HOLD = create("plasma_bow.hold");

    /** Loosing a bolt. */
    public static final BLibHolder<SoundEvent> PLASMA_BOW_FIRE = create("plasma_bow.fire");

    /** The bolt bursting on whatever it met. */
    public static final BLibHolder<SoundEvent> PLASMA_BOW_HIT = create("plasma_bow.hit");

    /** The shuriken, brought out. */
    public static final BLibHolder<SoundEvent> SHURIKEN_EQUIP = create("shuriken.equip");

    /**
     * The shuriken, thrown.
     * <p>
     * ⚠ No flight loop for this one — [stated] "it has no sound while its flying since its pretty instant."
     */
    public static final BLibHolder<SoundEvent> SHURIKEN_THROW = create("shuriken.throw");

    /** The disc striking a block it cannot place: the fallback when the material is not one of the three below. */
    public static final BLibHolder<SoundEvent> SMART_DISC_HIT_GENERAL = create("smart_disc.hit_general");

    /** The disc striking wood — logs, planks, anything vanilla gives the wood sound type. */
    public static final BLibHolder<SoundEvent> SMART_DISC_HIT_WOOD = create("smart_disc.hit_wood");

    /** The disc striking metal — iron, copper, netherite, chains. */
    public static final BLibHolder<SoundEvent> SMART_DISC_HIT_METAL = create("smart_disc.hit_metal");

    /** The disc striking stone or ore, deepslate included. */
    public static final BLibHolder<SoundEvent> SMART_DISC_HIT_STONE = create("smart_disc.hit_stone");

    /** The disc biting a living thing. */
    public static final BLibHolder<SoundEvent> SMART_DISC_HIT_ENTITY = create("smart_disc.hit_entity");

    /** The smart disc, brought out. */
    public static final BLibHolder<SoundEvent> SMART_DISC_EQUIP = create("smart_disc.equip");

    /** The smart disc, thrown. */
    public static final BLibHolder<SoundEvent> SMART_DISC_THROW = create("smart_disc.throw");

    /**
     * The disc in flight.
     * <p>
     * ⚠ LOOPED FROM THE ENTITY, client side — see SmartDiscSoundInstance. A server playSound would fire once and then
     * have no way to stop when the disc comes home or is caught.
     */
    public static final BLibHolder<SoundEvent> SMART_DISC_LOOP = create("smart_disc.loop");

    /** The disc coming home. */
    public static final BLibHolder<SoundEvent> SMART_DISC_RETURN = create("smart_disc.return");

    /** The lash extending, on the click. */
    public static final BLibHolder<SoundEvent> WHIP_ATTACK = create("whip.attack");

    /** The tip cracking at full extension, with the sparks. */
    public static final BLibHolder<SoundEvent> WHIP_CRACK = create("whip.crack");

    /**
     * The whip coiling in the hand.
     * <p>
     * ⚠ TWO MOMENTS, ONE SOUND. [stated] "this is when you equip the whip but also it plays when the whip attack ends
     * as it its 'rewrapping' in your hand."
     */
    public static final BLibHolder<SoundEvent> WHIP_EQUIP = create("whip.equip");

    /** The chain winding back in — played when the grapple ENDS, however long it lasted. */
    public static final BLibHolder<SoundEvent> GRAPPLE_CHAIN_RETRACT = create("grapple.chain_retract");

    /** The gauntlet fires a veritanium dart. */
    public static final BLibHolder<SoundEvent> GAUNTLET_DART = create("gauntlet.dart");

    /** The gauntlet fires a fire pellet. */
    public static final BLibHolder<SoundEvent> GAUNTLET_PELLET = create("gauntlet.pellet");

    /** The gauntlet launches a net. */
    public static final BLibHolder<SoundEvent> GAUNTLET_NET = create("gauntlet.net");

    /** Trigger pulled with no ammunition of the selected kind. */
    public static final BLibHolder<SoundEvent> GAUNTLET_EMPTY = create("gauntlet.empty");

    /** A net closing on a mob. */
    public static final BLibHolder<SoundEvent> PROJECTILE_NET_CATCH = create("projectile.net_catch");

    /** A fire pellet striking something. */
    public static final BLibHolder<SoundEvent> PROJECTILE_PELLET_HIT = create("projectile.pellet_hit");

    /** All four arming panels reach 9. */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_ARMED = create("gauntlet.destruct_armed");

    /** Arming cancelled from the worn GUI, and a successful disarm of a placed one. */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_ARMED_CANCEL = create("gauntlet.destruct_armed_cancel");

    /** A panel click on the disarm screen. */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_DISARM_BUTTON = create("gauntlet.destruct_disarm_button");

    /** A disarm panel that just matched the code above it. */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_DISARM_CONFIRM = create("gauntlet.destruct_disarm_button_confirm");

    /** Cloak engaging. */
    public static final BLibHolder<SoundEvent> CLOAK_ON = create("cloak.cloak_on");

    /** Cloak dropping, whether switched off, broken by damage, or shorted out by water. */
    public static final BLibHolder<SoundEvent> CLOAK_OFF = create("cloak.cloak_off");

    /** Looping electrical crackle while water is shorting an engaged cloak out. */
    public static final BLibHolder<SoundEvent> CLOAK_WET_LOOP = create("cloak.cloak_wet_loop");

    /** The caster swinging up off the back. */
    public static final BLibHolder<SoundEvent> CASTER_DEPLOY = create("caster.caster_deploy");

    /** The two-second charge whine, started once as the charge begins. */
    public static final BLibHolder<SoundEvent> CASTER_CHARGE = create("caster.caster_charge");

    /** The bolt leaving. */
    public static final BLibHolder<SoundEvent> CASTER_FIRE = create("caster.caster_fire");

    // [stated] "there will be new sounds for it use the place holders for now" — the hand caster's own events, pointed
    // in sounds.json at the plasma caster's sounds until the real ones arrive. Only sounds.json changes then.
    public static final BLibHolder<SoundEvent> HAND_CASTER_CHARGE = create("hand_caster.charge");

    public static final BLibHolder<SoundEvent> HAND_CASTER_FIRE = create("hand_caster.fire");

    public static final BLibHolder<SoundEvent> HAND_CASTER_RELOAD = create("hand_caster.reload");

    /** The unmasked roar. Plays once when the mask breaks. */
    public static final BLibHolder<SoundEvent> YAUTJA_ROAR = create("yautja.roar");

    /** The shoulder caster folding away — his PREDATOR_LASER_INITIATION reversed (CASTER_DEPLOY is it forwards). */
    public static final BLibHolder<SoundEvent> CASTER_RETRACT = create("caster.caster_retract");

    /**
     * The caster holding its charge: his composite of PREDATOR_LASER_LOOP_2 (reversed), the static discharge and the
     * squeaky electric bursts, seamless at 4.2 s. CASTER_CHARGE is this same loop's first 2 s faded in. The hand caster
     * plays it client side (CasterChargeLoopSounds), faded in, for as long as the button is held.
     */
    public static final BLibHolder<SoundEvent> CASTER_CHARGE_LOOP = create("caster.charge_loop");

    /**
     * The smoke bomb a Hunter drops at its feet when it escapes near death, just before it warps away. His Up-in-Smoke
     * clip, mono, tail trimmed to 1.2 s.
     */
    public static final BLibHolder<SoundEvent> YAUTJA_SMOKE_BOMB = create("yautja.smoke_bomb");

    /**
     * The countdown for a 3-second fuse: the trip mine once armed, and the sticky grenade once stuck. His Beep_04,
     * time-stretched (pitch kept) so it lasts EXACTLY 3.0 s — the beeps quicken and the final long tone ends on the
     * detonation. ⚠ If either fuse is retuned away from 60 ticks, this file no longer lines up.
     */
    public static final BLibHolder<SoundEvent> EXPLOSIVE_TIMER_BEEP = create("explosive.timer_beep");

    /**
     * The predator's clicking: heard by the hunted player alone during phase 1 and as they fall asleep under the
     * Hunter's Moon, and the yautja's ambient sound while it stalks (cloaked or with a target). Three of his recordings
     * (pred-clicks-3, PREDDY-CLICKS-2, the ASMR clip), trimmed, mono, levelled to match.
     */
    public static final BLibHolder<SoundEvent> YAUTJA_CLICK = create("yautja.click");

    /** The yautja's ambient sound when it is not stalking. His three IDLE recordings, trimmed, mono, levelled. */
    public static final BLibHolder<SoundEvent> YAUTJA_IDLE = create("yautja.idle");

    /** Climbing exertion — vaults, mantles, standing hops. His vo_PR_exert_jump01/02. See YautjaSounds. */
    public static final BLibHolder<SoundEvent> YAUTJA_CLIMB = create("yautja.climb");

    /** Long-jump exertion — the running leap and the fall-back leap. His vo_PR_exert_lng02/03. */
    public static final BLibHolder<SoundEvent> YAUTJA_LONG_JUMP = create("yautja.long_jump");

    /**
     * [stated] his "jump" recording, "for the charge". ⚠ Registered and ready but NOT played yet — there is no charge
     * move on the yautja to hang it on.
     */
    public static final BLibHolder<SoundEvent> YAUTJA_CHARGE = create("yautja.charge");

    /**
     * A non-Hunter yautja's death cry. His DEATH_0, DEATH_13, PAIN2 and PAIN_0, each at three pitches and lowered in
     * sounds.json — twelve variants from four files. A Hunter has no cry: its armed gauntlet's self-destruct laugh is
     * its death.
     */
    public static final BLibHolder<SoundEvent> YAUTJA_DEATH = create("yautja.death");

    /**
     * Taking damage. His four prd_pain_scream recordings, QUICK_PAIN, and PAIN2 / PAIN_0 / PAIN_1 / PAIN_87, at random
     * — nine in all. (PAIN2 and PAIN_0 double as death cries.)
     */
    public static final BLibHolder<SoundEvent> YAUTJA_HURT = create("yautja.hurt");

    /** A weapon swing grunt — his attack_pred, ATTACK_0 and ATTACK_1. See YautjaSounds.attack. */
    public static final BLibHolder<SoundEvent> YAUTJA_ATTACK = create("yautja.attack");

    /** The battleaxe slam and a combo's finishing blow — his PRED_YELL_SHORT. See YautjaSounds.heavyAttack. */
    public static final BLibHolder<SoundEvent> YAUTJA_HEAVY_ATTACK = create("yautja.heavy_attack");

    /** The taunting laugh — four of his Pred_Taunt recordings, picked at random. See YautjaTaunts. */
    public static final BLibHolder<SoundEvent> YAUTJA_TAUNT = create("yautja.taunt");

    /**
     * His SELF_DESTRUCT_LAUGH, cut to exactly the 20-second countdown, the laugh peaking just before the blast. Played
     * CLIENT side (DestructLaughSounds) so it can follow the gauntlet and fade out when it is disarmed. Streamed.
     */
    public static final BLibHolder<SoundEvent> GAUNTLET_DESTRUCT_LAUGH = create("gauntlet.destruct_laugh");

    /**
     * The skinned corpse. OPEN plays when it is opened (his Flesh_rip_07, trimmed to the rip itself) — [stated] "for
     * digging through the corpse inventory". HIT repeats while it is being mined and BREAK is the louder one when it
     * finally gives (both cut from his FleshSlash) — [stated] "make that the sound while mining it repeated and then a
     * louder one for the actual break". BREAK also plays when a corpse is placed. See SkinnedCorpseSoundType.
     */
    public static final BLibHolder<SoundEvent> CORPSE_OPEN = create("corpse.open");

    public static final BLibHolder<SoundEvent> CORPSE_HIT = create("corpse.hit");

    public static final BLibHolder<SoundEvent> CORPSE_BREAK = create("corpse.break");

    private static BLibHolder<SoundEvent> create(String path) {
        return REGISTRY.createHolder(path, () -> SoundEvent.createVariableRangeEvent(PredatorResources.location(path)));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
