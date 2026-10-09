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
    public static void capture() {
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
            var previousReadFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(36010);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(36008, mainTarget.frameBufferId);

            var activeTexture = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
            var previousBinding = RenderSystem.getShaderTexture(0);
            RenderSystem.bindTexture(texture.getId());

            org.lwjgl.opengl.GL11.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, targetWidth, targetHeight);

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
