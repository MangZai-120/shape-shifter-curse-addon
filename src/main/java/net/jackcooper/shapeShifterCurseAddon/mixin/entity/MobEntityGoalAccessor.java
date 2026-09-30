package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * MobEntity 的 goalSelector / targetSelector / initGoals 访问器（羊了个羊 AI 替换用）。
 * 两选择器与 initGoals 均为 protected，附属普通类无法直接触达——顶层 accessor mixin 暴露。
 */
@Mixin(MobEntity.class)
public interface MobEntityGoalAccessor {

	@Accessor("goalSelector")
	GoalSelector ssca$getGoalSelector();

	@Accessor("targetSelector")
	GoalSelector ssca$getTargetSelector();

	@Invoker("initGoals")
	void ssca$invokeInitGoals();
}