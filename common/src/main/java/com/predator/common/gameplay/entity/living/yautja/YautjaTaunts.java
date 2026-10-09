package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The yautja's taunting laugh. [stated] It plays "when they use the smoke bomb and also when they kill a hard enemy
 * something with alot of health and defense ... praetorians predaliens crushers queens empress harbinger. anyone using
 * a high dps weapon sniper gatling gun smart gun high hitting pred weapons or if anyone dies to a trip mine."
 * <h2>What counts</h2>
 * <ul>
 * <li>A kill by a yautja (directly or by its projectile) of anything in {@code #avp_predator:taunt_worthy_kills} —
 * avp_alien's praetorian, predalien, crusher, queen, empress and harbinger tags, plus the vanilla bosses.</li>
 * <li>…or of anything big and armoured that the tag does not know about: {@link #HARD_HEALTH} max health AND
 * {@link #HARD_ARMOR} armour, so a modded boss counts without being listed.</li>
 * <li>…or of anyone holding a weapon in {@code #avp_predator:taunt_worthy_weapons} — the sniper, Old Painless, the
 * smartgun, and the heavy predator weapons.</li>
 * <li>Any death to a trip mine a YAUTJA laid. The owning yautja laughs if it is still nearby; if it is gone, the laugh
 * comes from where the mine was. A player's own mine does not set it off — there is no predator to laugh.</li>
 * <li>The smoke bomb, when the Hunter escapes (pass 3 calls {@link #taunt}).</li>
 * </ul>
 * One laugh per yautja per {@link #COOLDOWN_TICKS}, so a fight with several kills is not a laugh track.
 */
public final class YautjaTaunts {

    public static final double HARD_HEALTH = 100.0;

    public static final double HARD_ARMOR = 10.0;

    public static final int COOLDOWN_TICKS = 200;

    /** How close a death must be to a yautja mine's blast, and how soon after it, to be blamed on the mine. */
    private static final double MINE_BLAST_RADIUS = 10.0;

    private static final long MINE_BLAST_WINDOW = 3;

    /** Carries the laugh about 32 blocks. */
    private static final float VOLUME = 2.0F;

    private static final Map<Yautja, Long> LAST_TAUNT = new WeakHashMap<>();

    private static final List<MineBlast> RECENT_MINE_BLASTS = new ArrayList<>();

    private record MineBlast(
        ServerLevel level,
        Vec3 centre,
        @Nullable UUID owner,
        long gameTime
    ) {}

    private YautjaTaunts() {}

    /** Laughs, unless this yautja laughed recently. */
    public static void taunt(Yautja yautja) {
        if (yautja.level().isClientSide || yautja.isSilent()) {
            return;
        }

        var now = yautja.level().getGameTime();
        var last = LAST_TAUNT.get(yautja);

        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }

        LAST_TAUNT.put(yautja, now);
        playAt(yautja.level(), yautja.position());
    }

    private static void playAt(net.minecraft.world.level.Level level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, PredatorSoundEvents.YAUTJA_TAUNT.get(), SoundSource.HOSTILE, VOLUME, 1.0F);
    }

    /** A yautja-laid trip mine just went off. Called by the mine, before its explosion. */
    public static void recordMineBlast(ServerLevel level, Vec3 centre, @Nullable UUID owner, boolean ownerIsMob) {
        if (!ownerIsMob) {
            return;
        }

        RECENT_MINE_BLASTS.removeIf(blast -> level.getGameTime() - blast.gameTime() > MINE_BLAST_WINDOW);
        RECENT_MINE_BLASTS.add(new MineBlast(level, centre, owner, level.getGameTime()));
    }

    /** Something died. Called at the start of its death drops. */
    public static void onDeath(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (source.getEntity() instanceof Yautja yautja) {
            if (yautja != victim && isWorthyKill(victim)) {
                taunt(yautja);
            }

            return;
        }

        if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == null) {
            var blast = mineBlastNear(level, victim.position());

            if (blast != null) {
                var owner = blast.owner() == null ? null : level.getEntity(blast.owner());

                if (owner instanceof Yautja yautja && yautja.isAlive() && yautja.distanceToSqr(blast.centre()) < 64 * 64) {
                    taunt(yautja);
                } else {
                    playAt(level, blast.centre());
                }
            }
        }
    }

    private static @Nullable MineBlast mineBlastNear(ServerLevel level, Vec3 pos) {
        var now = level.getGameTime();

        for (var blast : RECENT_MINE_BLASTS) {
            if (
                blast.level() == level && now - blast.gameTime() <= MINE_BLAST_WINDOW && blast.centre().closerThan(pos, MINE_BLAST_RADIUS)
            ) {
                return blast;
            }
        }

        return null;
    }

    private static boolean isWorthyKill(LivingEntity victim) {
        if (victim.getType().is(PredatorEntityTypeTags.TAUNT_WORTHY_KILLS)) {
            return true;
        }

        if (
            victim.getMainHandItem().is(PredatorItemTags.TAUNT_WORTHY_WEAPONS) || victim.getOffhandItem()
                .is(PredatorItemTags.TAUNT_WORTHY_WEAPONS)
        ) {
            return true;
        }

        return victim.getMaxHealth() >= HARD_HEALTH && victim.getAttributeValue(Attributes.ARMOR) >= HARD_ARMOR;
    }
}
