package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.RideAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import org.jetbrains.annotations.NotNull;

public class Form_SnowFoxSP extends AbstractFeralForm {
    public Form_SnowFoxSP(Identifier formID) {
        super(formID);
    }

    public static final AbstractAnimStateController RIDE_CONTROLLER = new RideAnimController(
            new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("familiar_fox_3_riding")),
            new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("form_feral_common_sneak_idle"))
    );

    public static final AbstractAnimStateController FALL_CONTROLLER_SP = new OneAnimController(
            new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(new Identifier("my_addon", "form_snow_fox_sp_fall"))
    );

    @Override
    public void Anim_registerAnims() {
        super.Anim_registerAnims();
        anim_fall = new AnimationHolder(new Identifier("my_addon", "form_snow_fox_sp_fall"), true);
    }

    @Override
    protected AbstractAnimStateController getControllerMapping(@NotNull AnimStateEnum animStateEnum) {
        return switch (animStateEnum) {
            case ANIM_STATE_SLEEP -> Form_FeralBase.SLEEP_CONTROLLER;
            case ANIM_STATE_CLIMB -> Form_FeralBase.CLIMB_CONTROLLER;
            case ANIM_STATE_FALL -> FALL_CONTROLLER_SP;
            case ANIM_STATE_JUMP -> Form_FeralBase.JUMP_CONTROLLER;
            case ANIM_STATE_RIDE -> RIDE_CONTROLLER;
            case ANIM_STATE_SWIM -> Form_FeralBase.SWIM_CONTROLLER;
            case ANIM_STATE_USE_ITEM -> Form_FeralBase.USE_ITEM_CONTROLLER;
            case ANIM_STATE_WALK -> Form_FeralBase.WALK_CONTROLLER;
            case ANIM_STATE_SPRINT -> Form_FeralBase.SPRINT_CONTROLLER;
            case ANIM_STATE_IDLE -> Form_FeralBase.IDLE_CONTROLLER;
            case ANIM_STATE_MINING -> Form_FeralBase.MINING_CONTROLLER;
            case ANIM_STATE_ATTACK -> Form_FeralBase.ATTACK_CONTROLLER;
            case ANIM_STATE_FLYING, ANIM_STATE_FALL_FLYING -> Form_FeralBase.FALL_FLYING_CONTROLLER;
            default -> Form_FeralBase.IDLE_CONTROLLER;
        };
    }
}
