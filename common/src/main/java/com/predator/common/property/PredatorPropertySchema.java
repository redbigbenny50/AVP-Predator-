package com.predator.common.property;

import com.blib.api.common.property.v1.BLibPropertySchema;

public class PredatorPropertySchema {

    static final BLibPropertySchema SCHEMA = BLibPropertySchema.builder()
        .withPropertyValueAlignment(true)
        .addComment("Block radius that a trip mine looks for a living entity.")
        .addProperty(PredatorProperties.Blocks.TripMine.RANGE, 3)
        .build();

    private PredatorPropertySchema() {
        throw new UnsupportedOperationException();
    }
}
