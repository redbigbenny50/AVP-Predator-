package com.predator.common.gameplay.entity.projectile;

import com.blib.api.common.block.v1.BlockBreakProgressManager;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The returning disc: seeks, bounces between nearby targets, then comes home.
 * <h2>His spec</h2> "the smart disc returns to you when its thrown and it can hit multiple targets at once if they are
 * close, it like bounces between them."
 * <h2>⚠⚠ What was wrong: none of that existed</h2> Both tracking calls in {@code tick} were COMMENTED OUT, so the disc
 * neither homed nor returned — it flew in a straight line like a snowball. And it could not have bounced even with them
 * enabled, because {@code onHitEntity} fell through to {@code ThrowableItemProjectile}, which discards on contact: the
 * disc died on its first hit.
 * <p>
 * The helper it used to call was broken in its own right. {@code ItemGoalUtil.trackToLivingEntity} took
 * {@code livingEntities.getFirst()} — an arbitrary entity from an unordered query, not the nearest — and re-homed every
 * tick with no memory of what it had already hit, so it would have locked onto one target and orbited it forever.
 * {@code trackToOwnerEntity} handed back a fresh default instance rather than the disc that was thrown, losing any name
 * or enchantment. Both are replaced by the state machine below and are no longer called.
 * <h2>The two phases</h2> <b>Outbound</b> — steer toward the nearest valid target not yet struck, within
 * {@link #SEEK_RANGE}. On a hit, record the victim and look for the next one. <b>Returning</b> — after
 * {@link #MAX_BOUNCES} hits, a block strike, or the flight timing out, steer back to the owner and hand the stack over
 * on contact.
 */
public class SmartDiscProjectile extends ThrowableItemProjectile implements LeafPiercing {

    /**
     * How many DISTINCT entities one throw may strike before it must come home.
     * <p>
     * Distinct, not total: {@link #struck} is keyed by UUID and the seek skips anyone already hit, so the disc cannot
     * pad this count by bouncing back onto the same mob.
     */
    private static final int MAX_BOUNCES = 3;

    /** Blocks. How far the disc looks for its next victim — his "if they are close". */
    private static final double SEEK_RANGE = 8.0;

    /** Blocks per tick while hunting. */
    private static final double SEEK_SPEED = 0.9;

    /** Blocks per tick on the way home. Faster, so a returning disc does not linger in the fight. */
    private static final double RETURN_SPEED = 1.1;

    /** Ticks before it gives up hunting and returns regardless. */
    private static final int OUTBOUND_TICKS = 120;

    /** Ticks after which a disc that cannot reach its owner drops rather than orbiting forever. */
    private static final int MAX_LIFETIME_TICKS = 400;

    private static final float DAMAGE = 5.0F;

    /** Deep red on the way out. */
    private static final Vector3f TRAIL_RED = new Vector3f(0.78F, 0.09F, 0.09F);

    /** Brighter coming back, so the leg that is heading AT you reads differently. */
    private static final Vector3f TRAIL_RED_RETURNING = new Vector3f(1.0F, 0.26F, 0.20F);

    /** Smaller than the 0.25-block disc it marks. */
    private static final float TRAIL_PARTICLE_SIZE = 0.35F;

    private static final int TRAIL_INTERVAL_TICKS = 2;

    private static final float BLOCK_DAMAGE = 2.0F;

    /**
     * Who has already been struck this throw.
     * <p>
     * ⚠⚠ THIS IS WHAT MAKES IT BOUNCE RATHER THAN ORBIT. Without a memory of previous victims the seek always finds the
     * entity it just hit — it is the nearest, after all — and the disc pinballs against one mob until it expires.
     */
    private final List<UUID> struck = new ArrayList<>();

    /** Pitch wander on the impact sounds. */
    private static final float HIT_PITCH_SPREAD = 0.14F;

    private boolean returning;

    /** ⚠ Client-side only: the flight hum is started once, on the first tick this disc is seen. */
    private boolean loopStarted;

    public SmartDiscProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public SmartDiscProjectile(Level level, LivingEntity livingEntity) {
        super(PredatorEntityTypes.SMART_DISC.get(), livingEntity, level);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return PredatorItems.SMART_DISC.get();
    }

    /** ⚠ A disc in flight must not arc; the steering below is the only thing that changes its course. */
    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            // ⚠ STARTED ON THE CLIENT, ONCE. The hum has to follow the disc and stop when it is gone, which a
            // server playSound cannot do — see SmartDiscSoundInstance.
            if (!loopStarted) {
                loopStarted = true;
                com.predator.client.sound.SmartDiscClientSounds.startLoop(this);
            }

            return;
        }

        if (isRemoved()) {
            return;
        }

        if (getOwner() == null || tickCount > MAX_LIFETIME_TICKS) {
            dropDisc();
            discard();

            return;
        }

        if (!returning && tickCount > OUTBOUND_TICKS) {
            turnForHome();
        }

        if (returning) {
            steerHome();
        } else {
            steerToNextTarget();
        }

        spawnTrail();
    }

    /**
     * A red trail, so the disc can be followed by eye.
     * <p>
     * ⚠ SIZE 0.35 AGAINST A 0.25-BLOCK ENTITY, and that ratio is the point. The plasma bolt shipped with a 0.8 trail on
     * a 0.73-block model and the first thing back from testing was that the trail looked bigger than the projectile. A
     * tracer should mark where the thing is, not replace it.
     * <p>
     * ⚠ Every other tick, one particle. This disc lives up to {@value #MAX_LIFETIME_TICKS} ticks and doubles back on
     * its own path, so a per-tick trail would draw a solid rope through the fight rather than a flight line.
     * <p>
     * Brighter on the way home: the return leg is the half a player needs to read, because that is when the disc is
     * coming at them rather than away.
     */
    private void spawnTrail() {
        if (!(level() instanceof ServerLevel serverLevel) || tickCount % TRAIL_INTERVAL_TICKS != 0) {
            return;
        }

        serverLevel.sendParticles(
            new DustParticleOptions(returning ? TRAIL_RED_RETURNING : TRAIL_RED, TRAIL_PARTICLE_SIZE),
            getX(),
            getY(),
            getZ(),
            1,
            0.03,
            0.03,
            0.03,
            0.0
        );
    }

    /** Hunts the nearest thing it has not already hit. Keeps its current heading when there is nothing to seek. */
    private void steerToNextTarget() {
        var target = findNextTarget();

        if (target == null) {
            return;
        }

        var toTarget = target.getEyePosition().subtract(position()).normalize();

        setDeltaMovement(toTarget.scale(SEEK_SPEED));
    }

    /**
     * Turns the disc for home.
     * <p>
     * ⚠ ONE FUNNEL for the three reasons it can start coming back — outbound time expiring, running out of targets, and
     * hitting something it cannot pass — so the flag is set in exactly one place.
     * <p>
     * ⚠⚠ THE RETURN SOUND IS NOT HERE. [stated] "the return shouldnt play per hit only when it returns to the player".
     * Turning for home happens out where the fighting is, often seconds before the disc arrives; the sound belongs to
     * the CATCH, so it lives in returnToOwner.
     */
    /**
     * ⚠ The impacts wander in pitch like the whip's, so a disc bouncing between five targets is not five identical
     * clicks. The catch and the flight loop are left alone.
     */
    private float variedPitch() {
        return com.predator.common.gameplay.whip.WhipCord.variedPitch(random, HIT_PITCH_SPREAD);
    }

    private void turnForHome() {
        if (returning) {
            return;
        }

        returning = true;
    }

    private void steerHome() {
        var owner = getOwner();

        if (owner == null) {
            return;
        }

        var toOwner = owner.getEyePosition().subtract(position());

        setDeltaMovement(toOwner.normalize().scale(RETURN_SPEED));

        if (getBoundingBox().inflate(0.5).intersects(owner.getBoundingBox())) {
            returnToOwner(owner);
        }
    }

    /**
     * {@return the nearest valid target not yet struck this throw, or null}
     * <p>
     * ⚠ NEAREST, not "first in the list". The previous helper took whatever the query happened to return first, which
     * is unordered — the disc would fly past a mob beside it to reach one further away.
     */
    private @Nullable LivingEntity findNextTarget() {
        var candidates = level().getEntitiesOfClass(
            LivingEntity.class,
            getBoundingBox().inflate(SEEK_RANGE),
            candidate -> candidate != getOwner()
                && candidate.isAlive()
                && !struck.contains(candidate.getUUID())
                && !candidate.getType().is(PredatorEntityTypeTags.PREDATORS)
                && !BLibEntityPredicates.isInvulnerable(candidate)
        );

        LivingEntity nearest = null;
        var nearestDistance = Double.MAX_VALUE;

        for (var candidate : candidates) {
            var distance = candidate.distanceToSqr(this);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        if (level().isClientSide || !(result.getEntity() instanceof LivingEntity target) || getOwner() == null) {
            return;
        }

        // Passing through its owner on the way home is the disc being caught, not a hit.
        if (target == getOwner()) {
            returnToOwner(target);

            return;
        }

        if (struck.contains(target.getUUID())) {
            return;
        }

        // ⚠ Scaled by the THROWER'S TIER — see YautjaTier.scaleThrown. A player's throw is unchanged.
        target.hurt(damageSources().thrown(getOwner(), target), YautjaTier.scaleThrown(getOwner(), DAMAGE));

        // ⚠ AFTER the hit and AFTER the owner check above, so catching your own disc is silent of this and only a
        // real bite sounds. [stated] "when it actually hits a mob its entity".
        level()
            .playSound(
                null,
                target.blockPosition(),
                PredatorSoundEvents.SMART_DISC_HIT_ENTITY.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                variedPitch()
            );
        struck.add(target.getUUID());

        // ⚠ super.onHitEntity is NOT called, and that is the whole feature: it discards the projectile, which is why
        // the disc used to die on its first victim.
        if (struck.size() >= MAX_BOUNCES) {
            turnForHome();
        }
    }

    /** A wall ends the outbound run — a disc that clips terrain comes back rather than grinding along it. */
    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        if (level().isClientSide) {
            return;
        }

        BlockBreakProgressManager.damage(level(), result.getBlockPos(), BLOCK_DAMAGE);

        // [stated] wood for logs, metal for metal blocks, stone for stone and ore, general for anything else.
        level()
            .playSound(
                null,
                result.getBlockPos(),
                ImpactMaterial.forBlock(level().getBlockState(result.getBlockPos())),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                variedPitch()
            );
        turnForHome();
    }

    /**
     * Hands the disc back.
     * <p>
     * ⚠ Returns the ACTUAL thrown stack via {@link #getItem()}, not a fresh default instance. The old helper built a
     * new one, so a named or enchanted disc came back stripped.
     */
    private void returnToOwner(Entity owner) {
        if (isRemoved()) {
            return;
        }

        // ⚠ THE CATCH IS THE MOMENT. Played at the OWNER rather than the disc's last position, so it lands in the
        // ear of whoever caught it.
        level()
            .playSound(
                null,
                owner.blockPosition(),
                PredatorSoundEvents.SMART_DISC_RETURN.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                1.0F
            );

        var stack = discStack();

        if (owner instanceof Player player && !BLibEntityPredicates.isInvulnerable(player)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        } else if (
            owner instanceof com.predator.common.gameplay.entity.living.yautja.Yautja yautja
                && yautja.isWeaponLocked()
                && !yautja.getMainHandItem().isEmpty()
        ) {
            // ⚠⚠ A LOCKED HAND IS NOT OVERWRITTEN. Catching the disc used to REPLACE whatever the yautja held, so a
            // tester's chosen weapon was swapped out the moment an earlier disc came home. It goes into the rack
            // instead, and only if that is full is it dropped at its feet.
            if (!(yautja.getInventory().addItemStack(stack) instanceof com.blib.api.common.inventory.v1.BLibInventory.AddResult.Success)) {
                yautja.spawnAtLocation(stack);
            }
        } else if (owner instanceof LivingEntity) {
            // A yautja catches it back into its hand rather than dropping it at its feet.
            ((LivingEntity) owner).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        }

        discard();
    }

    private void dropDisc() {
        if (level().isClientSide) {
            return;
        }

        var drop = new ItemEntity(level(), getX(), getY(), getZ(), discStack());

        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
    }

    private ItemStack discStack() {
        var stack = getItem().copy();

        if (stack.isEmpty()) {
            stack = new ItemStack(PredatorItems.SMART_DISC.get());
        }

        stack.setCount(1);

        return stack;
    }
}
