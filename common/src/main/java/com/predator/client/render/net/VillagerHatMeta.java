package com.predator.client.render.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerDataHolder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "does this villager actually have a hat?" the way vanilla answers it for itself.
 * <p>
 * A villager's {@code hat} / {@code hat_rim} parts are geometry on EVERY villager; whether anything shows is decided by
 * the textures. {@code VillagerProfessionLayer} reads a {@code villager} section from each texture's {@code .mcmeta}
 * ({@code "hat": "none" | "partial" | "full"}) for the biome TYPE texture and the PROFESSION texture. If either says
 * there is a hat, the hat has pixels and the net should cover it; if both say none (an unemployed plains villager), the
 * hat is invisible and the net must skip it — [stated] "detect if a villager is a farmer or not so you can turn
 * covering that hat layer on or off."
 * <p>
 * Texture paths follow vanilla's own convention:
 * {@code textures/entity/<villager|zombie_villager>/<type|profession>/<key>.png}. Results are cached per texture; the
 * cache is cleared on resource reload beside {@link NetTextureScale}.
 */
public final class VillagerHatMeta {

    private static final Map<ResourceLocation, VillagerMetaDataSection.Hat> CACHE = new ConcurrentHashMap<>();

    private VillagerHatMeta() {
        throw new UnsupportedOperationException();
    }

    /** {@return whether this entity is a villager-type mob wearing a hat that has pixels} Non-villagers: false. */
    public static boolean hasHat(Entity entity) {
        if (!(entity instanceof VillagerDataHolder holder)) {
            return false;
        }

        var data = holder.getVillagerData();
        var folder = entity instanceof ZombieVillager ? "zombie_villager" : "villager";
        var type = hat(folder, "type", BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType()));
        var profession = hat(folder, "profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()));

        return type != VillagerMetaDataSection.Hat.NONE || profession != VillagerMetaDataSection.Hat.NONE;
    }

    private static VillagerMetaDataSection.Hat hat(String folder, String kind, ResourceLocation key) {
        var texture = ResourceLocation.fromNamespaceAndPath(
            key.getNamespace(),
            "textures/entity/" + folder + "/" + kind + "/" + key.getPath() + ".png"
        );

        return CACHE.computeIfAbsent(texture, VillagerHatMeta::read);
    }

    private static VillagerMetaDataSection.Hat read(ResourceLocation texture) {
        try {
            return Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .flatMap(resource -> {
                    try {
                        return resource.metadata().getSection(VillagerMetaDataSection.SERIALIZER);
                    } catch (Exception exception) {
                        return java.util.Optional.empty();
                    }
                })
                .map(VillagerMetaDataSection::getHat)
                .orElse(VillagerMetaDataSection.Hat.NONE);
        } catch (Exception exception) {
            // ⚠ Swallowed deliberately: a missing or unreadable texture is one mob's cosmetic problem, not a crash.
            return VillagerMetaDataSection.Hat.NONE;
        }
    }

    public static void clear() {
        CACHE.clear();
    }
}
