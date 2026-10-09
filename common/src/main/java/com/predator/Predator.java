package com.predator;

import com.blib.api.BLibAPI;
import com.blib.api.common.mod.v1.BLibMod;
import com.predator.common.config.YautjaTierConfig;
import com.predator.common.data.fixer.migration.PredatorDataMigrations;
import com.predator.common.gameplay.cloak.PredatorCloakManager;
import com.predator.common.gameplay.explosion.plasma.PlasmaDetonation;
import com.predator.common.gameplay.gauntlet.destruct.GauntletDestructRegistry;
import com.predator.common.gameplay.net.PredatorNet;
import com.predator.common.gameplay.whip.WhipItem;
import com.predator.common.network.PredatorPacketDirectionRegistry;
import com.predator.common.network.PredatorServerPacketHandlerRegistry;
import com.predator.common.property.PredatorPropertyAccess;
import com.predator.common.registry.init.PredatorArmorMaterials;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import com.predator.common.registry.init.PredatorBlocks;
import com.predator.common.registry.init.PredatorCommands;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorEntitySpawns;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.PredatorGameRules;
import com.predator.common.registry.init.PredatorMenuTypes;
import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.creative_mode_tab.PredatorCreativeModeTabs;
import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorBlockItems;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.init.item.block.PredatorSpawnEggItems;
import com.predator.mixin.ParrotSoundMapAccessor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Predator {

    public static final String MOD_ID = "avp_predator";

    public static final BLibMod MOD = BLibAPI.createMod(MOD_ID);

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void initialize() {
        LOGGER.info("Initializing AVP (Predator) for mod loader '{}'", BLibAPI.getModLoaderType());

        PredatorPropertyAccess.INSTANCE.save();

        MOD.initialize(Predator::runInitialization);
    }

    private static void runInitialization() {
        // ⚠⚠ FORCE-LOADS PredatorGameRules SO ITS STATIC INITIALISER RUNS NOW. Gamerules register into a static
        // table that vanilla reads when a GameRules instance is BUILT, i.e. when a world loads. If the class were
        // first touched by the cloak goal or a command, registration would happen after that and the rule would
        // simply not exist in the world — present in code, absent from /gamerule, silently doing nothing.
        PredatorGameRules.touch();

        // ⚠ BEFORE any yautja can spawn — attributes are read at spawn time, so a config loaded later would leave
        // every already-loaded predator on the old numbers until its chunk reloaded.
        YautjaTierConfig.load();

        // Yautja honor: what each advancement is worth and the unlock threshold for being hunted.
        com.predator.common.config.YautjaHonorConfig.load();

        // ⚠ Aug 28 — the ONE cross-repo touch of the claim-HUD feature: the cloak plugs into BLib's perception
        // registry so faction awareness cannot see a concealed wearer — except observers the cloak simply does
        // not work on (xenomorphs, the warden — the cloak's own immune list), exactly his ruling: "if cloaked
        // they shouldnt notice you at all, except xenomorphs".
        com.blib.api.common.perception.v1.BLibPerception.register(
            (observer, player) -> !com.predator.common.gameplay.cloak.PredatorCloak.isConcealed(player)
                || com.predator.common.gameplay.cloak.PredatorCloak.isImmune(observer)
        );

        PredatorArmorItems.initialize();
        PredatorArmorMaterials.initialize();
        PredatorBlockEntityTypes.initialize();
        PredatorBlockItems.initialize();
        PredatorBlocks.initialize();
        PredatorCommands.initialize();
        PredatorCreativeModeTabs.initialize();
        PredatorDataComponents.initialize();
        PredatorEntitySpawns.initialize();
        PredatorEntityTypes.initialize();
        PredatorMenuTypes.initialize();
        PredatorItems.initialize();
        PredatorMobEffects.initialize();
        PredatorSoundEvents.initialize();
        PredatorSpawnEggItems.initialize();

        PredatorPacketDirectionRegistry.initialize();
        PredatorServerPacketHandlerRegistry.initialize();

        PredatorDataMigrations.initialize();
        com.predator.common.data.PredatorReloadListeners.initialize();

        // Cloak: the per-tick sweep re-derives engagement from "is a device still in the wearer's inventory", and
        // the tracking hook hands a newly-arrived observer the current state — without it, walking into range of an
        // already-cloaked predator would render them normally until the next state change, which may never come.
        MOD.events().preLevelTick().register(PredatorCloakManager::tickLevel);
        MOD.events().onPlayerStartTrackingEntity().register(PredatorCloakManager::onStartTracking);

        // Net: the sweep ages every net and frees what has run out; the tracking hook shows the overlay to someone
        // who walks into view of an already-netted mob, which otherwise would never be told.
        MOD.events().preLevelTick().register(PredatorNet::tickLevel);

        // Gauntlet self-destruct: the deadline backstop, and interrupted blasts picked back up after a restart.
        MOD.events().postLevelTick().register(GauntletDestructRegistry::tickLevel);

        // The whip's reel. One map lookup per player per tick when nobody is grappling.
        MOD.events().postLevelTick().register(level -> {
            for (var player : level.players()) {
                WhipItem.tickPlayer(player);
            }
        });
        MOD.events().postLevelTick().register(PlasmaDetonation::resumeUnfinished);

        // Yautja honor: retroactive credit on login, and the WY Ape set check.
        MOD.events().postLevelTick().register(com.predator.common.gameplay.hunt.YautjaHonor::tickLevel);

        // The hunt: the Hunter's Moon, phase 1, and (pass 3) the attack. Overworld only.
        MOD.events().postLevelTick().register(com.predator.common.gameplay.hunt.HuntDirector::tickLevel);
        // The freeze grenade's ice: thaws it back to what it was, and keeps whatever stands in it freezing.
        MOD.events().postLevelTick().register(com.predator.common.gameplay.hunt.FreezePatches::tickLevel);
        MOD.events().onServerStarted().register(PredatorGameRules::applySingleplayerDefault);
        MOD.events().onPlayerStartTrackingEntity().register(PredatorNet::onStartTracking);

        // TODO: Only run this once on server start.
        MOD.events().preLevelTick().register(Predator::injectCustomParrotSounds);
    }

    // Marine Spawns and Ash placement in nuked zones
    public static void injectCustomParrotSounds(Level level) {
        if (level.isClientSide) {
            return;
        }

        var sounds = ParrotSoundMapAccessor.getSoundMap();

        /*
         * TODO: Use Yautja sound when added
         */
        sounds.put(PredatorEntityTypes.YAUTJA.get(), SoundEvents.ALLAY_AMBIENT_WITH_ITEM);
    }
}
