package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.RideAnimController;

public class Form_FamiliarFox3 extends AbstractFeralForm {
	public static final AbstractAnimStateController RIDE_CONTROLLER = new RideAnimController(
			new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("familiar_fox_3_riding")),
			new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("form_feral_common_sneak_idle"))
	);

	public Form_FamiliarFox3(Identifier formID) {
		super(formID);
	}

	@Override
	protected AbstractAnimStateController createRideController() {
		return RIDE_CONTROLLER;
	}
}
