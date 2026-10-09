package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile;
import com.predator.common.gameplay.item.grenade.GrenadeKind;
import com.predator.common.gameplay.item.grenade.YautjaGrenadeItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A yautja throwing the grenades from its loadout — [stated] Oct 4, as proposed and agreed:
 * <ul>
 * <li>At a GROUP — {@link #GROUP_COUNT} or more valid targets within {@link #GROUP_RADIUS} of its target — or at a
 * target dug in OUT OF REACH (flying, or {@link PlasmaCaster#ABOVE_HEIGHT} or more above it), from {@link #MIN_RANGE}
 * to {@link #MAX_RANGE} blocks.</li>
 * <li>Never when it would be in the blast itself — each kind has its own safe distance, out to
 * {@link #IRRADIATED_SAFE_RANGE} for irradiated.</li>
 * <li>Never in a bare-handed fight (it is gated with the other weapon goals).</li>
 * <li>At most once every {@link #COOLDOWN_TICKS}.</li>
 * <li>A sticky grenade is thrown at the target itself, so it sticks to it; the others land at its feet.</li>
 * </ul>
 * Grenades are real items from the rack and are used up. A Hunter keeps two explosives back for breaching walls
 * (HuntDirector.throwBreach) — combat only spends explosives it has beyond those.
 * <p>
 * No goal flags, like every other weapon goal: GOAP owns movement and a throw must not interrupt it.
 */
public class YautjaGrenadeGoal extends Goal {

    public static final int COOLDOWN_TICKS = 200;

    public static final double MIN_RANGE = 6.0;

    public static final double MAX_RANGE = 24.0;

    /**
     * How far it must be from where the grenade lands. A blast hurts out to twice its power: fire and freeze (power 2)
     * reach 4 blocks, explosive and sticky (TNT, 4) reach 8, irradiated (9) reaches 18 — each with a block's margin.
     */
    public static final double HALF_POWER_SAFE_RANGE = 6.0;

    public static final double TNT_SAFE_RANGE = 9.0;

    public static final double IRRADIATED_SAFE_RANGE = 19.0;

    public static final int GROUP_COUNT = 3;

    public static final double GROUP_RADIUS = 4.0;

    /** Explosives a Hunter never spends in combat — they are for walls. */
    public static final int HUNTER_BREACH_RESERVE = 2;

    /** The order it prefers its grenades in when it carries more than one kind. */
    private static final GrenadeKind[] PREFERENCE = {
        GrenadeKind.STICKY,
        GrenadeKind.FIRE,
        GrenadeKind.FREEZE,
        GrenadeKind.IRRADIATED,
        GrenadeKind.EXPLOSIVE
    };

    private final Yautja yautja;

    private int nextThrowTick;

    private int releaseAtTick = -1;

    private @Nullable Item releasing;

    /** Oct 8 - where a siege throw is aimed (the underside of the floor under the prey), or null for a normal throw. */
    private net.minecraft.world.phys.@Nullable Vec3 siegeAim;

    public YautjaGrenadeGoal(Yautja yautja) {
        this.yautja = yautja;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        releaseAtTick = -1;
        releasing = null;
        siegeAim = null;
    }

    @Override
    public void tick() {
        if (yautja.level().isClientSide) {
            return;
        }

        var target = yautja.getTarget();

        // A throw winding up: keep facing, let go on the release frame — at a target still alive and in sight.
        if (releaseAtTick >= 0) {
            if (target != null && target.isAlive()) {
                yautja.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }

            if (yautja.tickCount >= releaseAtTick) {
                var item = releasing;
                var aim = siegeAim;
                releaseAtTick = -1;
                releasing = null;
                siegeAim = null;

                // Oct 8: a siege throw goes at the floor it was wound up at - the prey is out of sight by definition.
                if (item != null && aim != null) {
                    releaseAt(item, aim);
                    return;
                }

                if (item != null && target != null && target.isAlive() && yautja.hasLineOfSight(target) && isSafe(item, target)) {
                    release(item, target);
                }
            }

            return;
        }

        if (yautja.tickCount < nextThrowTick || target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target)) {
            return;
        }

        // Oct 8: prey above it that it failed to climb to (YautjaSiege) - a sticky at the floor under them.
        if (!yautja.hasLineOfSight(target) && trySiegeThrow()) {
            return;
        }

        var distance = yautja.distanceTo(target);

        if (distance < MIN_RANGE || distance > MAX_RANGE || !yautja.hasLineOfSight(target)) {
            return;
        }

        if (!isGroup(target) && !isOutOfReach(target)) {
            return;
        }

        var grenade = pick(target);

        if (grenade == null) {
            return;
        }

        nextThrowTick = yautja.tickCount + COOLDOWN_TICKS;
        releasing = grenade;
        YautjaCombat.faceTarget(yautja, target);
        yautja.playAttackAnimation(YautjaAttackAnimation.THROW_SHURIKEN);
        releaseAtTick = yautja.tickCount + YautjaAttackAnimation.THROW_SHURIKEN.impactTicks();
    }

    /**
     * Oct 8 - winds up a sticky grenade at the underside of the floor under the sieged prey, if the yautja can see that
     * block, it is in throwing range, and it is far enough away to be safe from the blast.
     */
    private boolean trySiegeThrow() {
        var aim = com.predator.common.gameplay.entity.living.yautja.YautjaSiege.aimPoint(yautja);
        var sticky = YautjaGrenadeItem.forKind(GrenadeKind.STICKY);

        if (aim == null || !yautja.getInventory().hasItem(sticky)) {
            return false;
        }

        var eye = yautja.getEyePosition();
        var distance = eye.distanceTo(aim);

        if (distance < TNT_SAFE_RANGE || distance > MAX_RANGE) {
            return false;
        }

        var hit = yautja.level()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    eye,
                    aim,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    yautja
                )
            );

        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(aim) > 1.0) {
            return false;
        }

        nextThrowTick = yautja.tickCount + COOLDOWN_TICKS;
        releasing = sticky;
        siegeAim = aim;
        yautja.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
        yautja.playAttackAnimation(YautjaAttackAnimation.THROW_SHURIKEN);
        releaseAtTick = yautja.tickCount + YautjaAttackAnimation.THROW_SHURIKEN.impactTicks();

        return true;
    }

    /** Oct 8 - the siege version of {@link #release}: a sticky lobbed at a point instead of at the prey. */
    private void releaseAt(Item item, net.minecraft.world.phys.Vec3 aimAt) {
        if (
            !(item instanceof YautjaGrenadeItem grenadeItem)
                || !(yautja.getInventory()
                    .removeItem(item, 1) instanceof com.blib.api.common.inventory.v1.BLibInventory.RemoveResult.Success)
        ) {
            return;
        }

        var level = yautja.level();
        var grenade = new YautjaGrenadeProjectile(level, yautja, grenadeItem.kind());
        var eye = yautja.getEyePosition();
        var aim = aimAt.subtract(eye);

        grenade.setItem(new ItemStack(item));
        grenade.setPos(eye.x, eye.y - 0.1, eye.z);
        grenade.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.2, aim.z, (float) Math.min(1.6, 0.55 + aim.length() * 0.05), 1.0F);
        level.addFreshEntity(grenade);
        yautja.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        yautja.playSound(SoundEvents.SNOWBALL_THROW, 0.8F, 0.5F);
    }

    private boolean isGroup(LivingEntity target) {
        var crowd = yautja.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(GROUP_RADIUS),
                candidate -> candidate != yautja && candidate.isAlive() && YautjaPredicates.isValidTarget(yautja, candidate)
            );

        return crowd.size() >= GROUP_COUNT;
    }

    private boolean isOutOfReach(LivingEntity target) {
        return PlasmaCaster.isOutOfReach(target) || target.getY() - yautja.getY() >= PlasmaCaster.ABOVE_HEIGHT;
    }

    /**
     * {@return the grenade it will throw, or null} — in its order of preference, safe at this range, and not a reserve.
     */
    private @Nullable Item pick(LivingEntity target) {
        for (var kind : PREFERENCE) {
            var item = YautjaGrenadeItem.forKind(kind);

            if (!yautja.getInventory().hasItem(item) || !isSafe(item, target)) {
                continue;
            }

            if (kind == GrenadeKind.EXPLOSIVE && yautja.isHunter() && !yautja.getInventory().hasItem(item, HUNTER_BREACH_RESERVE + 1)) {
                continue;
            }

            return item;
        }

        return null;
    }

    private boolean isSafe(Item item, LivingEntity target) {
        if (!(item instanceof YautjaGrenadeItem grenade)) {
            return false;
        }

        var safe = switch (grenade.kind()) {
            case IRRADIATED -> IRRADIATED_SAFE_RANGE;
            case EXPLOSIVE, STICKY -> TNT_SAFE_RANGE;
            default -> HALF_POWER_SAFE_RANGE;
        };

        return yautja.distanceToSqr(target) >= safe * safe;
    }

    /** Takes one from the rack and lobs it: at the target itself for a sticky, at its feet otherwise. */
    private void release(Item item, LivingEntity target) {
        if (
            !(item instanceof YautjaGrenadeItem grenadeItem)
                || !(yautja.getInventory()
                    .removeItem(item, 1) instanceof com.blib.api.common.inventory.v1.BLibInventory.RemoveResult.Success)
        ) {
            return;
        }

        var level = yautja.level();
        var grenade = new YautjaGrenadeProjectile(level, yautja, grenadeItem.kind());
        var eye = yautja.getEyePosition();
        var aimAt = grenadeItem.kind() == GrenadeKind.STICKY ? target.getBoundingBox().getCenter() : target.position();
        var aim = aimAt.subtract(eye);

        grenade.setItem(new ItemStack(item));
        grenade.setPos(eye.x, eye.y - 0.1, eye.z);
        // A lob: loft grows with distance so the drop over the throw lands it on the mark.
        grenade.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.2, aim.z, (float) Math.min(1.6, 0.55 + aim.length() * 0.05), 1.0F);
        level.addFreshEntity(grenade);
        yautja.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        yautja.playSound(SoundEvents.SNOWBALL_THROW, 0.8F, 0.5F);
    }
}
