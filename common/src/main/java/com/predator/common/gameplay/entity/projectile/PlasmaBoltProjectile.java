package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The bolt the shoulder caster fires: a flat, fast, straight line that hits one thing and stops.
 * <h2>⚠ The hand caster's bolt is the exception</h2> A bolt fired by the HAND CASTER (a player) really explodes on a
 * block (3/4 TNT). EVERY bolt — the yautja's too — shows an explosion's burst and boom on impact ([stated] "so it feels
 * more impactful"), but the yautja's never damages, breaks or burns anything by it. See {@link #setExplosive}.
 * <h2>What it deliberately is not</h2> ⚠ It does <b>not</b> explode, set fires, break blocks or splash. His rule, and
 * it is a gameplay rule rather than a cosmetic one: "doesnt explode or cause any explosion damage so its ok close
 * quarters if need be" is what makes it safe for the caster to keep firing while the yautja itself is in melee.
 * Anything added here that carries area effect breaks that guarantee.
 * <h2>⚠⚠ Why ThrowableProjectile and NOT Projectile</h2> {@code Projectile}'s own constructor is PACKAGE-PRIVATE in
 * vanilla. NeoForge's access transformer widens it to protected, so extending {@code Projectile} compiles against a
 * NeoForge-mapped jar and then fails in the common module, which is built against plain vanilla:
 * "Projectile(EntityType,Level) is not public in Projectile". {@code ThrowableProjectile} has a protected constructor
 * in vanilla, which is what makes this compile on both loaders. Do not "simplify" it back down to {@code Projectile}.
 * <p>
 * {@code AbstractHurtingProjectile} was the other candidate and is worse: it re-applies its own acceleration and spawns
 * its own trail every tick, which would argue with {@link PlasmaCaster#spawnBoltTrail}.
 */
public class PlasmaBoltProjectile extends ThrowableProjectile {

    private final PlasmaBoltAnimationDispatcher animationDispatcher = new PlasmaBoltAnimationDispatcher(this);

    private int life;

    /** Client-side edge guard so the looping clip is asked for once rather than every frame of flight. */
    private boolean dispatchedFireAnimation;

    public PlasmaBoltProjectile(EntityType<? extends PlasmaBoltProjectile> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public PlasmaBoltProjectile(Level level, LivingEntity shooter) {
        this(PredatorEntityTypes.PLASMA_BOLT.get(), level);
        setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // No per-bolt state worth syncing — position and velocity come from the vanilla spawn packet, and the model
        // is the same for every bolt.
    }

    /** Straight line, unaffected by weather or altitude. */
    /** Damage fixed by whoever fired it; below zero = the normal rule. Not saved: a bolt lives three seconds. */
    private float damageOverride = -1.0F;

    public void setDamageOverride(float damage) {
        this.damageOverride = damage;
    }

    /**
     * A HAND CASTER shot. [stated] "it doesnt seem to have enough umph when it hits can you make a large explosion
     * effect when it hits a mob and when it hits a block have it destroy 3/4 a tnt explosion."
     * <p>
     * ⚠⚠ ONLY the hand caster's bolts REALLY explode. The yautja's shoulder caster fires this same bolt, and its rule
     * stands — [stated] "explode when player fires not when yautja but still have the explode visual effect and boom
     * sound on hit": its bolts get the burst and boom only (see boom). Set by HandCasterItem when it fires; not saved
     * (a bolt lives three seconds).
     */
    private boolean explosive;

    /**
     * Oct 8 - blocks a SIEGE bolt may still burn through (YautjaSiege): a yautja that cannot climb to prey above it
     * fires up through the floor under them. Not saved - a bolt lives three seconds. Zero for every ordinary bolt.
     */
    private int siegeBreaks;

    /** Oct 8 - makes this a siege bolt that burns through up to {@code blocks} breakable blocks before it stops. */
    public void setSiegeBreaks(int blocks) {
        this.siegeBreaks = Math.max(0, blocks);
    }

    public void setExplosive(boolean explosive) {
        this.explosive = explosive;
    }

    /** [stated] "3/4 a tnt explosion" — TNT is 4.0. */
    public static float HAND_CASTER_EXPLOSION_POWER = 3.0F;

    /**
     * The LOOK and SOUND of an explosion, with none of its effect — vanilla's big TNT burst and its boom. Every bolt
     * that does not really explode gets this on impact, so a hit always lands with weight.
     */
    private void boom(ServerLevel level, Vec3 at) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(
            null,
            at.x,
            at.y,
            at.z,
            net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
            getSoundSource(),
            4.0F,
            (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F
        );
    }

    public void launch(Vec3 direction, double speed) {
        var normalized = direction.normalize().scale(speed);

        setDeltaMovement(normalized);

        // ⚠⚠ SET DIRECTLY, NOT VIA updateRotation() — that is why the bolt faced one fixed direction.
        // Projectile.updateRotation runs its target angle through lerpRotation, which blends at 0.2, so it
        // only ever moves 20% of the way toward the real heading per call. At spawn that leaves the model at a
        // fifth of the correct yaw, and a bolt that lives under a second never converges. The heading is known
        // exactly here, so there is nothing to interpolate toward.
        var horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);

        setYRot((float) (Mth.atan2(normalized.x, normalized.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(normalized.y, horizontal) * Mth.RAD_TO_DEG));

        // ⚠ Set explicitly rather than left to updateRotation's interpolation: the bolt lives for well under a second
        // at close range, so a rotation that eases in over several ticks is visible as the model swinging round.
        this.yRotO = getYRot();
        this.xRotO = getXRot();
    }

    /**
     * Straight line, unaffected by altitude.
     * <p>
     * ⚠ Zero, not vanilla's 0.03. A plasma bolt that arcs is a thrown rock.
     */
    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        // The parent moves it, runs the hit sweep and calls onHit, which lands in the overrides below.
        // ⚠ It also calls updateRotation(), which lerps at 0.2 toward the heading. Harmless now that launch()
        // sets the true angle up front — the lerp simply has nowhere to move — but that is WHY it is harmless,
        // not an accident.
        super.tick();

        if (isRemoved()) {
            return;
        }

        if (level().isClientSide && !dispatchedFireAnimation) {
            dispatchedFireAnimation = true;
            animationDispatcher.fire();
        }

        if (level() instanceof ServerLevel serverLevel) {
            PlasmaCaster.spawnBoltTrail(serverLevel, position());
        }

        life++;

        if (life > PlasmaCaster.BOLT_LIFETIME_TICKS || !level().hasChunkAt(blockPosition())) {
            discard();
        }
    }

    /**
     * ⚠ A yautja never takes a bolt from another yautja's caster. That is not a courtesy — the caster fires on its own
     * target while its owner is in melee, so without this a pair of hunters working the same fight would shoot each
     * other in the back constantly.
     */
    @Override
    protected boolean canHitEntity(@NotNull Entity entity) {
        // 🚨 [stated, relayed] "it appears the yautja are immune to the plasma caster ... and the bolt phases through
        // them". This read !(entity instanceof Yautja) — NO bolt could ever strike ANY yautja, whoever fired it, so the
        // hand caster's shots passed straight through them and dealt nothing. The rule exists so a yautja's shoulder
        // caster does not hit its fellow hunters; it now applies only when the SHOOTER is a yautja. A player's bolt
        // hits
        // them like anything else.
        var yautjaFriendlyFire = entity instanceof Yautja && getOwner() instanceof Yautja;

        return super.canHitEntity(entity) && !yautjaFriendlyFire && entity != getOwner();
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide) {
            return;
        }

        var owner = getOwner();
        var source = owner instanceof LivingEntity livingOwner
            ? damageSources().mobProjectile(this, livingOwner)
            : damageSources().magic();

        // ⚠⚠ FROM THE SHOOTER'S TIER, not the flat constant. The caster is 9 damage on a Youngblood and 24 on a
        // Clan Leader — a bolt that always hit for 20 would make the weakest rank hit as hard as the strongest,
        // and it is the most dangerous thing a yautja owns.
        // ⚠ Falls back to the constant when the shooter is gone (unloaded, killed mid-flight), so a bolt in the
        // air never silently does zero.
        // ⚠ The hand caster sets its own, scaled by how long it was charged (HandCasterItem).
        var damage = damageOverride >= 0.0F
            ? damageOverride
            : getOwner() instanceof Yautja yautja
                ? (float) yautja.getTier().casterDamage(yautja.isHunter())
                : PlasmaCaster.BOLT_DAMAGE;

        result.getEntity().hurt(source, damage);

        if (level() instanceof ServerLevel serverLevel) {
            PlasmaCaster.spawnImpact(serverLevel, result.getLocation());

            // [stated] "a large explosion effect when it hits a mob" — for EVERY bolt, the yautja's included ([stated]
            // "still
            // have the explode visual effect and boom sound on hit. so it feels more impactful"). An EFFECT: the bolt's
            // own
            // damage is the hit; nothing extra is dealt and no block is touched.
            boom(serverLevel, result.getLocation());
        }

        discard();
    }

    /**
     * {@return false, always — nothing deflects a plasma bolt}
     * <p>
     * ⚠⚠ His ruling: the bolt is not deflectable, the shuriken is. Vanilla deflection is a real path, not a
     * hypothetical: {@code Projectile.deflect} is reachable from a Breeze, a wind charge, a player, and from
     * {@code Projectile.onHit} itself when the thing struck is in {@code EntityTypeTags.REDIRECTABLE_PROJECTILE}. Any
     * of those would otherwise send a charged shoulder-cannon shot back at the hunter that fired it.
     * <p>
     * ⚠ Overriding the method rather than the tag. A tag governs whether THIS projectile can be redirected by hitting
     * it; the deflection paths above act on the projectile directly and would ignore it. Returning false here refuses
     * all of them, and refuses them WITHOUT calling super — {@code Projectile.deflect} applies the deflection,
     * reassigns the owner and returns true unconditionally, so there is no partial form to defer to.
     */
    @Override
    public boolean deflect(
        @NotNull ProjectileDeflection deflection,
        @Nullable Entity entity,
        @Nullable Entity owner,
        boolean fromAttack
    ) {
        return false;
    }

    /** Stops on whatever it hits and burns out there. No crater, no scorch, no block damage. */
    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        if (!level().isClientSide) {
            var pos = result.getBlockPos();

            // Oct 8 - a SIEGE bolt burns through the floor under the yautja's prey, like it burns through leaves, and
            // carries on - only where the yautja may break blocks (mobGriefing, hardness window, yautja_unbreakable),
            // and the block DROPS, so no one's build is deleted outright.
            if (siegeBreaks > 0 && com.predator.common.gameplay.entity.living.yautja.YautjaSiege.mayBreak(level(), pos)) {
                siegeBreaks--;
                level().destroyBlock(pos, true, getOwner());

                if (level() instanceof ServerLevel serverLevel) {
                    PlasmaCaster.spawnBoltTrail(serverLevel, Vec3.atCenterOf(pos));
                }

                return;
            }

            // ⚠⚠ HIS RULING: a plasma bolt does NOT quietly pass through foliage the way a thrown blade does.
            // It burns through. So the leaf is destroyed and the bolt CARRIES ON — no discard — which is what
            // lets it reach prey in a canopy while leaving a hole punched through the branches behind it.
            //
            // ⚠ Dropless. Burned leaves leave nothing; dropping saplings from every shot would turn the
            // caster into a harvesting tool.
            if (level().getBlockState(pos).is(BlockTags.LEAVES)) {
                level().destroyBlock(pos, false);

                if (level() instanceof ServerLevel serverLevel) {
                    PlasmaCaster.spawnBoltTrail(serverLevel, Vec3.atCenterOf(pos));
                }

                return;
            }

            PlasmaCaster.spawnImpact((ServerLevel) level(), position());

            // [stated] "when it hits a block have it destroy 3/4 a tnt explosion." A real explosion at 3.0 (TNT is
            // 4.0),
            // breaking blocks as TNT does — a player's weapon, so TNT mode, not the mob-griefing rule.
            // ⚠ The SHOOTER is spared the blast (like the trip mine spares its owner), or firing at a wall in front of
            // you
            // would be suicide. Everyone else in range is caught by it.
            if (!explosive) {
                // The yautja's caster: [stated] it "doesnt explode or cause any explosion damage" — so the LOOK and the
                // SOUND of one only ([stated] "still have the explode visual effect and boom sound on hit").
                boom((ServerLevel) level(), position());
            } else {
                var shooter = getOwner();
                var spareShooter = new net.minecraft.world.level.ExplosionDamageCalculator() {

                    @Override
                    public boolean shouldDamageEntity(@NotNull net.minecraft.world.level.Explosion explosion, @NotNull Entity entity) {
                        return entity != shooter;
                    }
                };

                level().explode(
                    this,
                    null,
                    spareShooter,
                    getX(),
                    getY(),
                    getZ(),
                    HAND_CASTER_EXPLOSION_POWER,
                    false,
                    net.minecraft.world.level.Level.ExplosionInteraction.TNT
                );
            }
        }

        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }
}
