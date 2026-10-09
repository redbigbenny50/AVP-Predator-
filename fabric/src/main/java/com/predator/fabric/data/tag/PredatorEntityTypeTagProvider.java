package com.predator.fabric.data.tag;

import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public class PredatorEntityTypeTagProvider extends FabricTagProvider.EntityTypeTagProvider {

    public PredatorEntityTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        // ⚠ ESCAPE TIERS. Vanilla mobs placed by hand; avp_alien's castes come from ITS side, where the strain tags
        // live. Anything untagged falls back to its bounding box (see NetEscape), so a modded mob is never treated as
        // queen-sized by default.
        getOrCreateTagBuilder(PredatorEntityTypeTags.NET_ESCAPE_MEDIUM)
            .add(
                EntityType.ZOMBIE,
                EntityType.SKELETON,
                EntityType.HUSK,
                EntityType.STRAY,
                EntityType.DROWNED,
                EntityType.WITHER_SKELETON,
                EntityType.PILLAGER,
                EntityType.VINDICATOR,
                EntityType.WITCH,
                EntityType.ZOMBIE_VILLAGER,
                EntityType.CREEPER,
                EntityType.ENDERMAN,
                EntityType.PIGLIN,
                EntityType.ZOMBIFIED_PIGLIN,
                EntityType.PIGLIN_BRUTE
            )
            // Villagers Reborn (slimpatch) variants of the vanilla types above.
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "pillager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "vindicator"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "human_zombie_villager"));

        getOrCreateTagBuilder(PredatorEntityTypeTags.NET_ESCAPE_MEDIUM_LARGE)
            .add(
                EntityType.SPIDER,
                EntityType.CAVE_SPIDER,
                EntityType.POLAR_BEAR,
                EntityType.HOGLIN,
                EntityType.ZOGLIN,
                EntityType.EVOKER,
                EntityType.ILLUSIONER
            )
            // Villagers Reborn (slimpatch) evoker.
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "evoker"));

        getOrCreateTagBuilder(PredatorEntityTypeTags.NET_ESCAPE_LARGE)
            .add(EntityType.RAVAGER, EntityType.IRON_GOLEM);

        getOrCreateTagBuilder(PredatorEntityTypeTags.NET_ESCAPE_HUGE)
            .add(EntityType.WARDEN, EntityType.ENDER_DRAGON, EntityType.WITHER);

        addPredators();
        addCloakImmune();
        addNetImmune();
        addSkinnable();
        addTauntWorthyKills();
        addThermalVisible();
        addThermalHot();
        addEmVisible();
        addHeatTiers();
        addPredatorHeat();
        addHatedEnemies();
        addSpaceHazardImmunities();
        addGigeresqueHosts();

        // Cross-mod tag contributions. NOT GATED ON isLoaded() ANY MORE - THAT GATE WAS THE BUG. The siblings are
        // blibModCompileOnly in fabric/build.gradle, so at datagen time they are on the compile classpath and NOT the
        // runtime one: the guard read false and the three files were simply never written on a clean run (the alien
        // provider had the identical fault, fixed Aug 17). These builders name the tags by string through
        // siblingEntityTagKey, so nothing from either sibling has to load, the JSON is byte-identical, and the files
        // generate every run. If a sibling is absent in play the tag is just an unused file.
        addRadiationResistant();
        addHatedByXenomorphs();
        addHosts();
    }

    private void addPredators() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.PREDATORS)
            .add(PredatorEntityTypes.YAUTJA.get());
    }

    /**
     * Observers the cloak does not work on.
     * <p>
     * The warden is added directly. Every xenomorph is added by referencing AVP-Alien's own aliens tag as an
     * <em>optional string-built tag</em> rather than importing {@code AlienEntityTypeTags} — the alien mod is
     * {@code modCompileOnly} here, and a hard class reference inside a datagen provider takes the whole provider down
     * silently, generating nothing while the build still reports success.
     */
    private void addCloakImmune() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.CLOAK_IMMUNE)
            .add(EntityType.WARDEN)
            .addOptionalTag(siblingEntityTag("avp_alien", "aliens"));
    }

    /**
     * What a capture net cannot hold.
     * <h2>⚠⚠ THIS TAG WAS DECLARED AND NEVER POPULATED ON THIS SIDE</h2> {@code PredatorEntityTypeTags.NET_IMMUNE}
     * existed and the net checked it, but nothing ever put anything in it — so every vanilla boss was netable.
     * avp_alien contributes its harbingers, queens and empresses from its own provider; the vanilla ones had no home
     * until now.
     * <p>
     * ⚠ The tag FAILS OPEN: an empty tag means everything is catchable, so a missing entry is silent. That is exactly
     * how this went unnoticed.
     * <p>
     * ⚠ A yautja is on the list too. Netting one is possible by design — his ruling — but a HUNTER should not be
     * trivially disabled by the thing it invented, and the dodge roll already gives it counterplay. Remove this line if
     * you want yautja-on-yautja netting to work without a dodge.
     */
    /**
     * Humanoids a yautja skins when it kills one unwatched. [stated] "a marine colonist other human like creatures".
     * avp_human's marine and colonist, and the Villagers Reborn (slimpatch) human variants, are OPTIONAL ids — a plain
     * string, never an import, so this provider never depends on those mods existing.
     */
    /** Kills worth a yautja's laugh: avp_alien's heavy castes (optional sibling tags) and the vanilla bosses. */
    private void addTauntWorthyKills() {
        var tag = getOrCreateTagBuilder(PredatorEntityTypeTags.TAUNT_WORTHY_KILLS)
            .add(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN, EntityType.ELDER_GUARDIAN, EntityType.RAVAGER);

        for (var caste : new String[] { "praetorians", "predaliens", "crushers", "queens", "empresses", "harbingers" }) {
            tag.addOptionalTag(siblingEntityTag("avp_alien", caste));
        }
    }

    private void addSkinnable() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.SKINNABLE)
            .add(
                EntityType.VILLAGER,
                EntityType.WANDERING_TRADER,
                EntityType.PILLAGER,
                EntityType.VINDICATOR,
                EntityType.EVOKER,
                EntityType.ILLUSIONER,
                EntityType.WITCH
            )
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "marine"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "colonist"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "male_villager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "female_villager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "human_trader"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "pillager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "vindicator"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "evoker"));
    }

    private void addNetImmune() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.NET_IMMUNE)
            .add(EntityType.WITHER)
            .add(EntityType.ENDER_DRAGON)
            .add(EntityType.WARDEN)
            .add(EntityType.ELDER_GUARDIAN)
            // ⚠ Not a mob you fight — netting a player's own vehicle or a map decoration is noise, not gameplay.
            .add(EntityType.ARMOR_STAND);
    }

    private static net.minecraft.resources.ResourceLocation siblingEntityTag(String namespace, String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** A sibling mod's entity-type tag as a key, so it can be built without that mod on the datagen classpath. */
    private static net.minecraft.tags.TagKey<EntityType<?>> siblingEntityTagKey(String namespace, String path) {
        return net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            siblingEntityTag(namespace, path)
        );
    }

    /**
     * Default thermal-vision visibility for vanilla mobs. Decided per-entity from a "would IR detect a meaningful heat
     * signature" lens:
     * <ul>
     * <li><b>Included:</b> warm-blooded mammals, birds, arthropods (have biological mass), undead (game convention),
     * nether mobs (often hot), most hostile biological mobs, players. Boss mobs (Wither, Ender Dragon, Warden) too.
     * Slime is gelatinous biological mass — included.</li>
     * <li><b>Excluded:</b> cold-blooded creatures (fish, axolotl, frog, turtle, squid), purely mechanical/inanimate
     * entities (iron golem, snow golem, armor stand, shulker, breeze).</li>
     * </ul>
     * Modders/datapacks override either way by re-declaring the tag.
     */
    private void addThermalVisible() {
        // ⚠⚠ BUILT FROM THE HEAT TIERS, NOT HAND-LISTED. These were two independent lists and they drifted: every
        // cold-blooded creature — squid, glow squid, cod, salmon, tropical fish, pufferfish, frog, tadpole, turtle,
        // axolotl — had a heat tier assigned and was MISSING from here, so it classified as background, never got
        // entity treatment, and rendered as a flat silhouette in world colour. The tier it had been given was never
        // used.
        //
        // Deriving one from the other makes that impossible: anything with a heat tier is thermally visible by
        // construction, and a datapack adding a mob to a tier gets visibility for free rather than having to know
        // about a second tag.
        getOrCreateTagBuilder(PredatorEntityTypeTags.THERMAL_VISIBLE)
            .addTag(PredatorEntityTypeTags.HEAT_WARM_BLOODED_LARGE)
            .addTag(PredatorEntityTypeTags.HEAT_WARM_BLOODED)
            .addTag(PredatorEntityTypeTags.HEAT_WARM_BLOODED_SMALL)
            .addTag(PredatorEntityTypeTags.HEAT_COLD_BLOODED)
            .addTag(PredatorEntityTypeTags.HEAT_COLD_BLOODED_AQUATIC)
            .addTag(PredatorEntityTypeTags.HEAT_BURNING)
            // The player is not in a tier tag — a datapack cannot sensibly remove the player from thermal — so it is
            // listed directly. Creepers and ghasts are here because they become hot on a state change (swelling,
            // charging) rather than by tier, and must be visible for that flare to be seen at all.
            .add(
                EntityType.PLAYER,
                EntityType.CREEPER,
                EntityType.GHAST
            );
    }

    /**
     * Default thermally-hot entities — they read as if fully lit + emissive in IR regardless of ambient lighting.
     * Includes fire creatures (blaze, magma cube), lava-dwellers (strider), and projectile-fire mobs (ghast). Modders
     * can override or extend.
     */
    private void addThermalHot() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.THERMAL_HOT)
            .add(
                EntityType.BLAZE,
                EntityType.GHAST,
                EntityType.MAGMA_CUBE,
                EntityType.STRIDER
            );
    }

    /**
     * Default electromagnetic-vision visibility. Targets end-realm beings — endermen, ender dragon, endermites.
     * Modders/datapacks override or extend by re-declaring the tag.
     */
    /**
     * Thermal heat tiers. The rule is biology and consequence, not hostility: an illager is as warm as a villager
     * because it is one, and a zombie is cold because it is dead.
     * <p>
     * ⚠ Anything absent reads at ambient, deliberately — the undead, arthropods, golems, slimes and xenomorphs all
     * belong nowhere in here. Arthropods in particular are meant to be invisible to thermal; they show on
     * electromagnetic instead, which is the whole reason that mode exists.
     */
    private void addHeatTiers() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_WARM_BLOODED_LARGE)
            .add(
                // People. The illager family are villagers by another name — same species, same body heat.
                EntityType.VILLAGER,
                EntityType.WANDERING_TRADER,
                EntityType.PILLAGER,
                EntityType.VINDICATOR,
                EntityType.EVOKER,
                EntityType.ILLUSIONER,
                EntityType.WITCH,
                // Large mammals
                EntityType.COW,
                EntityType.MOOSHROOM,
                EntityType.PIG,
                EntityType.SHEEP,
                EntityType.GOAT,
                EntityType.HORSE,
                EntityType.DONKEY,
                EntityType.MULE,
                EntityType.LLAMA,
                EntityType.TRADER_LLAMA,
                EntityType.CAMEL,
                EntityType.POLAR_BEAR,
                EntityType.PANDA,
                EntityType.HOGLIN,
                EntityType.RAVAGER,
                // A mammal, not a fish.
                EntityType.DOLPHIN,
                // A beast rather than a construct, so it reads as warm as anything else alive.
                EntityType.ENDER_DRAGON
            )
            // Villagers Reborn (slimpatch): human-shaped and warm, like the vanilla villagers and illagers above.
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "evoker"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "female_villager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "human_trader"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "male_villager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "pillager"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "vindicator"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("slimpatch", "wandering_trader"));

        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_WARM_BLOODED)
            .add(
                EntityType.WOLF,
                EntityType.FOX,
                EntityType.CAT,
                EntityType.OCELOT,
                EntityType.PIGLIN,
                EntityType.PIGLIN_BRUTE,
                EntityType.CHICKEN,
                EntityType.PARROT,
                EntityType.RABBIT,
                EntityType.SNIFFER,
                EntityType.ARMADILLO,
                EntityType.STRIDER
            )
            // Oct 7 - [stated] Gigeresque's popper, hammerpede, stalker, neoburster, neomorph_adolescent and neomorph
            // "should show on thermal" - flesh, not xenomorph chitin. Its own group tags, optional.
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresquemutants"))
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresqueneos"));

        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_WARM_BLOODED_SMALL)
            .add(EntityType.BAT);

        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_COLD_BLOODED)
            .add(
                // Amphibians and reptiles: findable, but plainly not prey-warm.
                EntityType.FROG,
                EntityType.TADPOLE,
                EntityType.TURTLE,
                EntityType.AXOLOTL
            );

        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_COLD_BLOODED_AQUATIC)
            .add(
                EntityType.COD,
                EntityType.SALMON,
                EntityType.TROPICAL_FISH,
                EntityType.PUFFERFISH,
                EntityType.SQUID,
                EntityType.GLOW_SQUID,
                // Cold like the water around them; the armour is what puts them on electromagnetic instead.
                EntityType.GUARDIAN,
                EntityType.ELDER_GUARDIAN
            );

        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_BURNING)
            .add(
                // Made of fire. A ghast is NOT here — it only burns while charging, which is decided in code.
                EntityType.BLAZE,
                EntityType.MAGMA_CUBE,
                // Ordnance in flight. Primed TNT only exists once the fuse is lit, so it is always hot.
                EntityType.FIREBALL,
                EntityType.SMALL_FIREBALL,
                EntityType.DRAGON_FIREBALL,
                EntityType.TNT
            );
    }

    private void addPredatorHeat() {
        // A yautja runs hot: they come from a hot world and the mesh is built for cold ones. Cloaking hides them, not
        // their body temperature — which makes mud a real counter to another hunter's thermal.
        getOrCreateTagBuilder(PredatorEntityTypeTags.HEAT_WARM_BLOODED_LARGE)
            .add(PredatorEntityTypes.YAUTJA.get());
    }

    private void addEmVisible() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.EM_VISIBLE)
            .add(
                // Arthropods — small but biological
                EntityType.BEE,
                EntityType.CAVE_SPIDER,
                EntityType.SILVERFISH,
                EntityType.SPIDER,
                // Spirits and small flyers. A ghast is a ghost by every reading except the fireball it is holding, and
                // on thermal it only appears while charging one — so this is where it belongs the rest of the time.
                EntityType.ALLAY,
                EntityType.VEX,
                EntityType.GHAST,
                // End-realm creatures
                EntityType.ENDERMAN,
                EntityType.ENDER_DRAGON,
                EntityType.ENDERMITE,
                // Sculk. The warden has no metabolism to read on thermal — sculk runs cold, which is why a frog raised
                // in a deep dark hatches the cold variant — so electromagnetic is the only way to see it coming.
                EntityType.WARDEN,
                // Armoured and half-machine in lore. They read faintly on thermal like the water around them, and
                // plainly here.
                EntityType.GUARDIAN,
                EntityType.ELDER_GUARDIAN,
                // Constructs and the things they throw. None has a thermal signature worth the name, so this is the
                // mode that finds them.
                EntityType.BREEZE,
                EntityType.SHULKER,
                EntityType.SHULKER_BULLET,
                EntityType.SNOWBALL,
                EntityType.WIND_CHARGE,
                EntityType.BREEZE_WIND_CHARGE
            )
            // Oct 7 - Gigeresque's xenomorphs. [stated] "giger creatures shouldnt show up on thermal they should show
            // on
            // em ... all the rest are technically xenomorphs so they should show as em" - every Gigeresque creature
            // except the neomorph and mutant lines (those are warm, see HEAT_WARM_BLOODED). Its own group tags where it
            // has them, plus the two ids its tags leave out. Optional, so nothing is needed without Gigeresque.
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresqueclassic"))
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresqueaqua"))
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresquerunners"))
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresquetemplebeasts"))
            .addOptionalTag(siblingEntityTag("gigeresque", "gigeresquemisc"))
            .addOptional(siblingEntityTag("gigeresque", "rom_alien"))
            .addOptional(siblingEntityTag("gigeresque", "aqua_egg"));
    }

    /**
     * The honor code's extension point ({@code YautjaPredicates}): an unarmed mob that is not hated is only ever fought
     * in self-defence, so a modded hostile the yautja should HUNT has to be named here. Ad Astra's hostile fauna, Sep
     * 22 - the same set the marines hate, minus the glacian ram, which is an animal.
     */
    private void addHatedEnemies() {
        getOrCreateTagBuilder(PredatorEntityTypeTags.HATED_ENEMIES)
            // Ad Astra. Mirrors the community avp_ad_astra datapack's marine list.
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "star_crawler"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "zombified_mogler"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "zombified_pygro"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "sulfur_creeper"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "corrupted_lunarian"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "pygro"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "pygro_brute"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "martian_raptor"))
            .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", "mogler"));
    }

    /**
     * [stated] Sep 22: "predators with their mask on should be able to survive in space without oxygen, they can
     * survive in the other conditions even radiation. if a yautja loses its mask then it will suffocate."
     * <p>
     * Heat, cold, acid rain and radiation are unconditional and go here by tag (names read from the 1.16.26 Ad Astra
     * and 1.4.25 Stellaris jars). ⚠ OXYGEN IS DELIBERATELY NOT A TAG: a tag is per entity type and cannot see the mask,
     * so the yautja is left OUT of {@code ad_astra:lives_without_oxygen}, {@code can_survive_in_space} and
     * {@code stellaris:no_oxygen_needed}; the mask rule lives in {@code Yautja.isInvulnerableTo}, which refuses both
     * mods' oxygen damage while the mask is on. avp_human's radiation tag is already filled from PREDATORS in
     * addRadiationResistant.
     * <p>
     * Raw ResourceLocations because neither space mod is on the datagen classpath; a tag file for an absent mod is
     * inert until that mod is installed.
     */
    private void addSpaceHazardImmunities() {
        var ad_astra = new String[] { "can_survive_extreme_heat", "can_survive_extreme_cold", "can_survive_in_acid_rain" };

        for (var path : ad_astra) {
            getOrCreateTagBuilder(
                net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ad_astra", path)
                )
            ).addTag(PredatorEntityTypeTags.PREDATORS);
        }

        getOrCreateTagBuilder(
            net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ENTITY_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("stellaris", "radiations_invulnerable")
            )
        ).addTag(PredatorEntityTypeTags.PREDATORS);
    }

    /**
     * Oct 7 - Gigeresque compatibility: the yautja is a neomorph host ({@code gigeresque:neohost}) and a classic
     * facehugger host ({@code gigeresque:classicalienhost}), tag names read from Gigeresque 0.8.16. From the PREDATORS
     * tag, so a future yautja type joins automatically. Merges with Gigeresque's own list ("replace": false); inert
     * data when Gigeresque is not installed.
     */
    private void addGigeresqueHosts() {
        // Neomorph spores, and the classic facehugger.
        for (var path : new String[] { "neohost", "classicalienhost" }) {
            getOrCreateTagBuilder(
                net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gigeresque", path)
                )
            ).addTag(PredatorEntityTypeTags.PREDATORS);
        }
    }

    private void addRadiationResistant() {
        getOrCreateTagBuilder(siblingEntityTagKey("avp_human", "radiation_resistant"))
            .addTag(PredatorEntityTypeTags.PREDATORS);
    }

    private void addHatedByXenomorphs() {
        getOrCreateTagBuilder(siblingEntityTagKey("avp_alien", "hated_by_xenomorphs"))
            .addTag(PredatorEntityTypeTags.PREDATORS);
    }

    private void addHosts() {
        getOrCreateTagBuilder(siblingEntityTagKey("avp_alien", "hosts"))
            .addTag(PredatorEntityTypeTags.PREDATORS);
    }
}
