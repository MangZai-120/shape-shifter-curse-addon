package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v2.PlayerAnimState;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.RideAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.MOD_ID;

// SP雪狐形态动画控制器
public class Form_SnowFoxSP extends PlayerFormBase {
    public Form_SnowFoxSP(Identifier formID) {
        super(formID);
        this.setBodyType(PlayerFormBodyType.FERAL);
    }

    private static AnimationHolder anim_idle = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sneak_idle = AnimationHolder.EMPTY;
    private static AnimationHolder anim_ride = AnimationHolder.EMPTY;
    private static AnimationHolder anim_walk = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sneak_walk = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sneak_rush = AnimationHolder.EMPTY;
    private static AnimationHolder anim_run = AnimationHolder.EMPTY;
    private static AnimationHolder anim_float = AnimationHolder.EMPTY;
    private static AnimationHolder anim_swim = AnimationHolder.EMPTY;
    private static AnimationHolder anim_dig = AnimationHolder.EMPTY;
    private static AnimationHolder anim_jump = AnimationHolder.EMPTY;
    private static AnimationHolder anim_climb = AnimationHolder.EMPTY;
    private static AnimationHolder anim_fall = AnimationHolder.EMPTY;
    private static AnimationHolder anim_attack = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sleep = AnimationHolder.EMPTY;
    private static AnimationHolder anim_elytra_fly = AnimationHolder.EMPTY;


	@Override
    public AnimationHolder Anim_getFormAnimToPlay(PlayerAnimState currentState) {
	    return switch (currentState) {
		    case ANIM_IDLE -> anim_idle;
		    case ANIM_SNEAK_IDLE, ANIM_RIDE_VEHICLE_IDLE -> anim_sneak_idle;
		    case ANIM_RIDE_IDLE -> anim_ride;
		    case ANIM_WALK -> anim_walk;
		    case ANIM_SNEAK_WALK -> anim_sneak_walk;
		    case ANIM_SNEAK_RUSH -> anim_sneak_rush;
		    case ANIM_RUN -> anim_run;
		    case ANIM_SWIM_IDLE -> anim_float;
		    case ANIM_SWIM -> anim_swim;
		    case ANIM_TOOL_SWING, ANIM_SNEAK_TOOL_SWING -> anim_dig;
		    case ANIM_JUMP, ANIM_SNEAK_JUMP -> anim_jump;
		    case ANIM_CLIMB_IDLE, ANIM_CLIMB -> anim_climb;
		    case ANIM_FALL, ANIM_SNEAK_FALL -> anim_fall;
		    case ANIM_SLEEP -> anim_sleep;
		    case ANIM_ATTACK_ONCE, ANIM_SNEAK_ATTACK_ONCE -> anim_attack;
		    case ANIM_ELYTRA_FLY, ANIM_CREATIVE_FLY -> anim_elytra_fly;
		    default -> anim_idle;
	    };
    }

	@Override
    public void Anim_registerAnims() {
        anim_idle = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_idle"), true);
        anim_sneak_idle = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_sneak_idle"), true);
        anim_ride = new AnimationHolder(new Identifier(MOD_ID, "familiar_fox_3_riding"), true);
        anim_walk = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_walk"), true, 1.2f, 2);
        anim_sneak_walk = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_sneak_walk"), true);
        anim_run = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_run"), true, 2.3f);
        anim_float = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_float"), true);
        anim_swim = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_swim"), true);
        anim_dig = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_dig"), true);
        anim_jump = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_jump"), true);
        anim_climb = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_climb"), true);
        anim_fall = new AnimationHolder(new Identifier("my_addon", "form_snow_fox_sp_fall"), true);
        anim_attack = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_attack"), true);
        anim_sleep = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_sleep"), true);
        anim_elytra_fly = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_elytra_fly"), true);
        anim_sneak_rush = new AnimationHolder(new Identifier(MOD_ID, "form_feral_common_run"), true, 2.3f);
    }

    public static final AbstractAnimStateController RIDE_CONTROLLER = new RideAnimController(new AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("familiar_fox_3_riding")), new AnimUtils.AnimationHolderData(ShapeShifterCurseFabric.identifier("form_feral_common_sneak_idle")));
    
    // Custom fall controller for Snow Fox SP
    public static final AbstractAnimStateController FALL_CONTROLLER_SP = new OneAnimController(new AnimUtils.AnimationHolderData(new Identifier("my_addon", "form_snow_fox_sp_fall")));

	@Override
    public @Nullable AbstractAnimStateController getAnimStateController(PlayerEntity player, AnimSystem.AnimSystemData animSystemData, @NotNull Identifier animStateID) {
        @Nullable AnimStateEnum animStateEnum = AnimStateEnum.getStateEnum(animStateID);
        if (animStateEnum != null) {
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
        return super.getAnimStateController(player, animSystemData, animStateID);
    }
}