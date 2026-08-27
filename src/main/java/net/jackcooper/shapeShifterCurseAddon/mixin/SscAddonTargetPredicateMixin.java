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

	// 缓存 Identifier，避免 AI 索敌高频路径每次 new Identifier 分配；
	// PowerType 不缓存——/reload 会重建 PowerTypeRegistry，缓存引用会失效，故每次查表（HashMap，开销极小）。
	private static final Identifier SSCA_FOX_SP_VISIBILITY = new Identifier("my_addon", "form_familiar_fox_sp_visibility");

	@ModifyVariable(method = "test", at = @At("STORE"), ordinal = 0)
	private double modifyMaxDistance(double d, @Nullable LivingEntity baseEntity, LivingEntity targetEntity) {
		if (targetEntity != null) {
			PowerType<?> powerType = PowerTypeRegistry.get(SSCA_FOX_SP_VISIBILITY);
			if (powerType != null && PowerHolderComponent.KEY.get(targetEntity).hasPower(powerType)) {
				return d * 0.67D;
			}
		}
		return d;
	}
}
