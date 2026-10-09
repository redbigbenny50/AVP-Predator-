package com.predator.common.gameplay.entity.projectile;

import com.blib.api.common.block.v1.BlockBreakProgressManager;
import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * A thrown blade that survives being thrown.
 * <h2>His spec</h2> "a throwable weapon that has stacks of ammo, it works like arrows how you can throw them and then
 * pick them up with it sometimes breaking."
 * <h2>⚠⚠ What was wrong: a thrown shuriken could never be recovered</h2> {@code onHitBlock} called {@code discard()}
 * and {@code onHitEntity} fell through to {@code ThrowableItemProjectile}, which discards too. Every shuriken thrown
 * was destroyed on contact and the item was gone for good — the pickup half of the weapon did not exist. The item was
 * also {@code stacksTo(1)}, so there were no stacks of ammo to throw in the first place.
 * <p>
 * <h2>⚠⚠ NOTHING IN THE HIERARCHY DISCARDS A THROWABLE ON IMPACT — IT MUST BE DONE HERE</h2>
 * {@code ThrowableProjectile} does not override {@code onHit}, and {@code Projectile.onHitBlock} only runs the block's
 * own projectile interaction. So a hit handler that does not call {@code discard()} leaves the shuriken flying — it
 * strikes again the next tick, and the next, dropping a blade every time. That is exactly what a whole pathway littered
 * with shuriken looks like, and it was introduced by replacing the original's explicit discard with a call to
 * {@code super}, on the assumption that the parent handled it. It does not.
 * <p>
 * ⚠ Dropping the blade rather than sticking it in the wall like an arrow is deliberate. An arrow's stuck-in-block state
 * is a whole entity mode — {@code AbstractArrow} carries {@code inGround}, a shake timer and a pickup mode — whereas a
 * blade lying on the floor to be walked over reads correctly for a throwing weapon at a fraction of the machinery.
 */
public class ShurikenProjectile extends ThrowableItemProjectile implements LeafPiercing {

    /** Ticks before an unrecovered shuriken gives up. Five seconds is far past any realistic throw. */
    private static final int MAX_LIFETIME_TICKS = 300;

    /** Damage on a direct hit. Unchanged — the recovery was broken, not the balance. */
    private static final float DAMAGE = 5.0F;

    private static final float BLOCK_DAMAGE = 2.0F;

    /**
     * Chance the blade is destroyed instead of dropping — his "sometimes breaking".
     * <p>
     * At 0.15 a shuriken survives roughly six throws, so a stack is a real supply rather than something that
     * evaporates. A break is not a total loss either: the blade leaves the shard it was forged from.
     */
    private static final float BREAK_CHANCE = 0.15F;

    /**
     * How far the blade backs off the surface so its light sample stays in air.
     * <p>
     * ⚠ NOT final, deliberately: javac inlines a {@code static final} primitive at every use site, so editing one and
     * hot-swapping does nothing. A plain static field is read at runtime and CAN be hot-swapped while the game is
     * running.
     */
    private static double LODGE_BACKOFF = 0.02;

    /**
     * How far the blade appears to sink into the face, in blocks. Applied in the RENDERER, not to the entity position.
     * <p>
     * ⚠ It cannot be applied to the position: an entity whose origin is inside a solid block samples light 0 and
     * renders pure black.
     */
    public static float LODGE_VISUAL_DEPTH = -0.04F;

    /** Set the moment a blade is put on the ground, so one throw can never leave two. */
    private boolean dropped;

    /**
     * ⚠⚠ THROWN FROM CREATIVE — SAME IDEA AS AbstractArrow's CREATIVE_ONLY PICKUP. The throw did not consume a
     * shuriken, so collecting it must not create one: [stated] "in creative i keep picking them up and getting more and
     * more". A creative blade vanishes on touch without entering any inventory, and leaves no item when it breaks or
     * times out. Saved with the entity so a lodged blade keeps the rule across a reload.
     */
    private boolean creative;

    /**
     * ⚠⚠ SYNCHED, not a plain field. The spin lives in SpinningItemRenderer and is driven by tickCount, which keeps
     * climbing after the blade sticks — so a server-only flag left it spinning in place in the wall. The client has to
     * know, and synched data is what carries it.
     */
    private static final EntityDataAccessor<Boolean> LODGED = SynchedEntityData.defineId(
        ShurikenProjectile.class,
        EntityDataSerializers.BOOLEAN
    );

    public ShurikenProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public ShurikenProjectile(Level level, LivingEntity livingEntity) {
        super(PredatorEntityTypes.SHURIKEN.get(), livingEntity, level);
        // ⚠⚠ A YAUTJA'S THROW IS TREATED EXACTLY LIKE A CREATIVE ONE: it cost nothing, so it yields nothing — no blade
        // to collect, no veritanium shard when it breaks. [stated] "this does not apply to items thrown by the yautja
        // just like skeletons how you cant collect their arrows ... we dont want them becoming a farm for fired ammo."
        // A yautja's ammunition is infinite, so every star it threw used to be a free shard. The existing creative
        // path already did precisely this, and "Creative" is already saved, so a lodged yautja star stays barren
        // across a reload.
        this.creative = livingEntity instanceof Player player && player.getAbilities().instabuild
            || livingEntity instanceof com.predator.common.gameplay.entity.living.yautja.Yautja;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Creative", creative);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.creative = tag.getBoolean("Creative");
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return PredatorItems.SHURIKEN.get();
    }

    @Override
    public void tick() {
        super.tick();

        // ⚠⚠ THE LIFETIME TIMER MUST NOT REAP A LODGED BLADE. Without this guard a shuriken stuck in a wall
        // silently vanished after MAX_LIFETIME_TICKS and ran dropBlade() — which would have looked like the lodge
        // failing at random, the exact symptom we just finished chasing down elsewhere.
        // ⚠ A lodged blade waits to be collected. It is only removed when a player walks into it.
        if (isLodged()) {
            return;
        }

        if (tickCount > MAX_LIFETIME_TICKS) {
            // ⚠ Dropped, not killed. One that flew off a cliff and timed out is still a shuriken; destroying it
            // silently is how a player's ammo disappears with no event to blame.
            dropBlade();
            discard();
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);

        if (level().isClientSide) {
            return;
        }

        BlockBreakProgressManager.damage(level(), result.getBlockPos(), BLOCK_DAMAGE);

        // ⚠⚠ HIS RULE: a shuriken that does not break LODGES in what it hit instead of falling. Only a break
        // drops anything.
        // ⚠ BREAK_CHANCE is rolled HERE now rather than inside dropBlade, because the outcome decides the whole
        // behaviour — a broken blade drops its shard and vanishes, an intact one sticks and waits to be collected.
        if (random.nextFloat() < BREAK_CHANCE) {
            dropShard();
            discard();

            return;
        }

        lodge(result);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide) {
            return;
        }

        if (result.getEntity() instanceof LivingEntity target && getOwner() != null) {
            // ⚠ Scaled by the THROWER'S TIER — see YautjaTier.scaleThrown. A player's throw is unchanged.
            target.hurt(damageSources().thrown(getOwner(), target), YautjaTier.scaleThrown(getOwner(), DAMAGE));
        }

        // ⚠ A mob is not something a blade can EMBED in, so this path still drops — but it now uses the SAME break
        // roll as a block hit, so the outcome is consistent: a break yields the shard, an intact blade yields the
        // shuriken back. Previously a hit on a mob always ran dropBlade(), which rolled the break separately and
        // made entity hits feel arbitrarily harsher than wall hits.
        if (random.nextFloat() < BREAK_CHANCE) {
            dropShard();
        } else {
            dropBlade();
        }

        discard();
    }

    /**
     * Puts the blade on the ground unless the throw broke it.
     * <p>
     * ⚠ Drops a copy of {@link #getItem()} rather than a fresh default instance, so a named or enchanted shuriken comes
     * back as itself. Building a new stack here is how custom data evaporates on every throw.
     */
    /**
     * Sticks the shuriken into whatever it hit.
     * <p>
     * ⚠ {@code ThrowableItemProjectile} has no {@code inGround} mechanic — that lives on {@code AbstractArrow}, with
     * its shake timer and pickup mode. Rather than change the parent class and everything that depends on it, this
     * stops the entity dead and turns gravity off, which reads identically and touches nothing else.
     * <p>
     * ⚠ Motion is zeroed AND {@code hasImpulse} set, so the client stops interpolating it forward — without that it
     * visibly drifts on for a moment after sticking.
     */
    /** {@return whether this shuriken has stuck into something} */
    public boolean isLodged() {
        return entityData.get(LODGED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LODGED, false);
    }

    private void lodge(BlockHitResult result) {
        // ⚠⚠ MOVE TO THE IMPACT POINT FIRST. onHitBlock fires when the movement RAY crosses the block, but the
        // entity is still back at its pre-move position — at throwing speed that is most of a block short, which
        // is why it hung in mid-air instead of biting into the face. Vanilla's arrows relocate themselves for the
        // same reason.
        // ⚠ Nudged 0.05 blocks ALONG the travel direction so it beds into the surface rather than resting exactly
        // on it, where it would z-fight with the block face.
        var hit = result.getLocation();
        var heading = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : Vec3.ZERO;

        // ⚠⚠ BACK OFF FROM THE FACE, DO NOT PUSH INTO IT. Nudging along the travel direction put the entity ORIGIN
        // inside the block, and an entity inside solid stone samples light level 0 — which rendered the blade
        // solid black. The visual embed is done in SpinningItemRenderer instead, where it costs no lighting.
        // ⚠ The tiny backward step keeps blockPosition() in the AIR block even when the hit lands exactly on the
        // boundary, which is the case that produced the black texture.
        setPos(
            hit.x - heading.x * LODGE_BACKOFF,
            hit.y - heading.y * LODGE_BACKOFF,
            hit.z - heading.z * LODGE_BACKOFF
        );

        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        hasImpulse = true;
        entityData.set(LODGED, true);
    }

    /**
     * ⚠ Walk into a lodged shuriken to collect it. It is the player's blade and it did not break, so making them mine
     * it out or wait for despawn would be a punishment for a clean throw.
     */
    @Override
    public void playerTouch(@NotNull net.minecraft.world.entity.player.Player player) {
        if (level().isClientSide || !isLodged() || isRemoved()) {
            return;
        }

        // ⚠ A creative throw cost nothing, so collecting it gives nothing: the blade is simply removed.
        if (creative) {
            playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.8F);
            discard();

            return;
        }

        if (player.getInventory().add(new ItemStack(PredatorItems.SHURIKEN.get()))) {
            playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.8F);
            discard();
        }
    }

    private void dropBlade() {
        // ⚠⚠ isRemoved is the belt-and-braces half of the fix below. Even if some future path calls this
        // twice, a single throw can only ever leave one blade.
        if (level().isClientSide || isRemoved() || dropped) {
            return;
        }

        dropped = true;

        // ⚠⚠ NO BREAK ROLL IN HERE. There used to be a SECOND roll of BREAK_CHANCE at this point, left behind when
        // the roll moved up to the hit handlers — so a "break" decided upstream only produced a shard 15% of the
        // time (2.25% of all throws) and handed the blade back otherwise. [stated] "i dont think they drop shards
        // when they break anymore." The decision is made once, by the caller: dropBlade() ALWAYS returns the intact
        // blade, dropShard() ALWAYS leaves the shard.
        // ⚠ A creative throw leaves nothing either way.
        if (creative) {
            return;
        }

        var stack = getItem().copy();

        if (stack.isEmpty()) {
            stack = new ItemStack(PredatorItems.SHURIKEN.get());
        }

        stack.setCount(1);
        drop(stack);
    }

    /**
     * The blade broke. A broken shuriken is not nothing — his rule: it drops the veritanium shard it was made from, so
     * a break costs the iron and the crafting rather than the whole blade. ⚠ Same one-drop guard as dropBlade.
     */
    private void dropShard() {
        if (level().isClientSide || isRemoved() || dropped) {
            return;
        }

        dropped = true;

        if (creative) {
            return;
        }

        drop(new ItemStack(PredatorItems.VERITANIUM_SHARD.get()));
    }

    /** Motionless, so it lands where it struck instead of skittering away from whoever wants it back. */
    private void drop(ItemStack stack) {
        var drop = new ItemEntity(level(), getX(), getY(), getZ(), stack);

        drop.setDeltaMovement(Vec3.ZERO);
        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
    }
}
