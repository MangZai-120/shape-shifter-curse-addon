package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * SSCA 附属状态效果注册（jackcooper 署名）。主类 onInitialize 调用 {@link #init()} 触发注册。
 */
public final class RegAddonEffects {

	private RegAddonEffects() {}

	/** 蛛网缠身：月织蛛减速蛛网踩踏施加的减速 debuff（防牛奶、任何形态不免疫）。 */
	public static final StatusEffect SPIDER_WEB_BOUND = Registry.register(
			Registries.STATUS_EFFECT,
			new Identifier("ssc_addon", "spider_web_bound"),
			new SpiderWebBoundEffect());

	/** 通用能量（瞬时）：喷溅 / 滞留型通用能量药水的原版投掷药水载体效果（饮用型不走效果）。 */
	public static final StatusEffect UNIVERSAL_ENERGY = Registry.register(
			Registries.STATUS_EFFECT,
			new Identifier("ssc_addon", "universal_energy"),
			new UniversalEnergyEffect());

	/** 通用能量 Potion：挂在原版喷溅 / 滞留投掷药水弹射物上的容器配方（仅作 AOE 载体，不可酿造）。 */
	public static final Potion UNIVERSAL_ENERGY_POTION = Registry.register(
			Registries.POTION,
			new Identifier("ssc_addon", "universal_energy"),
			new Potion(new net.minecraft.entity.effect.StatusEffectInstance(UNIVERSAL_ENERGY)));

	public static void init() {
		// 触发静态初始化即完成注册；引用 Potions 防止药水注册顺序问题（与主包同思路）
		Potions.WATER.getClass();
	}
}
