package com.predator.fabric.data.lang.en_us.provider;

import com.predator.common.registry.init.PredatorSoundEvents;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsSoundEventProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addSound(builder, PredatorSoundEvents.CASTER_DEPLOY, "Plasma caster deploys");

        addSound(builder, PredatorSoundEvents.CASTER_CHARGE, "Plasma caster charges");

        addSound(builder, PredatorSoundEvents.CASTER_FIRE, "Plasma caster fires");

        addSound(builder, PredatorSoundEvents.CASTER_RETRACT, "Plasma caster retracts");

        addSound(builder, PredatorSoundEvents.CASTER_CHARGE_LOOP, "Plasma caster hums");

        addSound(builder, PredatorSoundEvents.YAUTJA_ROAR, "Predator roars");

        addSound(builder, PredatorSoundEvents.YAUTJA_SMOKE_BOMB, "Smoke bomb bursts");

        addSound(builder, PredatorSoundEvents.EXPLOSIVE_TIMER_BEEP, "Explosive beeps");

        addSound(builder, PredatorSoundEvents.YAUTJA_CLICK, "Something clicks");

        addSound(builder, PredatorSoundEvents.YAUTJA_IDLE, "Predator rattles");

        addSound(builder, PredatorSoundEvents.YAUTJA_CLIMB, "Predator grunts");

        addSound(builder, PredatorSoundEvents.YAUTJA_LONG_JUMP, "Predator leaps");

        addSound(builder, PredatorSoundEvents.YAUTJA_CHARGE, "Predator charges");

        addSound(builder, PredatorSoundEvents.YAUTJA_TAUNT, "Predator laughs");

        addSound(builder, PredatorSoundEvents.YAUTJA_DEATH, "Predator dies");

        addSound(builder, PredatorSoundEvents.YAUTJA_HURT, "Predator hurts");

        addSound(builder, PredatorSoundEvents.YAUTJA_ATTACK, "Predator snarls");

        addSound(builder, PredatorSoundEvents.YAUTJA_HEAVY_ATTACK, "Predator roars");

        addSound(builder, PredatorSoundEvents.GAUNTLET_DESTRUCT_LAUGH, "Self-destruct laughs");

        addSound(builder, PredatorSoundEvents.ITEM_ARMOR_EQUIP_VERITANIUM, "Veritanium armor clanks");

        addSound(builder, PredatorSoundEvents.JUKEBOX_SOUNDS_PREDATOR_MUSIC_1, "Hunter plays");

        addSound(builder, PredatorSoundEvents.VISION_SWAP, "Vision mode shifts");

        addSound(builder, PredatorSoundEvents.CORPSE_OPEN, "Corpse squelches open");

        addSound(builder, PredatorSoundEvents.CORPSE_HIT, "Corpse is cut");

        addSound(builder, PredatorSoundEvents.CORPSE_BREAK, "Corpse is torn apart");

        // ⚠ The hand caster's events point at the shoulder caster's recordings, and a pointing event shows ITS OWN
        // subtitle, not the one it points at — without these the hand caster played with no subtitle at all.
        addSound(builder, PredatorSoundEvents.HAND_CASTER_CHARGE, "Hand caster charges");

        addSound(builder, PredatorSoundEvents.HAND_CASTER_FIRE, "Hand caster fires");

        addSound(builder, PredatorSoundEvents.HAND_CASTER_RELOAD, "Hand caster reloads");
    };

    private static void addSound(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<SoundEvent> soundEventSupplier,
        String value
    ) {
        addSound(translationBuilder, soundEventSupplier.get(), value);
    }

    private static void addSound(FabricLanguageProvider.TranslationBuilder translationBuilder, SoundEvent soundEvent, String value) {
        translationBuilder.add("subtitles." + soundEvent.getLocation().getPath(), value);
    }
}
