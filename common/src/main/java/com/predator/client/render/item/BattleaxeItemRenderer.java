package com.predator.client.render.item;

import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;
import com.predator.PredatorResources;
import net.minecraft.resources.ResourceLocation;

/**
 * The battleaxe, drawn from its geo model.
 * <p>
 * ⚠ No animator: the axe has no item clips of its own — its swings are the WIELDER's animations, not the axe's.
 */
public class BattleaxeItemRenderer extends AzItemRenderer {

    public static final String NAME = "battleaxe";

    private static final ResourceLocation MODEL = PredatorResources.itemGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.itemTextureLocation(NAME);

    public BattleaxeItemRenderer() {
        super(AzItemRendererConfig.builder(MODEL, TEXTURE).useNewOffset(true).build());
    }
}
