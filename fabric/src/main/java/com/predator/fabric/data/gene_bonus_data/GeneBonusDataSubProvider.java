package com.predator.fabric.data.gene_bonus_data;

import com.blib.api.common.entity.v1.EntityTypePredicate;
import com.human.common.gameplay.gene.Gene;
import com.human.common.gameplay.gene.GeneBonusData;
import com.human.common.gameplay.gene.GeneBonusDataEntry;
import com.human.common.gameplay.gene.GeneTier;
import com.human.common.gameplay.gene.Genes;
import com.just.core.functional.tuple.Tuple2;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Yautja species gene data (team-reviewed donor table, Oct 25/26), on the predators tag so every yautja variant shares
 * it. Values are derived from avp_human's gene caps via {@link GeneTier}.
 */
public class GeneBonusDataSubProvider extends GeneBonusDataProvider {

    public GeneBonusDataSubProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    protected void generate() {
        add(
            PredatorEntityTypeTags.PREDATORS,
            List.of(
                gene(Genes.MAX_HEALTH, GeneTier.PRIME),
                gene(Genes.ATTACK_DAMAGE, GeneTier.PRIME),
                gene(Genes.STEALTH, GeneTier.STRONG),
                gene(Genes.ACID_RESISTANCE, GeneTier.STRONG),
                gene(Genes.JUMP_HEIGHT, GeneTier.AVERAGE),
                gene(Genes.KEEN_SENSES, GeneTier.AVERAGE),
                gene(Genes.KNOCKBACK_RESISTANCE, GeneTier.AVERAGE),
                gene(Genes.ARMOR_PENETRATION, GeneTier.AVERAGE)
            )
        );
    }

    private static Tuple2<Gene, Double> gene(Gene gene, GeneTier tier) {
        return new Tuple2<>(gene, tier.speciesValue(gene));
    }

    /**
     * Raises a gene a broader file already gives this mob up to {@code target}: files that match the same mob are
     * SUMMED, so the narrower file only carries the difference.
     */
    private static Tuple2<Gene, Double> topUp(Gene gene, GeneTier target, GeneTier alreadyGiven) {
        return new Tuple2<>(gene, target.speciesValue(gene) - alreadyGiven.speciesValue(gene));
    }

    private void add(TagKey<EntityType<?>> entityTypeTagKey, List<Tuple2<Gene, Double>> genes) {
        add(entityTypeTagKey.location().getPath() + "_gene_bonuses", new EntityTypePredicate.Tag(entityTypeTagKey), genes);
    }

    private void add(EntityType<?> entityType, List<Tuple2<Gene, Double>> genes) {
        add(
            BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getPath() + "_gene_bonuses",
            new EntityTypePredicate.Single(entityType),
            genes
        );
    }

    private void add(String name, EntityTypePredicate predicate, List<Tuple2<Gene, Double>> genes) {
        add(
            name,
            new GeneBonusData(
                predicate,
                genes.stream()
                    .map(tuple -> new GeneBonusDataEntry(tuple.v1().id(), Genes.operation(tuple.v1()), tuple.v2()))
                    .toList()
            )
        );
    }
}
