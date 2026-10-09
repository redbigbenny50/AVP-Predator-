package com.predator.common.gameplay.cloak;

import com.predator.Predator;
import com.predator.common.gameplay.effect.PredatorMud;
import com.predator.common.gameplay.item.CloakingDeviceItem;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.network.packet.S2CCloakStatePayload;
import com.predator.common.network.packet.S2CMudStatePayload;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.tag.PredatorDamageTypeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative cloak runtime. Owns which entities have their field up, the damage the field has absorbed, the
 * melee-reveal window and the overload cooldown — and pushes every change out to tracking clients.
 * <p>
 * All state here is <em>transient</em> by design. Engagement is derived each tick from "is a cloaking device still in
 * this player's inventory", which is exactly the rule asked for: drop it, or stash it in a chest, and the field drops.
 * That also means nothing has to be persisted, and a relog comes back uncloaked, which is the safe default.
 */
public final class PredatorCloakManager {

    private static final Map<UUID, Runtime> RUNTIMES = new HashMap<>();

    private PredatorCloakManager() {
        throw new UnsupportedOperationException();
    }

    /** Per-player throttle for the device-less scan path — see the note in {@code tickPlayer}. */
    private static final java.util.Map<java.util.UUID, Integer> NO_DEVICE_NEXT_SCAN = new java.util.HashMap<>();

    /** One second: past the join window in which a payload can be lost, imperceptible to the player. */
    private static final int RESYNC_DELAY_TICKS = 20;

    private static final class Runtime {

        private boolean cloaked;

        private int resyncAtTick;

        private float absorbedDamage;

        private int revealUntilTick;

        private int cooldownUntilTick;

        private int lastOverloadMessageTick;

        private int continuousWetTicks;

        private int wetGraceTicks;

        private boolean broadcastState;

        /**
         * Entity id the runtime was last seen under. Entity ids are reassigned on every login, so a mismatch means this
         * is a NEW session for the same UUID.
         */
        private int lastEntityId = -1;
    }

    public static boolean isCloaked(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }

        var runtime = RUNTIMES.get(entity.getUUID());
        return runtime != null && runtime.cloaked;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Per-tick sweep
    // ---------------------------------------------------------------------------------------------------------------

    /** Registered against {@code preLevelTick}. Cheap: one pass over the level's players, no entity-wide scan. */
    public static void tickLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var now = serverLevel.getServer().getTickCount();

        for (var player : serverLevel.players()) {
            tickPlayer(player, now);
        }

        // Mobs still hunting a wearer who has just re-cloaked, walking to where they last saw them.
        PredatorCloakLostTrack.tick(serverLevel);
    }

    private static void tickPlayer(ServerPlayer player, int now) {
        var runtime = RUNTIMES.get(player.getUUID());

        // ⚠ RUNTIMES is static and survives leaving a world, because in singleplayer the integrated server restarts
        // inside the same game process. So on relog the map still holds the PREVIOUS session's entry, saying cloaked
        // with broadcastState already true — the restore branch below never ran, and setCloaked early-returned because
        // it thought the client already knew. Server said cloaked, client had just cleared its cache on world change,
        // and nothing ever re-synchronised them: the cloak silently vanished and the state was inconsistent from then
        // on. Entity ids are reassigned per login, so a mismatch is a reliable "this is a new session" signal.
        if (runtime != null && runtime.lastEntityId != player.getId()) {
            RUNTIMES.remove(player.getUUID());
            runtime = null;
        }

        if (runtime == null) {
            // ⚠⚠ Aug 27 — THE DOUBLE-SCAN FIX, deferred from the crash hunt and now built on its own. Without the
            // throttle below, every player WITHOUT a cloaking device paid two full inventory sweeps per tick forever
            // (storedCooldownRemaining + storedActivation, one data-component lookup per slot) — the runtime only
            // exists once a device is found, so the no-device path was the hot one. Both sweeps now run at most once
            // per second per device-less player. Responsiveness is untouched: toggling the device creates the runtime
            // directly and never passes through here, and the only thing the scans serve is the once-per-login
            // restore, where a sub-second delay is imperceptible.
            var nextScanTick = NO_DEVICE_NEXT_SCAN.get(player.getUUID());

            if (nextScanTick != null && now < nextScanTick) {
                return;
            }

            // Fresh login. Restore the overload FIRST — otherwise relogging cleared it, which let a player burn the
            // cloak out and immediately re-engage.
            var storedCooldownTicks = storedCooldownRemaining(player);

            if (storedCooldownTicks > 0) {
                var restored = RUNTIMES.computeIfAbsent(player.getUUID(), uuid -> new Runtime());
                restored.lastEntityId = player.getId();
                restored.cooldownUntilTick = now + storedCooldownTicks;
                restored.resyncAtTick = now + RESYNC_DELAY_TICKS;
                restored.lastOverloadMessageTick = 0;
                player.getCooldowns().addCooldown(PredatorItems.CLOAKING_DEVICE.get(), storedCooldownTicks);
                setStoredActivation(player, false);
                return;
            }

            if (storedActivation(player)) {
                var restored = RUNTIMES.computeIfAbsent(player.getUUID(), uuid -> new Runtime());
                restored.lastEntityId = player.getId();
                restored.resyncAtTick = now + RESYNC_DELAY_TICKS;
                setCloaked(player, restored, true);
                NO_DEVICE_NEXT_SCAN.remove(player.getUUID());
                return;
            }

            NO_DEVICE_NEXT_SCAN.put(player.getUUID(), now + 20);
            return;
        }

        NO_DEVICE_NEXT_SCAN.remove(player.getUUID());

        // ⚠ Aug 27 — LOGIN RESYNC. The restore branch above fires its S2CCloakStatePayload during the player's very
        // first server ticks, which is exactly the window where a joining client can still miss a payload — the
        // live symptom was rejoining while cloaked and rendering fully visible until a manual toggle cycle. One
        // deliberate re-send a second after join, to the player AND everyone tracking them, closes that window; the
        // client-side cache is a plain id-keyed map, so a duplicate arrival is idempotent.
        if (runtime.resyncAtTick != 0 && now >= runtime.resyncAtTick) {
            runtime.resyncAtTick = 0;
            var resync = new S2CCloakStatePayload(player.getId(), runtime.cloaked);
            Predator.MOD.networking().sendToClient(player, resync);
            Predator.MOD.networking().sendToAllClientsTrackingEntity(player, resync);
        }

        // The device must still be somewhere in the player's own inventory. Dropping it or stashing it kills the field.
        if (runtime.cloaked && !hasDevice(player)) {
            setCloaked(player, runtime, false);
            message(player, Component.translatable("message.avp_predator.cloak.deactivated").withStyle(ChatFormatting.AQUA));
        }

        // Sustained soaking burns the device out. The counter resets the moment they are dry, so repeated short
        // crossings never accumulate into a failure — only standing in it does.
        if (runtime.cloaked && player.isInWaterOrRain()) {
            runtime.wetGraceTicks = PredatorCloak.WATER_GRACE_TICKS;
            runtime.continuousWetTicks++;

            if (runtime.continuousWetTicks >= PredatorCloak.WATER_TOLERANCE_TICKS) {
                setCloaked(player, runtime, false);
                startCooldown(player, runtime, now);
                return;
            }
        } else if (runtime.wetGraceTicks > 0) {
            // ⚠ Grace period, not an immediate reset. Bouncing in and out of water cleared the soak counter on every
            // frame out of the water, so hopping indefinitely kept the field alive — the exact limitation the water
            // rule exists to impose. The counter now keeps accruing until the wearer has been continuously dry for
            // WATER_GRACE_TICKS.
            runtime.wetGraceTicks--;
            runtime.continuousWetTicks++;
        } else {
            runtime.continuousWetTicks = 0;
        }

        // Melee-reveal window expiring: the field comes back up on its own, no cooldown. Deliberate — the reveal is a
        // brief tell so nearby mobs can react, not a punishment.
        if (!runtime.cloaked && runtime.revealUntilTick > 0 && now >= runtime.revealUntilTick) {
            runtime.revealUntilTick = 0;

            if (hasDevice(player) && now >= runtime.cooldownUntilTick) {
                setCloaked(player, runtime, true);
            }
        }

        if (runtime.cooldownUntilTick > now) {
            if (now - runtime.lastOverloadMessageTick >= PredatorCloak.OVERLOAD_MESSAGE_INTERVAL_TICKS) {
                runtime.lastOverloadMessageTick = now;
                var seconds = Math.max(1, (runtime.cooldownUntilTick - now) / 20);
                message(
                    player,
                    Component.translatable("message.avp_predator.cloak.overloaded")
                        .append(Component.literal(" (" + seconds + "s)"))
                        .withStyle(ChatFormatting.RED)
                );
            }
        }

        if (!runtime.cloaked && runtime.revealUntilTick == 0 && runtime.cooldownUntilTick <= now && !hasDevice(player)) {
            RUNTIMES.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Player actions
    // ---------------------------------------------------------------------------------------------------------------

    /** Left-click while holding the device. Toggles the field, or refuses and explains why. */
    public static void toggle(ServerPlayer player) {
        var now = player.server.getTickCount();
        var runtime = RUNTIMES.computeIfAbsent(player.getUUID(), uuid -> new Runtime());
        runtime.lastEntityId = player.getId();

        if (runtime.cloaked) {
            setCloaked(player, runtime, false);
            startCooldown(player, runtime, now);
            return;
        }

        if (now < runtime.cooldownUntilTick) {
            runtime.lastOverloadMessageTick = 0;
            return;
        }

        if (!hasDevice(player)) {
            return;
        }

        runtime.absorbedDamage = 0.0F;
        runtime.revealUntilTick = 0;
        runtime.continuousWetTicks = 0;
        setStoredCooldown(player, 0L);
        setCloaked(player, runtime, true);
        message(player, Component.translatable("message.avp_predator.cloak.activated").withStyle(ChatFormatting.AQUA));
        player.level()
            .playSound(null, player.blockPosition(), PredatorSoundEvents.CLOAK_ON.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * A melee connection drops the field for {@link PredatorCloak#MELEE_REVEAL_TICKS}, then it re-engages for free.
     * Ranged attacks deliberately do not trigger this.
     */
    public static void onMeleeHit(LivingEntity attacker) {
        var runtime = RUNTIMES.get(attacker.getUUID());

        if (runtime == null || !runtime.cloaked) {
            return;
        }

        if (!(attacker.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        runtime.revealUntilTick = serverLevel.getServer().getTickCount() + PredatorCloak.MELEE_REVEAL_TICKS;

        // Players carry the action-bar line and the device component; mobs need only the flag and the broadcast.
        if (attacker instanceof ServerPlayer serverPlayer) {
            setCloaked(serverPlayer, runtime, false);
            return;
        }

        setCloakedInternal(attacker, runtime, false);
    }

    /**
     * Damage absorption. Chip damage is survivable; four hearts total collapses the field and starts the 60s overload.
     * Damage types in {@code avp_predator:cloak_ignores} (starvation, poison, wither, magic, drowning) never count —
     * the field is shorted by impacts, not by attrition.
     */
    public static void onDamaged(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return;
        }

        var runtime = RUNTIMES.get(entity.getUUID());

        if (runtime == null || !runtime.cloaked) {
            return;
        }

        if (source.is(PredatorDamageTypeTags.CLOAK_IGNORES)) {
            return;
        }

        runtime.absorbedDamage += amount;

        if (runtime.absorbedDamage < PredatorCloak.DAMAGE_BREAK_THRESHOLD) {
            return;
        }

        if (entity instanceof ServerPlayer player) {
            setCloaked(player, runtime, false);
            startCooldown(player, runtime, player.server.getTickCount());
            return;
        }

        // Non-player wearers take the same four-heart break, minus the player-only trimmings.
        setCloakedInternal(entity, runtime, false);
        runtime.absorbedDamage = 0.0F;

        if (entity.level() instanceof ServerLevel serverLevel) {
            runtime.cooldownUntilTick = serverLevel.getServer().getTickCount() + PredatorCloak.COOLDOWN_TICKS;
        }
    }

    /** Called when a player disconnects so the map does not grow without bound. */
    public static void forget(Entity entity) {
        RUNTIMES.remove(entity.getUUID());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sync
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Registered against {@code onPlayerStartTrackingEntity}. Without this a player walking into range of an
     * already-cloaked predator would render them normally until the next state change — which, for a cloak, could be
     * never.
     */
    public static void onStartTracking(Entity tracked, Player observer) {
        if (!(observer instanceof ServerPlayer serverObserver) || !(tracked instanceof LivingEntity living)) {
            return;
        }

        // Mud rides along on the same hook for the same reason: vanilla sends no effect data on pairing either, so a
        // muddy mob that wandered into range would read as thermally visible until its next scheduled refresh.
        var mudTicks = PredatorMud.remainingTicks(living);

        if (mudTicks > 0) {
            Predator.MOD.networking()
                .sendToClient(serverObserver, new S2CMudStatePayload(tracked.getId(), mudTicks));
        }

        if (!isCloaked(living)) {
            return;
        }

        Predator.MOD.networking().sendToClient(serverObserver, new S2CCloakStatePayload(tracked.getId(), true));
    }

    /**
     * Engages the field on a non-player wearer (currently the yautja). Mobs have no device and no inventory, so their
     * engagement is driven by AI rather than by an item — but every other rule is the shared one.
     */
    public static void engage(LivingEntity entity) {
        if (entity.level().isClientSide || entity instanceof ServerPlayer) {
            return;
        }

        var runtime = RUNTIMES.computeIfAbsent(entity.getUUID(), uuid -> new Runtime());

        if (runtime.cloaked) {
            return;
        }

        runtime.absorbedDamage = 0.0F;
        runtime.revealUntilTick = 0;
        runtime.continuousWetTicks = 0;
        setCloakedInternal(entity, runtime, true);
    }

    /**
     * Drops the field for a set number of ticks and lets it come back on its own afterwards, free.
     * <p>
     * The same mechanism {@link #onMeleeHit} uses, with a caller-chosen duration and repeatable: calling it again while
     * a window is open EXTENDS it rather than restarting the drop, which is what a sustained reveal needs. The plasma
     * caster holds one of these open for as long as it is deployed, which is his rule that a yautja "would decloak to
     * charge and fire it then cloak again after. This gives the party its attacking a chance to attack it back."
     * <p>
     * ⚠ Costs no overload and no cooldown, deliberately. A hunter that fights this way for an hour must not end up with
     * a burnt-out cloak; the 60s penalty belongs to damage breaking the field, not to choosing to show yourself.
     */
    public static void revealFor(LivingEntity entity, int ticks) {
        if (entity.level().isClientSide) {
            return;
        }

        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var runtime = RUNTIMES.get(entity.getUUID());

        if (runtime == null) {
            return;
        }

        var until = serverLevel.getServer().getTickCount() + ticks;

        if (until > runtime.revealUntilTick) {
            runtime.revealUntilTick = until;
        }

        if (!runtime.cloaked) {
            return;
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            setCloaked(serverPlayer, runtime, false);
            return;
        }

        setCloakedInternal(entity, runtime, false);
    }

    /**
     * {@return whether a deliberate reveal window is still open on this wearer}
     * <p>
     * ⚠⚠ This closes a real hole rather than only serving the caster. {@code revealUntilTick} was only ever honoured on
     * the PLAYER path; for a mob, {@code YautjaCloakGoal} re-engaged the instant it saw an uncloaked yautja that was
     * not on cooldown — so the two-second melee-hit reveal a yautja is honor-bound to give was being cancelled on the
     * very next tick and never actually showed. The goal now asks this first.
     */
    public static boolean isRevealed(LivingEntity entity) {
        var runtime = RUNTIMES.get(entity.getUUID());

        if (runtime == null || !(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        return serverLevel.getServer().getTickCount() < runtime.revealUntilTick;
    }

    /** {@return whether this wearer is still inside an overload cooldown} */
    public static boolean isOnCooldown(LivingEntity entity) {
        var runtime = RUNTIMES.get(entity.getUUID());

        if (runtime == null || !(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        return serverLevel.getServer().getTickCount() < runtime.cooldownUntilTick;
    }

    /**
     * Per-tick rules shared by every wearer that is not a player: water tolerance, melee-reveal expiry and the
     * overload. Driven from {@code YautjaCloakGoal} rather than a level sweep, so the cost is paid only by mobs that
     * actually have a cloak.
     */
    public static void tickNonPlayer(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var runtime = RUNTIMES.get(entity.getUUID());

        if (runtime == null || !runtime.cloaked) {
            return;
        }

        var now = serverLevel.getServer().getTickCount();

        if (entity.isInWaterOrRain()) {
            runtime.wetGraceTicks = PredatorCloak.WATER_GRACE_TICKS;
            runtime.continuousWetTicks++;

            if (runtime.continuousWetTicks >= PredatorCloak.WATER_TOLERANCE_TICKS) {
                setCloakedInternal(entity, runtime, false);
                runtime.continuousWetTicks = 0;
                runtime.absorbedDamage = 0.0F;
                runtime.cooldownUntilTick = now + PredatorCloak.COOLDOWN_TICKS;
            }
        } else if (runtime.wetGraceTicks > 0) {
            // Same grace as the player path — a yautja hopping a stream must not dodge the water limit either.
            runtime.wetGraceTicks--;
            runtime.continuousWetTicks++;
        } else {
            runtime.continuousWetTicks = 0;
        }
    }

    /**
     * The part of engagement that is identical for every wearer: flip the flag and tell every tracking client. Player
     * extras (action-bar lines, the device component, the HUD effect, the vanilla item cooldown) live in
     * {@link #setCloaked}, which calls through to this.
     */
    private static void setCloakedInternal(LivingEntity entity, Runtime runtime, boolean cloaked) {
        if (runtime.cloaked == cloaked && runtime.broadcastState == cloaked) {
            return;
        }

        runtime.cloaked = cloaked;
        runtime.broadcastState = cloaked;

        Predator.MOD.networking()
            .sendToAllClientsTrackingEntity(entity, new S2CCloakStatePayload(entity.getId(), cloaked));

        if (cloaked) {
            PredatorCloakLostTrack.onConcealed(entity);
        }
    }

    private static void setCloaked(ServerPlayer player, Runtime runtime, boolean cloaked) {
        if (runtime.cloaked == cloaked && runtime.broadcastState == cloaked) {
            return;
        }

        runtime.cloaked = cloaked;
        runtime.broadcastState = cloaked;

        // Persisted on the DEVICE, not the player, so it survives a relog for free and travels with the item. The
        // per-tick inventory sweep re-engages from it on login, and dropping the device still drops the field because
        // the sweep can no longer find it.
        setStoredActivation(player, cloaked);

        // HUD marker only — carries no behaviour. Without it a cloaked player in first person has no way to tell.
        if (cloaked) {
            player.addEffect(new MobEffectInstance(PredatorMobEffects.getCloakHolder(), -1, 0, true, false, true));
        } else {
            player.removeEffect(PredatorMobEffects.getCloakHolder());
        }

        if (cloaked) {
            PredatorCloakLostTrack.onConcealed(player);
        }

        var payload = new S2CCloakStatePayload(player.getId(), cloaked);
        Predator.MOD.networking().sendToAllClientsTrackingEntity(player, payload);
        // Trackers exclude the entity itself, so the wearer needs their own copy — they render their own arms and,
        // in third person, their own body.
        Predator.MOD.networking().sendToClient(player, payload);
    }

    private static void startCooldown(ServerPlayer player, Runtime runtime, int now) {
        runtime.absorbedDamage = 0.0F;
        runtime.continuousWetTicks = 0;
        runtime.revealUntilTick = 0;
        runtime.cooldownUntilTick = now + PredatorCloak.COOLDOWN_TICKS;
        runtime.lastOverloadMessageTick = 0;
        setStoredCooldown(player, player.level().getGameTime() + PredatorCloak.COOLDOWN_TICKS);

        // Vanilla's cooldown tracker is already synced and draws the sweep overlay on the icon for free.
        player.getCooldowns().addCooldown(PredatorItems.CLOAKING_DEVICE.get(), PredatorCloak.COOLDOWN_TICKS);
        player.level()
            .playSound(null, player.blockPosition(), PredatorSoundEvents.CLOAK_OFF.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    private static boolean hasDevice(Player player) {
        var inventory = player.getInventory();

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            if (isDevice(inventory.getItem(index))) {
                return true;
            }
        }

        return isDevice(player.getOffhandItem()) || isDevice(seatedDevice(player));
    }

    /**
     * ⚠⚠ THE DEVICE SEATED IN THE WORN GAUNTLET IS NOT AN INVENTORY SLOT. It lives inside the gauntlet's contents
     * component, so every inventory sweep in this class walked straight past it: the C key reached the server, the
     * gauntlet handler saw a device in the cloak slot and called toggle(), and toggle() answered "no device" and
     * returned without a word. [stated] "cloaking device on gauntlet ... displays the message of the keybind required
     * to activate it but doesnt actually activate." Every read and write below now also covers this stack.
     */
    private static ItemStack seatedDevice(Player player) {
        var gauntlet = GauntletItem.equipped(player);

        return gauntlet.isEmpty() ? ItemStack.EMPTY : new GauntletContents(gauntlet).getItem(GauntletContents.CLOAK_SLOT);
    }

    /**
     * Writes a component onto the seated device. The contents hand out COPIES, so the write goes back through setItem.
     */
    private static <V> void setOnSeatedDevice(Player player, net.minecraft.core.component.DataComponentType<V> type, V value) {
        var gauntlet = GauntletItem.equipped(player);

        if (gauntlet.isEmpty()) {
            return;
        }

        var contents = new GauntletContents(gauntlet);
        var seated = contents.getItem(GauntletContents.CLOAK_SLOT);

        if (isDevice(seated)) {
            seated.set(type, value);
            contents.setItem(GauntletContents.CLOAK_SLOT, seated);
        }
    }

    /**
     * Reads the stored on/off flag from whichever cloaking device the player is carrying.
     * <p>
     * Storing it on the stack is what makes the cloak survive a relog. A transient server-side map cannot, and player
     * persistent data differs per loader; an item component is saved with the inventory on both.
     */
    private static boolean storedActivation(Player player) {
        var inventory = player.getInventory();

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index);

            if (isDevice(stack) && Boolean.TRUE.equals(stack.get(PredatorDataComponents.CLOAK_ACTIVE.get()))) {
                return true;
            }
        }

        var seated = seatedDevice(player);

        return isDevice(seated) && Boolean.TRUE.equals(seated.get(PredatorDataComponents.CLOAK_ACTIVE.get()));
    }

    /** {@return ticks left on a cooldown recorded before the last relog, or 0 if none} */
    private static int storedCooldownRemaining(Player player) {
        var inventory = player.getInventory();
        var now = player.level().getGameTime();

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index);

            if (!isDevice(stack)) {
                continue;
            }

            var until = stack.get(PredatorDataComponents.CLOAK_COOLDOWN_UNTIL.get());

            if (until != null && until > now) {
                return (int) Math.min(until - now, PredatorCloak.COOLDOWN_TICKS);
            }
        }

        var seated = seatedDevice(player);

        if (isDevice(seated)) {
            var until = seated.get(PredatorDataComponents.CLOAK_COOLDOWN_UNTIL.get());

            if (until != null && until > now) {
                return (int) Math.min(until - now, PredatorCloak.COOLDOWN_TICKS);
            }
        }

        return 0;
    }

    private static void setStoredCooldown(Player player, long untilGameTime) {
        var inventory = player.getInventory();

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index);

            if (isDevice(stack)) {
                stack.set(PredatorDataComponents.CLOAK_COOLDOWN_UNTIL.get(), untilGameTime);
            }
        }

        setOnSeatedDevice(player, PredatorDataComponents.CLOAK_COOLDOWN_UNTIL.get(), untilGameTime);
    }

    private static void setStoredActivation(Player player, boolean active) {
        var inventory = player.getInventory();

        for (var index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index);

            if (isDevice(stack)) {
                stack.set(PredatorDataComponents.CLOAK_ACTIVE.get(), active);
            }
        }

        setOnSeatedDevice(player, PredatorDataComponents.CLOAK_ACTIVE.get(), active);
    }

    private static boolean isDevice(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CloakingDeviceItem;
    }

    private static void message(ServerPlayer player, Component component) {
        player.displayClientMessage(component, true);
    }
}
