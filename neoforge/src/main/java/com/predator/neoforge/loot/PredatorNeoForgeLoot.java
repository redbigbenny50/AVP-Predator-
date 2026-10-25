package com.predator.neoforge.loot;

import com.predator.Predator;
import com.predator.common.data.loot.PredatorVanillaLoot;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * avp_predator's additions to vanilla loot on NeoForge - Oct 24, the music disc fragments in ancient city and dungeon
 * chests (PredatorVanillaLoot; Fabric adds the same pools through its loot event). Pools of their own, so a chest's
 * normal loot is untouched. A failure is logged and skipped - never allowed to break the table's loading.
 */
public final class PredatorNeoForgeLoot {

    private PredatorNeoForgeLoot() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PredatorNeoForgeLoot::onLoad);
    }

    private static void onLoad(LootTableLoadEvent event) {
        var key = event.getKey();
        var table = event.getTable();
        if (key == null || table == null || table.isFrozen()) {
            return;
        }
        for (var pool : PredatorVanillaLoot.extraPools(key)) {
            try {
                table.addPool(pool.build());
            } catch (RuntimeException exception) {
                Predator.LOGGER.warn("avp_predator: could not add a pool to {}", key.location(), exception);
            }
        }
    }
}
