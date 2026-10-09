package com.predator.client.render.layer;

import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import com.predator.PredatorResources;
import com.predator.client.cloak.PredatorCloakRendering;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaArmorVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class YautjaArmorLayer<T extends Yautja> implements AzRenderLayer<UUID, T> {

    /**
     * ⚠⚠ PER ARMOUR VARIANT, NOT ONE FIXED SHEET. This layer used to hardcode the base armour texture, which is the
     * same shape as the Aug 28 cloak bug: a per-variant thing resolved from a single hardcoded location, so every
     * yautja wore the same set no matter what it rolled.
     * <p>
     * ⚠ REGULAR resolves to the existing {@code yautja_jungle_armor.png} — nothing that already ships is renamed, and
     * only ALT1 adds a suffix.
     */
    public static ResourceLocation armorTexture(YautjaArmorVariant variant) {
        return PredatorResources.entityTextureLocation("yautja_jungle_armor" + variant.textureSuffix());
    }

    /** Resolved on first draw then cached, mirroring the body pass — RenderType memoizes, this avoids re-deriving. */
    private static final Map<YautjaArmorVariant, RenderType> CUTOUT_BY_VARIANT = new EnumMap<>(YautjaArmorVariant.class);

    private static final Map<YautjaArmorVariant, RenderType> TRANSLUCENT_BY_VARIANT = new EnumMap<>(YautjaArmorVariant.class);

    private static final int FALLBACK_COLOR = -1;

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {
        var localPlayer = Minecraft.getInstance().player;

        if (localPlayer == null) {
            return;
        }

        var animatable = context.animatable();
        var renderPipeline = context.rendererPipeline();

        var isInvisible = animatable.isInvisible();
        // ⚠ The armour set is the ENTITY's, read fresh each frame — a yautja that changes variant (a command, a
        // future tier promotion) must not keep drawing the set it spawned in.
        var armorVariant = animatable.getArmorVariant();
        var renderType = isInvisible
            ? TRANSLUCENT_BY_VARIANT.computeIfAbsent(armorVariant, v -> RenderType.entityTranslucentCull(armorTexture(v)))
            : CUTOUT_BY_VARIANT.computeIfAbsent(armorVariant, v -> RenderType.entityCutout(armorTexture(v)));
        var vertexConsumer = context.multiBufferSource().getBuffer(renderType);

        // A concealed (cloaked) yautja's buffer source drops armour passes outright, so re-rendering the whole model
        // into it is pure waste - that re-render was ~7% of the frame with a group of cloaked yautja on screen.
        if (PredatorCloakRendering.isDiscarded(vertexConsumer)) {
            return;
        }

        var alphaValue = animatable.isInvisibleTo(localPlayer) ? 0 : 0.38;
        int color;

        if (isInvisible) {
            var alpha = (int) (alphaValue * 0xFF) << 24;
            color = (context.renderColor() & 0xFFFFFF) | alpha;
        } else {
            color = FALLBACK_COLOR;
        }

        var previousColor = context.renderColor();
        var previousConsumer = context.vertexConsumer();

        context.setRenderColor(color);
        context.setVertexConsumer(vertexConsumer);

        try {
            renderPipeline.reRender(context);
        } finally {
            // Leave the context as later layers expect it.
            context.setRenderColor(previousColor);
            context.setVertexConsumer(previousConsumer);
        }
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {}
}
