package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TargetPredicate.class)
public class SscAddonTargetPredicateMixin {

	// 缓存 Identifier，避免 AI 索敌高频路径每次 new Identifier 分配。
	private static final Identifier SSCA_FOX_SP_VISIBILITY = net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_VISIBILITY;

	@ModifyVariable(method = "test", at = @At("STORE"), ordinal = 0)
	private double modifyMaxDistance(double d, @Nullable LivingEntity baseEntity, LivingEntity targetEntity) {
		// 注意：该 power 同时授予使魔 SP 与红堕落使魔两个形态，不能按单一形态粗筛。
		// 反编译 Apoli 2.9.2 证实：PowerTypeRegistry.get 对未注册 id 直接抛 IllegalArgumentException
		// （永不返回 null，旧代码的 != null 判断是无效防御）。在 /reload 清空重填窗口或 power 文件缺失时，
		// 该异常会在 AI 索敌 tick 内每 tick 抛出 → 全部怪物 AI 崩溃循环。必须先 contains 判存在再 get。
		if (targetEntity != null && PowerTypeRegistry.contains(SSCA_FOX_SP_VISIBILITY)) {
			PowerType<?> powerType = PowerTypeRegistry.get(SSCA_FOX_SP_VISIBILITY);
			if (PowerHolderComponent.KEY.get(targetEntity).hasPower(powerType)) {
				return d * 0.67D;
			}
		}
		return d;
	}
}
