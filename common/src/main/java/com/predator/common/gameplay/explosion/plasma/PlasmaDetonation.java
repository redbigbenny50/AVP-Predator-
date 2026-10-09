package com.predator.common.gameplay.explosion.plasma;

import com.blib.api.common.explosion.v1.Explosion;
import com.blib.api.common.explosion.v1.ExplosionUtil;
import com.blib.api.common.server.v1.ServerScheduler;
import com.predator.Predator;
import com.predator.common.gameplay.entity.plasma.PlasmaCloudEntity;
import com.predator.common.network.packet.S2CPlasmaFlashPayload;
import com.predator.common.registry.init.PredatorDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The gauntlet self-destruct's blast. avp_human's nuke, rebuilt inside avp_predator on the same BLib {@code Explosion}
 * engine, minus everything he ruled out: no fallout biome, no radiation, no irradiated strain, no crater drainer. What
 * remains: a resumable multi-cycle carve of a 3x3-chunk sphere, an outright kill of everything inside it credited to
 * the wearer, the cloud, and lightning through it.
 * <h2>His spec</h2> [stated] "blow up a 3x3 chunk area ... mushroom cloud like the nuke ... more on the blue side with
 * lightning effects coursing through it ... kills outright, the crater isnt as deep, fire in the crater, no fall off no
 * radiation ... nukes blast sound ... 'atomized by heated plasma'".
 * <h2>⚠ Dials are non-final statics</h2>
 */
public final class PlasmaDetonation {

    /** Horizontal radius in blocks: 24 = a chunk and a half each way = a 3x3 chunk footprint. */
    public static int RADIUS = 24;

    /** How high the sphere reaches. */
    public static int RADIUS_UP = 14;

    /** How deep the crater goes — [stated] not as deep as the nuke (whose is 32). */
    public static int RADIUS_DOWN = 8;

    public static int MAX_KNOCKBACK = 5;

    /** [stated] the nuke's 40 s "lingered for too long"; 15 s. */
    public static int CLOUD_DURATION_TICKS = 20 * 15;

    /** Visual-only lightning bolts spawned inside the cloud, spread over the first {@link #LIGHTNING_SECONDS}. */
    public static int LIGHTNING_BOLTS = 14;

    public static int LIGHTNING_SECONDS = 4;

    private static final int CYCLE_BUDGET_MS = 15;

    private static final Set<ResourceKey<Level>> RESUMED = new HashSet<>();

    private static MinecraftServer resumeGuard;

    private PlasmaDetonation() {
        throw new UnsupportedOperationException();
    }

    /** Detonates at {@code center}. {@code armer} is credited with every kill (may be offline; then no credit). */
    public static void detonate(ServerLevel level, Vec3 center, @Nullable UUID armer) {
        create(level, center, RADIUS, MAX_KNOCKBACK, UUID.randomUUID(), armer).explode();
    }

    private static Explosion create(
        ServerLevel level,
        Vec3 center,
        int radius,
        int maxKnockback,
        UUID explosionId,
        @Nullable UUID armer
    ) {
        var effects = new PlasmaDetonationEffects();
        var holder = new Explosion[1];
        var explosion = Explosion.builder(level, center)
            .withMaxMillisecondsPerCycle(CYCLE_BUDGET_MS)
            .withRadius(Direction.Plane.HORIZONTAL, radius)
            .withRadius(Direction.UP, RADIUS_UP)
            .withRadius(Direction.DOWN, RADIUS_DOWN)
            .onExplosionStart(() -> {
                killEverythingInside(level, center, radius, maxKnockback, armer);
                broadcastClientEffects(level, center, radius);
                spawnCloud(level, center, radius);
                spawnLightning(level, center, radius);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 12.0F, 0.6F);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 12.0F, 0.8F);
            })
            .onBlockSample(effects::apply)
            .onCycleFinish(
                sampled -> PlasmaDetonationSavedData.get(level)
                    .record(explosionId, center, radius, maxKnockback, holder[0].saveState())
            )
            .onExplosionFinish(() -> PlasmaDetonationSavedData.get(level).forget(explosionId))
            .build();

        holder[0] = explosion;

        return explosion;
    }

    /** ⚠ Outright, no falloff — [stated]. The damage type carries the death message and bypasses armour. */
    private static void killEverythingInside(ServerLevel level, Vec3 center, int radius, int maxKnockback, @Nullable UUID armer) {
        var credited = armer == null ? null : level.getServer().getPlayerList().getPlayer(armer);
        // ⚠ Not DamageSources.source(key, entity): that overload is PRIVATE in vanilla and only looks public through
        // NeoForge's access transformer, so it compiles in a NeoForge harness and fails in :common. The public
        // constructor with a holder is the same thing.
        var source = new DamageSource(
            level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PredatorDamageTypes.PLASMA_DETONATION),
            credited
        );

        for (var entity : ExplosionUtil.getEntitiesInRadius(level, center, radius)) {
            // ⚠ The damage type bypasses invulnerability so nothing can SOAK it — which also strips creative mode's
            // protection, like /kill. A creative or spectator player is not a target ("it killed me in creative").
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            var distance = entity.distanceToSqr(center);

            entity.hurt(source, Float.MAX_VALUE);
            ExplosionUtil.applyKnockback(center, radius, entity, maxKnockback, distance);
        }
    }

    /** Flash intensity 0..1 sent to nearby players; the client scales it by distance. */
    public static float FLASH_INTENSITY = 1.0F;

    /** Shake intensity 0..1. Zero disables the shake for everyone. */
    public static float SHAKE_INTENSITY = 0.6F;

    /**
     * How far the flash and shake reach, as a multiple of the blast radius. [stated] 4.5x — a smaller device than the
     * nuke (108 blocks for the gauntlet).
     */
    public static double EFFECT_RANGE_SCALE = 4.5D;

    /** One packet per player within EFFECT_RANGE_SCALE x radius. */
    private static void broadcastClientEffects(ServerLevel level, Vec3 center, int radius) {
        var payload = new S2CPlasmaFlashPayload(BlockPos.containing(center).asLong(), radius, FLASH_INTENSITY, SHAKE_INTENSITY);
        var range = radius * EFFECT_RANGE_SCALE;
        var rangeSqr = range * range;

        for (var player : level.players()) {
            if (player.distanceToSqr(center) <= rangeSqr) {
                Predator.MOD.networking().sendToClient(player, payload);
            }
        }
    }

    private static void spawnCloud(ServerLevel level, Vec3 center, int radius) {
        var surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(center.x), Mth.floor(center.z));
        var y = Math.abs(center.y - surfaceY) > 4.0 ? surfaceY : center.y;
        var cloud = new PlasmaCloudEntity(level, center.x, y, center.z);

        cloud.configure(radius, level.getRandom().nextLong(), CLOUD_DURATION_TICKS);
        level.addFreshEntity(cloud);
    }

    /**
     * Lightning through the cloud: visual-only bolts (no fire, no damage — the blast already did that) at random points
     * inside the column, spread across the first seconds so the cloud crackles as it rises.
     */
    private static void spawnLightning(ServerLevel level, Vec3 center, int radius) {
        var random = level.getRandom();

        for (var i = 0; i < LIGHTNING_BOLTS; i++) {
            var delay = Duration.ofMillis(random.nextInt(LIGHTNING_SECONDS * 1000));
            var angle = random.nextDouble() * Math.PI * 2.0;
            var reach = random.nextDouble() * radius * 0.6;
            var x = center.x + Math.cos(angle) * reach;
            var z = center.z + Math.sin(angle) * reach;
            var y = center.y + random.nextInt(RADIUS_UP + 12);

            ServerScheduler.schedule(() -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(level);

                if (bolt == null) {
                    return;
                }

                bolt.moveTo(x, y, z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 2.0, 3.0, 2.0, 0.2);
            }, delay);
        }
    }

    /** Picks an interrupted detonation back up after a restart — registered on {@code postLevelTick}. */
    public static void resumeUnfinished(Level level) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var server = serverLevel.getServer();

        if (server != resumeGuard) {
            resumeGuard = server;
            RESUMED.clear();
        }

        if (!RESUMED.add(serverLevel.dimension())) {
            return;
        }

        var saved = PlasmaDetonationSavedData.get(serverLevel);

        for (var pending : saved.pendingExplosions()) {
            try {
                create(serverLevel, pending.center(), pending.radius(), pending.maxKnockback(), pending.id(), null)
                    .resume(pending.cursorState());
            } catch (RuntimeException exception) {
                Predator.LOGGER.error(
                    "Could not resume plasma detonation {} in {}; discarding it.",
                    pending.id(),
                    serverLevel.dimension().location(),
                    exception
                );
                saved.forget(pending.id());
            }
        }
    }

    /** {@return the sphere's centre for a placed gauntlet} — the block's own centre. */
    public static Vec3 centerOf(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }
}
