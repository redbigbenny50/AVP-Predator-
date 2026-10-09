package com.predator.client.render.entity;

import com.predator.common.gameplay.entity.projectile.FirePelletProjectile;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** A ghast fireball's renderer at half size: the item sprite as a camera-facing billboard, full-bright. */
public class FirePelletRenderer extends ThrownItemRenderer<FirePelletProjectile> {

    public FirePelletRenderer(EntityRendererProvider.Context context) {
        super(context, 0.5F, true);
    }
}
