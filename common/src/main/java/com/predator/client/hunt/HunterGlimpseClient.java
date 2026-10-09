package com.predator.client.hunt;

import com.predator.client.cloak.PredatorCloakClientState;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.network.packet.S2CHunterGlimpsePayload;
import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The Hunter glimpsed during phase 1: a yautja that exists ONLY on the hunted player's client, standing at the edge of
 * their view and watching them.
 * <h2>How it plays</h2> [stated] "not cloaked at first then when you take notice of it and stare too long or walk to it
 * then it cloaks and then it vanishes."
 * <ol>
 * <li>It appears UNCLOAKED, a plain figure far off.</li>
 * <li>It is NOTICED when the player stares at it (looks within {@link #STARE_HALF_ANGLE} degrees of it, with a clear
 * line of sight, for {@link #STARE_TICKS}) or walks toward it (gets within {@link #NOTICE_DISTANCE}).</li>
 * <li>Noticed, it cloaks — the normal shimmer — and {@link #CLOAKED_TICKS} later it is gone. Closing to
 * {@link #VANISH_DISTANCE} makes it vanish at once.</li>
 * <li>Never noticed, it slips away the same way after {@link #UNNOTICED_TICKS}.</li>
 * </ol>
 * <h2>Why client-only</h2> A real server entity would be visible to everyone, could be shot, would be attacked by
 * xenomorphs, and would have to be cleaned up from saves. This one is a picture: added straight into the client's world
 * with a negative id no server entity can ever have, and removed again.
 */
public final class HunterGlimpseClient {

    /** Looking this close to it counts as looking at it. */
    private static final float STARE_HALF_ANGLE = 8.0F;

    /** How long a stare it tolerates before it cloaks: 1.5 s. */
    private static final int STARE_TICKS = 30;

    /** Walking this close counts as noticing it. Glimpses appear 40-56 blocks out. */
    private static final double NOTICE_DISTANCE = 32.0;

    /** Once cloaked, how long before it is gone: 1.5 s of shimmer. */
    private static final int CLOAKED_TICKS = 30;

    /** Closer than this and it is gone at once. */
    private static final double VANISH_DISTANCE = 20.0;

    /** Unnoticed, it leaves on its own after 15 s. */
    private static final int UNNOTICED_TICKS = 300;

    /** Negative ids are never handed out by a server. */
    private static int nextId = -1_400_000;

    private static @Nullable Yautja glimpse;

    private static int age;

    private static int stareTicks;

    /** -1 while still uncloaked; otherwise ticks since it cloaked. */
    private static int cloakedFor = -1;

    private HunterGlimpseClient() {}

    public static void onGlimpse(S2CHunterGlimpsePayload payload) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;

        if (level == null) {
            return;
        }

        remove();

        var yautja = PredatorEntityTypes.YAUTJA.get().create(level);

        if (yautja == null) {
            return;
        }

        yautja.setId(nextId--);
        yautja.moveTo(payload.x(), payload.y(), payload.z(), payload.yaw(), 0.0F);
        yautja.setYHeadRot(payload.yaw());
        yautja.setYBodyRot(payload.yaw());
        yautja.setNoAi(true);
        yautja.setSilent(true);

        level.addEntity(yautja);

        glimpse = yautja;
        age = 0;
        stareTicks = 0;
        cloakedFor = -1;
    }

    /** Called every client tick. */
    public static void clientTick(Minecraft minecraft) {
        if (glimpse == null) {
            return;
        }

        var player = minecraft.player;

        if (minecraft.level == null || player == null || glimpse.level() != minecraft.level) {
            glimpse = null;
            return;
        }

        age++;
        var distanceSqr = player.distanceToSqr(glimpse);

        if (distanceSqr < VANISH_DISTANCE * VANISH_DISTANCE) {
            remove();
            return;
        }

        if (cloakedFor >= 0) {
            if (++cloakedFor >= CLOAKED_TICKS) {
                remove();
            }

            return;
        }

        stareTicks = isLookingAt(player, glimpse) ? stareTicks + 1 : 0;

        if (stareTicks >= STARE_TICKS || distanceSqr < NOTICE_DISTANCE * NOTICE_DISTANCE || age >= UNNOTICED_TICKS) {
            cloakedFor = 0;
            PredatorCloakClientState.set(glimpse.getId(), true);
        }
    }

    private static boolean isLookingAt(Player player, Entity target) {
        var toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        var look = player.getViewVector(1.0F);
        var cos = look.dot(toTarget);

        return cos >= Mth.cos(STARE_HALF_ANGLE * Mth.DEG_TO_RAD) && player.hasLineOfSight(target);
    }

    private static void remove() {
        if (glimpse == null) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        PredatorCloakClientState.set(glimpse.getId(), false);

        if (minecraft.level != null && glimpse.level() == minecraft.level) {
            minecraft.level.removeEntity(glimpse.getId(), Entity.RemovalReason.DISCARDED);
        }

        glimpse = null;
    }
}
