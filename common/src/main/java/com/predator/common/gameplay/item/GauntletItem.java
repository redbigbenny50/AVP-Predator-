package com.predator.common.gameplay.item;

import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehavior;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.gameplay.item.gauntlet.GauntletAmmo;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.gameplay.menu.GauntletMenu;
import com.predator.common.registry.init.PredatorBlocks;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The player's wrist gauntlet.
 * <h2>His spec</h2> "the gauntlet is only equipped if its in the offhand slot. if the player only selects the item in
 * the menu then they are holding it like a normal item." Right-click fires, shift+right-click cycles the selection, G
 * opens the GUI, C toggles the cloak.
 * <h2>⚠⚠ EQUIPPED MEANS OFFHAND, AND NOTHING ELSE</h2> Everything hangs off {@link #isEquipped}: which model renders,
 * whether arms draw, whether it fires. Held in the main hand it is inert cargo. That one rule is what keeps a gauntlet
 * from competing with the weapon you are actually swinging.
 */
public class GauntletItem extends Item {

    public GauntletItem() {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    /**
     * {@return whether this stack is the one worn in the player's offhand} <strong>⚠⚠ NOT REFERENCE EQUALITY, AND THAT
     * IS DELIBERATE</strong> This first compared {@code player.getOffhandItem() == stack}, which is right for the MENU
     * — {@code stillValid} must reject a stack that was swapped out from under it. It is WRONG for rendering: the
     * render pipeline makes no promise that the renderer receives the same object the player is holding, and against a
     * copy this returned false. That cascaded into everything: the item geo was chosen instead of the player geo, so
     * there were no arm bones; the dispatcher never started {@code ready}, so nothing was playing; and BLib only draws
     * arms when {@code isArmBone(bone) && isAnimationPlaying}. No arm, no gauntlet, in first person.
     * <p>
     * ⚠ Value comparison means two identical gauntlets, one in each hand, both report equipped. That is harmless — they
     * render the same and the offhand one is the one that fires — and it is a far better failure than the whole model
     * vanishing.
     * <p>
     * ⚠ The MENU still uses strict identity in its own {@code stillValid}. Do not "unify" these: they answer different
     * questions, and loosening the menu's check is how a container starts writing into the wrong stack.
     */
    public static boolean isEquipped(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        var offhand = player.getOffhandItem();

        return offhand == stack
            || (offhand.is(PredatorItems.GAUNTLET.get()) && ItemStack.isSameItemSameComponents(offhand, stack));
    }

    /**
     * Keeps the worn gauntlet's idle loop alive. <strong>⚠⚠ SERVER-SIDE, FROM inventoryTick — THE PATTERN AZURELIB'S
     * OWN EXAMPLE USES</strong> Its gun-with-arm example drives the idle loop from {@code Item.inventoryTick} with
     * {@code !level.isClientSide()} and a NETWORKED send. Every client-side attempt I made failed for a measured
     * reason: BLib hands out a NEW ANIMATOR INSTANCE EVERY FRAME for a held item, because
     * {@code AzProvider.provideAnimator} caches on the ItemStack and the renderer receives a different stack object
     * each frame. A locally dispatched loop was discarded before it could play — the diagnostic showed a different
     * animator id every line and {@code playing=false} throughout.
     * <p>
     * ⚠ BLib 0.3.10 had no networked dispatch at all, so it is added in {@code com.blib.mod.common.animation_sync} —
     * one self-contained package plus two registration lines.
     * <p>
     * ⚠ Sent every tick, not once. The loop is idempotent on the client, so a repeat does not restart it, and a player
     * who re-logs or comes back into view still gets it.
     */
    @Override
    public void inventoryTick(
        @NotNull ItemStack stack,
        @NotNull Level level,
        @NotNull net.minecraft.world.entity.Entity entity,
        int slotId,
        boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        // ⚠⚠ A COUNTING GAUNTLET IN A POCKET. It keeps counting (the registry is refreshed from here), it ticks
        // audibly, and it will NOT stay in the offhand: [stated] "cant be worn in the offhand, you still hear the
        // countdown sound and it keeps counting down." Ejected to the main inventory or dropped if that is full.
        if (GauntletSelfDestruct.isCounting(stack) && level instanceof ServerLevel serverLevel) {
            if (isEquipped(player, stack)) {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }

                player.displayClientMessage(Component.translatable("gauntlet.avp_predator.destruct.cannot_wear"), true);
                return;
            }

            GauntletSelfDestruct.observe(serverLevel, stack, player.position(), () -> stack.setCount(0));

            // [stated] findable through walls "like spectral arrows": whoever is carrying a counting gauntlet glows.
            // Topped
            // up before it runs out rather than every tick, so the effect is not resent twenty times a second.
            if (!stack.isEmpty() && GauntletSelfDestruct.isCounting(stack)) {
                var glowing = player.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);

                if (glowing == null || glowing.getDuration() < 10) {
                    player.addEffect(
                        new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING,
                            40,
                            0,
                            false,
                            false,
                            true
                        )
                    );
                }
            }

            return;
        }

        if (!isEquipped(player, stack)) {
            return;
        }

        syncClip(player, "ready", AzPlayBehaviors.LOOP);
    }

    /**
     * Sends one of the worn gauntlet's clips to every tracking client and the wearer, on the side the wearer's offhand
     * is on. SERVER ONLY (the sync ignores client calls). ⚠ Every clip the world should see goes through here — ready,
     * fire, open, close — so the clip name is built in exactly one place.
     */
    public static void syncClip(Player player, String clip, AzPlayBehavior behavior) {
        var side = arm(player) == HumanoidArm.LEFT ? "left" : "right";

        com.blib.mod.common.animation_sync.BLibItemAnimationSync.play(
            player,
            InteractionHand.OFF_HAND,
            "gauntlet",
            "gauntlet." + side + "." + clip,
            behavior
        );
    }

    /** {@return the offhand gauntlet, or empty} */
    public static ItemStack equipped(Player player) {
        var offhand = player.getOffhandItem();

        return offhand.is(PredatorItems.GAUNTLET.get()) ? offhand : ItemStack.EMPTY;
    }

    /**
     * {@return which arm wears the gauntlet}
     * <p>
     * ⚠ The OPPOSITE of the main arm, read from the player rather than hardcoded. Vanilla has a left-handed setting
     * under Skin Customisation, so both sets of clips genuinely get used — this is not just insurance against a
     * third-party mod.
     */
    public static HumanoidArm arm(Player player) {
        return player.getMainArm().getOpposite();
    }

    public static GauntletAmmo selected(ItemStack stack) {
        return GauntletAmmo.byName(stack.getOrDefault(PredatorDataComponents.GAUNTLET_AMMO.get(), GauntletAmmo.DART.serializedName()));
    }

    public static void select(ItemStack stack, GauntletAmmo ammo) {
        stack.set(PredatorDataComponents.GAUNTLET_AMMO.get(), ammo.serializedName());
    }

    /**
     * ⚠⚠ THIS ONLY RUNS IF THE MAIN HAND PASSED. Vanilla tries the main hand first and only reaches the offhand when
     * that returned PASS — so a gun that aims down sights, a shield that raises, or a bow that draws all keep their
     * right-click and the gauntlet stays quiet. A sword has no use action, passes, and the gauntlet fires. That is his
     * rule, and it is enforced by vanilla's own ordering rather than by guessing at what an item does.
     */
    /**
     * ⚠⚠ RIGHT-CLICK THE GROUND WHILE HOLDING (not wearing) = SET IT DOWN AS A BLOCK. [stated] "the gauntlet needs to
     * be placeable like a block if you rightclick the ground while holding it NOT wearing it." Placing an ARMED one is
     * what starts the countdown. Top faces only; the exact stack moves into the block, contents and all.
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var stack = context.getItemInHand();

        if (player == null || context.getHand() != InteractionHand.MAIN_HAND || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        var pos = context.getClickedPos().above();

        if (
            !level.getBlockState(pos).canBeReplaced() || !level.getBlockState(context.getClickedPos())
                .isFaceSturdy(level, context.getClickedPos(), Direction.UP)
        ) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        level.setBlock(pos, PredatorBlocks.GAUNTLET_BLOCK.get().defaultBlockState(), 3);

        if (level.getBlockEntity(pos) instanceof GauntletBlockEntity entity) {
            entity.placeGauntlet(stack);
        }

        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);

        if (GauntletSelfDestruct.isArmed(stack)) {
            player.displayClientMessage(Component.translatable("gauntlet.avp_predator.destruct.started"), true);
        }

        stack.setCount(0);

        return InteractionResult.CONSUME;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        // ⚠ Main hand means carried, not worn. No firing, no cycling — it is cargo.
        if (hand != InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                cycle(player, stack);
            }

            // ⚠ CONSUME, not SUCCESS. InteractionResult.shouldSwing() is true only for SUCCESS, and that swing is
            // vanilla's punch — cycling ammunition should not throw a fist.
            return InteractionResultHolder.consume(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // ⚠ The main hand owns this click — see PredatorItemTags.BLOCKS_GAUNTLET_FIRE.
        if (player.getMainHandItem().is(PredatorItemTags.BLOCKS_GAUNTLET_FIRE)) {
            return InteractionResultHolder.pass(stack);
        }

        var fired = GauntletLauncher.fire(level, player, stack);

        // ⚠ The recoil is sent from the SERVER, like the idle loop, for the same reason: a client-side dispatch
        // lands on an animator that is discarded the next frame.
        if (fired) {
            syncClip(player, "fire", AzPlayBehaviors.PLAY_ONCE);
        }

        // ⚠⚠ CONSUME, NOT SUCCESS, AND THIS IS THE PUNCH HE SAW. InteractionResult.shouldSwing() returns true only
        // for SUCCESS, and that is vanilla's arm swing. Firing a wrist launcher must not swing the arm — the
        // gauntlet's own fire clip is the animation, and the two were fighting.
        return level.isClientSide || fired
            ? InteractionResultHolder.consume(stack)
            : InteractionResultHolder.fail(stack);
    }

    /**
     * Steps to the next ammunition type and reports the result above the hotbar.
     * <p>
     * ⚠ It selects even when the round is missing. Refusing to move past an empty type would trap a player on a slot
     * they cannot use — the message tells them it is empty, and another click moves on.
     */
    private void cycle(Player player, ItemStack stack) {
        var next = selected(stack).next();

        select(stack, next);

        var contents = new GauntletContents(stack);
        var available = next.itemPath() != null && contents.countOf(next) > 0;

        player.displayClientMessage(
            available
                ? Component.translatable(next.nameKey())
                : Component.translatable(next.missingKey(), Component.translatable(next.nameKey())),
            true
        );

        player.level()
            .playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                // ⚠ The SECOND empty click — cycling TO an ammo type you have none of. The launcher's trigger-pull
                // click was swapped in fix 19 and this one was missed; both are the gauntlet's own empty sound now.
                available ? SoundEvents.UI_BUTTON_CLICK.value() : PredatorSoundEvents.GAUNTLET_EMPTY.get(),
                SoundSource.PLAYERS,
                available ? 0.5F : 1.0F,
                available ? 1.6F : 1.0F
            );
    }

    public static MenuProvider provider(ItemStack stack) {
        return new SimpleMenuProvider(
            (id, inventory, player) -> new GauntletMenu(id, inventory, stack, InteractionHand.OFF_HAND),
            Component.translatable("container.avp_predator.gauntlet")
        );
    }
}
