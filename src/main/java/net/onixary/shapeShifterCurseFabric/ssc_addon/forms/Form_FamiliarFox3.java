package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.RideAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Form_FamiliarFox3 extends AbstractFeralForm {
    public Form_FamiliarFox3(Identifier formID) {
        super(formID);
    }

    public static final AbstractAnimStateController RIDE_CONTROLLER = new RideAnimController(
            new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("familiar_fox_3_riding")),
            new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("form_feral_common_sneak_idle"))
    );

    @Override
    protected AbstractAnimStateController getControllerMapping(@NotNull AnimStateEnum animStateEnum) {
        if (animStateEnum == AnimStateEnum.ANIM_STATE_RIDE) {
            return RIDE_CONTROLLER;
        }
        return super.getControllerMapping(animStateEnum);
    }
}
