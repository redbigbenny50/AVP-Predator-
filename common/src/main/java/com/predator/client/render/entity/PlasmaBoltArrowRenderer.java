package com.predator.client.render.entity;

import com.predator.PredatorResources;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltArrowProjectile;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** The plasma bow's bolt. ⚠ Stand-in art: the dart's model recoloured red, as asked, until real art exists. */
public class PlasmaBoltArrowRenderer extends ArrowRenderer<PlasmaBoltArrowProjectile> {

    private static final ResourceLocation TEXTURE = PredatorResources.entityTextureLocation("plasma_bolt_arrow");

    public PlasmaBoltArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull PlasmaBoltArrowProjectile entity) {
        return TEXTURE;
    }
}
