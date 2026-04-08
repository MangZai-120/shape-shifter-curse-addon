package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 侵蚀烙印标记效果 - 无实际机制效果
 * 仅用于将烙印状态同步到客户端，以驱动 entity_glow 条件检测。
 * 拆分为3个独立效果以支持不同层数显示不同图标：
 * - erosion_brand_marker_1: 1层(黄色)
 * - erosion_brand_marker_2: 2层(橙色)
 * - erosion_brand_marker_3: 3层(红色)
 * 绿色冷却状态不显示图标。
 */
public class ErosionBrandMarkerEffect extends StatusEffect {

	public ErosionBrandMarkerEffect(int color) {
		super(StatusEffectCategory.NEUTRAL, color);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return false; // 无任何tick效果
	}
}
