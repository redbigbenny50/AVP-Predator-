package com.predator.client.vision;

import com.predator.Predator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Oct 10 - FLUSHES THE QUEUED DRAWS AT A MOB'S BOUNDARY, WHATEVER KIND OF BUFFER SOURCE IT WAS HANDED.
 * <p>
 * The vision tags each mob's draws with per-mob values (the lane that hides it in one mode, its heat material) that
 * BLib hands to the shader when the queued vertices are drawn. So the queue has to be drawn while those values are set
 * - which the mixins did with {@code if (buffer instanceof BufferSource) bufferSource.endBatch()}. Any other kind of
 * source was silently NOT flushed, the mob's vertices waited in the queue, and they were drawn later with the values
 * already cleared.
 * <p>
 * [maskProbe, tester] after a shader pack had been switched on and off, BLib's own log showed the entity shaders being
 * applied with laneA=false, laneB=false, material=0 for every mob, every second - written correctly, to the right
 * program - while before the switch the same shaders saw the lanes set. Cows then showed in EM and cold xenos in
 * thermal. The values were being pushed and popped around mobs whose draws were no longer flushed inside the window.
 * <p>
 * A source that is not a {@code BufferSource} is now flushed through the level's own buffer source, which is where the
 * entity pass ultimately queues its vertices (Iris swaps that for its batched source; flushing it draws everything
 * pending, which is exactly the boundary that is wanted). The first such source is logged by class name, once.
 */
public final class VisionBatchFlush {

    private static boolean loggedOther;

    private VisionBatchFlush() {}

    public static void flush(MultiBufferSource buffer) {
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
            return;
        }

        if (!loggedOther) {
            loggedOther = true;
            Predator.LOGGER.info(
                "[Predator][vision] entity buffer source {} is not a BufferSource; flushing the level buffer source instead",
                buffer == null ? "null" : buffer.getClass().getName()
            );
        }

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }
}
