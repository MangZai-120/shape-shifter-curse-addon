package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * PrioritizedGoal.goal / priority 访问器（羊了个羊 AI 备份/回放用）：
 * 备份时从包装器里拆出裸 Goal 与优先级，恢复时按原优先级回放。
 */
@Mixin(PrioritizedGoal.class)
public interface PrioritizedGoalAccessor {

	@Accessor("goal")
	Goal ssca$getGoal();

	@Accessor("priority")
	int ssca$getPriority();
}
