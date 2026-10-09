package com.predator.common.data.fixer.migration.impl;

import com.blib.api.common.data_fix.v1.BLibDataFixerRegistry;
import com.blib.api.common.data_fix.v1.BLibDataMigration;
import com.blib.api.common.mod.v1.model.Version;
import com.predator.PredatorResources;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Renames {@code avp_predator:yautja} to {@code avp_predator:yautja_jungle} without eating anyone's world.
 * <p>
 * ⚠ THIS MIGRATION IS THE ONLY REASON THE RENAME IS SAFE. A bare registry rename is destructive in two ways: every
 * yautja already in a loaded world fails to resolve and is silently dropped, and every spawn egg sitting in an
 * inventory, chest or shulker turns to nothing. Registering both ids here means the old name still resolves and is
 * rewritten to the new one on load.
 * <p>
 * The rename is happening now because there is currently exactly ONE predator type. Once several ship, the same change
 * costs a migration per type, and any world that skipped a version loses whatever was not covered.
 */
public class Predator_0_1_4_To_0_1_5_YautjaJungle implements BLibDataMigration {

    private static final String OLD_ID = "yautja";

    private static final String NEW_ID = "yautja_jungle";

    private static final String OLD_SPAWN_EGG_ID = "yautja_spawn_egg";

    private static final String NEW_SPAWN_EGG_ID = "yautja_jungle_spawn_egg";

    @Override
    public Version fromVersion() {
        return new Version(0, 1, 4);
    }

    @Override
    public Version toVersion() {
        return new Version(0, 1, 5);
    }

    @Override
    public void apply() {
        BLibDataFixerRegistry.register(
            new BLibDataFixerRegistry.Entry.Direct(
                BuiltInRegistries.ENTITY_TYPE,
                PredatorResources.location(OLD_ID),
                PredatorResources.location(NEW_ID)
            )
        );

        BLibDataFixerRegistry.register(
            new BLibDataFixerRegistry.Entry.Direct(
                BuiltInRegistries.ITEM,
                PredatorResources.location(OLD_SPAWN_EGG_ID),
                PredatorResources.location(NEW_SPAWN_EGG_ID)
            )
        );
    }
}
