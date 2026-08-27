package net.jackcooper.shapeShifterCurseAddon.resource;

import net.minecraft.util.Identifier;

/**
 * 资源条定义（jackcooper）：SSCA 统一资源条框架的声明核心。
 *
 * <p>一条资源（mana/血/灵魂/寒霜/种子…）= 一个 {@link ResourceBarDef} 实例，声明式描述：
 * <ul>
 *   <li>{@link #id}：apoli resource 的注册 id（沿用现有 power，底层存储不变、存档兼容）；</li>
 *   <li>{@link #kind}：语义分类（"mana"/"blood"/"energy"/"seed"），供通用判定/物品按类过滤；</li>
 *   <li>{@link #maxDefault}：默认上限（实际以 resource power JSON 的 max 为准，此处供回退显示）；</li>
 *   <li>{@link #regen}：回复规则列表（可空，见 {@link RegenRule}）；</li>
 *   <li>{@link #triggers}：变更回调列表（可空，见 {@link BarTrigger}）。</li>
 * </ul>
 *
 * <p>规则对象在 {@link BarKeys} 静态初始化时挂载，加减功能 = 增删一条规则对象，
 * 不影响其它条与其它规则（可插拔设计）。
 */
public final class ResourceBarDef {

	/** apoli resource 注册 id（如 my_addon:form_allay_sp_mana_resource）。 */
	public final Identifier id;
	/** 语义分类：mana / blood / energy / seed（供通用物品与判定按类过滤）。 */
	public final String kind;
	/** 默认上限（回退显示用，实际以 power JSON max 为准）。 */
	public final int maxDefault;

	public ResourceBarDef(Identifier id, String kind, int maxDefault) {
		this.id = id;
		this.kind = kind;
		this.maxDefault = maxDefault;
	}

	// ==================== 可插拔规则（运行时增删） ====================

	private final java.util.List<RegenRule> regen = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final java.util.List<BarTrigger> triggers = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final java.util.List<ThresholdEffect> thresholds = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** 挂一条回复规则（可插拔：删规则 = 从列表移除）。 */
	public ResourceBarDef addRegen(RegenRule rule) {
		regen.add(rule);
		return this;
	}

	/** 挂一条变更回调。 */
	public ResourceBarDef addTrigger(BarTrigger trigger) {
		triggers.add(trigger);
		return this;
	}

	/** 挂一条分段效果（如蝙蝠血条 <25% 减伤 / 75-100 增强吸血）。 */
	public ResourceBarDef addThreshold(ThresholdEffect effect) {
		thresholds.add(effect);
		return this;
	}

	/** 只读视图（调度器遍历用）。 */
	public java.util.List<RegenRule> regenRules() {
		return java.util.Collections.unmodifiableList(regen);
	}

	public java.util.List<BarTrigger> triggers() {
		return java.util.Collections.unmodifiableList(triggers);
	}

	public java.util.List<ThresholdEffect> thresholds() {
		return java.util.Collections.unmodifiableList(thresholds);
	}

	@Override
	public String toString() {
		return "ResourceBarDef[" + id + "/" + kind + "]";
	}
}
