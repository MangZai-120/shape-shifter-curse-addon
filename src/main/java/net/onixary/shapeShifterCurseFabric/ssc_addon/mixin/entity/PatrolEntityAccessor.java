package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.mob.PatrolEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 PatrolEntity#setPatrolling(boolean) 给附属包使用。
 * 该方法原本是 protected，无 access widener 时无法调用，
 * 通过 Mixin Accessor 在编译期生成桥接方法 invokeSetPatrolling 即可。
 *
 * 仅供契灵袭击逻辑（MancianimaPassive#spawnRaiders）激活劫掠队的巡逻 AI，
 * 让劫掠者真正朝 setPatrolTarget 设定的村庄中心移动，而不是仅在出生点徘徊。
 */
@Mixin(PatrolEntity.class)
public interface PatrolEntityAccessor {
	@Invoker("setPatrolling")
	void invokeSetPatrolling(boolean patrolling);
}
