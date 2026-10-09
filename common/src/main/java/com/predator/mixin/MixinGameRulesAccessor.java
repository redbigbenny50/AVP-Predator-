package com.predator.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code GameRules.register} is private; this is the standard invoker (avp_alien and BLib carry the same one). */
@Mixin(GameRules.class)
public interface MixinGameRulesAccessor {

    @Invoker("register")
    static <T extends GameRules.Value<T>> GameRules.Key<T> avp_predator$register(
        String name,
        GameRules.Category category,
        GameRules.Type<T> type
    ) {
        throw new AssertionError();
    }
}
