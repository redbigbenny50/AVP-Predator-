package com.predator.common.registry.init;

import com.blib.api.common.entity.v1.SilencedEntityTypeBuilder;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.blib.api.common.registry.v1.impl.BLibEntityAttributeRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.plasma.PlasmaCloudEntity;
import com.predator.common.gameplay.entity.projectile.CombiStickProjectile;
import com.predator.common.gameplay.entity.projectile.FirePelletProjectile;
import com.predator.common.gameplay.entity.projectile.NetProjectile;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltArrowProjectile;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import com.predator.common.gameplay.entity.projectile.ShurikenProjectile;
import com.predator.common.gameplay.entity.projectile.SmartDiscProjectile;
import com.predator.common.gameplay.entity.projectile.VeritaniumDartProjectile;
import com.predator.common.gameplay.whip.WhipHookEntity;
import com.predator.common.gameplay.whip.WhipLashEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class PredatorEntityTypes {

    public static final BLibEntityAttributeRegistry ATTRIBUTE_REGISTRY = Predator.MOD.registries().createEntityAttributeRegistry();

    public static final BLibRegistry<EntityType<?>> TYPE_REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.ENTITY_TYPE);

    /**
     * The shoulder caster's bolt.
     * <p>
     * Sized to the visible geometry rather than to the model, which is about two blocks long: a hitbox that big would
     * clip terrain it visually passed and hit things the bolt never touched. The bolt is a point of damage travelling
     * fast; the planes are the tell, not the weapon.
     */
    public static final BLibHolder<EntityType<PlasmaBoltProjectile>> PLASMA_BOLT = create(
        "plasma_bolt",
        EntityType.Builder.<PlasmaBoltProjectile>of(PlasmaBoltProjectile::new, MobCategory.MISC)
            .sized(0.4F, 0.4F)
            .clientTrackingRange(6)
            .updateInterval(1)
    );

    /**
     * The wrist-bracer dart.
     * <p>
     * ⚠ {@code updateInterval(1)} because it flies flat and fast; on the default interval a client sees it teleport
     * between positions rather than travel.
     */
    public static final BLibHolder<EntityType<VeritaniumDartProjectile>> VERITANIUM_DART = create(
        "veritanium_dart",
        EntityType.Builder.<VeritaniumDartProjectile>of(VeritaniumDartProjectile::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(1)
    );

    /** The gauntlet's fire pellet. Same tracking as the dart: it is small and fast. */
    /** The five yautja grenades share one entity; the thrown item says which it is. */
    public static final BLibHolder<EntityType<com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile>> YAUTJA_GRENADE =
        create(
            "yautja_grenade",
            EntityType.Builder.<com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile>of(
                com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile::new,
                MobCategory.MISC
            )
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(1)
        );

    public static final BLibHolder<EntityType<FirePelletProjectile>> FIRE_PELLET = create(
        "fire_pellet",
        EntityType.Builder.<FirePelletProjectile>of(FirePelletProjectile::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(4)
            .updateInterval(1)
    );

    /**
     * The self-destruct's cloud. ⚠ clientTrackingRange 32: the default 5 chunks would send the cloud only to players
     * inside the crater. The renderer draws it out to max(1200, radius x 10) blocks.
     */
    public static final BLibHolder<EntityType<PlasmaCloudEntity>> PLASMA_CLOUD = create(
        "plasma_cloud",
        EntityType.Builder.<PlasmaCloudEntity>of(PlasmaCloudEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(32)
            .updateInterval(1)
    );

    /** The plasma bow's bolt. Flat and fast, so it needs the tight update interval the dart uses. */
    public static final BLibHolder<EntityType<PlasmaBoltArrowProjectile>> PLASMA_BOLT_ARROW = create(
        "plasma_bolt_arrow",
        EntityType.Builder.<PlasmaBoltArrowProjectile>of(PlasmaBoltArrowProjectile::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(1)
    );

    /** The whip's lash — the cord in flight during a swing. No collision; it exists so the cord is drawn and hit. */
    public static final BLibHolder<EntityType<WhipLashEntity>> WHIP_LASH = create(
        "whip_lash",
        EntityType.Builder.<WhipLashEntity>of(WhipLashEntity::new, MobCategory.MISC)
            .sized(0.1F, 0.1F)
            .clientTrackingRange(8)
            .updateInterval(1)
    );

    /** The grapple hook. Tracking range covers the full 32-block line. */
    public static final BLibHolder<EntityType<WhipHookEntity>> WHIP_HOOK = create(
        "whip_hook",
        EntityType.Builder.<WhipHookEntity>of(WhipHookEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(8)
            .updateInterval(1)
    );

    public static final BLibHolder<EntityType<ShurikenProjectile>> SHURIKEN = create(
        "shuriken",
        EntityType.Builder.<ShurikenProjectile>of(ShurikenProjectile::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
    );

    public static final BLibHolder<EntityType<SmartDiscProjectile>> SMART_DISC = create(
        "smart_disc",
        EntityType.Builder.<SmartDiscProjectile>of(SmartDiscProjectile::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
    );

    public static final BLibHolder<EntityType<com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile>> PLASMA_SHURIKEN =
        create(
            "plasma_shuriken",
            EntityType.Builder.<com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile>of(
                com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile::new,
                MobCategory.MISC
            )
                .sized(0.25F, 0.25F)
        );

    public static final BLibHolder<EntityType<com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity>> VERITANIUM_ARROW =
        create(
            "veritanium_arrow",
            EntityType.Builder.<com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity>of(
                com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity::new,
                MobCategory.MISC
            )
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(20)
        );

    public static final BLibHolder<EntityType<NetProjectile>> NET = create(
        "net",
        EntityType.Builder.<NetProjectile>of(NetProjectile::new, MobCategory.MISC)
            .sized(0.35F, 0.35F)
    );

    public static final BLibHolder<EntityType<CombiStickProjectile>> COMBI_STICK = create(
        "combi_stick",
        EntityType.Builder.<CombiStickProjectile>of(CombiStickProjectile::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
    );

    public static final BLibHolder<EntityType<Yautja>> YAUTJA = create(
        "yautja_jungle",
        EntityType.Builder.of(Yautja::new, MobCategory.MONSTER)
            .sized(0.7f, 2.48f)
    );

    public static <T extends Entity> BLibHolder<EntityType<T>> create(String id, EntityType.Builder<T> builder) {
        return TYPE_REGISTRY.createHolder(id, () -> ((SilencedEntityTypeBuilder) builder).blib$buildWithoutDataFixerCheck());
    }

    public static void initialize() {
        TYPE_REGISTRY.registerAll();
        ATTRIBUTE_REGISTRY.register(YAUTJA, Yautja::createYautjaAttributes);
    }
}
