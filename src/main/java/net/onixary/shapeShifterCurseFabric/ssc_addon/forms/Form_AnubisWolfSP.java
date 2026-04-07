package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v2.PlayerAnimState;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_SnowFox3;

public class Form_AnubisWolfSP extends AbstractFeralForm {
	public Form_AnubisWolfSP(Identifier formID) {
		super(formID);
	}

	@Override
	protected AnimationHolder getAnimStateMapping(PlayerAnimState currentState) {
		return switch (currentState) {
			case ANIM_SNEAK_IDLE, ANIM_RIDE_VEHICLE_IDLE -> anim_sneak_idle;
			case ANIM_RIDE_IDLE -> anim_ride;
			case ANIM_WALK -> anim_walk;
			case ANIM_SNEAK_WALK -> anim_sneak_walk;
			case ANIM_RUN, ANIM_SNEAK_RUSH -> anim_run;
			case ANIM_SWIM_IDLE -> anim_float;
			case ANIM_SWIM -> anim_swim;
			case ANIM_TOOL_SWING, ANIM_SNEAK_TOOL_SWING -> anim_dig;
			case ANIM_JUMP, ANIM_SNEAK_JUMP, ANIM_FALL, ANIM_SNEAK_FALL -> anim_jump;
			case ANIM_CLIMB_IDLE, ANIM_CLIMB -> anim_climb;
			case ANIM_SLEEP -> anim_sleep;
			case ANIM_ATTACK_ONCE, ANIM_SNEAK_ATTACK_ONCE -> anim_attack;
			case ANIM_ELYTRA_FLY, ANIM_CREATIVE_FLY -> anim_elytra_fly;
			default -> anim_idle;
		};
	}

	@Override
	protected AbstractAnimStateController createRideController() {
		return Form_SnowFox3.RIDE_CONTROLLER;
	}
}
