package com.predator.fabric.data.lang.en_us.provider;

import com.blib.api.common.advancement.v1.BLibAdvancement;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

public class EnUsAdvancementProvider {

    /**
     * The yautja honor tab. ⚠ The advancement JSONs themselves are hand-written under resources/data (this mod has no
     * advancement datagen), so only their titles and descriptions come from here.
     */
    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        builder.add("advancements.avp_predator.hunt.root.title", "Yautja Honor");
        builder.add("advancements.avp_predator.hunt.root.description", "Prove yourself worthy prey");
        builder.add("advancements.avp_predator.hunt.blooded.title", "Blooded");
        builder.add("advancements.avp_predator.hunt.blooded.description", "Kill a yautja");
        builder.add("advancements.avp_predator.hunt.kill_elder_guardian.title", "Lord of the Deep");
        builder.add("advancements.avp_predator.hunt.kill_elder_guardian.description", "Kill an Elder Guardian");
        builder.add("advancements.avp_predator.hunt.kill_wither.title", "Withered");
        builder.add("advancements.avp_predator.hunt.kill_wither.description", "Kill the Wither");
        builder.add("advancements.avp_predator.hunt.kill_warden.title", "Silence in the Deep");
        builder.add("advancements.avp_predator.hunt.kill_warden.description", "Kill the Warden");
        builder.add("advancements.avp_predator.hunt.live_fire.title", "Live Fire");
        builder.add("advancements.avp_predator.hunt.live_fire.description", "Hit a hostile with a gun");
        builder.add("advancements.avp_predator.hunt.wy_ape_set.title", "Company Muscle");
        builder.add("advancements.avp_predator.hunt.wy_ape_set.description", "Wear a full set of WY Ape armor");
        builder.add("advancements.avp_predator.hunt.veritanium_set.title", "Wearing the Hunt");
        builder.add("advancements.avp_predator.hunt.veritanium_set.description", "Obtain a full set of veritanium armor");
        builder.add("advancements.avp_predator.hunt.survive_the_hunt.title", "Survivor");
        builder.add("advancements.avp_predator.hunt.survive_the_hunt.description", "Live through all three nights of a hunt");
        builder.add("advancements.avp_predator.hunt.hunters_trophy.title", "Hunter's Trophy");
        builder.add("advancements.avp_predator.hunt.hunters_trophy.description", "Kill a Hunter");
    };

    private static void addAdvancement(
        FabricLanguageProvider.TranslationBuilder builder,
        BLibAdvancement advancementAccess,
        String title,
        String description
    ) {
        builder.add(advancementAccess.titleTranslationKey(), title);
        builder.add(advancementAccess.descriptionTranslationKey(), description);
    }
}
