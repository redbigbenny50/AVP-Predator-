package com.predator.client.cloak;

import com.blib.internal.client.posteffect.BLibIrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.predator.PredatorResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A copy of the scene as it stood just before entities were drawn — terrain, sky, everything behind the cloaked body —
 * exposed as an ordinary texture so a render type can sample it.
 * <p>
 * This is the one thing refraction cannot do without. Seeing "through" a body means reading pixels that are already on
 * screen, and you cannot sample the framebuffer you are currently drawing into. So the colour is copied out to a
 * scratch texture first, and the cloak samples the copy.
 * <h2>Why the capture happens before entities, not after</h2> Copying after the entity pass would include the cloaked
 * body itself, so it would refract its own previous frame's silhouette — feedback, and it looks like smeared ghosting.
 * Capturing before means a cloaked hunter refracts terrain but not other mobs standing behind them. That is the correct
 * trade: terrain is the overwhelming majority of what is behind anything, and self-feedback is far more noticeable than
 * a mob that fails to bend.
 * <h2>Shader packs</h2> With a pack active the bound framebuffer at capture time belongs to Iris, not vanilla, and
 * there is no guarantee it holds a finished scene. Rather than sample something undefined, {@link #isAvailable()}
 * reports false and the cloak falls back to the plain silhouette. That is why this never needed a custom core shader,
 * and why the pack path has nothing to debug.
 */
public final class PredatorSceneColor extends AbstractTexture {

    public static final ResourceLocation LOCATION = PredatorResources.location("scene_color_snapshot");

    private static PredatorSceneColor instance;

    private static boolean capturedThisFrame;

    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private static boolean renderingLevel;

    private int width;

    private int height;

    private PredatorSceneColor() {}

    private static PredatorSceneColor instance() {
        if (instance == null) {
            instance = new PredatorSceneColor();
            Minecraft.getInstance().getTextureManager().register(LOCATION, instance);
        }

        return instance;
    }

    /**
     * ⚠⚠⚠ Aug 27 — THE REFRACTION IS RESTORED. An earlier session hard-disabled this ({@code return false}) because the
     * capture below was a SUSPECT for the Fabric world-join crash (exit 0xC0000409, no report). That suspicion is now
     * DISPROVEN BY MEASUREMENT: a fourteen-jar bytecode bisect pinned the crash to
     * {@code GL40.glBlendEquationi(1, GL_MAX)} in BLib's translucent mask blending — stock predator with only that one
     * BLib instruction removed loads and plays. This capture was innocent; disabling it silently cost the refraction on
     * BOTH loaders and sent a whole evening hunting a "cloak regression" that was really this switch.
     * <p>
     * The three defects the disable comment cited were real hygiene problems even though none of them was the crash,
     * and each is fixed rather than argued with: the copy PINS THE READ FRAMEBUFFER to the main target instead of
     * trusting whatever is bound (the Sodium concern), the world-change path RELEASES the old texture instead of
     * orphaning it, and the GL section sits in a catch-everything that disables refraction for the session on the first
     * failure — plus {@code -Dpredator.cloak.refraction=false} as a field kill switch. If a future crash hunt wants to
     * suspect this again: flip the flag on one affected machine FIRST and let the measurement decide, instead of
     * editing this file.
     */
    private static final boolean REFRACTION_ENABLED = !"false".equals(System.getProperty("predator.cloak.refraction"));

    private static boolean disabledAfterFailure;

    /** {@return whether the snapshot holds usable scene colour for this frame} */
    public static boolean isAvailable() {
        // ⚠ PACK active, not MOD active — isShaderModActive() is true whenever Iris is merely INSTALLED, and gating
        // on it made the refraction vanish the moment Iris entered the mods folder. Only an actually-loaded pack
        // makes the framebuffer contents unsafe to sample.
        return REFRACTION_ENABLED
            && !disabledAfterFailure
            && capturedThisFrame
            && !BLibIrisCompat.isShaderPackActive();
    }

    /**
     * Copies the current colour attachment into the scratch texture. Called once per frame from
     * {@code MixinLevelRenderer_SceneColorCapture}, immediately before the entity pass.
     */
    /** Pre-Oct-9 entry point, kept for any caller without the camera: copies the whole screen as before. */
    public static void capture() {
        capture(null, null, null);
    }

    public static void capture(
        net.minecraft.client.@org.jetbrains.annotations.Nullable Camera camera,
        org.joml.@org.jetbrains.annotations.Nullable Matrix4f viewMatrix,
        org.joml.@org.jetbrains.annotations.Nullable Matrix4f projectionMatrix
    ) {
        // ⚠ Veil stage-1 instrument — free when the probe flag is off; see PredatorVeilProbe.
        PredatorVeilProbe.tick();

        capturedThisFrame = false;

        if (BLibIrisCompat.isShaderPackActive()) {
            return;
        }

        var minecraft = Minecraft.getInstance();

        // ⚠ Relog / world change invalidates two things at once, and both showed up as a magenta, part-black player.
        //
        // 1. ENTITY IDS ARE REASSIGNED. The client cloak cache is keyed by entity id, so an id that was cloaked in the
        // previous session can be handed to an unrelated entity — or back to the player — in the new one. The client
        // then renders a cloak the server never asked for, which is why this appeared with the cloak switched OFF.
        // 2. THE SCENE TEXTURE IS CLOSED by the resource reload that runs on world join. Releasing our GL id here —
        // rather than orphaning it as the old code did — keeps the swap leak-free; the next instance() re-registers
        // under the same location.
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            capturedThisFrame = false;
            renderingLevel = false;
            if (instance != null) {
                instance.releaseId();
                instance = null;
            }
            PredatorCloakClientState.clear();
            PredatorCloakSounds.clear();
            com.predator.client.effect.PredatorMudClientState.clear();

            // ⚠ Entity ids are per-session and get REUSED, so a stale netted set would put a net on whatever
            // unlucky mob inherited the number in the next world.
            com.predator.client.net.ClientNetState.clear();

            // ⚠ And the measured texture sizes go with it: a resource pack can swap a mob's texture for one of a
            // different resolution, which would leave the net scaled for the old sheet.
            com.predator.client.render.net.NetTextureScale.clear();
            com.predator.client.render.net.VillagerHatMeta.clear();
        }

        var mainTarget = minecraft.getMainRenderTarget();
        var targetWidth = mainTarget.width;
        var targetHeight = mainTarget.height;

        if (targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

        renderingLevel = true;

        if (!REFRACTION_ENABLED || disabledAfterFailure) {
            return;
        }

        // Oct 9 - PERFORMANCE (reports of FPS loss "caused by the predator cloaking"): this copied the WHOLE screen
        // into the refraction texture every frame for every player with the mod installed - plus a GPU state query
        // that can stall the pipeline - whether or not a cloaked mob was anywhere near. Only a cloaked entity reads
        // the copy, so it is taken only when one is close enough to be on screen. With nobody cloaked, the cloak now
        // costs nothing per frame.
        // Oct 9 - [stated] "lets do all three": copy only what a VISIBLE cloak needs. region() answers null when no
        // cloaked entity is on screen (no copy at all), the padded screen rectangle around the visible ones, or the
        // whole screen whenever the projection cannot be trusted.
        var region = region(minecraft, camera, viewMatrix, projectionMatrix, targetWidth, targetHeight);

        if (region == null) {
            return;
        }

        try {
            var texture = instance();

            if (texture.width != targetWidth || texture.height != targetHeight) {
                com.mojang.blaze3d.platform.TextureUtil.prepareImage(texture.getId(), targetWidth, targetHeight);
                texture.width = targetWidth;
                texture.height = targetHeight;
            }

            // ⚠ glCopyTexSubImage2D reads from the READ framebuffer, and the old code trusted whatever happened to
            // be bound there — under Sodium that is not reliably the main target. Pin it explicitly and restore the
            // previous binding on the way out. 36008 = GL_READ_FRAMEBUFFER, 36010 = GL_READ_FRAMEBUFFER_BINDING —
            // spec literals, since LWJGL constant spellings are the one thing this sandbox cannot verify.
            // Oct 9 - NO PIPELINE STALL. Asking the driver which framebuffer is bound (glGetInteger) can make the CPU
            // wait for the GPU. In plain play the level is being drawn into the main target, so that is the read
            // binding to restore. Only where another renderer may have swapped framebuffers (Veil) is the binding
            // asked for, as before.
            var previousReadFramebuffer = READ_BINDING_MAY_DIFFER
                ? org.lwjgl.opengl.GL11.glGetInteger(36010)
                : mainTarget.frameBufferId;
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(36008, mainTarget.frameBufferId);

            var activeTexture = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
            var previousBinding = RenderSystem.getShaderTexture(0);
            RenderSystem.bindTexture(texture.getId());

            // Same offset in the texture as on screen, so the ripple samples it exactly as it would a full copy.
            org.lwjgl.opengl.GL11.glCopyTexSubImage2D(3553, 0, region[0], region[1], region[0], region[1], region[2], region[3]);

            RenderSystem.bindTexture(previousBinding);
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(activeTexture);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(36008, previousReadFramebuffer);

            capturedThisFrame = true;
        } catch (Throwable throwable) {
            // One failure disables refraction for the session — a cloak that bends light slightly less is better
            // than one that fails every frame. Logged once; the silhouette and ripple carry on regardless.
            disabledAfterFailure = true;
            com.predator.Predator.LOGGER.warn("[PredatorSceneColor] scene capture failed; refraction disabled for this session", throwable);
        }
    }

    /**
     * {@return whether the world is currently being drawn}
     * <p>
     * ⚠ Entity renders also happen OUTSIDE the world — the inventory and creative-screen player previews go through
     * {@code EntityRenderDispatcher} too. There the projection and modelview matrices belong to the GUI, so
     * screen-space sampling produces nonsense, and it was also where the buffer desync crash surfaced. The cloak falls
     * back to the plain silhouette for those.
     */
    /** Oct 9 - a cloaked entity further than this is never copied for. */
    private static final double CLOAK_COPY_RANGE = 96.0;

    /** Oct 9 - grow each cloaked entity's box by this much (blocks) for poses, weapons and dreadlocks past the box. */
    private static final double BOX_GROWTH = 0.6;

    /** Oct 9 - screen padding around the cloaked area for the ripple's outer edge: at least this many pixels ... */
    private static final int MIN_PADDING_PIXELS = 48;

    /** ... or this fraction of the area's own size, whichever is larger (a near cloak ripples over more pixels). */
    private static final double PADDING_FRACTION = 0.35;

    /** Oct 9 - covering more than this share of the screen, the whole screen is copied (simpler, no saving to lose). */
    private static final double FULL_COPY_SHARE = 0.6;

    /** Oct 9 - whether something other than vanilla may have changed the read framebuffer (Veil's own buffers). */
    private static final boolean READ_BINDING_MAY_DIFFER = classExists("foundry.veil.Veil");

    /**
     * Oct 9 - {@return the screen rectangle to copy as {x, y, width, height} in framebuffer pixels (origin
     * bottom-left), or null when no cloaked entity is visible} <p> Each cloaked entity within range has its (grown) box
     * projected to the screen; boxes wholly off screen or behind the camera are skipped, and the visible ones are
     * joined into one rectangle with generous padding for the ripple's outer edge. Falls back to the WHOLE screen when
     * the projection cannot be trusted: no camera matrices, the camera inside a cloaked box, a box straddling the
     * camera plane, or the area already covering most of the screen. Being too large only costs time; being too small
     * would show stale image at the ripple's edge.
     */
    private static int @org.jetbrains.annotations.Nullable [] region(
        Minecraft minecraft,
        net.minecraft.client.@org.jetbrains.annotations.Nullable Camera camera,
        org.joml.@org.jetbrains.annotations.Nullable Matrix4f viewMatrix,
        org.joml.@org.jetbrains.annotations.Nullable Matrix4f projectionMatrix,
        int width,
        int height
    ) {
        var ids = PredatorCloakClientState.cloakedIds();

        if (ids.isEmpty() || minecraft.level == null) {
            return null;
        }

        var full = new int[] { 0, 0, width, height };

        if (camera == null || viewMatrix == null || projectionMatrix == null) {
            return anyCloakedNear(minecraft, minecraft.gameRenderer.getMainCamera().getPosition()) ? full : null;
        }

        var cameraPos = camera.getPosition();
        var viewProjection = new org.joml.Matrix4f(projectionMatrix).mul(viewMatrix);
        var corner = new org.joml.Vector4f();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        var anyVisible = false;

        for (var id : ids) {
            var entity = minecraft.level.getEntity(id);

            // Oct 10 - only an entity that really is cloaked (a reused id may now belong to someone else).
            if (
                !(entity instanceof net.minecraft.world.entity.LivingEntity living)
                    || !PredatorCloakClientState.isCloaked(living)
                    || entity.distanceToSqr(cameraPos) > CLOAK_COPY_RANGE * CLOAK_COPY_RANGE
            ) {
                continue;
            }

            // Grow for overhanging parts, and by its speed so a fast mover is covered wherever it is drawn this frame.
            var growth = BOX_GROWTH + entity.getDeltaMovement().length() * 2.0;
            var box = entity.getBoundingBox().inflate(growth);

            if (box.contains(cameraPos)) {
                return full;
            }

            double eMinX = Double.MAX_VALUE, eMinY = Double.MAX_VALUE, eMaxX = -Double.MAX_VALUE, eMaxY = -Double.MAX_VALUE;
            var behind = 0;

            for (var i = 0; i < 8; i++) {
                corner.set(
                    (float) (((i & 1) == 0 ? box.minX : box.maxX) - cameraPos.x),
                    (float) (((i & 2) == 0 ? box.minY : box.maxY) - cameraPos.y),
                    (float) (((i & 4) == 0 ? box.minZ : box.maxZ) - cameraPos.z),
                    1.0F
                );
                viewProjection.transform(corner);

                if (corner.w <= 0.05F) {
                    behind++;
                    continue;
                }

                var ndcX = corner.x / corner.w;
                var ndcY = corner.y / corner.w;
                eMinX = Math.min(eMinX, ndcX);
                eMaxX = Math.max(eMaxX, ndcX);
                eMinY = Math.min(eMinY, ndcY);
                eMaxY = Math.max(eMaxY, ndcY);
            }

            if (behind == 8) {
                continue; // wholly behind the camera
            }

            if (behind > 0) {
                return full; // straddles the camera plane - its screen extent is unbounded
            }

            if (eMaxX < -1.0 || eMinX > 1.0 || eMaxY < -1.0 || eMinY > 1.0) {
                continue; // wholly off screen
            }

            anyVisible = true;
            minX = Math.min(minX, eMinX);
            maxX = Math.max(maxX, eMaxX);
            minY = Math.min(minY, eMinY);
            maxY = Math.max(maxY, eMaxY);
        }

        if (!anyVisible) {
            return null;
        }

        // NDC (-1..1) to framebuffer pixels, bottom-left origin - the same origin glCopyTexSubImage2D uses.
        var px0 = (minX * 0.5 + 0.5) * width;
        var px1 = (maxX * 0.5 + 0.5) * width;
        var py0 = (minY * 0.5 + 0.5) * height;
        var py1 = (maxY * 0.5 + 0.5) * height;
        var padX = Math.max(MIN_PADDING_PIXELS, (px1 - px0) * PADDING_FRACTION);
        var padY = Math.max(MIN_PADDING_PIXELS, (py1 - py0) * PADDING_FRACTION);
        var x0 = Math.max(0, (int) Math.floor(px0 - padX));
        var y0 = Math.max(0, (int) Math.floor(py0 - padY));
        var x1 = Math.min(width, (int) Math.ceil(px1 + padX));
        var y1 = Math.min(height, (int) Math.ceil(py1 + padY));

        if (x1 <= x0 || y1 <= y0) {
            return null;
        }

        if ((double) (x1 - x0) * (y1 - y0) > FULL_COPY_SHARE * width * height) {
            return full;
        }

        return new int[] { x0, y0, x1 - x0, y1 - y0 };
    }

    /** {@return whether any cloaked entity the client knows of is within copy range of a point} */
    private static boolean anyCloakedNear(Minecraft minecraft, net.minecraft.world.phys.Vec3 point) {
        for (var id : PredatorCloakClientState.cloakedIds()) {
            var entity = minecraft.level.getEntity(id);

            if (entity != null && entity.distanceToSqr(point) <= CLOAK_COPY_RANGE * CLOAK_COPY_RANGE) {
                return true;
            }
        }

        return false;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, PredatorSceneColor.class.getClassLoader());
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean isRenderingLevel() {
        return renderingLevel;
    }

    public static void beginLevelRender() {
        renderingLevel = true;
    }

    public static void endLevelRender() {
        renderingLevel = false;
    }

    /** Dropped when the level changes; the copy is only meaningful within one frame anyway. */
    public static void invalidate() {
        capturedThisFrame = false;
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // Nothing to load — this texture's contents come from the framebuffer, not from a resource pack.
    }
}
