package com.predator.common.gameplay.net;

/**
 * The net state a mob carries on ITSELF.
 * <h2>⚠⚠ WHY NOT A STATIC MAP</h2> The old net kept netted mobs in a {@code Map<UUID, Integer>} and scanned it every
 * tick. That could not survive a restart, it could not survive the mob unloading, and it cost work proportional to how
 * many mobs were netted — [stated] "make sure it persists as well. we dont want them despawning or unnetting" and "make
 * it efficient because its possible some players might net a large amount of things and just store them."
 * <p>
 * State now rides the entity's own NBT and is driven from the entity's own tick, so a captured mob is exactly as cheap
 * as any other mob, a hundred of them in a pen cost nothing extra, and they come back netted after a restart.
 * {@code MixinMob_NettedHold} implements this on every {@code Mob}.
 */
public interface NettedMob {

    boolean avp_predator$isNetted();

    void avp_predator$setNetted(boolean netted);

    /** {@return whether the net has closed for good — no more escape rolls} */
    boolean avp_predator$isCaptured();

    void avp_predator$setCaptured(boolean captured);

    int avp_predator$getFailedRolls();

    void avp_predator$setFailedRolls(int rolls);

    /** Game time of the next struggle/roll. Absolute, so it survives a reload. */
    long avp_predator$getNextRollTime();

    void avp_predator$setNextRollTime(long time);
}
