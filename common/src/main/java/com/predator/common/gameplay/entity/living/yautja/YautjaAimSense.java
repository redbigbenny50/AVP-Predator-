package com.predator.common.gameplay.entity.living.yautja;

import com.predator.PredatorResources;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A yautja senses a weapon held on it.
 * <p>
 * Oct 16 - [stated] "for guns like the sniper if you keep the scope aimed on them for longer than 2 - 3 seconds it will
 * turn to look at you and attack even if you are normally outside its usual field of vision is goes for any kind of
 * aiming even tacz and point blank". He chose: crosshair on it while holding a gun (the same for every gun mod), and
 * guns plus a drawn bow or a loaded crossbow.
 * <h2>The rule</h2> A player holding such a weapon keeps one yautja under the crosshair, in clear line of sight and
 * within {@link #MAX_RANGE}, for a random {@link #MIN_AIM_TICKS}-{@link #MAX_AIM_TICKS} ticks. A slip shorter than
 * {@link #GRACE_TICKS} does not reset the count - nobody's aim is perfect. Then it notices: it turns on the shooter
 * with its click, takes him as prey through the same 60 s grudge a strike gives (YautjaProvocation), and its awareness
 * stretches to reach him for as long as that grudge lasts. Aiming on keeps it fresh.
 * <h2>⚠ Why the follow range has to stretch</h2> Its normal awareness IS its follow range - 35 blocks, no view cone. A
 * target set from further away is dropped by the targeting checks that measure against that range, so a sniper at 100
 * blocks would be noticed and forgotten in the same tick.
 * <h2>Cost</h2> Only players holding one of these weapons are looked at, every {@link #SCAN_INTERVAL} ticks: one block
 * ray and one entity sweep each. Nobody armed, nothing done.
 */
public final class YautjaAimSense {

    /** Blocks. A sniper's reach - and the escape distance of the grudge it earns. */
    public static double MAX_RANGE = 128.0D;

    /** [stated] "2 - 3 seconds". Re-rolled for every new yautja the crosshair settles on. */
    public static int MIN_AIM_TICKS = 40;

    public static int MAX_AIM_TICKS = 60;

    /** A slip off target shorter than this keeps the count. */
    public static int GRACE_TICKS = 6;

    private static final int SCAN_INTERVAL = 2;

    /** How long the stretched awareness lasts - the grudge's own 60 s. */
    private static final int REACH_TICKS = YautjaProvocation.PROVOKED_TICKS;

    private static final ResourceLocation REACH_MODIFIER = PredatorResources.location("aim_sense_reach");

    /** avp_human's own gun tag, named by string so this mod needs no compile dependency on it. */
    private static final TagKey<Item> HUMAN_GUNS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("avp_human", "guns")
    );

    /** TACZ's gun interface - every TACZ gun item implements it (verified in TACZ's source). */
    private static final String TACZ_GUN = "com.tacz.guns.api.item.IGun";

    /**
     * Point Blank's projectile-dealing item base - the class predator's own Point Blank hit hook targets, whose hit
     * method is handed the firing gun. ⚠ Point Blank's source is not public, so "every gun extends it, and only guns"
     * is inferred from that hook rather than read.
     */
    private static final String POINT_BLANK_GUN = "com.vicmatskiv.pointblank.item.HurtingItem";

    /** Per item type: is it a TACZ or Point Blank gun? The class walk runs once per item, ever. */
    private static final Map<Item, Boolean> GUN_CLASS_CACHE = new HashMap<>();

    private static final class Aim {

        @Nullable
        UUID yautja;

        int heldTicks;

        int neededTicks;

        long lastOnTargetTick;
    }

    /** ⚠ Weak keys: a player who logs out is forgotten, never kept alive by this map. */
    private static final Map<Player, Aim> AIMS = new WeakHashMap<>();

    /** Yautja whose awareness was stretched, and the game time it shrinks back. */
    private static final Map<Yautja, Long> STRETCHED = new WeakHashMap<>();

    private YautjaAimSense() {
        throw new UnsupportedOperationException();
    }

    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var now = serverLevel.getGameTime();

        if (now % 20 == 0 && !STRETCHED.isEmpty()) {
            shrinkExpired(now);
        }

        if (now % SCAN_INTERVAL != 0) {
            return;
        }

        // ⚠ The loaded yautja are listed ONCE per scan, and only if somebody is actually aiming - so a world where
        // nobody holds a weapon on anything does no entity work at all.
        java.util.List<? extends Yautja> loaded = null;

        for (var player : serverLevel.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || !isAiming(player)) {
                AIMS.remove(player);
                continue;
            }

            if (loaded == null) {
                loaded = serverLevel.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Yautja.class), Yautja::isAlive);
            }

            tickPlayer(serverLevel, player, now, loaded);
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, long now, java.util.List<? extends Yautja> loaded) {
        var yautja = loaded.isEmpty() ? null : yautjaUnderCrosshair(level, player, loaded);
        var aim = AIMS.computeIfAbsent(player, p -> new Aim());

        if (yautja == null) {
            // Off target: the count survives a slip, not a look away.
            if (now - aim.lastOnTargetTick > GRACE_TICKS) {
                aim.yautja = null;
                aim.heldTicks = 0;
            }

            return;
        }

        if (!yautja.getUUID().equals(aim.yautja) || now - aim.lastOnTargetTick > GRACE_TICKS) {
            aim.yautja = yautja.getUUID();
            aim.heldTicks = 0;
            aim.neededTicks = MIN_AIM_TICKS + player.getRandom().nextInt(MAX_AIM_TICKS - MIN_AIM_TICKS + 1);
        }

        aim.lastOnTargetTick = now;
        aim.heldTicks += SCAN_INTERVAL;

        if (aim.heldTicks >= aim.neededTicks) {
            notice(yautja, player, now);
            // Holding on keeps the grudge fresh, every 2-3 s.
            aim.heldTicks = 0;
            aim.neededTicks = MIN_AIM_TICKS + player.getRandom().nextInt(MAX_AIM_TICKS - MIN_AIM_TICKS + 1);
        }
    }

    /** It feels the weapon on it: turn, click, hunt. */
    private static void notice(Yautja yautja, ServerPlayer player, long now) {
        var alreadyOnHim = yautja.getTarget() == player;

        YautjaProvocation.aimedAtBy(yautja, player, MAX_RANGE);
        stretchReach(yautja, player, now);

        if (!alreadyOnHim) {
            yautja.setTarget(player);
            yautja.getLookControl().setLookAt(player, 180.0F, 90.0F);
            yautja.level().playSound(null, yautja.blockPosition(), PredatorSoundEvents.YAUTJA_CLICK.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * Stretches its follow range to reach the shooter (plus a margin), for the grudge's length. ⚠ Only ever up: a
     * Hunter, whose follow range is already longer, is left alone.
     */
    private static void stretchReach(Yautja yautja, Player player, long now) {
        var attribute = yautja.getAttribute(Attributes.FOLLOW_RANGE);

        if (attribute == null) {
            return;
        }

        var wanted = Math.min(MAX_RANGE, yautja.distanceTo(player) + 16.0D);
        var extra = wanted - attribute.getBaseValue();

        attribute.removeModifier(REACH_MODIFIER);

        if (extra > 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(REACH_MODIFIER, extra, AttributeModifier.Operation.ADD_VALUE));
            STRETCHED.put(yautja, now + REACH_TICKS);
        }
    }

    private static void shrinkExpired(long now) {
        var iterator = STRETCHED.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();

            if (now >= entry.getValue() || entry.getKey().isRemoved()) {
                var attribute = entry.getKey().getAttribute(Attributes.FOLLOW_RANGE);

                if (attribute != null) {
                    attribute.removeModifier(REACH_MODIFIER);
                }

                iterator.remove();
            }
        }
    }

    /**
     * {@return the yautja under this player's crosshair, out to the first block in the way, or null}
     * <p>
     * ⚠⚠ CHEAP FIRST. The first version cast a 128-block block ray and then swept a box along the whole line of sight
     * for entities - looking diagonally that box is ~128 x 128 x 128, hundreds of chunk sections - every 2 ticks for
     * every armed player, yautja nearby or not. Now each loaded yautja within range is tested against the line of sight
     * directly (a box test, no world access), and the block ray is cast ONLY to the nearest one actually under the
     * crosshair, to make sure no wall stands between. No yautja in the sights: no ray at all.
     */
    private static @Nullable Yautja yautjaUnderCrosshair(ServerLevel level, Player player, java.util.List<? extends Yautja> loaded) {
        var eye = player.getEyePosition();
        var look = player.getViewVector(1.0F);
        var end = eye.add(look.scale(MAX_RANGE));
        Yautja nearest = null;
        var nearestDistanceSqr = Double.MAX_VALUE;
        net.minecraft.world.phys.Vec3 nearestHit = null;

        for (var yautja : loaded) {
            if (yautja.distanceToSqr(eye) > MAX_RANGE * MAX_RANGE) {
                continue;
            }

            var hit = yautja.getBoundingBox().inflate(yautja.getPickRadius()).clip(eye, end);

            if (hit.isPresent()) {
                var distanceSqr = eye.distanceToSqr(hit.get());

                if (distanceSqr < nearestDistanceSqr) {
                    nearest = yautja;
                    nearestDistanceSqr = distanceSqr;
                    nearestHit = hit.get();
                }
            }
        }

        if (nearest == null) {
            return null;
        }

        // The first block stops the look: no sensing through walls.
        var block = level.clip(new ClipContext(eye, nearestHit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        return block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? nearest : null;
    }

    /** {@return whether the player is holding a weapon "on" something: a gun, a drawn bow, a loaded crossbow} */
    private static boolean isAiming(Player player) {
        var stack = player.getMainHandItem();

        if (stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof BowItem) {
            return player.isUsingItem() && player.getUseItem() == stack;
        }

        if (stack.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(stack);
        }

        return isGun(stack);
    }

    private static boolean isGun(ItemStack stack) {
        if (stack.is(HUMAN_GUNS)) {
            return true;
        }

        return GUN_CLASS_CACHE.computeIfAbsent(stack.getItem(), YautjaAimSense::isGunClass);
    }

    /**
     * Walks the item's classes and interfaces for TACZ's or Point Blank's gun type, by name - neither mod is needed.
     */
    private static boolean isGunClass(Item item) {
        for (Class<?> type = item.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (type.getName().equals(POINT_BLANK_GUN)) {
                return true;
            }

            for (var anInterface : type.getInterfaces()) {
                if (anInterface.getName().equals(TACZ_GUN)) {
                    return true;
                }
            }
        }

        return false;
    }
}
