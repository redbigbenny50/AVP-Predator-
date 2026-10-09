package com.predator.common.gameplay.hunt;

import com.predator.common.config.YautjaHonorConfig;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Runs each player's hunt through its stages. Pass 2: the Hunter's Moon and phase 1. Pass 3 adds the attack.
 * <h2>The timeline, as he ruled it</h2>
 * <ol>
 * <li><b>Worthy</b> — honor reached the threshold (see {@link YautjaHonor}). Nothing visible happens.</li>
 * <li><b>The Hunter's Moon</b> — the next full-moon night: [stated] "The hunters moon shines a foreboding presence".
 * Sleeping through it is allowed: [stated] "they will hear the clicking as they fade out and when they wake up the
 * corpse is at the closest open spot near their bed."</li>
 * <li><b>Phase 1</b> — "the following sunrise": clicks, glimpses, and the calling-card corpse outside the player's door
 * or wherever they spend the most time.</li>
 * <li><b>Awaiting the attack</b> — phase 2 comes the following night. [stated] Sleep is refused on attack nights.</li>
 * </ol>
 * <h2>The hunt waits, it is never skipped</h2> [stated] "dimension the yautja will wait same as if they die or log out.
 * you cant skip the hunt." Everything here only advances while the player is in the overworld and alive, and each stage
 * needs its OWN conditions met with the player present — so someone who spends phase 1's day in the Nether comes back
 * to a phase 1 that has not happened yet, rather than to a hunt that ran on without them.
 * <h2>Time</h2> Read from the overworld clock: day = {@code dayTime / 24000}, daylight is ticks 0-12000 of that day,
 * night 13000-23000. The full moon is vanilla's moon phase 0 — every 8th night.
 */
public final class HuntDirector {

    private static final int NIGHT_START = 13000;

    private static final int NIGHT_END = 23000;

    private static final int DAY_END = 12000;

    /** Phase 1 falls back to placing the calling card regardless, this late in the day. */
    private static final int CALLING_CARD_DEADLINE = 11000;

    /** The calling card waits until the player is at least this far from the spot, so it is never placed in view. */
    private static final double CALLING_CARD_AWAY_DISTANCE = 32.0;

    /** How often a player's position is sampled for the home map. */
    private static final int VISIT_SAMPLE_INTERVAL = 200;

    /** Clicks come every 30 to 90 seconds during phase 1. */
    private static final int CLICK_MIN_TICKS = 600;

    private static final int CLICK_MAX_TICKS = 1800;

    private static final int DOOR_SEARCH_RADIUS = 10;

    private static final int CALLING_CARD_RETRY_TICKS = 100;

    /** The first glimpse comes 15 s into phase 1; after that every 60 to 150 seconds. */
    private static final int FIRST_GLIMPSE_DELAY = 300;

    private static final int GLIMPSE_MIN_TICKS = 1200;

    private static final int GLIMPSE_MAX_TICKS = 3000;

    /** "At the edge of your distance": far enough to be a shape, not a mob in your face. */
    private static final double GLIMPSE_MIN_DISTANCE = 40.0;

    private static final double GLIMPSE_MAX_DISTANCE = 56.0;

    /** Within this half-angle of where the player is looking, so it is actually seen. */
    private static final float GLIMPSE_HALF_ANGLE = 35.0F;

    private HuntDirector() {}

    /** Registered on post-level-tick; only the overworld drives a hunt. */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }

        var gameTime = serverLevel.getGameTime();
        var huntLedger = HuntLedger.get(serverLevel.getServer());
        var honorLedger = HonorLedger.get(serverLevel.getServer());

        for (var player : serverLevel.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            var entry = huntLedger.entry(player.getUUID());

            if (gameTime % VISIT_SAMPLE_INTERVAL == 0) {
                huntLedger.recordVisit(entry, player.blockPosition());
            }

            tickPlayer(serverLevel, player, huntLedger, entry, honorLedger.entry(player.getUUID()).worthy(), gameTime);
        }
    }

    private static void tickPlayer(
        ServerLevel level,
        ServerPlayer player,
        HuntLedger ledger,
        HuntLedger.Entry entry,
        boolean worthy,
        long gameTime
    ) {
        var dayTime = level.getDayTime();
        var day = dayTime / 24000L;
        var timeOfDay = dayTime % 24000L;
        var isNight = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
        var isDaylight = timeOfDay < DAY_END;

        switch (entry.stage) {
            case NONE -> {
                // ⚠ Not the same moon a hunt just ended under (a debug stop, or a hunt over before dawn): the next one.
                if (worthy && isNight && level.getMoonPhase() == 0 && day != entry.moonDay) {
                    ledger.beginMoon(entry, day);
                    player.sendSystemMessage(
                        Component.literal("The hunters moon shines a foreboding presence")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)
                    );
                }
            }
            case MOON -> {
                if (player.isSleeping() && !entry.sleptOnMoon) {
                    ledger.markSlept(entry);
                    playClick(player, level);
                }

                if (isDaylight && day > entry.moonDay) {
                    ledger.setStage(entry, HuntLedger.Stage.PHASE_ONE);
                    entry.nextClickTick = gameTime + randomClickDelay(player);
                    entry.nextGlimpseTick = gameTime + FIRST_GLIMPSE_DELAY;

                    if (entry.sleptOnMoon) {
                        placeCallingCardByBed(level, player, ledger, entry);
                    }
                }
            }
            case PHASE_ONE -> {
                if (isDaylight) {
                    if (gameTime >= entry.nextClickTick) {
                        playClick(player, level);
                        entry.nextClickTick = gameTime + randomClickDelay(player);
                    }

                    if (gameTime >= entry.nextGlimpseTick) {
                        showGlimpse(level, player);
                        entry.nextGlimpseTick = gameTime + GLIMPSE_MIN_TICKS + player.getRandom()
                            .nextInt(GLIMPSE_MAX_TICKS - GLIMPSE_MIN_TICKS);
                    }

                    // ⚠ Every five seconds, not every tick: a try that finds no room scans the whole door search area.
                    if (!entry.callingCardPlaced && (gameTime % CALLING_CARD_RETRY_TICKS == 0 || timeOfDay >= CALLING_CARD_DEADLINE)) {
                        tryPlaceCallingCardAtHome(level, player, ledger, entry, timeOfDay >= CALLING_CARD_DEADLINE);
                    }
                } else if (isNight && entry.callingCardPlaced) {
                    // ⚠ Only once the calling card has actually been left — a player who was away all day comes
                    // back to a phase 1 still waiting for them.
                    ledger.setStage(entry, HuntLedger.Stage.AWAITING_ATTACK);
                }
            }
            case AWAITING_ATTACK -> {
                if (
                    isNight && timeOfDay >= ARRIVAL_TIME && gameTime >= entry.nextSendTick && !sendHunter(
                        level,
                        player,
                        ledger,
                        entry,
                        false,
                        false
                    )
                ) {
                    // No arrival spot this time (a cave, a sea): try again in a second, not every tick.
                    entry.nextSendTick = gameTime + BRAIN_INTERVAL;
                }
            }
            case ATTACK, RETURN_ATTACK -> {
                if (isDaylight) {
                    // The night is over and it is still alive: it withdraws, and comes back tomorrow night.
                    withdraw(level, ledger, entry, HuntLedger.Stage.AWAITING_RETURN);
                } else {
                    tickPendingWarp(level, player, entry, gameTime);

                    if (gameTime % BRAIN_INTERVAL == 0) {
                        tickHunter(level, player, ledger, entry);
                    }
                }
            }
            case WAITING -> tickWaiting(level, player, ledger, entry, gameTime);
            case FINAL -> {
                if (isDaylight) {
                    withdraw(level, ledger, entry, HuntLedger.Stage.AWAITING_RETURN);
                } else if (gameTime % BRAIN_INTERVAL == 0) {
                    tickHunter(level, player, ledger, entry);
                }
            }
            case AWAITING_RETURN -> {
                if (
                    isNight && timeOfDay >= ARRIVAL_TIME && gameTime >= entry.nextSendTick && !sendHunter(
                        level,
                        player,
                        ledger,
                        entry,
                        true,
                        false
                    )
                ) {
                    entry.nextSendTick = gameTime + BRAIN_INTERVAL;
                }
            }
        }
    }

    // ---------------------------------------------------------------- the attack (pass 3a)

    /** An hour after nightfall — the night has settled before it comes. */
    private static final int ARRIVAL_TIME = 14000;

    /** The Hunter's judgement and targeting are re-run every second. */
    private static final int BRAIN_INTERVAL = 20;

    /** It arrives this far out, behind the player and out of their sight. */
    private static final double ARRIVAL_MIN_DISTANCE = 32.0;

    private static final double ARRIVAL_MAX_DISTANCE = 40.0;

    /** Base defences: what stands within this radius of the player is dealt with first. */
    private static final double DEFENCE_RADIUS = 32.0;

    /** Missing this long while the player is here at night (unloaded, lost), a fresh Hunter is sent in its place. */
    private static final int HUNTER_MISSING_LIMIT = 600;

    /**
     * Sends the Hunter. A first attack rolls the tier on the ladder; the return (phase 3) keeps the same tier and gains
     * the +10%. Tries again next tick if no arrival spot was found.
     */
    static boolean sendHunter(
        ServerLevel level,
        ServerPlayer player,
        HuntLedger ledger,
        HuntLedger.Entry entry,
        boolean returning,
        boolean replacement
    ) {
        var spot = findArrivalSpot(level, player);

        if (spot == null) {
            return false;
        }

        var hunter = com.predator.common.registry.init.PredatorEntityTypes.YAUTJA.get().create(level);

        if (hunter == null) {
            return false;
        }

        var honor = HonorLedger.get(level.getServer()).entry(player.getUUID()).honor();
        // ⚠ A return, or a REPLACEMENT for a Hunter that went missing, keeps the tier already rolled.
        var tier = (returning || replacement) && !entry.hunterTier.isEmpty()
            ? com.predator.common.gameplay.entity.living.yautja.YautjaTier.byName(entry.hunterTier)
            : HunterLadder.roll(entry, honor, player.getRandom());
        var facing = (float) (Mth.atan2(player.getZ() - spot.getZ() - 0.5, player.getX() - spot.getX() - 0.5) * Mth.RAD_TO_DEG) - 90.0F;

        hunter.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, facing, 0.0F);
        hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), net.minecraft.world.entity.MobSpawnType.EVENT, null);

        // ⚠ Order matters. The hunted player and the return flag are read by applyTierAttributes, so they are set
        // before setTier, which applies the attributes and heals to the new maximum.
        hunter.setHuntedPlayer(player.getUUID());
        hunter.setHuntReturn(returning);
        hunter.setHunter(true);
        hunter.setTier(tier);
        // The Hunter's grenades: two explosives for walls and a combat kind — re-rolled now the tier is known.
        hunter.stockGrenades();
        hunter.setPersistenceRequired();
        hunter.setHuntBareHanded(HunterJudgement.isUnarmed(player));

        level.addFreshEntity(hunter);
        hunter.setTarget(player);

        // A replacement is the same hunt, not a new one: it does not count again.
        if (!returning && !replacement) {
            entry.huntsFaced++;
        }

        ledger.sendHunter(
            entry,
            hunter.getUUID(),
            tier.serializedName(),
            returning ? HuntLedger.Stage.RETURN_ATTACK : HuntLedger.Stage.ATTACK
        );

        return true;
    }

    /**
     * Behind the player — more than 90 degrees from where they are looking — 32-40 blocks out, on open ground within 16
     * blocks of their height, and preferably with no line of sight to them. Falls back to any ground spot behind them.
     */
    private static @Nullable BlockPos findArrivalSpot(ServerLevel level, ServerPlayer player) {
        var random = player.getRandom();
        BlockPos fallback = null;

        for (var attempt = 0; attempt < 24; attempt++) {
            var yaw = player.getYRot() + 180.0F + (random.nextFloat() * 2.0F - 1.0F) * 90.0F;
            var distance = ARRIVAL_MIN_DISTANCE + random.nextDouble() * (ARRIVAL_MAX_DISTANCE - ARRIVAL_MIN_DISTANCE);
            var x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            var z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
            var column = BlockPos.containing(x, player.getY(), z);

            if (!level.hasChunkAt(column)) {
                continue;
            }

            var groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(),
                column.getZ()
            );

            if (Math.abs(groundY - player.getY()) > 16) {
                continue;
            }

            var feet = new BlockPos(column.getX(), groundY, column.getZ());

            if (
                !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                    || !level.getBlockState(feet.above(2)).isAir() || !level.getFluidState(feet.below()).isEmpty()
            ) {
                continue;
            }

            var hidden = level.clip(
                new net.minecraft.world.level.ClipContext(
                    player.getEyePosition(),
                    net.minecraft.world.phys.Vec3.atCenterOf(feet.above()),
                    net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player
                )
            ).getType() != net.minecraft.world.phys.HitResult.Type.MISS;

            if (hidden) {
                return feet;
            }

            if (fallback == null) {
                fallback = feet;
            }
        }

        return fallback;
    }

    /**
     * Once a second while the Hunter is out: judge the prey's hotbar, deal with base defences first, then the player.
     * Also replaces a Hunter that has gone missing (its chunk unloaded, or it was lost) — the stale one removes itself
     * if it ever comes back, since it is no longer the current Hunter.
     */
    private static void tickHunter(ServerLevel level, ServerPlayer player, HuntLedger ledger, HuntLedger.Entry entry) {
        var hunter = entry.hunterId == null
            ? null
            : level.getEntity(entry.hunterId) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja yautja ? yautja : null;

        if (hunter == null || !hunter.isAlive()) {
            entry.hunterMissingTicks += BRAIN_INTERVAL;

            if (entry.hunterMissingTicks >= HUNTER_MISSING_LIMIT) {
                if (entry.stage == HuntLedger.Stage.FINAL) {
                    // ⚠ A wounded Hunter lost from its final fight is not re-sent at full health tonight: it returns
                    // tomorrow, as one that was never found would.
                    ledger.markHunterGone(entry, HuntLedger.Stage.AWAITING_RETURN);
                } else {
                    sendHunter(level, player, ledger, entry, entry.stage == HuntLedger.Stage.RETURN_ATTACK, true);
                }
            }

            return;
        }

        entry.hunterMissingTicks = 0;
        hunter.setHuntBareHanded(HunterJudgement.isUnarmed(player));

        // [stated] it has "noted" the base's defences — sentry guns, guards — and takes them out first.
        var current = hunter.getTarget();

        if (current != null && current.isAlive() && current != player && isDefence(current, player)) {
            return;
        }

        var defence = nearestDefence(level, player, hunter);

        hunter.setTarget(defence != null ? defence : player);

        if (defence == null) {
            checkStall(level, player, entry, hunter);
        }
    }

    // ---------------------------------------------------------------- the warp in

    /** [stated] it warps in "if they refuse to come out and face it" — after this long getting nowhere. */
    private static final int WARP_AFTER_TICKS = 1200;

    /** Within this it is in the fight, not stalled. */
    private static final double ENGAGED_DISTANCE = 6.0;

    /** Gaining at least this much ground resets the stall. */
    private static final double PROGRESS_STEP = 1.0;

    private static final double WARP_MIN_DISTANCE = 4.0;

    private static final double WARP_MAX_DISTANCE = 8.0;

    /**
     * [stated] "The hunters are able to infiltrate bases by warping to the player if they refuse to come out and face
     * it." Counts how long the Hunter has failed to reach a player it cannot see. It only counts while there is no line
     * of sight between them, so a Hunter fighting at range — caster, bow, spear throws — is never mistaken for a stuck
     * one. Breaking blocks is part of getting closer: every block it digs through that brings it nearer resets the
     * count.
     */
    private static void checkStall(
        ServerLevel level,
        ServerPlayer player,
        HuntLedger.Entry entry,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter
    ) {
        var distance = hunter.distanceTo(player);

        if (entry.warpedTonight || distance <= ENGAGED_DISTANCE || hunter.hasLineOfSight(player)) {
            entry.stallTicks = 0;
            entry.stallBestDistance = distance;
            return;
        }

        if (distance < entry.stallBestDistance - PROGRESS_STEP) {
            entry.stallBestDistance = distance;
            entry.stallTicks = 0;
            return;
        }

        entry.stallTicks += BRAIN_INTERVAL;

        // Before warping in, it tries the wall: [stated] "it will also use grenades to get past walls if it needs too".
        if (entry.stallTicks >= BREACH_AFTER_TICKS && level.getGameTime() >= entry.nextBreachTick && throwBreach(level, player, hunter)) {
            entry.nextBreachTick = level.getGameTime() + BREACH_COOLDOWN_TICKS;
        }

        if (entry.stallTicks >= WARP_AFTER_TICKS && beginWarp(level, player, entry, hunter)) {
            entry.warpedTonight = true;
            entry.stallTicks = 0;
        }
    }

    /** Stuck this long, it grenades the wall; the warp still comes at 60 s if that did not open a way. */
    private static final int BREACH_AFTER_TICKS = 300;

    /** [proposed, agreed] at most one breaching grenade every 10 seconds, so it does not level the base. */
    private static final int BREACH_COOLDOWN_TICKS = 200;

    /** Closer than this, it digs or waits for the warp instead of throwing. */
    private static final double BREACH_MIN_DISTANCE = 5.0;

    /**
     * The first block between the Hunter and its prey, if it is one the Hunter cannot dig through — on the break
     * blacklist (metal, avp_human's industrial walls, the {@code yautja_unbreakable} tag) or past its 4.5 hardness
     * window — gets an explosive grenade marked as a breach (see YautjaGrenadeProjectile). Anything it CAN dig through
     * it simply digs. {@return whether one was thrown}
     */
    private static boolean throwBreach(
        ServerLevel level,
        ServerPlayer player,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter
    ) {
        var eye = hunter.getEyePosition();
        var hit = level.clip(
            new net.minecraft.world.level.ClipContext(
                eye,
                player.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                hunter
            )
        );

        // ⚠ Real grenades from its rack: no explosive left, no breach — the warp is what remains.
        var explosive = com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_EXPLOSIVE.get();

        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !hunter.getInventory().hasItem(explosive)) {
            return false;
        }

        var state = level.getBlockState(hit.getBlockPos());
        var hardness = state.getDestroySpeed(level, hit.getBlockPos());

        if (
            hardness < 0.0F
                || !(com.predator.common.gameplay.entity.living.yautja.path.YautjaPathing.isBlacklisted(state) || hardness > 4.5F)
        ) {
            return false;
        }

        var target = hit.getLocation();

        // ⚠ Never at a wall it is standing against: a TNT-strength blast at arm's length would maul the Hunter itself.
        if (target.distanceToSqr(eye) < BREACH_MIN_DISTANCE * BREACH_MIN_DISTANCE) {
            return false;
        }

        var aim = target.subtract(eye);
        var grenade = new com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile(
            level,
            hunter,
            com.predator.common.gameplay.item.grenade.GrenadeKind.EXPLOSIVE
        );

        if (
            !(hunter.getInventory().removeItem(explosive, 1) instanceof com.blib.api.common.inventory.v1.BLibInventory.RemoveResult.Success)
        ) {
            return false;
        }

        grenade.setItem(new net.minecraft.world.item.ItemStack(explosive));
        grenade.setBreach(true);
        grenade.setPos(eye.x, eye.y - 0.1, eye.z);
        // A little loft for the drop over the distance.
        grenade.shoot(aim.x, aim.y + aim.length() * 0.08, aim.z, 1.3F, 0.0F);
        level.addFreshEntity(grenade);
        hunter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, hunter.blockPosition(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8F, 0.5F);

        return true;
    }

    /** From the first click to the Hunter appearing: 1.5 s. */
    private static final int WARP_DELAY = 30;

    /** The second click comes halfway. */
    private static final int WARP_SECOND_CLICK = 15;

    /**
     * Starts a warp: the landing spot is chosen now and the player hears the first click FROM it — [stated] "play the
     * clicking sound close to the player before it appears then the smokebomb when it does warp in". A second click
     * follows, then {@link #tickPendingWarp} brings it in. {@return whether a spot was found}
     */
    static boolean beginWarp(
        ServerLevel level,
        ServerPlayer player,
        HuntLedger.Entry entry,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter
    ) {
        var spot = findWarpSpot(level, player, hunter);

        if (spot == null) {
            return false;
        }

        entry.warpSpot = spot;
        entry.warpStartTick = level.getGameTime();
        playClickAt(player, spot.add(0.0, 1.8, 0.0));

        return true;
    }

    /** Runs every tick while a warp is pending: the second click, then the arrival. */
    private static void tickPendingWarp(ServerLevel level, ServerPlayer player, HuntLedger.Entry entry, long gameTime) {
        if (entry.warpStartTick < 0 || entry.warpSpot == null) {
            return;
        }

        var elapsed = gameTime - entry.warpStartTick;

        if (elapsed == WARP_SECOND_CLICK) {
            playClickAt(player, entry.warpSpot.add(0.0, 1.8, 0.0));
        }

        if (elapsed < WARP_DELAY) {
            return;
        }

        var spot = entry.warpSpot;
        entry.warpStartTick = -1;
        entry.warpSpot = null;

        if (
            entry.hunterId != null && level.getEntity(
                entry.hunterId
            ) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja hunter
                && hunter.isAlive()
        ) {
            // ⚠ The spot is re-checked: the player had a second and a half to build, or a door to shut. If it is no
            // longer good a fresh one is found; if there is none, the warp is simply lost for tonight.
            var landing = hunter.level().noCollision(hunter, hunter.getDimensions(hunter.getPose()).makeBoundingBox(spot))
                ? spot
                : findWarpSpot(level, player, hunter);

            if (landing != null) {
                arrive(level, player, hunter, landing);
            }
        }
    }

    /** The Hunter appears at the spot in a burst of smoke, with the smoke bomb, cloaked and facing the player. */
    private static void arrive(
        ServerLevel level,
        ServerPlayer player,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter,
        net.minecraft.world.phys.Vec3 spot
    ) {
        // Where it was: just a wisp, it is far off and unseen.
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.SMOKE,
            hunter.getX(),
            hunter.getY() + 1.0,
            hunter.getZ(),
            10,
            0.4,
            0.6,
            0.4,
            0.01
        );

        var facing = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;

        hunter.teleportTo(spot.x, spot.y, spot.z);
        hunter.setYRot(facing);
        hunter.setYHeadRot(facing);
        hunter.getNavigation().stop();
        com.predator.common.gameplay.cloak.PredatorCloakManager.engage(hunter);
        hunter.setTarget(player);

        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
            spot.x,
            spot.y + 1.0,
            spot.z,
            40,
            1.2,
            1.0,
            1.2,
            0.02
        );
        level.playSound(null, BlockPos.containing(spot), PredatorSoundEvents.YAUTJA_SMOKE_BOMB.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** One click from a given spot, heard only by this player — the same private click as phase 1, placed by hand. */
    private static void playClickAt(ServerPlayer player, net.minecraft.world.phys.Vec3 at) {
        var random = player.getRandom();

        player.connection.send(
            new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(PredatorSoundEvents.YAUTJA_CLICK.get()),
                SoundSource.HOSTILE,
                at.x,
                at.y,
                at.z,
                1.0F,
                0.9F + random.nextFloat() * 0.2F,
                random.nextLong()
            )
        );
    }

    /**
     * Behind the player (more than 90 degrees from where they look), 4-8 blocks away, on solid ground with three blocks
     * of headroom (a yautja is 2.48 tall), and with an unbroken line from its eyes to the player — so it lands in the
     * same room, not in the wall or on the far side of it.
     */
    private static @Nullable net.minecraft.world.phys.Vec3 findWarpSpot(
        ServerLevel level,
        ServerPlayer player,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter
    ) {
        var random = player.getRandom();

        for (var attempt = 0; attempt < 32; attempt++) {
            var yaw = player.getYRot() + 180.0F + (random.nextFloat() * 2.0F - 1.0F) * 90.0F;
            var distance = WARP_MIN_DISTANCE + random.nextDouble() * (WARP_MAX_DISTANCE - WARP_MIN_DISTANCE);
            var x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            var z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;

            // Same floor as the player, or one block up or down.
            for (var dy = 0; dy <= 2; dy++) {
                var feet = BlockPos.containing(x, player.getY() + (dy == 1 ? 1 : dy == 2 ? -1 : 0), z);

                if (!isStandable(level, feet)) {
                    continue;
                }

                var target = net.minecraft.world.phys.Vec3.atBottomCenterOf(feet);
                var eyes = target.add(0.0, hunter.getEyeHeight(), 0.0);
                var clear = level.clip(
                    new net.minecraft.world.level.ClipContext(
                        eyes,
                        player.getEyePosition(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        hunter
                    )
                ).getType() == net.minecraft.world.phys.HitResult.Type.MISS;

                if (clear && level.noCollision(hunter, hunter.getDimensions(hunter.getPose()).makeBoundingBox(target))) {
                    return target;
                }
            }
        }

        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty()
            && level.getFluidState(feet).isEmpty();
    }

    private static @Nullable net.minecraft.world.entity.LivingEntity nearestDefence(
        ServerLevel level,
        ServerPlayer player,
        net.minecraft.world.entity.LivingEntity hunter
    ) {
        var defences = level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            player.getBoundingBox().inflate(DEFENCE_RADIUS),
            candidate -> candidate.isAlive() && candidate != hunter && isDefence(candidate, player)
        );

        net.minecraft.world.entity.LivingEntity nearest = null;
        var best = Double.MAX_VALUE;

        for (var candidate : defences) {
            var distance = candidate.distanceToSqr(hunter);

            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    /**
     * {@return whether this stands guard over the player} — a sentry turret, an iron golem, a wolf (or other pet) the
     * player tamed, or an avp_human marine. Matched without a compile dependency on avp_human.
     */
    private static boolean isDefence(net.minecraft.world.entity.LivingEntity candidate, ServerPlayer player) {
        if (candidate instanceof net.minecraft.world.entity.player.Player) {
            return false;
        }

        if (com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster.isSentryTurret(candidate)) {
            return true;
        }

        if (candidate instanceof net.minecraft.world.entity.animal.IronGolem) {
            return true;
        }

        if (candidate instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame() && pet.isOwnedBy(player)) {
            return true;
        }

        var key = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());

        return "avp_human".equals(key.getNamespace()) && key.getPath().contains("marine");
    }

    /**
     * The Hunter leaves: a burst of smoke where it stood, the smoke-bomb sound, a laugh, and it is gone. Used when the
     * night ends with it alive, and by the debug stop. Pass 3b's wounded escape (blood trail, waiting spot) builds on
     * this.
     */
    static void withdraw(ServerLevel level, HuntLedger ledger, HuntLedger.Entry entry, HuntLedger.Stage next) {
        var hunter = entry.hunterId == null
            ? null
            : level.getEntity(entry.hunterId) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja yautja ? yautja : null;

        if (hunter != null && hunter.isAlive()) {
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                hunter.getX(),
                hunter.getY() + 1.0,
                hunter.getZ(),
                40,
                1.5,
                1.0,
                1.5,
                0.02
            );
            level.playSound(null, hunter.blockPosition(), PredatorSoundEvents.YAUTJA_SMOKE_BOMB.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            com.predator.common.gameplay.entity.living.yautja.YautjaTaunts.taunt(hunter);
            hunter.discard();
        }

        if (next == HuntLedger.Stage.NONE) {
            ledger.endHunt(entry);
        } else {
            ledger.markHunterGone(entry, next);
        }
    }

    // ---------------------------------------------------------------- the escape (pass 3b)

    /** [stated] it waits "5 minutes" at the end of the trail. */
    private static final int WAIT_TICKS = 6000;

    /** It flees about this far from the player — [stated] "warps 32 blocks from the player". */
    private static final double ESCAPE_MIN_DISTANCE = 28.0;

    private static final double ESCAPE_MAX_DISTANCE = 36.0;

    /** Coming this close to it — with it in sight — ends the wait and starts the final fight. */
    private static final double FOUND_DISTANCE = 12.0;

    /**
     * {@return whether this Hunter may flee right now} — only during phase 2's attack ([stated] phase 3 has no escape),
     * and only once.
     */
    public static boolean mayEscape(ServerLevel level, com.predator.common.gameplay.entity.living.yautja.Yautja hunter) {
        var playerId = hunter.getHuntedPlayer();

        if (playerId == null || hunter.isHuntReturn() || hunter.hasHuntEscaped()) {
            return false;
        }

        var entry = HuntLedger.get(level.getServer()).entry(playerId);

        return entry.stage == HuntLedger.Stage.ATTACK && hunter.getUUID().equals(entry.hunterId);
    }

    /**
     * Wounded near death, it flees. [stated] "smoke bomb at its feet filling the area with smoke, then it warps ~32
     * blocks from the player, preferably outside under open sky" and leaves a trail of its glowing blood "from where it
     * fled to where it is", where it waits 5 minutes.
     * <p>
     * ⚠ If there is nowhere to go it simply fights on, and the escape is spent — the clamp that kept it at 10% is
     * lifted, so it can die.
     */
    public static void escape(ServerLevel level, com.predator.common.gameplay.entity.living.yautja.Yautja hunter) {
        hunter.setHuntEscaped(true);

        var player = level.getServer().getPlayerList().getPlayer(hunter.getHuntedPlayer());

        if (player == null || player.level() != level) {
            return;
        }

        var destination = findEscapeSpot(level, player, hunter);

        if (destination == null) {
            return;
        }

        var ledger = HuntLedger.get(level.getServer());
        var entry = ledger.entry(player.getUUID());
        var from = hunter.position();

        // The smoke bomb, at its feet: a thick cloud, the sound, and the laugh.
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
            from.x,
            from.y + 0.5,
            from.z,
            120,
            3.0,
            1.2,
            3.0,
            0.015
        );
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 60, 2.0, 1.0, 2.0, 0.02);
        level.playSound(null, hunter.blockPosition(), PredatorSoundEvents.YAUTJA_SMOKE_BOMB.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        com.predator.common.gameplay.entity.living.yautja.YautjaTaunts.taunt(hunter);

        // ⚠ The trail is laid BEFORE the move: the walking route is planned from where it stands.
        YautjaBloodTrail.lay(level, hunter, destination);

        hunter.teleportTo(destination.x, destination.y, destination.z);
        hunter.setTarget(null);
        hunter.setNoAi(true);

        // 🚨 Forget the blow that drove it off. The wait ends early if the player STRIKES it, read as "hurt by them in
        // the last second" — and the hit that dropped it to 10% was, every time, less than a second ago. So the first
        // check after the escape (half a second later) counted the escape's own wound as being found, and the Hunter
        // turned straight round and came back ([tester] "preds actually just run back at you making it kinda
        // pointless"). Cleared here, only a NEW hit at the end of the trail counts.
        hunter.setLastHurtByMob(null);

        entry.waitUntil = level.getGameTime() + WAIT_TICKS;
        ledger.setStage(entry, HuntLedger.Stage.WAITING);
    }

    /**
     * Waiting at the end of the trail. Found — the player comes within {@link #FOUND_DISTANCE} with it in sight, or
     * strikes it — and the last fight begins, there and then. Not found in 5 minutes, and it is gone: the trail ends at
     * an empty spot, and it returns the next night.
     */
    private static void tickWaiting(ServerLevel level, ServerPlayer player, HuntLedger ledger, HuntLedger.Entry entry, long gameTime) {
        var hunter = entry.hunterId == null
            ? null
            : level.getEntity(entry.hunterId) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja yautja ? yautja : null;

        if (gameTime >= entry.waitUntil) {
            if (hunter != null) {
                hunter.discard();
            }

            ledger.markHunterGone(entry, HuntLedger.Stage.AWAITING_RETURN);

            return;
        }

        if (hunter == null || gameTime % 10 != 0) {
            return;
        }

        var struck = hunter.getLastHurtByMob() == player && hunter.tickCount - hunter.getLastHurtByMobTimestamp() < 20;
        var found = hunter.distanceToSqr(player) <= FOUND_DISTANCE * FOUND_DISTANCE && hunter.hasLineOfSight(player);

        if (struck || found) {
            hunter.setNoAi(false);
            hunter.setTarget(player);
            com.predator.common.gameplay.entity.living.yautja.YautjaSounds.heavyAttack(hunter);
            ledger.setStage(entry, HuntLedger.Stage.FINAL);
        }
    }

    /**
     * About 32 blocks from the player, on open ground — under open sky if at all possible — with room for its height
     * and no water underfoot.
     */
    private static @Nullable net.minecraft.world.phys.Vec3 findEscapeSpot(
        ServerLevel level,
        ServerPlayer player,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter
    ) {
        var random = player.getRandom();
        net.minecraft.world.phys.Vec3 covered = null;

        for (var attempt = 0; attempt < 48; attempt++) {
            var yaw = random.nextFloat() * 360.0F;
            var distance = ESCAPE_MIN_DISTANCE + random.nextDouble() * (ESCAPE_MAX_DISTANCE - ESCAPE_MIN_DISTANCE);
            var x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            var z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
            var column = BlockPos.containing(x, player.getY(), z);

            if (!level.hasChunkAt(column)) {
                continue;
            }

            var groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(),
                column.getZ()
            );
            var surface = new BlockPos(column.getX(), groundY, column.getZ());

            // Open sky first: the surface itself.
            if (Math.abs(groundY - player.getY()) <= 24 && isStandable(level, surface)) {
                return net.minecraft.world.phys.Vec3.atBottomCenterOf(surface);
            }

            // Otherwise somewhere under cover at the player's level.
            if (covered == null) {
                for (var dy = -3; dy <= 3; dy++) {
                    var feet = column.atY((int) Math.floor(player.getY()) + dy);

                    if (
                        isStandable(level, feet)
                            && level.noCollision(
                                hunter,
                                hunter.getDimensions(hunter.getPose()).makeBoundingBox(net.minecraft.world.phys.Vec3.atBottomCenterOf(feet))
                            )
                    ) {
                        covered = net.minecraft.world.phys.Vec3.atBottomCenterOf(feet);
                        break;
                    }
                }
            }
        }

        return covered;
    }

    /** {@return whether this yautja is the Hunter its player's hunt is waiting on right now} */
    public static boolean isCurrentHunter(ServerLevel level, com.predator.common.gameplay.entity.living.yautja.Yautja yautja) {
        var player = yautja.getHuntedPlayer();

        if (player == null) {
            return true;
        }

        var entry = HuntLedger.get(level.getServer()).entry(player);

        return (entry.stage == HuntLedger.Stage.ATTACK
            || entry.stage == HuntLedger.Stage.RETURN_ATTACK
            || entry.stage == HuntLedger.Stage.WAITING
            || entry.stage == HuntLedger.Stage.FINAL) && yautja.getUUID().equals(entry.hunterId);
    }

    /**
     * The Hunter is dead — the hunt is won. Honor for the kill by its tier, the ladder climbs, the two post-hunt
     * advancements, and the hunt is over until the next full moon.
     */
    public static void onHunterKilled(
        ServerLevel level,
        com.predator.common.gameplay.entity.living.yautja.Yautja hunter,
        net.minecraft.world.damagesource.DamageSource source
    ) {
        var playerId = hunter.getHuntedPlayer();

        if (playerId == null) {
            return;
        }

        var server = level.getServer();
        var ledger = HuntLedger.get(server);
        var entry = ledger.entry(playerId);

        if (!hunter.getUUID().equals(entry.hunterId)) {
            return;
        }

        // ⚠ Only the player's OWN kill is a won hunt. A Hunter that falls into the void, burns, or is torn apart by
        // xenos ends the hunt — the player survived it — but earns no kill honor, no trophy and no climb up the
        // ladder. The killer is read through projectiles and grenades to whoever threw or fired them.
        var killedByPlayer = source.getEntity() != null && playerId.equals(source.getEntity().getUUID());
        var player = server.getPlayerList().getPlayer(playerId);

        if (killedByPlayer) {
            var honorLedger = HonorLedger.get(server);
            var honor = honorLedger.entry(playerId).honor() + YautjaHonorConfig.killHonor(HunterLadder.rungOf(hunter.getTier()));

            honorLedger.setHonor(playerId, honor, YautjaHonorConfig.unlockThreshold());
            HunterLadder.unlock(entry, honor);
            HunterLadder.climb(entry);
        }

        ledger.endHunt(entry);

        if (player != null) {
            YautjaHonor.award(player, YautjaHonor.SURVIVE_THE_HUNT);

            if (killedByPlayer) {
                YautjaHonor.award(player, YautjaHonor.HUNTERS_TROPHY);
            }
        }
    }

    /**
     * Any death. If the Hunter has killed its prey, the hunt is over — it leaves, and the ladder does not move. (Pass
     * 3c leaves the skinned corpse in place of the player's drops.) A player who dies to anything else is simply
     * paused, as always: the Hunter waits.
     */
    public static void onDeath(
        ServerLevel level,
        net.minecraft.world.entity.LivingEntity victim,
        net.minecraft.world.damagesource.DamageSource source
    ) {
        if (
            !(victim instanceof ServerPlayer player)
                || !(source.getEntity() instanceof com.predator.common.gameplay.entity.living.yautja.Yautja killer)
                || !player.getUUID().equals(killer.getHuntedPlayer())
        ) {
            return;
        }

        var ledger = HuntLedger.get(level.getServer());
        var entry = ledger.entry(player.getUUID());

        if (!killer.getUUID().equals(entry.hunterId)) {
            return;
        }

        withdraw(level, ledger, entry, HuntLedger.Stage.NONE);
    }

    /** Debug: the phase-2 Hunter is wounded to the threshold and flees. {@return whether it did} */
    public static boolean forceEscape(ServerPlayer player) {
        var entry = HuntLedger.get(player.server).entry(player.getUUID());
        var level = player.server.overworld();

        if (
            entry.hunterId == null || !(level.getEntity(
                entry.hunterId
            ) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja hunter)
                || !mayEscape(level, hunter)
        ) {
            return false;
        }

        hunter.setHealth(hunter.getMaxHealth() * com.predator.common.gameplay.entity.living.yautja.Yautja.ESCAPE_HEALTH_FRACTION);
        escape(level, hunter);

        return entry.stage == HuntLedger.Stage.WAITING;
    }

    /** Debug: the Hunter that is out warps in now. {@return whether it did} */
    public static boolean forceWarp(ServerPlayer player) {
        var entry = HuntLedger.get(player.server).entry(player.getUUID());
        var level = player.server.overworld();

        if (
            entry.hunterId == null || player.serverLevel() != level
                || !(level.getEntity(entry.hunterId) instanceof com.predator.common.gameplay.entity.living.yautja.Yautja hunter)
        ) {
            return false;
        }

        return beginWarp(level, player, entry, hunter);
    }

    /** {@return whether this death is the player's own Hunter killing them} — read before the hunt is ended. */
    public static boolean isOwnHunterKill(ServerLevel level, ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        if (
            !(source.getEntity() instanceof com.predator.common.gameplay.entity.living.yautja.Yautja killer)
                || !player.getUUID().equals(killer.getHuntedPlayer())
        ) {
            return false;
        }

        return killer.getUUID().equals(HuntLedger.get(level.getServer()).entry(player.getUUID()).hunterId);
    }

    /** Debug: ends a player's hunt, sending away any Hunter that is out. */
    public static void stopHunt(ServerPlayer player) {
        var ledger = HuntLedger.get(player.server);
        withdraw(player.server.overworld(), ledger, ledger.entry(player.getUUID()), HuntLedger.Stage.NONE);
    }

    /** Debug: sends the Hunter now, ignoring the clock. {@return whether one was sent} */
    public static boolean forceAttack(ServerPlayer player, boolean returning) {
        var ledger = HuntLedger.get(player.server);
        var entry = ledger.entry(player.getUUID());

        withdraw(player.server.overworld(), ledger, entry, entry.stage);

        return player.serverLevel() == player.server.overworld() && sendHunter(
            player.serverLevel(),
            player,
            ledger,
            entry,
            returning,
            false
        );
    }

    // ---------------------------------------------------------------- sleep

    /**
     * {@return whether this player may not sleep} [stated] sleep is refused on the nights the Hunter attacks, so an
     * attack can never be slept through. Every other night — the Hunter's Moon included — is fine.
     */
    public static boolean refusesSleep(ServerPlayer player) {
        var stage = HuntLedger.get(player.server).entry(player.getUUID()).stage;

        return stage == HuntLedger.Stage.AWAITING_ATTACK
            || stage == HuntLedger.Stage.ATTACK
            || stage == HuntLedger.Stage.AWAITING_RETURN
            || stage == HuntLedger.Stage.RETURN_ATTACK
            || stage == HuntLedger.Stage.WAITING
            || stage == HuntLedger.Stage.FINAL;
    }

    // ---------------------------------------------------------------- clicks

    /**
     * One click, heard only by the hunted player, from somewhere behind them 10-16 blocks out. Sent as a packet to that
     * player alone, so nobody else nearby hears a thing.
     */
    public static void playClick(ServerPlayer player, ServerLevel level) {
        var random = player.getRandom();
        var yaw = (player.getYRot() + 180.0F + (random.nextFloat() - 0.5F) * 120.0F) * Mth.DEG_TO_RAD;
        var distance = 10.0 + random.nextDouble() * 6.0;
        var x = player.getX() - Mth.sin(yaw) * distance;
        var z = player.getZ() + Mth.cos(yaw) * distance;
        var y = player.getY() + random.nextDouble() * 5.0 - 1.0;

        player.connection.send(
            new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(PredatorSoundEvents.YAUTJA_CLICK.get()),
                SoundSource.HOSTILE,
                x,
                y,
                z,
                1.2F,
                0.9F + random.nextFloat() * 0.2F,
                random.nextLong()
            )
        );
    }

    /**
     * Shows the hunted player a cloaked yautja standing somewhere ahead of them, 40-56 blocks out, facing them. It is a
     * client-only picture (see {@code HunterGlimpseClient}) — sent to this one player, nothing spawned on the server.
     * Skipped silently if no spot ahead has ground to stand on.
     */
    public static void showGlimpse(ServerPlayer player, ServerLevel level) {
        showGlimpse(level, player);
    }

    private static void showGlimpse(ServerLevel level, ServerPlayer player) {
        var random = player.getRandom();

        for (var attempt = 0; attempt < 10; attempt++) {
            var yaw = player.getYRot() + (random.nextFloat() * 2.0F - 1.0F) * GLIMPSE_HALF_ANGLE;
            var distance = GLIMPSE_MIN_DISTANCE + random.nextDouble() * (GLIMPSE_MAX_DISTANCE - GLIMPSE_MIN_DISTANCE);
            var x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            var z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
            var column = BlockPos.containing(x, player.getY(), z);

            if (!level.hasChunkAt(column)) {
                continue;
            }

            var groundY = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(),
                column.getZ()
            );

            if (Math.abs(groundY - player.getY()) > 20) {
                continue;
            }

            var feet = new BlockPos(column.getX(), groundY, column.getZ());

            if (
                !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir() || !level.getFluidState(feet.below())
                    .isEmpty()
            ) {
                continue;
            }

            // Facing the player.
            var facing = (float) (Mth.atan2(player.getZ() - (feet.getZ() + 0.5), player.getX() - (feet.getX() + 0.5)) * Mth.RAD_TO_DEG)
                - 90.0F;

            com.predator.Predator.MOD.networking()
                .sendToClient(
                    player,
                    new com.predator.common.network.packet.S2CHunterGlimpsePayload(
                        feet.getX() + 0.5,
                        feet.getY(),
                        feet.getZ() + 0.5,
                        facing
                    )
                );
            return;
        }
    }

    private static int randomClickDelay(ServerPlayer player) {
        return CLICK_MIN_TICKS + player.getRandom().nextInt(CLICK_MAX_TICKS - CLICK_MIN_TICKS);
    }

    // ---------------------------------------------------------------- the calling card

    private static void placeCallingCardByBed(ServerLevel level, ServerPlayer player, HuntLedger ledger, HuntLedger.Entry entry) {
        var bed = player.getRespawnPosition();

        // ⚠ And only a LOADED bed: reading blocks round an unloaded one would load (or generate) its chunks on the
        // spot.
        if (bed == null || player.getRespawnDimension() != Level.OVERWORLD || !level.isLoaded(bed)) {
            // No bed to wake beside: fall back to the usual spot during the day.
            return;
        }

        SkinnedCorpses.placeCallingCardNearBed(level, bed).ifPresent(pos -> ledger.markCallingCardPlaced(entry));
    }

    /**
     * Leaves the calling card at the player's home: outside the door nearest their most-visited spot, or at that spot
     * if there is no door. Waits until they are well away from it so it is never placed in front of them, unless
     * {@code force} (late in the day).
     */
    private static void tryPlaceCallingCardAtHome(
        ServerLevel level,
        ServerPlayer player,
        HuntLedger ledger,
        HuntLedger.Entry entry,
        boolean force
    ) {
        var home = entry.favouriteSpot();

        if (home == null) {
            home = player.blockPosition();
        }

        if (!force && player.blockPosition().closerThan(home, CALLING_CARD_AWAY_DISTANCE)) {
            return;
        }

        if (!level.isLoaded(home)) {
            if (!force) {
                return;
            }

            home = player.blockPosition();
        }

        var door = findOutsideOfDoor(level, home);
        var placed = SkinnedCorpses.placeCallingCard(level, door != null ? door : home);

        if (placed.isEmpty() && door != null) {
            placed = SkinnedCorpses.placeCallingCard(level, home);
        }

        // ⚠ Marked placed even when nowhere would take it, so a hunt can never stall on a spot with no room.
        if (placed.isPresent() || force) {
            ledger.markCallingCardPlaced(entry);
        }
    }

    /** {@return the block just outside the door nearest {@code home} — the side open to the sky — or null if none} */
    private static @Nullable BlockPos findOutsideOfDoor(ServerLevel level, BlockPos home) {
        BlockPos nearest = null;
        var nearestDistance = Double.MAX_VALUE;

        for (
            var pos : BlockPos.betweenClosed(
                home.offset(-DOOR_SEARCH_RADIUS, -4, -DOOR_SEARCH_RADIUS),
                home.offset(DOOR_SEARCH_RADIUS, 4, DOOR_SEARCH_RADIUS)
            )
        ) {
            var state = level.getBlockState(pos);

            if (
                !state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
            ) {
                continue;
            }

            var distance = pos.distSqr(home);

            if (distance < nearestDistance) {
                nearest = pos.immutable();
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return null;
        }

        Direction facing = level.getBlockState(nearest).getValue(DoorBlock.FACING);
        var front = nearest.relative(facing);
        var back = nearest.relative(facing.getOpposite());

        if (level.canSeeSky(back) && !level.canSeeSky(front)) {
            return back;
        }

        return front;
    }

    // ---------------------------------------------------------------- debug

    /** Debug: starts the Hunter's Moon for this player right now, whatever the moon or their honor. */
    public static void forceMoon(ServerPlayer player) {
        var ledger = HuntLedger.get(player.server);
        var level = player.server.overworld();
        ledger.beginMoon(ledger.entry(player.getUUID()), level.getDayTime() / 24000L);
        player.sendSystemMessage(
            Component.literal("The hunters moon shines a foreboding presence").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)
        );
    }
}
