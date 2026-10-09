package com.predator.client.render.entity;

import com.predator.PredatorResources;
import com.predator.common.gameplay.entity.projectile.VeritaniumDartProjectile;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the dart using vanilla's arrow renderer.
 * <p>
 * ⚠ {@code ArrowRenderer} is abstract with exactly one thing left to supply — the texture — so "use the arrow model for
 * now" costs a single method rather than a model file. When the real dart art arrives, replacing the texture is enough;
 * only a differently SHAPED dart would need a geo model and a renderer to match.
 * <p>
 * ⚠ The texture shipped at that path is a hand-made placeholder, NOT Minecraft's {@code arrow.png}. Copying Mojang's
 * asset into a mod jar is a licensing problem, and the real file is coming anyway.
 */
public class VeritaniumDartRenderer extends ArrowRenderer<VeritaniumDartProjectile> {

    private static final ResourceLocation TEXTURE = PredatorResources.entityTextureLocation("veritanium_dart");

    public VeritaniumDartRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull VeritaniumDartProjectile entity) {
        return TEXTURE;
    }
}
