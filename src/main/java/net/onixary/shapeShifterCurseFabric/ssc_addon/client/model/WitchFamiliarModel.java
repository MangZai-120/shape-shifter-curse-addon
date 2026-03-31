package net.onixary.shapeShifterCurseFabric.ssc_addon.client.model;

import mod.azure.azurelib.model.GeoModel;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

/**
 * 女巫使魔模型 - 复用原版使魔的3D模型，使用专属纹理
 */
public class WitchFamiliarModel extends GeoModel<WitchFamiliarEntity> {

    @Override
    public Identifier getModelResource(WitchFamiliarEntity entity) {
        // 使用专属Geo模型（带正确骨骼层级的使魔模型）
        return new Identifier("ssc_addon", "geo/witch_familiar.geo.json");
    }

    @Override
    public Identifier getTextureResource(WitchFamiliarEntity entity) {
        return new Identifier("ssc_addon", "textures/entity/witch_familiar.png");
    }

    @Override
    public Identifier getAnimationResource(WitchFamiliarEntity entity) {
        // 使用附属包女巫使魔专属动画（基于原版使魔四足动作）
        return new Identifier("ssc_addon", "animations/witch_familiar.animation.json");
    }
}
