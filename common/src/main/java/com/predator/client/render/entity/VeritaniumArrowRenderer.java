package com.predator.client.render.entity;

import com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * The veritanium arrow in flight and stuck in things.
 * <p>
 * textures/entity/veritanium_arrow.png ships as a copy of VANILLA's arrow texture — a template for the artist, who
 * repaints that same file. Vanilla draws an arrow in CODE from one 32x32 sheet, top-left corner only: rows 0-4 are the
 * side view (fletching x0-3, the shaft along row 2, the HEAD at x13-15 rows 1-3); rows 5-9 are the back-end cross seen
 * from behind.
 */
public class VeritaniumArrowRenderer extends ArrowRenderer<VeritaniumArrowEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "avp_predator",
        "textures/entity/veritanium_arrow.png"
    );

    public VeritaniumArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull VeritaniumArrowEntity entity) {
        return TEXTURE;
    }
}
