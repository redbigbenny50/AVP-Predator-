package com.predator.common.gameplay.net;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player counterplay to being netted: the struggle bar.
 * <h2>⚠⚠ DELIBERATELY THE SAME MECHANIC AS avp_alien'S HostStruggle, NUMBER FOR NUMBER</h2> His ruling: a netted player
 * should "struggle out of it like you can struggle free from a facehugger or a drone capturing you". So this is not a
 * second struggle system with its own feel — it is the same one. The cooldown, the gain per mash, the decay grace and
 * the decay rate are all copied from {@code HostStruggle} so a player who has learned to break a drone's grip already
 * knows how to break a net.
 * <p>
 * ⚠ Mobs do not get this. They are simply held, exactly as they are by a capture — resistance is the thing that makes a
 * player different from prey.
 * <h2>Why the mash cooldown exists</h2> ⚠ {@value #MASH_COOLDOWN_TICKS} ticks between accepted inputs caps an
 * autoclicker at a human's rate. Without it the mechanic is not a struggle, it is a check for whether the player has a
 * macro.
 * <h2>Why it decays</h2> ⚠ The bar bleeds back down after a short grace, so it has to be filled in one sustained effort
 * rather than chipped at. That is what makes the escape a race against the hunter closing on you.
 */
public final class NetStruggle {

    /** Minimum ticks between two accepted mashes. Copied from HostStruggle — caps autoclickers at a human's rate. */
    public static final int MASH_COOLDOWN_TICKS = 3;

    /** ⚠ Bar fill per accepted mash. 1/40 rather than the drone's 1/60: a net is thinner than a xenomorph's grip. */
    private static final float MASH_GAIN = 1.0F / 40.0F;

    /** Ticks of silence before the bar starts bleeding back down. */
    private static final int DECAY_GRACE_TICKS = 10;

    /** Decay per tick once the grace has lapsed. */
    private static final float DECAY_PER_TICK = 1.0F / 400.0F;

    private static final Map<ServerPlayer, State> STRUGGLES = new WeakHashMap<>();

    private NetStruggle() {
        throw new UnsupportedOperationException();
    }

    /**
     * Driven from the player's own tick.
     * <p>
     * ⚠ From the PLAYER's tick, not the net's — same reasoning HostStruggle documents. The bar has to be torn down
     * correctly even if whatever netted them dies, unloads, or is never ticked again.
     */
    public static void tickPlayer(Player player) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!PredatorNet.isNetted(player)) {
            end(serverPlayer);

            return;
        }

        var state = STRUGGLES.computeIfAbsent(serverPlayer, NetStruggle::begin);
        var now = serverPlayer.level().getGameTime();

        if (now - state.lastInputTick > DECAY_GRACE_TICKS) {
            state.fill = Math.max(0.0F, state.fill - DECAY_PER_TICK);
        }

        state.bar.setProgress(Math.clamp(state.fill, 0.0F, 1.0F));

        if (state.fill >= 1.0F) {
            end(serverPlayer);
            PredatorNet.release(serverPlayer);
        }
    }

    /** A mash arrived from the client. Rate-limited; ignored if the player is not actually netted. */
    public static void onMash(ServerPlayer player) {
        var state = STRUGGLES.get(player);

        if (state == null || !PredatorNet.isNetted(player)) {
            return;
        }

        var now = player.level().getGameTime();

        if (now - state.lastInputTick < MASH_COOLDOWN_TICKS) {
            // Too soon — an autoclicker earns nothing here.
            return;
        }

        state.lastInputTick = now;
        state.fill = Math.min(1.0F, state.fill + MASH_GAIN);
    }

    private static State begin(ServerPlayer player) {
        var bar = new ServerBossEvent(
            Component.translatable("bossbar.avp_predator.net_struggle"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS
        );

        // ⚠ Shown to the struggling player ALONE. It is their problem, not an event for the server.
        bar.addPlayer(player);

        return new State(bar);
    }

    /** ⚠ Always removes the player from the bar. A bar left attached stays on screen with nothing driving it. */
    private static void end(ServerPlayer player) {
        var state = STRUGGLES.remove(player);

        if (state != null) {
            state.bar.removeAllPlayers();
        }
    }

    private static final class State {

        private final ServerBossEvent bar;

        private float fill;

        private long lastInputTick;

        private State(ServerBossEvent bar) {
            this.bar = bar;
        }
    }
}
