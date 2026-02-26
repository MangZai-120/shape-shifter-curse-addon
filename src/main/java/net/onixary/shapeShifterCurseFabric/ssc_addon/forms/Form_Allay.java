package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v2.PlayerAnimState;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.WithSneakAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Form_Allay extends PlayerFormBase {
    private static final String ANIM_NS = "my_addon";

    public Form_Allay(Identifier formID) {
        super(formID);
    }

    private static AnimationHolder anim_walk = AnimationHolder.EMPTY;
    private static AnimationHolder anim_run = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sneak_idle = AnimationHolder.EMPTY;
    private static AnimationHolder anim_sneak_walk = AnimationHolder.EMPTY;
    private static AnimationHolder anim_digging = AnimationHolder.EMPTY;
    private static AnimationHolder anim_flying = AnimationHolder.EMPTY;
    private static AnimationHolder anim_idle = AnimationHolder.EMPTY;
    private static AnimationHolder anim_attack = AnimationHolder.EMPTY;

    @Override
    public AnimationHolder Anim_getFormAnimToPlay(PlayerAnimState currentState) {
	    return switch (currentState) {
		    case ANIM_RUN -> anim_run;
		    case ANIM_SNEAK_IDLE -> anim_sneak_idle;
		    case ANIM_SNEAK_WALK -> anim_sneak_walk;
		    case ANIM_IDLE -> anim_idle;
		    case ANIM_FLY, ANIM_JUMP, ANIM_FALL, ANIM_SNEAK_FALL, ANIM_SLOW_FALL, ANIM_CREATIVE_FLY -> anim_flying;
		    case ANIM_TOOL_SWING -> anim_digging;
		    case ANIM_ATTACK_ONCE -> anim_attack;
		    default -> anim_walk;
	    };
    }

    @Override
    public void Anim_registerAnims() {
        anim_walk = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_moving"), true);
        anim_run = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_run"), true);
        anim_sneak_idle = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_sneaking"), true);
        anim_sneak_walk = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_sneaking_walk"), true);
        anim_digging = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_digging"), true);
        anim_flying = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_fly"), true);
        anim_idle = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_idle"), true);
        anim_attack = new AnimationHolder(new Identifier(ANIM_NS, "allay_sp_attack"), true);
    }

    public static final AbstractAnimStateController WALK_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_moving")), new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_sneaking_walk")));
    public static final AbstractAnimStateController SPRINT_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_run")), new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_sneaking_walk")));
    public static final AbstractAnimStateController IDLE_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_idle")), new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_sneaking")));
    public static final AbstractAnimStateController MINING_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_digging")));
    public static final AbstractAnimStateController ATTACK_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_attack")));
    public static final AbstractAnimStateController FLYING_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(new Identifier(ANIM_NS, "allay_sp_fly")));

    @Override
    public @Nullable AbstractAnimStateController getAnimStateController(PlayerEntity player, AnimSystem.AnimSystemData animSystemData, @NotNull Identifier animStateID) {
        @Nullable AnimStateEnum animStateEnum = AnimStateEnum.getStateEnum(animStateID);
        if (animStateEnum != null) {
	        return switch (animStateEnum) {
		        case ANIM_STATE_SPRINT -> SPRINT_CONTROLLER;
		        case ANIM_STATE_IDLE -> IDLE_CONTROLLER;
		        case ANIM_STATE_MINING -> MINING_CONTROLLER;
		        case ANIM_STATE_ATTACK -> ATTACK_CONTROLLER;
		        case ANIM_STATE_JUMP, ANIM_STATE_FALL, ANIM_STATE_FALL_FLYING, ANIM_STATE_FLYING -> FLYING_CONTROLLER;
		        default -> WALK_CONTROLLER;
	        };
        }
        return super.getAnimStateController(player, animSystemData, animStateID);
    }
}
