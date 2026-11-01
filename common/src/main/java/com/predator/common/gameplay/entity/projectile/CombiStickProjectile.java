package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * The thrown combi stick.
 * <h2>⚠⚠ IT ALWAYS COMES BACK</h2> ⚠⚠ IT NO LONGER DROPS WITH LOYALTY — his members did not want that, so the drop is
 * now PLAIN and a player adds the enchantment themselves if they want it back.
 * <p>
 * The return was never hardcoded here and still is not: this is an {@code AbstractArrow}, so vanilla reads Loyalty off
 * the thrown stack exactly as it does for a trident. Removing the free enchantment changed WHAT DROPS, not what the
 * projectile can do — an enchanted stick returns, an unenchanted one sticks where it lands.
 * <h2>Thrown by a yautja, it costs nothing</h2> ⚠ The yautja builds a FRESH stack for the projectile and never touches
 * its own hand — exactly what vanilla's Drowned does with its trident ({@code new ItemStack(Items.TRIDENT)}, hand slot
 * untouched). That is why a yautja can throw repeatedly and still visibly be holding its spear.
 */
public class CombiStickProjectile extends AbstractArrow {

    /**
     * {@return whether the spear is embedded in a block}
     * <p>
     * ⚠ {@code inGround} is protected on AbstractArrow, so the RENDERER cannot read it — hence this accessor. It is
     * used to pull the drawn model back out of the ground; see CombiStickRenderer.
     */
    public boolean isStuckInGround() {
        return inGround;
    }

    /** ⚠ Above a trident's 8: this is a two-metre bladed shaft and the drop is meant to be worth hunting for. */
    private static final float DAMAGE = 10.0F;

    public CombiStickProjectile(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    public CombiStickProjectile(Level level, LivingEntity thrower, ItemStack stack) {
        super(PredatorEntityTypes.COMBI_STICK.get(), thrower, level, stack, null);

        // ⚠ Scaled by the THROWER'S TIER — see YautjaTier.scaleThrown. A player's throw is unchanged.
        setBaseDamage(YautjaTier.scaleThrown(thrower, DAMAGE));
    }

    /**
     * ⚠ The stack it becomes when picked up, which is the stack it was THROWN with — enchantments and all. Returning a
     * plain new item here would strip the Loyalty off the spear the first time anyone caught it.
     */
    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(PredatorItems.COMBI_STICK.get());
    }

    @Override
    protected @NotNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    /**
     * ⚠ A yautja's thrown spear must not be catchable, or a player would farm them by standing still. Only a spear a
     * PLAYER threw can be picked back up, which is what the pickup rule set at construction already encodes — this only
     * guards the case where the owner is gone.
     */
    @Override
    protected boolean tryPickup(@NotNull Player player) {
        // ⚠⚠ A CREATIVE THROWER NEVER LOSES THE STACK, SO PICKING IT UP DUPLICATES IT. The throw only shrinks the
        // held stack when the player is NOT in creative — so in creative you keep the spear AND collect the thrown
        // one, and every throw-and-retrieve cycle mints a new item. Collect it silently instead.
        if (player.getAbilities().instabuild) {
            discard();

            return false;
        }

        return getOwner() == player && super.tryPickup(player);
    }

    /**
     * Set once the spear has hit something. Like a trident it then cannot hit again until it is picked up - it bounces
     * off and falls instead of passing through.
     */
    private boolean dealtDamage;

    /**
     * 🚨 Oct 29 BUG FIX: thrown at an enemy, the combi stick DISAPPEARED. It used AbstractArrow's entity hit, and
     * AbstractArrow.onHitEntity discards the projectile after a successful hit whenever its pierce level is 0 - an
     * arrow is used up when it lands in a mob. A trident overrides the whole hit instead: it deals its damage, then
     * bounces back off the target ({@code deltaMovement * (-0.01, -0.1, -0.01)}) and drops to the ground to be picked
     * up. This now does exactly what ThrownTrident.onHitEntity does (read from the 1.21.1 source), with the spear's own
     * flat damage, still scaled by a yautja thrower's tier.
     */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        var entity = result.getEntity();
        var owner = getOwner();
        var source = damageSources().trident(this, owner == null ? this : owner);
        var damage = (float) getBaseDamage();

        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            damage = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(
                serverLevel,
                getWeaponItem(),
                entity,
                source,
                damage
            );
        }

        dealtDamage = true;

        if (entity.hurt(source, damage)) {
            if (entity.getType() == net.minecraft.world.entity.EntityType.ENDERMAN) {
                return;
            }

            if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(
                    serverLevel,
                    entity,
                    source,
                    getWeaponItem()
                );
            }

            if (entity instanceof LivingEntity living) {
                doKnockback(living, source);
                doPostHurtEffects(living);
            }
        }

        // Bounce back off the target and fall - it stays in the world to be picked up.
        setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    /** Once it has hit something it cannot hit again - it is falling, not flying. Same as the trident. */
    @Override
    protected @org.jetbrains.annotations.Nullable EntityHitResult findHitEntity(
        @NotNull net.minecraft.world.phys.Vec3 startVec,
        @NotNull net.minecraft.world.phys.Vec3 endVec
    ) {
        return dealtDamage ? null : super.findHitEntity(startVec, endVec);
    }

    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DealtDamage", dealtDamage);
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dealtDamage = tag.getBoolean("DealtDamage");
    }

}
