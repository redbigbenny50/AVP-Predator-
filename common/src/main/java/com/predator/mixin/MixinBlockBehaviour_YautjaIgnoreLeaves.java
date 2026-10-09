package com.predator.mixin;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.projectile.LeafPiercing;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A yautja pushes through leaves but still stands on them.
 * <h2>His spec</h2> "give the pred the same ability xenos have where they can run through leaves but can still stand on
 * em. this way they can run through the jungle and climb trees."
 * <h2>⚠ Deliberately the same shape as avp_alien's version</h2> {@code MixinBlockBehaviour_XenomorphsIgnoreLeaves}
 * solves this exact problem, and this is the same four checks in the same order against the same injection point. Two
 * mods disagreeing about what a leaf block is would be a miserable bug to chase, so where the behaviour is meant to be
 * identical the implementation is too.
 * <h2>The {@code isAbove} check is the whole trick</h2> ⚠⚠ Returning an empty shape unconditionally would let a yautja
 * fall straight through a canopy. Asking whether the entity is ABOVE the block first means the collision box survives
 * for anything standing on top and vanishes only for something pushing in from the side — "run through them, still
 * stand on them" in one condition.
 * <h2>What this does NOT change</h2> ⚠ Pathfinding still treats leaves as solid: BLib classifies terrain from
 * {@code isCollisionShapeFullBlock}, which takes no entity context and so never reaches this code. That is the right
 * outcome — routes go OVER a canopy rather than through it, and this only stops the yautja snagging when it brushes
 * one.
 */
@Mixin(BlockBehaviour.class)
public class MixinBlockBehaviour_YautjaIgnoreLeaves {

    @Inject(
        method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void avp_predator$yautjaPushThroughLeaves(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context,
        CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (!state.is(BlockTags.LEAVES)) {
            return;
        }

        if (!(context instanceof EntityCollisionContext entityContext)) {
            return;
        }

        var entity = entityContext.getEntity();

        // ⚠⚠ EVERY PREDATOR PROJECTILE PASSES THROUGH, UNCONDITIONALLY — no isAbove test, unlike the yautja.
        // These are flying weapons that stand on nothing, and the disc comes back down through the same canopy
        // it went out through, so the standing rule would bounce them off the treetops.
        //
        // ⚠ THIS IS WHY THE CASTER APPEARED TO MISS TREED PREY BY MILES. Only the disc was listed here, and
        // the disc was the one weapon reported as WORKING against prey in a tree. The bolt was detonating on
        // the underside of the canopy, which from the ground looks like a shot that went nowhere near.
        if (entity instanceof LeafPiercing) {
            callback.setReturnValue(Shapes.empty());

            return;
        }

        if (!(entity instanceof Yautja)) {
            return;
        }

        // Standing on the canopy: keep the block solid.
        if (entityContext.isAbove(Shapes.block(), pos, false)) {
            return;
        }

        // Pushing in from the side: no collision.
        callback.setReturnValue(Shapes.empty());
    }
}
