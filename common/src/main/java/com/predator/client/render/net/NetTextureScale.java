package com.predator.client.render.net;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much to scale the net's UVs so its mesh looks the same on every mob.
 * <h2>The problem this solves</h2> The overlay re-renders the mob's own model, so it samples the net texture through
 * THAT MOB'S UV coordinates. The net is therefore stretched across whatever sheet the mob uses, and the visible cell
 * size works out at {@code 512 / sheetSize} cells per block. A 64px mob wears an 8-cell mesh, a 256px mob wears a
 * 2-cell one — the same net looking four times coarser on the bigger creature.
 * <p>
 * ⚠ Vanilla is a consistent 16 texels per block on every mob measured — player, zombie, iron golem, ravager, ender
 * dragon — so SHEET SIZE is the only variable. That is what makes a single correction factor possible at all; if texel
 * density varied too, no per-mob scalar could fix it.
 * <h2>The correction</h2> Scaling the UVs by {@code sheetSize / BASELINE} makes every mob sample the same number of
 * cells per block. A 256px dragon reads its UVs four times larger and so tiles the net four times more often, landing
 * at the same physical mesh as a player.
 * <h2>⚠ Reads the PNG header only, and caches</h2> Texture size is not exposed by the renderer, so it has to come from
 * the resource itself. Decoding a whole image per frame would be absurd; this reads each texture once and remembers it.
 * A miss falls back to the baseline, which means an unreadable texture looks slightly off rather than crashing the
 * render thread.
 */
public final class NetTextureScale {

    /**
     * The sheet size everything is normalised to.
     * <p>
     * ⚠ 64 because that is what the majority of mobs use, so most of the game needs no correction at all and the common
     * path multiplies by exactly 1.
     */
    private static final float BASELINE = 64.0F;

    private static final Map<ResourceLocation, Float> CACHE = new ConcurrentHashMap<>();

    private NetTextureScale() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return the UV multiplier that makes this mob's net match everything else's}
     * <p>
     * ⚠ Takes a supplier rather than a location because {@code getTextureLocation} is protected on the render layer and
     * can only be reached from inside it.
     */
    public static <T> float forEntity(T entity, java.util.function.Function<T, ResourceLocation> texture) {
        var location = texture.apply(entity);

        return location == null ? 1.0F : CACHE.computeIfAbsent(location, NetTextureScale::measure);
    }

    private static float measure(ResourceLocation texture) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);

        if (resource.isEmpty()) {
            return 1.0F;
        }

        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            return image.getWidth() / BASELINE;
        } catch (Exception exception) {
            // ⚠ Swallowed deliberately. A texture that will not decode is a cosmetic problem for one mob; throwing
            // here would take down the render thread for every entity in the world.
            return 1.0F;
        }
    }

    /** ⚠ Must be called on resource reload — a resource pack can swap a mob's texture for one of a different size. */
    public static void clear() {
        CACHE.clear();
    }
}
