package com.predator.client.cloak;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The cloak's render types: {@code entityNoOutline} without the translucency sort.
 * <p>
 * ⚠ Oct 9 — WHY. Vanilla sorts every translucent batch back-to-front when it is drawn. With a group of cloaked yautja
 * on screen that sort was 26-42% of the render thread, and batching all cloaked entities together (see the
 * cross-entity note in {@link PredatorCloakRendering}) did not shrink it: the cost scales with the number of quads, not
 * the number of batches. Each cloak pass is a single faint layer (5-14% alpha) drawn with COLOR_WRITE only, so the
 * order its own quads blend in is not something the eye can read at that level - sorting them buys nothing visible.
 * <p>
 * Identical state to vanilla's {@code entityNoOutline}: same shader, translucent blending, no culling, lightmap,
 * overlay, colour-only writes. Only {@code sortOnUpload} differs. Flip {@link #SORT_CLOAK_PASSES} to put the sort back
 * for a side-by-side comparison.
 * <p>
 * Access: extends {@link RenderType} only to reach the protected state shards; it is never instantiated.
 * {@code RenderType.create} is made public by NeoForge's access transformer; Fabric needs the access-widener entries
 * listed in the hand-off notes.
 */
public final class PredatorCloakRenderTypes extends RenderType {

    /** {@code true} restores vanilla's {@code entityNoOutline} (sorted) for every cloak pass. */
    private static final boolean SORT_CLOAK_PASSES = false;

    private static final Map<ResourceLocation, RenderType> UNSORTED_NO_OUTLINE = new HashMap<>();

    private PredatorCloakRenderTypes(
        String name,
        VertexFormat format,
        VertexFormat.Mode mode,
        int bufferSize,
        boolean affectsCrumbling,
        boolean sortOnUpload,
        Runnable setupState,
        Runnable clearState
    ) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException("holder for render type factories");
    }

    /**
     * {@code entityNoOutline(texture)} without the upload sort. Memoized per texture, so the result can be compared by
     * identity and reused across entities (which is what lets the batched replay share one batch per texture).
     */
    public static RenderType noOutline(ResourceLocation texture) {
        if (SORT_CLOAK_PASSES) {
            return RenderType.entityNoOutline(texture);
        }

        // Render thread only.
        return UNSORTED_NO_OUTLINE.computeIfAbsent(texture, PredatorCloakRenderTypes::createUnsortedNoOutline);
    }

    private static RenderType createUnsortedNoOutline(ResourceLocation texture) {
        var state = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_NO_OUTLINE_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false);

        return RenderType.create(
            "predator_cloak_no_outline",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            state
        );
    }
}
