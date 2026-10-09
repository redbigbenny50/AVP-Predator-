package com.predator.common.network;

import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import com.predator.common.gameplay.cloak.PredatorCloakManager;
import com.predator.common.gameplay.component.PredatorVisionMode;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.gameplay.net.NetStruggle;
import com.predator.common.network.packet.C2SArmGauntletPayload;
import com.predator.common.network.packet.C2SCyclePredatorVisionPayload;
import com.predator.common.network.packet.C2SDisarmGauntletPayload;
import com.predator.common.network.packet.C2SNetStruggleMashPayload;
import com.predator.common.network.packet.C2SOpenGauntletPayload;
import com.predator.common.network.packet.C2SToggleCloakPayload;
import com.predator.common.network.packet.C2SToggleGauntletCloakPayload;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorGameRules;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorArmorItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public class PredatorServerListener {

    private PredatorServerListener() {
        throw new UnsupportedOperationException();
    }

    /**
     * One struggle input from a netted player.
     * <p>
     * ⚠ The payload carries nothing and this trusts nothing from it — the server decides whether this player is netted
     * and rate-limits the input itself. A client-supplied target would let anyone free anyone.
     */
    /**
     * Opens the gauntlet menu.
     * <p>
     * ⚠ The server locates the offhand gauntlet itself. The payload carries nothing, so a client cannot ask to open a
     * container it is not holding.
     */
    /**
     * Throws the combi stick after a charged left-click. <strong>⚠⚠ THE SERVER RE-CHECKS EVERYTHING</strong> The
     * payload carries only a charge value. This confirms the player is actually holding an EXTENDED combi stick and
     * clamps the charge to the legal range — a client that lied could otherwise throw a stick it does not have, or
     * throw one at arbitrary power.
     */

    /** The worn GUI's panels: arm (four 9s) or cancel (any panel blanked). Only a worn, not-yet-counting gauntlet. */
    /** The hand caster's trigger. Everything is decided server-side — see HandCasterItem. */
    public static void handleHandCasterPayload(com.predator.common.network.packet.C2SHandCasterPayload payload, Player player) {
        switch (payload.action()) {
            case com.predator.common.network.packet.C2SHandCasterPayload.PRESS -> com.predator.common.gameplay.item.HandCasterItem
                .startCharge(player);
            case com.predator.common.network.packet.C2SHandCasterPayload.RELEASE -> com.predator.common.gameplay.item.HandCasterItem
                .release(
                    player,
                    payload.hasTip() ? new net.minecraft.world.phys.Vec3(payload.tipX(), payload.tipY(), payload.tipZ()) : null
                );
            default -> com.predator.common.gameplay.item.HandCasterItem.cancel(player);
        }
    }

    public static void handleArmGauntletPayload(C2SArmGauntletPayload payload, Player player) {
        var gauntlet = GauntletItem.equipped(player);

        if (gauntlet.isEmpty() || GauntletSelfDestruct.isCounting(gauntlet)) {
            return;
        }

        // ⚠ Rule off: said twice on purpose — [stated] "you get a popup on your screen and chat".
        if (!PredatorGameRules.isSelfDestructEnabled(player.level()) && payload.mode() != C2SArmGauntletPayload.CANCEL) {
            var message = Component.translatable("gauntlet.avp_predator.destruct.disabled");

            player.displayClientMessage(message, true);
            player.displayClientMessage(message, false);
            return;
        }

        switch (payload.mode()) {
            case C2SArmGauntletPayload.ARM -> {
                GauntletSelfDestruct.arm(player, gauntlet);
                player.displayClientMessage(Component.translatable("gauntlet.avp_predator.destruct.armed"), false);
            }
            case C2SArmGauntletPayload.CANCEL -> {
                if (GauntletSelfDestruct.cancelArming(gauntlet)) {
                    player.displayClientMessage(Component.translatable("gauntlet.avp_predator.destruct.cancelled"), true);
                }
            }
            default -> {
                // PROBE: enabled, nothing to say.
            }
        }
    }

    /** A disarm attempt on a placed gauntlet. The server holds the code; the client only sends what it typed. */
    public static void handleDisarmGauntletPayload(C2SDisarmGauntletPayload payload, Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        var pos = BlockPos.of(payload.blockPos());

        if (player.distanceToSqr(pos.getCenter()) > 64.0 || !(level.getBlockEntity(pos) instanceof GauntletBlockEntity entity)) {
            return;
        }

        if (GauntletSelfDestruct.tryDisarm(level, entity.getGauntlet(), payload.code())) {
            entity.setChanged();
            level.sendBlockUpdated(pos, entity.getBlockState(), entity.getBlockState(), 3);
            level.playSound(null, pos, PredatorSoundEvents.GAUNTLET_DESTRUCT_ARMED_CANCEL.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
            player.displayClientMessage(Component.translatable("gauntlet.avp_predator.destruct.disarmed"), true);
        }
    }

    public static void handleOpenGauntletPayload(C2SOpenGauntletPayload payload, Player player) {
        var gauntlet = GauntletItem.equipped(player);

        if (gauntlet.isEmpty()) {
            return;
        }

        player.openMenu(GauntletItem.provider(gauntlet));

        // ⚠⚠ THE WRIST DOOR OPENS FOR EVERYONE, FROM THE SERVER. It used to be dispatched by the screen, client-side,
        // so only the player using the GUI ever saw it. [stated] "its more noticed by the people around the player
        // than the player themselves so they can see if their friend is accessing the inventory." Held, not played
        // once: the clip is authored hold_on_last_frame and stays open until close is sent from the menu's removed().
        GauntletItem.syncClip(player, "open", AzPlayBehaviors.HOLD_ON_LAST_FRAME);
        // With the clip, not from the screen: everyone who can see the wearer hears the door.
        player.level().playSound(null, player.blockPosition(), PredatorSoundEvents.GAUNTLET_OPEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Toggles the cloak seated in the gauntlet.
     * <p>
     * ⚠⚠ RE-CHECKS THE HOUSING SERVER-SIDE. The keybind already refuses when nothing is fitted, but a client that lies
     * would otherwise get a free cloak — the housing is the whole cost of the ability.
     */
    public static void handleToggleGauntletCloakPayload(C2SToggleGauntletCloakPayload payload, Player player) {
        var gauntlet = GauntletItem.equipped(player);

        if (gauntlet.isEmpty()) {
            return;
        }

        var contents = new GauntletContents(gauntlet);

        if (contents.getItem(GauntletContents.CLOAK_SLOT).isEmpty()) {
            player.displayClientMessage(Component.translatable("gauntlet.avp_predator.no_cloak"), true);

            return;
        }

        // ⚠ ServerPlayer specifically — the cloak manager syncs state to trackers and cannot do that from a
        // client-side Player.
        if (player instanceof ServerPlayer serverPlayer) {
            PredatorCloakManager.toggle(serverPlayer);
        }
    }

    public static void handleNetStruggleMashPayload(C2SNetStruggleMashPayload payload, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetStruggle.onMash(serverPlayer);
        }
    }

    public static void handleCyclePredatorVisionPayload(C2SCyclePredatorVisionPayload payload, Player serverPlayer) {
        var helmet = serverPlayer.getItemBySlot(EquipmentSlot.HEAD);

        if (!helmet.is(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get())) {
            return;
        }

        var component = PredatorDataComponents.VISION_MODE.get();
        var current = helmet.getOrDefault(component, PredatorVisionMode.REGULAR);
        helmet.set(component, current.cycleNext());
    }

    /**
     * Left-click while holding a cloaking device. The held-item check is repeated here rather than trusted from the
     * client — the packet carries no payload precisely so that a spoofed one buys nothing.
     */
    public static void handleToggleCloakPayload(C2SToggleCloakPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(serverPlayer.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.CloakingDeviceItem)) {
            return;
        }

        PredatorCloakManager.toggle(serverPlayer);
    }
}
