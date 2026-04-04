package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.RideAnimController;

public class Form_SnowFoxSP extends AbstractFeralForm {
	public static final AbstractAnimStateController RIDE_CONTROLLER = new RideAnimController(
			new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("familiar_fox_3_riding")),
			new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("form_feral_common_sneak_idle"))
	);
	public static final AbstractAnimStateController FALL_CONTROLLER_SP = new OneAnimController(
			new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils.AnimationHolderData(new Identifier("my_addon", "form_snow_fox_sp_fall"))
	);

	public Form_SnowFoxSP(Identifier formID) {
		super(formID);
	}

	@Override
	public void Anim_registerAnims() {
		super.Anim_registerAnims();
		anim_fall = new AnimationHolder(new Identifier("my_addon", "form_snow_fox_sp_fall"), true);
	}

	@Override
	protected AbstractAnimStateController createRideController() {
		return RIDE_CONTROLLER;
	}

	@Override
	protected AbstractAnimStateController createFallController() {
		return FALL_CONTROLLER_SP;
	}
}
