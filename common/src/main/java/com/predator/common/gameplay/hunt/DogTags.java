package com.predator.common.gameplay.hunt;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

/**
 * Stamped dog tags for the dead a Hunter leaves behind.
 * <h2>⚠ The item belongs to avp_human, and is looked up BY NAME</h2> {@code avp_human:dog_tag} is a plain item there
 * (its marines already carry one). avp_predator has no compile dependency on avp_human, so the item is resolved from
 * the registry at runtime: with avp_human installed the tag exists, without it {@link #create} returns empty and the
 * caller simply leaves the tags out. [stated] "dog tag item is in avp human".
 * <p>
 * The name and rank live on the stack itself (custom name + lore), so nothing on avp_human's side has to know about
 * them — the tooltip reads "Cpl. J. Reyes" / "Corporal, USCM".
 */
public final class DogTags {

    public static final ResourceLocation DOG_TAG_ID = ResourceLocation.fromNamespaceAndPath("avp_human", "dog_tag");

    /** [stated] "a random name and military rank up to captain". Abbreviation, full title. */
    private static final String[][] RANKS = {
        { "Pvt", "Private" },
        { "PFC", "Private First Class" },
        { "LCpl", "Lance Corporal" },
        { "Cpl", "Corporal" },
        { "Sgt", "Sergeant" },
        { "SSgt", "Staff Sergeant" },
        { "Lt", "Lieutenant" },
        { "Capt", "Captain" }
    };

    private static final String[] SURNAMES = {
        "Reyes",
        "Hudson",
        "Vasquez",
        "Drake",
        "Frost",
        "Crowe",
        "Dietrich",
        "Ferro",
        "Spunkmeyer",
        "Wierzbowski",
        "Apone",
        "Gorman",
        "Hicks",
        "Kowalski",
        "Okafor",
        "Lindqvist",
        "Tanaka",
        "Moreau",
        "Brennan",
        "Castillo",
        "Nakamura",
        "Petrov",
        "Abara",
        "Holt",
        "Sato",
        "Whitfield",
        "Mbeki",
        "Larsen",
        "Quinn",
        "Varga"
    };

    private static final String INITIALS = "ABCDEFGHJKLMNPRSTVW";

    private DogTags() {}

    /**
     * {@return a freshly stamped dog tag, or empty when avp_human is not installed}
     */
    public static Optional<ItemStack> create(RandomSource random) {
        var item = BuiltInRegistries.ITEM.getOptional(DOG_TAG_ID);

        if (item.isEmpty()) {
            return Optional.empty();
        }

        var rank = RANKS[random.nextInt(RANKS.length)];
        var surname = SURNAMES[random.nextInt(SURNAMES.length)];
        var initial = INITIALS.charAt(random.nextInt(INITIALS.length()));

        var stack = new ItemStack(item.get());

        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal(rank[0] + ". " + initial + ". " + surname).withStyle(style -> style.withItalic(false))
        );
        stack.set(
            DataComponents.LORE,
            new ItemLore(
                List.of(
                    Component.literal(rank[1] + ", USCM")
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))
                )
            )
        );

        return Optional.of(stack);
    }
}
