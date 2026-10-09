package com.predator.util;

import com.predator.common.gameplay.entity.projectile.ShurikenProjectile;
import com.predator.common.gameplay.entity.projectile.SmartDiscProjectile;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;

public class ItemGoalUtil {

    public static void shootShuriken(PathfinderMob entity) {
        // The throw animation is a server-side event, so it crosses as synched data and the client plays it
        // once. Dispatching an Az command from here would be a no-op — commands run client-side only.
        // 🚨 NO ANIMATION HERE. YautjaThrowGoal plays the throw clip when the WIND-UP starts and calls this on the
        // RELEASE frame; playing it here as well restarted the clip at the very moment the weapon left the hand.

        // TODO: Change sound effect here.
        entity.level()
            .playSound(
                null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                com.predator.common.registry.init.PredatorSoundEvents.SHURIKEN_THROW.get(),
                SoundSource.PLAYERS,
                0.5F,
                0.4F / (entity.level().getRandom().nextFloat() * 0.4F + 0.8F)
            );

        if (!entity.level().isClientSide && entity.getTarget() != null) {
            var targetX = entity.getTarget().getX();
            var targetY = entity.getTarget().getY(1.0);
            var targetZ = entity.getTarget().getZ();
            var sourceX = entity.getX();
            var sourceY = entity.getY(0.5);
            var sourceZ = entity.getZ();
            var directionX = targetX - sourceX;
            var directionY = targetY - sourceY;
            var directionZ = targetZ - sourceZ;
            var length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
            directionX /= length;
            directionY /= length;
            directionZ /= length;

            var shurikenItemEntity = new ShurikenProjectile(entity.level(), entity);
            shurikenItemEntity.setOwner(entity);
            shurikenItemEntity.setPos(sourceX, sourceY, sourceZ);
            var velocity = 1.5F;
            shurikenItemEntity.setDeltaMovement(directionX * velocity, directionY * velocity, directionZ * velocity);

            entity.level().addFreshEntity(shurikenItemEntity);
        }
    }

    public static void shootSmartDisc(PathfinderMob entity) {
        // The throw animation is a server-side event, so it crosses as synched data and the client plays it
        // once. Dispatching an Az command from here would be a no-op — commands run client-side only.
        // 🚨 NO ANIMATION HERE. YautjaThrowGoal plays the throw clip when the WIND-UP starts and calls this on the
        // RELEASE frame; playing it here as well restarted the clip at the very moment the weapon left the hand.

        // TODO: Change sound effect here.
        entity.level()
            .playSound(
                null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                com.predator.common.registry.init.PredatorSoundEvents.SMART_DISC_THROW.get(),
                SoundSource.PLAYERS,
                0.5F,
                0.4F / (entity.level().getRandom().nextFloat() * 0.4F + 0.8F)
            );

        if (!entity.level().isClientSide && entity.getTarget() != null) {
            var targetX = entity.getTarget().getX();
            var targetY = entity.getTarget().getY(1.0);
            var targetZ = entity.getTarget().getZ();
            var sourceX = entity.getX();
            var sourceY = entity.getY(0.5);
            var sourceZ = entity.getZ();
            var directionX = targetX - sourceX;
            var directionY = targetY - sourceY;
            var directionZ = targetZ - sourceZ;
            var length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
            directionX /= length;
            directionY /= length;
            directionZ /= length;

            var smartDiscItemEntity = new SmartDiscProjectile(entity.level(), entity);
            smartDiscItemEntity.setOwner(entity);
            smartDiscItemEntity.setPos(sourceX, sourceY, sourceZ);
            var velocity = 3.5F;
            smartDiscItemEntity.setDeltaMovement(directionX * velocity, directionY * velocity, directionZ * velocity);
            entity.level().addFreshEntity(smartDiscItemEntity);
        }
    }

}
