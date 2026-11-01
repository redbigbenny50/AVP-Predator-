package com.predator.client.animation.entity;

import com.blib.api.client.animation.v1.pose.AzPoseModifierRegistry;
import com.blib.api.client.animation.v1.pose.secondary.AzSpringBone;
import com.predator.common.gameplay.entity.living.yautja.Yautja;

/**
 * Spring physics on the yautja's dreadlocks (BLib pose system, AzureLib 3.2.3 port, Oct 31).
 * <p>
 * No clip animates a dreadlock, so until now they hung rigid from the head. Each of the 42 dreadlocks is now its own
 * short spring chain (base, middle, end segments) pinned where it leaves the crest: they swing when the yautja turns
 * its head, runs, leaps or stops, and settle back to hanging straight when it is still.
 * </p>
 * <p>
 * The chains were read from {@code yautja_jungle.geo.json} on Oct 31. A dreadlock renamed or removed in Blockbench is
 * simply skipped (a chain whose bones are missing does nothing), so a model change can never crash this.
 * </p>
 */
public final class YautjaDreadlockPhysics {

    /** Pose modifier ids are {@code dread0} .. {@code dread41}. */
    public static final String ID_PREFIX = "dread";

    /** How far each dreadlock's last segment reaches below its pivot, in pixels. */
    private static final float TIP_PIXELS = -2.0F;

    /** Close to hanging straight, but light enough to swing. */
    private static final float STIFFNESS = 0.20F;

    private static final float DAMPING = 0.30F;

    /** A touch of weight, so a swing settles downwards rather than floating. */
    private static final float GRAVITY = 0.002F;

    private static final String[][] CHAINS = {
        { "gLeftDreadBase1", "gLeftDreadMiddle1", "gLeftDreadEnd1" },
        { "gLeftDreadBase2", "gLeftDreadMiddle2", "gLeftDreadEnd2" },
        { "gLeftDreadBase3", "gLeftDreadMiddle3", "gLeftDreadEnd3", "gLeftDreadEnd5" },
        { "gLeftDreadBase4", "gLeftDreadMiddle4", "gLeftDreadEnd4", "gLeftDreadEnd6" },
        { "gRightDreadBase1", "gRightDreadMiddle1", "gRightDreadEnd1" },
        { "gRightDreadBase2", "gRightDreadMiddle2", "gRightDreadEnd2" },
        { "gRightDreadBase3", "gRightDreadMiddle3", "gRightDreadEnd3", "gRightDreadEnd5" },
        { "gRightDreadBase4", "gRightDreadMiddle4", "gRightDreadEnd4", "gRightDreadEnd6" },
        { "gLeftBackBigDreadBase1", "gLeftBackBigDreadMiddle1", "gLeftBackBigDreadEnd1", "gLeftBackBigDreadEnd4" },
        { "gLeftBackBigDreadBase2", "gLeftBackBigDreadMiddle2", "gLeftBackBigDreadEnd2", "gLeftBackBigDreadEnd5" },
        { "gLeftBackBigDreadBase3", "gLeftBackBigDreadMiddle3", "gLeftBackBigDreadEnd3", "gLeftBackBigDreadEnd6", "gLeftBackBigDreadEnd7" },
        { "gRightBackBigDreadBase1", "gRightBackBigDreadMiddle1", "gRightBackBigDreadEnd1", "gRightBackBigDreadEnd4" },
        { "gRightBackBigDreadBase2", "gRightBackBigDreadMiddle2", "gRightBackBigDreadEnd2", "gRightBackBigDreadEnd5" },
        {
            "gRightBackBigDreadBase3",
            "gRightBackBigDreadMiddle3",
            "gRightBackBigDreadEnd3",
            "gRightBackBigDreadEnd6",
            "gRightBackBigDreadEnd7" },
        { "gLeftBigBackDreadBase", "gLeftBigBackDreadMiddle", "gLeftBigBackDreadEnd", "gLeftBigBackDreadEnd3" },
        { "gLeftBigBackDreadBase3", "gLeftBigBackDreadMiddle3", "gLeftBigBackDreadEnd5", "gLeftBigBackDreadEnd6" },
        { "gLeftBigBackDreadBase2", "gLeftBigBackDreadMiddle2", "gLeftBigBackDreadEnd2", "gLeftBigBackDreadEnd4", "gLeftBigBackDreadEnd7" },
        {
            "gLeftBigFrontDreadBase2",
            "gLeftBigFrontDreadMiddle2",
            "gLeftBigFrontDreadEnd2",
            "gLeftBigFrontDreadEnd3",
            "gLeftBigFrontDreadEnd4" },
        { "gLeftLowerDreadBase1", "gLeftLowerDreadMiddle1", "gLeftLowerDreadEnd1" },
        { "gLeftLowerDreadBase2", "gLeftLowerDreadMiddle2", "gLeftLowerDreadEnd2" },
        { "gLeftLowerDreadBase3", "gLeftLowerDreadMiddle3", "gLeftLowerDreadEnd3" },
        { "gLeftLowerDreadBase4", "gLeftLowerDreadMiddle4", "gLeftLowerDreadEnd4" },
        { "gLeftLowerDreadBase5", "gLeftLowerDreadMiddle5", "gLeftLowerDreadEnd5" },
        { "gRightLowerDreadBase1", "gRightLowerDreadMiddle1", "gRightLowerDreadEnd1" },
        { "gRightLowerDreadBase2", "gRightLowerDreadMiddle2", "gRightLowerDreadEnd2" },
        { "gRightLowerDreadBase3", "gRightLowerDreadMiddle3", "gRightLowerDreadEnd3" },
        { "gRightLowerDreadBase4", "gRightLowerDreadMiddle4", "gRightLowerDreadEnd4" },
        { "gRightLowerDreadBase5", "gRightLowerDreadMiddle5", "gRightLowerDreadEnd5" },
        { "gLeftLowerBackDreadBase1", "gLeftLowerBackDreadMiddle1", "gLeftLowerBackDreadEnd1" },
        { "gLeftLowerBackDreadBase2", "gLeftLowerBackDreadMiddle2", "gLeftLowerBackDreadEnd2" },
        { "gLeftLowerBackDreadBase3", "gLeftLowerBackDreadMiddle3", "gLeftLowerBackDreadEnd3" },
        { "gLeftLowerBackDreadBase4", "gLeftLowerBackDreadMiddle4", "gLeftLowerBackDreadEnd4" },
        { "gRightLowerBackDreadBase1", "gRightLowerBackDreadMiddle1", "gRightLowerBackDreadEnd1" },
        { "gRightLowerBackDreadBase2", "gRightLowerBackDreadMiddle2", "gRightLowerBackDreadEnd2" },
        { "gRightLowerBackDreadBase3", "gRightLowerBackDreadMiddle3", "gRightLowerBackDreadEnd3" },
        { "gRightLowerBackDreadBase4", "gRightLowerBackDreadMiddle4", "gRightLowerBackDreadEnd4" },
        { "gBackDreadBase1", "gBackDreadMiddle1", "gBackDreadEnd1" },
        { "gBackDreadBase2", "gBackDreadMiddle2", "gBackDreadEnd2" },
        { "gBackDreadBase3", "gBackDreadMiddle3", "gBackDreadEnd3" },
        { "gBackDreadBase4", "gBackDreadMiddle4", "gBackDreadEnd4" },
        { "gBackDreadBase5", "gBackDreadMiddle5", "gBackDreadEnd5" },
        { "gBackDreadBase6", "gBackDreadMiddle6", "gBackDreadEnd6" },
    };

    /** Called from {@link YautjaAnimator#registerPoseModifiers}. */
    public static void register(AzPoseModifierRegistry<Yautja> modifiers) {
        for (int i = 0; i < CHAINS.length; i++) {
            modifiers.add(
                ID_PREFIX + i,
                AzSpringBone.<Yautja>chain(CHAINS[i])
                    .tip(0, TIP_PIXELS, 0)
                    .stiffness(STIFFNESS)
                    .damping(DAMPING)
                    .gravity(GRAVITY)
                    .build()
            );
        }
    }

    private YautjaDreadlockPhysics() {}
}
