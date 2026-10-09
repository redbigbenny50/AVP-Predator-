package com.predator.client.cloak;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * ⚠ Aug 28 — THE VEIL INSTRUMENT, stage 1 of the Veil integration (the Iris playbook: measure first, port second). Pure
 * reflection — compiles against nothing of Veil's, costs nothing when Veil is absent or the flag is off, and cannot
 * crash: any failure logs once and disables itself for the session.
 * <p>
 * Enable with {@code -Dpredator.veil.probe=true} and run a Veil pack (Sable). Once a second it answers the two
 * questions static analysis could not: WHERE THE FRAME LIVES (the full table of Veil's framebuffers — id, size, colour
 * attachments, depth — against vanilla's main target and the actual GL draw/read bindings at our capture moment) and
 * WHAT VEIL IS RUNNING (its active post pipelines). Those two answers pick the integration route: register the vision
 * as a Veil post pipeline, or composite after VeilPostProcessingEvent.Post.
 */
public final class PredatorVeilProbe {

    private static final boolean ENABLED = "true".equals(System.getProperty("predator.veil.probe"));

    private static boolean resolved;

    private static boolean disabled;

    private static long lastLogMillis;

    private static Method rendererMethod;

    private static Method framebufferManagerMethod;

    private static Method framebuffersMethod;

    private static Method postManagerMethod;

    private static Method activePipelinesMethod;

    private static Method fboIdMethod;

    private static Method fboWidthMethod;

    private static Method fboHeightMethod;

    private static Method fboColorsMethod;

    private static Method fboHasDepthMethod;

    private PredatorVeilProbe() {}

    public static void tick() {
        if (!ENABLED || disabled) {
            return;
        }

        if (System.currentTimeMillis() - lastLogMillis < 1000L) {
            return;
        }

        lastLogMillis = System.currentTimeMillis();

        try {
            if (!resolved) {
                resolve();
            }

            var renderer = rendererMethod.invoke(null);

            if (renderer == null) {
                com.predator.Predator.LOGGER.info("[VeilProbe] Veil present, renderer() null (too early?)");
                return;
            }

            var mainTarget = Minecraft.getInstance().getMainRenderTarget();
            var drawBinding = org.lwjgl.opengl.GL11.glGetInteger(36006);
            var readBinding = org.lwjgl.opengl.GL11.glGetInteger(36010);

            com.predator.Predator.LOGGER.info(
                "[VeilProbe] vanillaMain id={} {}x{} | GL drawBinding={} readBinding={}",
                mainTarget.frameBufferId,
                mainTarget.width,
                mainTarget.height,
                drawBinding,
                readBinding
            );

            var manager = framebufferManagerMethod.invoke(renderer);
            @SuppressWarnings("unchecked")
            var framebuffers = (Map<ResourceLocation, Object>) framebuffersMethod.invoke(manager);

            for (var entry : framebuffers.entrySet()) {
                var fbo = entry.getValue();
                com.predator.Predator.LOGGER.info(
                    "[VeilProbe] veilFbo {} id={} {}x{} colors={} depth={}",
                    entry.getKey(),
                    fboIdMethod.invoke(fbo),
                    fboWidthMethod.invoke(fbo),
                    fboHeightMethod.invoke(fbo),
                    fboColorsMethod.invoke(fbo),
                    fboHasDepthMethod.invoke(fbo)
                );
            }

            var postManager = postManagerMethod.invoke(renderer);
            var pipelines = (List<?>) activePipelinesMethod.invoke(postManager);
            com.predator.Predator.LOGGER.info("[VeilProbe] activePostPipelines={}", pipelines);
        } catch (Throwable throwable) {
            disabled = true;
            com.predator.Predator.LOGGER.warn("[VeilProbe] disabled after failure", throwable);
        }
    }

    private static void resolve() throws Exception {
        var renderSystem = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
        rendererMethod = renderSystem.getMethod("renderer");
        var rendererClass = Class.forName("foundry.veil.api.client.render.VeilRenderer");
        framebufferManagerMethod = rendererClass.getMethod("getFramebufferManager");
        postManagerMethod = rendererClass.getMethod("getPostProcessingManager");
        framebuffersMethod = Class.forName("foundry.veil.api.client.render.framebuffer.FramebufferManager")
            .getMethod("getFramebuffers");
        activePipelinesMethod = Class.forName("foundry.veil.api.client.render.post.PostProcessingManager")
            .getMethod("getActivePipelines");
        var fbo = Class.forName("foundry.veil.api.client.render.framebuffer.AdvancedFbo");
        fboIdMethod = fbo.getMethod("getId");
        fboWidthMethod = fbo.getMethod("getWidth");
        fboHeightMethod = fbo.getMethod("getHeight");
        fboColorsMethod = fbo.getMethod("getColorAttachments");
        fboHasDepthMethod = fbo.getMethod("hasDepthAttachment");
        resolved = true;
    }
}
