package net.jackcooper.shapeShifterCurseAddon.client.model;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class OrcafisModel extends GeoModel<OrcafisEntity> {
    private static final Identifier GEO = new Identifier("my_addon", "geo/orcafis.geo.json");
    private static final Identifier SKIN = new Identifier("my_addon", "textures/entity/orcafis/orcafis_boss.png");
    private static final Identifier LAND_SKIN = new Identifier("my_addon", "textures/entity/orcafis/orcafis_land.png");
    private static final Identifier MOTION = new Identifier("my_addon", "animations/orcafis.animation.json");

    @Override public Identifier getModelResource(OrcafisEntity entity) { return GEO; }
    @Override public Identifier getTextureResource(OrcafisEntity entity) {
        return switch (entity.motion()) {
            case WALK, BURROW_ENTER, BURROW, BREACH, RISE, SWEEP, PLOW, LAND_BITE -> LAND_SKIN;
            case REST -> entity.isTouchingWater() ? SKIN : LAND_SKIN;
            default -> SKIN;
        };
    }
    @Override public Identifier getAnimationResource(OrcafisEntity entity) { return MOTION; }
}
