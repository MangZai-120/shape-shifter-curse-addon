package net.jackcooper.shapeShifterCurseAddon.client.model;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class OrcafisModel extends GeoModel<OrcafisEntity> {
    private static final Identifier GEO = new Identifier("ssc_addon", "geo/orcafis.geo.json");
    private static final Identifier SKIN = new Identifier("ssc_addon", "textures/entity/orcafis/orcafis_boss.png");
    private static final Identifier LAND_SKIN = new Identifier("ssc_addon", "textures/entity/orcafis/orcafis_land.png");
    private static final Identifier MOTION = new Identifier("ssc_addon", "animations/orcafis.animation.json");

    @Override public Identifier getModelResource(OrcafisEntity entity) { return GEO; }
    @Override public Identifier getTextureResource(OrcafisEntity entity) {
        return switch (entity.motion()) {
            case WALK, BURROW_ENTER, BURROW, BREACH, RISE, SWEEP, PLOW, LAND_BITE, ROCK_TOSS, ROLL_LEFT, ROLL_RIGHT -> LAND_SKIN;
            case REST -> entity.isTouchingWater() ? SKIN : LAND_SKIN;
            default -> SKIN;
        };
    }
    @Override public Identifier getAnimationResource(OrcafisEntity entity) { return MOTION; }

    @Override public void setCustomAnimations(OrcafisEntity entity, long id,
            software.bernie.geckolib.core.animation.AnimationState<OrcafisEntity> state) {
        super.setCustomAnimations(entity,id,state);
        // These two unanimated helper bones are common to the render and collision rigs.
        var neck = getAnimationProcessor().getBone("shore_neck_aim");
        var height = getAnimationProcessor().getBone("shore_water_height");
        if (neck != null) neck.setRotX(entity.jetElevation());
        if (height != null) height.setPosY(16 * entity.undertowOffset());
    }
}
