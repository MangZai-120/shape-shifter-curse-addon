package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * SSCA 附属状态效果注册（jackcooper 署名）。主类 onInitialize 调用 {@link #init()} 触发注册。
 */
public final class RegAddonEffects {

	private RegAddonEffects() {}

	/** 蛛网缠身：魔法蜘蛛减速蛛网踩踏施加的减速 debuff（防牛奶、任何形态不免疫）。 */
	public static final StatusEffect SPIDER_WEB_BOUND = Registry.register(
			Registries.STATUS_EFFECT,
			new Identifier("ssc_addon", "spider_web_bound"),
			new SpiderWebBoundEffect());

	public static void init() {
		// 触发静态初始化即完成注册
	}
}
