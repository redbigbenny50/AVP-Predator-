package com.predator.common.gameplay.hunt;

import com.predator.Predator;
import com.predator.PredatorResources;
import com.predator.common.config.YautjaHonorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns advancements into yautja honor, and awards the predator's own code-driven advancements.
 * <h2>How honor arrives</h2>
 * <ul>
 * <li><b>Live:</b> {@code MixinPlayerAdvancements_Honor} calls {@link #onAdvancementCompleted} the moment any
 * advancement completes.</li>
 * <li><b>Retroactively:</b> the first tick a player is seen in a session, every advancement on the honor list is
 * checked and any already-completed one is credited — [stated] players in existing worlds get credit once. The ledger
 * remembers what has paid out, so re-scanning on every login can never pay twice.</li>
 * </ul>
 * <h2>The code-awarded advancements</h2> Two of the new advancements have no vanilla trigger that can express them:
 * <ul>
 * <li>{@code hunt/live_fire} — hit a HOSTILE with an avp_human gun. Detected from the damage itself: avp_human's
 * bullets carry the {@code avp_human:bullet} damage type and the shooter as the source entity.</li>
 * <li>{@code hunt/wy_ape_set} — WEAR the full WY Ape set, checked every two seconds from item ids.</li>
 * </ul>
 * Both reference avp_human only by id, so without it they simply never fire.
 */
public final class YautjaHonor {

    public static final ResourceLocation LIVE_FIRE = PredatorResources.location("hunt/live_fire");

    public static final ResourceLocation WY_APE_SET = PredatorResources.location("hunt/wy_ape_set");

    public static final ResourceLocation SURVIVE_THE_HUNT = PredatorResources.location("hunt/survive_the_hunt");

    public static final ResourceLocation HUNTERS_TROPHY = PredatorResources.location("hunt/hunters_trophy");

    private static final ResourceKey<DamageType> HUMAN_BULLET = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("avp_human", "bullet")
    );

    private static final String[] APE_PIECES = { "ape_helmet", "ape_chestplate", "ape_leggings", "ape_boots" };

    private static final EquipmentSlot[] APE_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private static final int ARMOR_CHECK_INTERVAL = 40;

    /**
     * Players already scanned this session. ⚠ Weak, keyed by the ServerPlayer OBJECT: a new one is made on every login
     * and respawn, so each of those rescans — cheap, and the ledger makes repeats harmless.
     */
    private static final Map<ServerPlayer, Boolean> SCANNED = new WeakHashMap<>();

    private YautjaHonor() {}

    // ---------------------------------------------------------------- crediting

    /** An advancement just completed for this player. */
    public static void onAdvancementCompleted(ServerPlayer player, ResourceLocation advancement) {
        var honor = YautjaHonorConfig.honorFor(advancement);

        if (honor <= 0) {
            return;
        }

        var ledger = HonorLedger.get(player.server);
        var becameWorthy = ledger.credit(player.getUUID(), advancement.toString(), honor, YautjaHonorConfig.unlockThreshold());

        if (becameWorthy) {
            Predator.LOGGER.info(
                "{} is now worthy of the hunt ({} honor)",
                player.getName().getString(),
                ledger.entry(player.getUUID()).honor()
            );
        }
    }

    /** Credits every listed advancement this player already holds. */
    public static void scanExisting(ServerPlayer player) {
        var advancements = player.server.getAdvancements();

        for (var id : YautjaHonorConfig.all().keySet()) {
            var location = ResourceLocation.tryParse(id);

            if (location == null) {
                continue;
            }

            var holder = advancements.get(location);

            if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                onAdvancementCompleted(player, location);
            }
        }
    }

    // ---------------------------------------------------------------- code-awarded advancements

    /**
     * Completes an advancement for a player by granting every criterion it still lacks. Does nothing if it does not
     * exist (a data pack removed it) or is already done.
     */
    public static void award(ServerPlayer player, ResourceLocation advancement) {
        var holder = player.server.getAdvancements().get(advancement);

        if (holder == null) {
            return;
        }

        var progress = player.getAdvancements().getOrStartProgress(holder);

        if (progress.isDone()) {
            return;
        }

        // ⚠ Copied first: awarding a criterion changes the progress this is iterating.
        var remaining = new ArrayList<String>();
        progress.getRemainingCriteria().forEach(remaining::add);

        for (var criterion : remaining) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    /** From {@code MixinLivingEntity_HonorLiveFire}: something took damage. */
    public static void onHurt(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || !(victim instanceof Enemy) || !source.is(HUMAN_BULLET)) {
            return;
        }

        if (source.getEntity() instanceof ServerPlayer shooter) {
            award(shooter, LIVE_FIRE);
        }
    }

    private static boolean wearsFullApeSet(ServerPlayer player) {
        for (var i = 0; i < APE_SLOTS.length; i++) {
            var key = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(APE_SLOTS[i]).getItem());

            if (!"avp_human".equals(key.getNamespace()) || !APE_PIECES[i].equals(key.getPath())) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------------- tick

    /** Registered on post-level-tick. Cheap: a map lookup per player, plus an armour check every two seconds. */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var checkArmor = serverLevel.getGameTime() % ARMOR_CHECK_INTERVAL == 0;

        for (var player : serverLevel.players()) {
            if (SCANNED.putIfAbsent(player, Boolean.TRUE) == null) {
                scanExisting(player);
            }

            if (checkArmor && wearsFullApeSet(player)) {
                award(player, WY_APE_SET);
            }
        }
    }
}
