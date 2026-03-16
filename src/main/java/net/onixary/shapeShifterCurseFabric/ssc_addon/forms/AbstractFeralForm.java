package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v2.PlayerAnimState;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.MOD_ID;

/**
 * 四足野性形态的抽象基类
 * 仅限雪狐等共用四足形态动画的形态使用
 */
public abstract class AbstractFeralForm extends PlayerFormBase {
    protected AnimationHolder anim_idle = AnimationHolder.EMPTY;
    protected AnimationHolder anim_sneak_idle = AnimationHolder.EMPTY;
    protected AnimationHolder anim_ride = AnimationHolder.EMPTY;
    protected AnimationHolder anim_walk = AnimationHolder.EMPTY;
    protected AnimationHolder anim_sneak_walk = AnimationHolder.EMPTY;
    protected AnimationHolder anim_sneak_rush = AnimationHolder.EMPTY;
    protected AnimationHolder anim_run = AnimationHolder.EMPTY;
    protected AnimationHolder anim_float = AnimationHolder.EMPTY;
    protected AnimationHolder anim_swim = AnimationHolder.EMPTY;
    protected AnimationHolder anim_dig = AnimationHolder.EMPTY;
    protected AnimationHolder anim_jump = AnimationHolder.EMPTY;
    protected AnimationHolder anim_climb = AnimationHolder.EMPTY;
    protected AnimationHolder anim_fall = AnimationHolder.EMPTY;
    protected AnimationHolder anim_attack = AnimationHolder.EMPTY;
    protected AnimationHolder anim_sleep = AnimationHolder.EMPTY;
    protected AnimationHolder anim_elytra_fly = AnimationHolder.EMPTY;

    protected AbstractFeralForm(Identifier formID) {
        super(formID);
        this.setBodyType(PlayerFormBodyType.FERAL);
    }

    @Override
    public AnimationHolder Anim_getFormAnimToPlay(PlayerAnimState currentState) {
        return getAnimStateMapping(currentState);
    }

    protected AnimationHolder getAnimStateMapping(PlayerAnimState currentState) {
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
        anim_idle = new AnimationHolder(new Identifier(MOD_ID, getAnimId("idle")), true);
        anim_sneak_idle = new AnimationHolder(new Identifier(MOD_ID, getAnimId("sneak_idle")), true);
        anim_ride = new AnimationHolder(new Identifier(MOD_ID, getAnimId("ride")), true);
        anim_walk = new AnimationHolder(new Identifier(MOD_ID, getAnimId("walk")), true, 1.2f, 2);
        anim_sneak_walk = new AnimationHolder(new Identifier(MOD_ID, getAnimId("sneak_walk")), true);
        anim_run = new AnimationHolder(new Identifier(MOD_ID, getAnimId("run")), true, 2.3f);
        anim_float = new AnimationHolder(new Identifier(MOD_ID, getAnimId("float")), true);
        anim_swim = new AnimationHolder(new Identifier(MOD_ID, getAnimId("swim")), true);
        anim_dig = new AnimationHolder(new Identifier(MOD_ID, getAnimId("dig")), true);
        anim_jump = new AnimationHolder(new Identifier(MOD_ID, getAnimId("jump")), true);
        anim_climb = new AnimationHolder(new Identifier(MOD_ID, getAnimId("climb")), true);
        anim_fall = new AnimationHolder(new Identifier(MOD_ID, getAnimId("fall")), true);
        anim_attack = new AnimationHolder(new Identifier(MOD_ID, getAnimId("attack")), true);
        anim_sleep = new AnimationHolder(new Identifier(MOD_ID, getAnimId("sleep")), true);
        anim_elytra_fly = new AnimationHolder(new Identifier(MOD_ID, getAnimId("elytra_fly")), true);
        anim_sneak_rush = new AnimationHolder(new Identifier(MOD_ID, getAnimId("run")), true, 2.3f);
    }

    protected String getAnimId(String animName) {
        return "form_feral_common_" + animName;
    }

    protected Identifier getAnimIdentifier(String animName) {
        return new Identifier(MOD_ID, getAnimId(animName));
    }

    @Override
    public @Nullable AbstractAnimStateController getAnimStateController(PlayerEntity player, AnimSystem.AnimSystemData animSystemData, @NotNull Identifier animStateID) {
        AnimStateEnum animStateEnum = AnimStateEnum.getStateEnum(animStateID);
        if (animStateEnum != null) {
            return getControllerMapping(animStateEnum);
        }
        return super.getAnimStateController(player, animSystemData, animStateID);
    }

    protected AbstractAnimStateController getControllerMapping(AnimStateEnum animStateEnum) {
        return switch (animStateEnum) {
            case ANIM_STATE_SLEEP -> createSleepController();
            case ANIM_STATE_CLIMB -> createClimbController();
            case ANIM_STATE_FALL -> createFallController();
            case ANIM_STATE_JUMP -> createJumpController();
            case ANIM_STATE_RIDE -> createRideController();
            case ANIM_STATE_SWIM -> createSwimController();
            case ANIM_STATE_USE_ITEM -> createUseItemController();
            case ANIM_STATE_WALK -> createWalkController();
            case ANIM_STATE_SPRINT -> createSprintController();
            case ANIM_STATE_IDLE -> createIdleController();
            case ANIM_STATE_MINING -> createMiningController();
            case ANIM_STATE_ATTACK -> createAttackController();
            case ANIM_STATE_FLYING, ANIM_STATE_FALL_FLYING -> createFallFlyingController();
            default -> createIdleController();
        };
    }

    protected AbstractAnimStateController createSleepController() {
        return Form_FeralBase.SLEEP_CONTROLLER;
    }

    protected AbstractAnimStateController createClimbController() {
        return Form_FeralBase.CLIMB_CONTROLLER;
    }

    protected AbstractAnimStateController createFallController() {
        return Form_FeralBase.FALL_CONTROLLER;
    }

    protected AbstractAnimStateController createJumpController() {
        return Form_FeralBase.JUMP_CONTROLLER;
    }

    protected AbstractAnimStateController createRideController() {
        return Form_FeralBase.RIDE_CONTROLLER;
    }

    protected AbstractAnimStateController createSwimController() {
        return Form_FeralBase.SWIM_CONTROLLER;
    }

    protected AbstractAnimStateController createUseItemController() {
        return Form_FeralBase.USE_ITEM_CONTROLLER;
    }

    protected AbstractAnimStateController createWalkController() {
        return Form_FeralBase.WALK_CONTROLLER;
    }

    protected AbstractAnimStateController createSprintController() {
        return Form_FeralBase.SPRINT_CONTROLLER;
    }

    protected AbstractAnimStateController createIdleController() {
        return Form_FeralBase.IDLE_CONTROLLER;
    }

    protected AbstractAnimStateController createMiningController() {
        return Form_FeralBase.MINING_CONTROLLER;
    }

    protected AbstractAnimStateController createAttackController() {
        return Form_FeralBase.ATTACK_CONTROLLER;
    }

    protected AbstractAnimStateController createFallFlyingController() {
        return Form_FeralBase.FALL_FLYING_CONTROLLER;
    }
}
