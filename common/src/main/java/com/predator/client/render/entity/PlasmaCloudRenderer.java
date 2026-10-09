package com.predator.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.predator.PredatorResources;
import com.predator.common.gameplay.entity.plasma.PlasmaCloudEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The gauntlet self-destruct's cloud: avp_human's mushroom-cloud renderer ported verbatim, with a BLUE palette. The
 * cloud is thousands of billboarded cloudlets over one 8x8 sprite; colour is computed per cloudlet as it rises and
 * cools (white-hot core, electric blue body, deep blue smoke). The lightning inside it is not drawn here — it is
 * visual-only LightningBolt entities spawned by PlasmaDetonation for the first seconds.
 */
public class PlasmaCloudRenderer extends EntityRenderer<PlasmaCloudEntity> {

    private static final double MIN_RENDER_DISTANCE = 1200.0D;

    /**
     * Hard ceiling on cloudlets, matching the clamp the config exposes.
     * <p>
     * The per-effect budget itself is READ FROM CONFIG ({@code blocks.nuke.cloud_particle_budget}) rather than fixed
     * here - the property existed but nothing consulted it, so the knob did nothing.
     * </p>
     */
    /** Cloudlet budget per cloud; the nuke reads this from config, the plasma cloud is a fixed dial. */
    private static final int CLOUD_PARTICLE_BUDGET = 12_000;

    private static final int MAX_CLOUDLETS_PER_EFFECT = 20_000;

    private static final int MIN_CLOUDLETS_PER_EFFECT = 512;

    /** How often the cache is swept for clouds that have finished. */
    private static final int CACHE_PRUNE_INTERVAL_IN_TICKS = 20;

    private static final ResourceLocation CLOUDLET_TEXTURE = PredatorResources.location("textures/particle/plasma_cloudlet.png");

    private static final RenderType CLOUDLET_RENDER_TYPE = RenderType.entityTranslucent(CLOUDLET_TEXTURE);

    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    private final Map<Integer, CloudletSet> cloudletCache = new HashMap<>();

    private int lastCachePruneTick = Integer.MIN_VALUE;

    public PlasmaCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
        @NotNull PlasmaCloudEntity entity,
        float entityYaw,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource bufferSource,
        int packedLight
    ) {
        var cloudlets = cloudletsFor(entity);
        cloudlets.tickTo(entity, entity.tickCount);
        if (!cloudlets.cloudlets.isEmpty()) {
            var vertices = bufferSource.getBuffer(CLOUDLET_RENDER_TYPE);
            var matrix = poseStack.last().pose();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            var left = camera.getLeftVector();
            var up = camera.getUpVector();

            // ⚠⚠ THE WHOLE CLOUD FADES OUT OVER ITS LAST STRETCH. Each cloudlet fades on its OWN lifetime, and stem/cap
            // cloudlets are still being emitted at 80% of the entity's life with lifetimes that outrun it — so when
            // the entity discarded, whatever was still mid-life vanished in one frame: [stated] "it stays mostly
            // solid then just goes poof." A global multiplier over the final END_FADE fraction of the entity's
            // duration takes the entire cloud to zero before the entity is gone.
            var endFade = cloudlets.endFade(entity, partialTick);

            for (var cloudlet : cloudlets.cloudlets) {
                renderCloudlet(vertices, matrix, left, up, cloudlet, partialTick, endFade);
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public boolean shouldRender(PlasmaCloudEntity entity, Frustum camera, double camX, double camY, double camZ) {
        var renderDistance = Math.max(MIN_RENDER_DISTANCE, entity.getRadius() * 10.0D);
        return entity.distanceToSqr(camX, camY, camZ) <= renderDistance * renderDistance;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull PlasmaCloudEntity entity) {
        return CLOUDLET_TEXTURE;
    }

    /**
     * Reads the configured cloudlet budget, clamped to the same range the config schema documents.
     */
    private static int cloudletBudget() {
        return Mth.clamp(
            CLOUD_PARTICLE_BUDGET,
            MIN_CLOUDLETS_PER_EFFECT,
            MAX_CLOUDLETS_PER_EFFECT
        );
    }

    /**
     * Drops cached cloudlet sets whose cloud no longer exists.
     * <p>
     * WITHOUT THIS THE CACHE LEAKS FOR THE WHOLE SESSION. It is keyed on entity id and nothing ever removed from it, so
     * every detonation left its set - up to a full budget of Cloudlet objects each - retained until the renderer itself
     * was discarded. A handful of nukes over a long session is real memory held for nothing.
     * </p>
     * <p>
     * Swept once a second rather than per frame, and only when something is actually cached; at most a couple of clouds
     * are ever live at once, so the walk is trivial.
     * </p>
     */
    private void pruneCloudletCache(PlasmaCloudEntity entity) {
        if (cloudletCache.isEmpty()) {
            return;
        }

        var level = entity.level();
        var now = entity.tickCount;

        if (lastCachePruneTick != Integer.MIN_VALUE && now - lastCachePruneTick < CACHE_PRUNE_INTERVAL_IN_TICKS) {
            return;
        }

        lastCachePruneTick = now;
        cloudletCache.keySet().removeIf(id -> {
            var cloud = level.getEntity(id);
            return cloud == null || cloud.isRemoved();
        });
    }

    private CloudletSet cloudletsFor(PlasmaCloudEntity entity) {
        pruneCloudletCache(entity);

        var cached = cloudletCache.get(entity.getId());
        if (
            cached == null ||
                cached.seed != entity.getSeed() ||
                cached.radius != entity.getRadius() ||
                cached.durationTicks != entity.getDurationTicks()
        ) {
            cached = new CloudletSet(entity);
            cloudletCache.put(entity.getId(), cached);
        }
        return cached;
    }

    private static void renderCloudlet(
        VertexConsumer vertices,
        Matrix4f matrix,
        Vector3f left,
        Vector3f up,
        Cloudlet cloudlet,
        float partialTick,
        float endFade
    ) {
        var alpha = cloudlet.alpha(partialTick) * endFade;
        if (alpha <= 0.01F) {
            return;
        }

        var size = cloudlet.size(partialTick);
        var x = Mth.lerp(partialTick, cloudlet.previousX, cloudlet.x);
        var y = Mth.lerp(partialTick, cloudlet.previousY, cloudlet.y);
        var z = Mth.lerp(partialTick, cloudlet.previousZ, cloudlet.z);
        var red = cloudlet.red();
        var green = cloudlet.green();
        var blue = cloudlet.blue();

        addBillboardVertex(vertices, matrix, x, y, z, left, up, -size, -size, red, green, blue, alpha, 1.0F, 1.0F);
        addBillboardVertex(vertices, matrix, x, y, z, left, up, -size, size, red, green, blue, alpha, 1.0F, 0.0F);
        addBillboardVertex(vertices, matrix, x, y, z, left, up, size, size, red, green, blue, alpha, 0.0F, 0.0F);
        addBillboardVertex(vertices, matrix, x, y, z, left, up, size, -size, red, green, blue, alpha, 0.0F, 1.0F);
    }

    private static void addBillboardVertex(
        VertexConsumer vertices,
        Matrix4f matrix,
        double x,
        double y,
        double z,
        Vector3f left,
        Vector3f up,
        float horizontal,
        float vertical,
        float red,
        float green,
        float blue,
        float alpha,
        float u,
        float v
    ) {
        vertices
            .addVertex(
                matrix,
                (float) x + left.x() * horizontal + up.x() * vertical,
                (float) y + left.y() * horizontal + up.y() * vertical,
                (float) z + left.z() * horizontal + up.z() * vertical
            )
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setNormal(0.0F, 1.0F, 0.0F)
            .setLight(FULL_BRIGHT);
    }

    private static float easeOutCubic(float value) {
        var inverse = 1.0F - Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static final class CloudletSet {

        private final long seed;

        private final int radius;

        private final int durationTicks;

        private final RandomSource random;

        private final List<Cloudlet> cloudlets = new ArrayList<>();

        private int simulatedTicks;

        private int age;

        private double stemHeight;

        private double capRadius;

        private double rollerSize;

        private CloudletSet(PlasmaCloudEntity entity) {
            seed = entity.getSeed();
            radius = Math.max(4, entity.getRadius());
            durationTicks = Math.max(220, entity.getDurationTicks());
            random = RandomSource.create(seed);
            stemHeight = Math.max(3.0D, radius * 0.06D);
            capRadius = Math.max(3.0D, radius * 0.06D);
            rollerSize = Math.max(1.5D, radius * 0.035D);
        }

        private void tickTo(PlasmaCloudEntity entity, int targetTicks) {
            if (simulatedTicks == 0 && targetTicks > 6) {
                simulatedTicks = targetTicks - 6;
                age = simulatedTicks;
            }

            while (simulatedTicks < targetTicks) {
                tick(entity);
                simulatedTicks++;
            }
        }

        /** Fraction of the entity's duration over which the whole cloud fades to nothing at the end. */
        private static final float END_FADE = 0.3F;

        /** {@return 1 for most of the cloud's life, easing to 0 across the last {@link #END_FADE} of it} */
        private float endFade(PlasmaCloudEntity entity, float partialTick) {
            var life = (entity.tickCount + partialTick) / (float) Math.max(1, durationTicks);
            var remaining = Mth.clamp((1.0F - life) / END_FADE, 0.0F, 1.0F);

            return remaining * remaining * (3.0F - 2.0F * remaining);
        }

        private void tick(PlasmaCloudEntity entity) {
            age++;
            var progress = age / (float) durationTicks;
            var formation = easeOutCubic(Math.min(1.0F, progress / 0.58F));
            stemHeight = radius * (0.08D + formation * 0.72D);
            capRadius = radius * (0.08D + formation * 0.54D);
            rollerSize = Math.max(1.8D, capRadius * 0.38D);

            if (age == 1) {
                entity.level().addParticle(ParticleTypes.FLASH, entity.getX(), entity.getY() + 1.0D, entity.getZ(), 0.0D, 0.0D, 0.0D);
                spawnInitialHotCoreCloudlets();
            }
            if (age < 52) {
                spawnHotCoreCloudlets();
            }
            if (progress < 0.8F) {
                spawnStemCloudlets();
                spawnCapCloudlets();
            }
            if (age < Math.min(125, durationTicks / 4)) {
                spawnGroundShockCloudlets();
            }
            if (radius >= 20 && age % 3 == 0 && progress > 0.08F && progress < 0.42F) {
                spawnCondensationCloudlets();
            }

            for (Iterator<Cloudlet> iterator = cloudlets.iterator(); iterator.hasNext();) {
                var cloudlet = iterator.next();
                cloudlet.tick(this);
                if (cloudlet.isComplete()) {
                    iterator.remove();
                }
            }
        }

        private void spawnInitialHotCoreCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (18.0D + Math.sqrt(radius) * 1.8D), 22, 48));
            spawnHotCoreCloudlets(count, Math.max(2.5D, radius * 0.16D), Math.max(5.0D, radius * 0.12D), 44, 92);
        }

        private void spawnHotCoreCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (4.0D + Math.sqrt(radius) * 0.85D), 6, 16));
            spawnHotCoreCloudlets(count, Math.max(2.0D, radius * 0.1D), Math.max(4.0D, radius * 0.08D), 28, 68);
        }

        private void spawnHotCoreCloudlets(int count, double spread, double height, int minLife, int maxLife) {
            for (var i = 0; i < count; i++) {
                var angle = random.nextDouble() * Math.PI * 2.0D;
                var radial = Math.pow(random.nextDouble(), 1.7D) * spread;
                var size = Math.max(3.2D, radius * (0.034D + random.nextDouble() * 0.02D));
                cloudlets.add(
                    new Cloudlet(
                        Math.cos(angle) * radial,
                        random.nextDouble() * height,
                        Math.sin(angle) * radial,
                        size,
                        size * (2.15D + random.nextDouble() * 0.75D),
                        cloudletLife(minLife, maxLife),
                        CloudletType.HOT_CORE,
                        angle
                    )
                );
            }
        }

        private void spawnStemCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (12.0D + Math.sqrt(radius) * 2.0D), 12, 38));
            var spread = Math.max(1.2D, rollerSize * 0.34D);
            for (var i = 0; i < count; i++) {
                var angle = random.nextDouble() * Math.PI * 2.0D;
                var radial = Math.abs(random.nextGaussian()) * spread;
                var size = Math.max(2.2D, radius * 0.028D);
                cloudlets.add(
                    new Cloudlet(
                        Math.cos(angle) * radial,
                        0.0D,
                        Math.sin(angle) * radial,
                        size,
                        size * 2.8D,
                        cloudletLife(230, 420),
                        CloudletType.STEM,
                        angle
                    )
                );
            }
        }

        private void spawnCapCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (3.0D + Math.sqrt(radius) * 0.7D), 4, 12));
            for (var i = 0; i < count; i++) {
                var around = random.nextDouble() * Math.PI * 2.0D;
                var tubeAngle = random.nextDouble() * Math.PI * 2.0D;
                var filledTube = rollerSize * (0.58D + random.nextDouble() * 0.42D);
                var horizontalRadius = Math.max(0.5D, capRadius + Math.cos(tubeAngle) * filledTube);
                var cloudletY = stemHeight + Math.sin(tubeAngle) * filledTube;
                var size = Math.max(2.4D, radius * 0.03D);
                cloudlets.add(
                    new Cloudlet(
                        Math.cos(around) * horizontalRadius,
                        cloudletY,
                        Math.sin(around) * horizontalRadius,
                        size,
                        size * 2.5D,
                        cloudletLife(190, 360),
                        CloudletType.CAP,
                        around
                    )
                );
            }
        }

        private void spawnGroundShockCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (8.0D + radius * 0.12D), 10, 30));
            var shockLife = Mth.clamp(age / (double) Math.min(125, durationTicks / 4), 0.0D, 1.0D);
            var shockRadius = radius * Mth.lerp(easeOutCubic((float) shockLife), 0.08D, 1.34D);
            var ringThickness = Math.max(2.0D, radius * 0.055D);
            var height = Math.max(1.1D, radius * 0.018D);
            for (var i = 0; i < count; i++) {
                var angle = random.nextDouble() * Math.PI * 2.0D;
                var distance = Math.max(0.0D, shockRadius + (random.nextDouble() * 2.0D - 1.0D) * ringThickness);
                var size = Math.max(2.8D, radius * (0.034D + random.nextDouble() * 0.022D));
                cloudlets.add(
                    new Cloudlet(
                        Math.cos(angle) * distance,
                        0.08D + random.nextDouble() * height,
                        Math.sin(angle) * distance,
                        size,
                        size * (1.8D + random.nextDouble() * 0.75D),
                        cloudletLife(72, 138),
                        CloudletType.SHOCK,
                        angle
                    )
                );
            }
        }

        private void spawnCondensationCloudlets() {
            var count = spawnBudget(Mth.clamp((int) (8.0D + Math.sqrt(radius)), 8, 20));
            var bandRadius = Math.min(radius * 0.92D, age * Math.max(0.7D, radius * 0.012D));
            var bandY = stemHeight * 0.62D;
            for (var i = 0; i < count; i++) {
                var angle = random.nextDouble() * Math.PI * 2.0D;
                var size = Math.max(2.0D, radius * 0.026D);
                cloudlets.add(
                    new Cloudlet(
                        Math.cos(angle) * bandRadius,
                        bandY + random.nextGaussian() * size * 0.35D,
                        Math.sin(angle) * bandRadius,
                        size,
                        size * 1.5D,
                        cloudletLife(55, 105),
                        CloudletType.CONDENSATION,
                        angle
                    )
                );
            }
        }

        private int spawnBudget(int requested) {
            return Math.max(0, Math.min(requested, cloudletBudget() - cloudlets.size()));
        }

        private int cloudletLife(int minimum, int maximum) {
            return minimum + random.nextInt(maximum - minimum + 1);
        }
    }

    private static final class Cloudlet {

        private double x;

        private double y;

        private double z;

        private double previousX;

        private double previousY;

        private double previousZ;

        private final double startSize;

        private final double endSize;

        private final int lifetime;

        private final CloudletType type;

        private final double angle;

        private int age;

        private Cloudlet(
            double x,
            double y,
            double z,
            double startSize,
            double endSize,
            int lifetime,
            CloudletType type,
            double angle
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            previousX = x;
            previousY = y;
            previousZ = z;
            this.startSize = startSize;
            this.endSize = endSize;
            this.lifetime = lifetime;
            this.type = type;
            this.angle = angle;
        }

        private void tick(CloudletSet cloud) {
            previousX = x;
            previousY = y;
            previousZ = z;
            age++;

            var horizontalDistance = Math.max(0.001D, Math.sqrt(x * x + z * z));
            var normalX = horizontalDistance > 0.002D ? x / horizontalDistance : Math.cos(angle);
            var normalZ = horizontalDistance > 0.002D ? z / horizontalDistance : Math.sin(angle);

            switch (type) {
                case HOT_CORE -> {
                    x += normalX * (0.1D + cloud.radius * 0.0013D);
                    z += normalZ * (0.1D + cloud.radius * 0.0013D);
                    y += Math.max(0.035D, 0.18D + cloud.radius * 0.001D - age * 0.0025D);
                }
                case STEM -> {
                    if (y < cloud.stemHeight * 0.74D) {
                        var inwardPull = Math.min(0.09D, horizontalDistance * 0.02D);
                        y += 0.34D + cloud.radius * 0.0014D;
                        x -= normalX * inwardPull;
                        z -= normalZ * inwardPull;
                        x += Math.cos(angle + age * 0.1D) * 0.028D;
                        z += Math.sin(angle + age * 0.1D) * 0.028D;
                    } else {
                        applyTorusConvection(cloud, normalX, normalZ, horizontalDistance, 0.3D);
                    }
                }
                case CAP -> applyTorusConvection(cloud, normalX, normalZ, horizontalDistance, 0.24D);
                case SHOCK -> {
                    x += normalX * (0.46D + cloud.radius * 0.0026D);
                    z += normalZ * (0.46D + cloud.radius * 0.0026D);
                    y += 0.006D;
                }
                case CONDENSATION -> {
                    x += normalX * 0.18D;
                    z += normalZ * 0.18D;
                    y += 0.008D;
                }
            }
        }

        private void applyTorusConvection(CloudletSet cloud, double normalX, double normalZ, double horizontalDistance, double speed) {
            var radialOffset = horizontalDistance - cloud.capRadius;
            var verticalOffset = y - cloud.stemHeight;
            var crossDistance = Math.max(0.001D, Math.sqrt(radialOffset * radialOffset + verticalOffset * verticalOffset));
            var rollerTarget = cloud.rollerSize * (0.72D + 0.16D * (0.5D + 0.5D * Math.sin(angle * 7.0D)));
            var correction = Mth.clamp((rollerTarget - crossDistance) * 0.045D, -0.2D, 0.2D);
            var radialMotion = verticalOffset / crossDistance * speed + radialOffset / crossDistance * correction;
            var verticalMotion = -radialOffset / crossDistance * speed + verticalOffset / crossDistance * correction;
            x += normalX * radialMotion;
            z += normalZ * radialMotion;
            y += verticalMotion + 0.01D;
        }

        private boolean isComplete() {
            return age >= lifetime;
        }

        private float alpha(float partialTick) {
            var life = (age + partialTick) / lifetime;
            var fadeIn = Mth.clamp(life / (type == CloudletType.HOT_CORE ? 0.045F : 0.12F), 0.0F, 1.0F);
            var fadeOutWindow = type == CloudletType.HOT_CORE ? 0.46F : 0.28F;
            var fadeOut = Mth.clamp((1.0F - life) / fadeOutWindow, 0.0F, 1.0F);
            var base = switch (type) {
                case HOT_CORE -> 0.88F;
                case STEM, CAP -> 0.72F;
                case SHOCK -> 0.58F;
                case CONDENSATION -> 0.34F;
            };
            return base * fadeIn * fadeOut;
        }

        private float size(float partialTick) {
            var life = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
            return (float) Mth.lerp(easeOutCubic(life), startSize, endSize);
        }

        private float red() {
            return color(0);
        }

        private float green() {
            return color(1);
        }

        private float blue() {
            return color(2);
        }

        private float color(int channel) {
            var life = Mth.clamp(age / (float) lifetime, 0.0F, 1.0F);
            if (type == CloudletType.CONDENSATION) {
                return channel == 2 ? 0.93F : 0.9F;
            }
            if (type == CloudletType.HOT_CORE) {
                var hot = switch (channel) {
                    case 0 -> 0.85F;
                    case 1 -> 0.97F;
                    default -> 1.0F;
                };
                var cool = switch (channel) {
                    case 0 -> 0.12F;
                    case 1 -> 0.30F;
                    default -> 0.85F;
                };
                return Mth.lerp(Mth.clamp(life * 1.35F, 0.0F, 1.0F), hot, cool);
            }
            if (type == CloudletType.SHOCK) {
                // The ground ring. [stated] "grey, can we make it like a blue white color": white-hot at the front,
                // cooling to electric blue rather than to smoke.
                var hot = switch (channel) {
                    case 0 -> 0.90F;
                    case 1 -> 0.97F;
                    default -> 1.0F;
                };
                var cool = switch (channel) {
                    case 0 -> 0.35F;
                    case 1 -> 0.65F;
                    default -> 1.0F;
                };
                return Mth.lerp(Mth.clamp(life * 1.1F, 0.0F, 1.0F), hot, cool);
            }

            var hot = switch (channel) {
                case 0 -> 0.40F;
                case 1 -> 0.70F;
                default -> 1.0F;
            };
            var cool = switch (channel) {
                case 0 -> 0.10F;
                case 1 -> 0.14F;
                default -> 0.30F;
            };
            return Mth.lerp(Mth.clamp(life * 1.45F, 0.0F, 1.0F), hot, cool);
        }
    }

    private enum CloudletType {
        HOT_CORE,
        STEM,
        CAP,
        SHOCK,
        CONDENSATION
    }
}
