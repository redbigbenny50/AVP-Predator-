package com.predator.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.common.gameplay.entity.projectile.ShurikenProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

public class SpinningItemRenderer<T extends Entity & ItemSupplier> extends EntityRenderer<T> {

    /**
     * Degrees of tilt on a lodged blade so it is never a perfect hairline edge-on to the camera.
     * <p>
     * ⚠ Not final, so it can be hot-swapped while the game runs — javac inlines a {@code static final} primitive at
     * every use site and the edit would do nothing.
     */
    private static float LODGE_CANT = 30.0F;

    private final ItemRenderer itemRenderer;

    private final float scale;

    /**
     * True: the disc lies flat and spins like a frisbee - the plasma shuriken, the smart disc, the net. False: the disc
     * stands upright and spins like a wheel - the ordinary shuriken.
     * <p>
     * ⚠ Only affects a blade IN FLIGHT. A LODGED shuriken is oriented by the branch above regardless, because a blade
     * buried in a wall has to stand perpendicular to the surface whichever way it flew.
     * </p>
     */
    private final boolean spinsFlat;

    private final boolean fullBright;

    public SpinningItemRenderer(EntityRendererProvider.Context context, float scale, boolean fullBright) {
        this(context, scale, fullBright, true);
    }

    public SpinningItemRenderer(EntityRendererProvider.Context context, float scale, boolean fullBright, boolean spinsFlat) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.scale = scale;
        this.fullBright = fullBright;
        this.spinsFlat = spinsFlat;
    }

    /** ⚠ Defaults to FLAT, which is what every throwable did before. The ordinary shuriken opts out at registration. */
    public SpinningItemRenderer(EntityRendererProvider.Context context) {
        this(context, 1.0F, false, true);
    }

    /** Upright, spinning like a wheel. ⚠ The bound must match the class's own T, which is Entity AND ItemSupplier. */
    public static <E extends net.minecraft.world.entity.Entity & net.minecraft.world.entity.projectile.ItemSupplier> SpinningItemRenderer<E> vertical(
        EntityRendererProvider.Context context
    ) {
        return new SpinningItemRenderer<>(context, 1.0F, false, false);
    }

    @Override
    protected int getBlockLightLevel(@NotNull T entity, @NotNull BlockPos pos) {
        return this.fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    public void render(
        T entity,
        float entityYaw,
        float partialTicks,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffer,
        int packedLight
    ) {
        if (entity.tickCount >= 2 || this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) > 12.25) {
            poseStack.pushPose();
            poseStack.scale(this.scale, this.scale, this.scale);

            // ⚠⚠ A LODGED SHURIKEN MUST NOT KEEP SPINNING. The spin is driven by tickCount, which climbs forever,
            // so a blade stuck in a wall carried on rotating in place. Freezing it at the stuck angle is what makes
            // it read as embedded rather than hovering.
            // ⚠ The flag is SYNCHED — it has to be, because this is the client and the lodge happens on the server.
            var lodged = entity instanceof ShurikenProjectile shuriken && shuriken.isLodged();

            // ⚠⚠ THE BASE XP(90) MOVED INTO THE BRANCHES. It used to be applied to everything, which laid EVERY
            // throwable flat like a frisbee - [stated] Sep 25: "when you throw it, its sideways it needs to fly
            // vertically ... the plasma one is sideways the normal one is verticle". The lodged branch still needs it
            // (its whole comment block below is written against a Z-facing normal), and the flat spinners still want
            // it, but a vertical flyer must not have it at all.
            if (lodged) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90));

                // ⚠⚠ ZP(yaw) DID NOTHING. The base XP(90) already points the disc's normal along Z, and rotating
                // about Z cannot change a normal that IS Z — so the blade faced north/south whichever way you
                // threw it. Throw SOUTH into a north-facing wall and the disc ended up PARALLEL to that face,
                // then the visual depth sank it straight into the block: invisible, but still there to pick up.
                //
                // ⚠ A lodged blade must stand PERPENDICULAR to the surface — edge buried, face out. YP rotates
                // about the VERTICAL axis, which is what actually turns the disc to follow the throw direction.
                // ⚠⚠ THE CANT GOES BEFORE THE YAW. Applied after, its axis is the ALREADY-ROTATED Z, so how much
                // the tilt actually helps depends on which way you threw — it favoured some headings and worked
                // against others. South was the worst case: the tilt was leaning the blade back INTO the face
                // rather than out of it.
                // ⚠ Before the yaw, the tilt is fixed relative to the BLADE and rotates with it, so every heading
                // gets the same amount.
                poseStack.mulPose(Axis.ZP.rotationDegrees(LODGE_CANT));

                poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

                // ⚠⚠ THE EMBED IS DONE HERE, NOT BY MOVING THE ENTITY. Pushing the entity origin into the block
                // made it sample light level 0 from inside solid stone and render pure black.
                // ⚠ Sunk along the TRAVEL axis — local Z after the YP above — not local Y. Y drove it into the
                // floor rather than into the face it actually hit.
                poseStack.translate(0.0F, 0.0F, ShurikenProjectile.LODGE_VISUAL_DEPTH);
            } else {
                if (this.spinsFlat) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                }

                float rotation = (entity.tickCount + partialTicks) * 15.0F;

                poseStack.mulPose(Axis.ZP.rotation(rotation));
            }
            this.itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
            );
            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    public @NotNull ResourceLocation getTextureLocation(@NotNull Entity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
