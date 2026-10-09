package com.predator.common.registry.init;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.effect.AdrenalineRushEffect;
import com.predator.common.gameplay.effect.CloakStatusEffect;
import com.predator.common.gameplay.effect.MudStatusEffect;
import com.predator.common.gameplay.effect.RoarStunEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import java.util.function.Supplier;

public class PredatorMobEffects {

    private static final BLibRegistry<MobEffect> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.MOB_EFFECT);

    private static final BLibHolder<MobEffect> MUD = create("mud", MudStatusEffect::new);

    private static final BLibHolder<MobEffect> CLOAK = create("cloak", CloakStatusEffect::new);

    /** Applied by the yautja roar. See RoarStunEffect. */
    private static final BLibHolder<MobEffect> ROAR_STUN = create("roar_stun", RoarStunEffect::new);

    /** ⚠ Same shape as the two below — the backing Holder, which is what MobEffectInstance takes. */
    /** A yautja's second wind when the odds turn. See AdrenalineRushEffect. */
    private static final BLibHolder<MobEffect> ADRENALINE_RUSH = create("adrenaline_rush", AdrenalineRushEffect::new);

    /** Frozen solid — the stand-in used only without avp_human (see FreezeBridge). */
    private static final BLibHolder<MobEffect> FROZEN_SOLID = create(
        "frozen_solid",
        com.predator.common.gameplay.effect.FrozenSolidStandInEffect::new
    );

    public static Holder<MobEffect> getFrozenSolidHolder() {
        return FROZEN_SOLID.getBackingHolder();
    }

    public static Holder<MobEffect> getAdrenalineRushHolder() {
        return ADRENALINE_RUSH.getBackingHolder();
    }

    public static Holder<MobEffect> getRoarStunHolder() {
        return ROAR_STUN.getBackingHolder();
    }

    public static Holder<MobEffect> getCloakHolder() {
        return CLOAK.getBackingHolder();
    }

    public static Holder<MobEffect> getMudHolder() {
        return MUD.getBackingHolder();
    }

    private static BLibHolder<MobEffect> create(String path, Supplier<MobEffect> mobEffectSupplier) {
        return REGISTRY.createHolder(path, mobEffectSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
