package com.predator.client.render.entity;

import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import com.predator.PredatorResources;
import com.predator.client.animation.entity.YautjaAnimator;
import com.predator.client.render.layer.YautjaArmorLayer;
import com.predator.client.render.layer.YautjaItemLayer;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaVariant;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

public class YautjaRenderer extends AzEntityRenderer<Yautja> {

    private static final String NAME = "yautja_jungle";

    private static final ResourceLocation MODEL = PredatorResources.entityGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.entityTextureLocation(NAME);

    /**
     * One render type per variant, built ON FIRST USE rather than at class load.
     * <p>
     * ⚠ These MUST NOT be created in a static initialiser. RenderType.entityCutoutNoCull and entityTranslucent touch
     * Minecraft's render-type machinery, and a static block runs at whatever moment this class is first loaded. On
     * NeoForge the @Mod client constructor runs late and defers work onto the event bus, so that happened to be safe.
     * On Fabric, onInitializeClient runs during mod loading — before the render system is fully up — so the same static
     * block executed far too early and took the client down during world load, with no crash report, because the
     * failure was below the JVM. Identical code, different loader timing: NeoForge fine, Fabric dead. Populated lazily
     * below, which also means a client that never draws a yautja never builds them at all.
     */
    private static final Map<YautjaVariant, RenderType> CUTOUT_BY_VARIANT = new EnumMap<>(YautjaVariant.class);

    private static final Map<YautjaVariant, RenderType> TRANSLUCENT_BY_VARIANT = new EnumMap<>(YautjaVariant.class);

    public YautjaRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<Yautja>builder(MODEL, TEXTURE)
                .setRenderType(YautjaRenderer::getRenderType)
                .setAnimatorProvider(YautjaAnimator::new)
                .addRenderLayer(new YautjaArmorLayer<>())
                .addRenderLayer(new YautjaItemLayer())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static RenderType getRenderType(Yautja yautja) {
        var variant = yautja.getVariant();

        return yautja.isInvisible()
            ? translucentFor(variant)
            : cutoutFor(variant);
    }

    /**
     * ⚠⚠ Aug 28 — THE SINGLE SOURCE OF TRUTH FOR A VARIANT'S TEXTURE, public because the CLOAK needs the same answer.
     * The wrap identifies "the body pass" by texture, and it was asking the renderer config — which carries the BASE
     * texture — while the body actually draws with THIS per-variant location. NORMAL's suffix resolves to the base so
     * it matched; TIGER and BRUSH never did, so every one of their passes was dropped as not-the-body and a cloaked
     * variant vanished outright. One texture in three worked — his spawn-egg observation — and both sides now derive
     * the location HERE so they can never disagree again.
     */
    public static ResourceLocation variantTexture(YautjaVariant variant) {
        return PredatorResources.entityTextureLocation(NAME + variant.textureSuffix());
    }

    /** Resolved on first draw, then cached. RenderType itself memoizes, so this map is just to avoid re-deriving. */
    private static RenderType cutoutFor(YautjaVariant variant) {
        return CUTOUT_BY_VARIANT.computeIfAbsent(
            variant,
            v -> RenderType.entityCutoutNoCull(variantTexture(v))
        );
    }

    private static RenderType translucentFor(YautjaVariant variant) {
        return TRANSLUCENT_BY_VARIANT.computeIfAbsent(
            variant,
            v -> RenderType.entityTranslucent(variantTexture(v))
        );
    }
}
