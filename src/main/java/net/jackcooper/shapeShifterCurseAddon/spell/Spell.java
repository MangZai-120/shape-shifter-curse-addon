package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 魔法（法术）抽象基类（jackcooper）。仿 Iron's Spellbooks 的「行为类 + 外置数值」分离：
 * 本类只负责行为与身份，<b>数值全部来自 {@link SpellConfig}</b>（{@code data/ssc_addon/spells/<id>.json}，
 * 数据包可覆盖；缺文件/缺字段回退 {@link SpellConfig#fallback()}）。
 *
 * <p>数值语义（均为「装入魔法书且卷轴满次数」时的基准值）：</p>
 * <ul>
 *   <li>基础伤害 / 冷却 / 施法时间 / 法力消耗；</li>
 *   <li><b>装书内</b>：伤害 ×耐久比、冷却与施法时间 ×(2−耐久比)（耐久比 = 卷轴剩余次数/上限）；</li>
 *   <li><b>单独使用</b>：固定惩罚（伤害 ×solo 倍率、冷却/施法时间 ×对应倍率），每次消耗 1 次数。</li>
 * </ul>
 * <p>缩放由调用方（服务端施法逻辑）统一计算后把最终威力传入 {@link #cast}，本类只负责发出效果。</p>
 */
public abstract class Spell implements SpellRegistry.SpellConfigInjector {
	private final Identifier id;
	private final SpellRarity rarity;
	/** 数值配置（由 SpellRegistry reload 时注入；恒非 null——注入前为 fallback）。 */
	private volatile SpellConfig config = SpellConfig.fallback();

	protected Spell(Identifier id, SpellRarity rarity) {
		this.id = id;
		this.rarity = rarity;
	}

	public Identifier getId() {
		return id;
	}

	public SpellRarity getRarity() {
		return rarity;
	}

	/** 数值配置（JSON 加载 / reload 后由注册表写入）。 */
	public SpellConfig getConfig() {
		return config;
	}

	/** 注册表专用注入桥（服务端 reload / 客户端 S2C 同步都会走这里）。 */
	@Override
	public void ssc_addon$applyConfig(SpellConfig config) {
		this.config = config == null ? SpellConfig.fallback() : config;
	}

	/**
	 * 指定等级下的有效品质（JSON levels[].rarity 优先；缺省回退构造品质）。
	 * 品质决定：单独使用次数上限、名称颜色、HUD 品质覆盖层颜色。
	 */
	public SpellRarity getRarity(int level) {
		String fromJson = config.rarity(level);
		if (fromJson != null) {
			return SpellRarity.byId(fromJson);
		}
		return rarity;
	}

	/** 基础伤害（装书内、卷轴满次数时）。无伤害类魔法为 0。 */
	public float getBaseDamage() {
		return config.baseDamage;
	}

	/** 基础冷却（tick）。 */
	public int getBaseCooldownTicks() {
		return config.baseCooldownTicks;
	}

	/** 基础施法时间（tick），0 = 无前摇瞬发。 */
	public int getBaseCastTimeTicks() {
		return config.baseCastTimeTicks;
	}

	/** 每次施法消耗的魔法书法力。 */
	public int getManaCost() {
		return config.manaCost;
	}

	/** 单独使用时的伤害倍率。 */
	public float getSoloDamageMultiplier() {
		return config.soloDamageMultiplier;
	}

	/** 单独使用时的冷却倍率。 */
	public float getSoloCooldownMultiplier() {
		return config.soloCooldownMultiplier;
	}

	/** 单独使用时的施法时间倍率。 */
	public float getSoloCastTimeMultiplier() {
		return config.soloCastTimeMultiplier;
	}

	/** 指定等级的伤害倍率（相对基础值；JSON levels[].damage_multiplier，缺省 1.0）。 */
	public float getDamageMultiplier(int level) {
		return config.damageMultiplier(level);
	}

	/** 指定等级的冷却倍率（相对基础值；JSON levels[].cooldown_multiplier，缺省 1.0）。 */
	public float getCooldownMultiplier(int level) {
		return config.cooldownMultiplier(level);
	}

	/** 指定等级的飞行速度倍率（相对基础值；JSON levels[].speed_multiplier，缺省 1.0）。 */
	public float getSpeedMultiplier(int level) {
		return config.speedMultiplier(level);
	}

	/**
	 * 「按住瞄准型」法术的最大施法距离（格）；0 = 非按住瞄准型（默认，按下立即施放）。
	 * 覆写为正值后，客户端按住施法键时会在准星落点持续显示预览圈，松开才真正施放；
	 * 服务端落点用同一几何（{@link #computeAimImpact}）计算，双端一致。
	 */
	public double getAimMaxRange() {
		return 0.0;
	}

	/**
	 * 按住瞄准时落点预览圈的半径（格，应含等级缩放，与服务端实际 AOE 半径一致）；
	 * 仅 {@link #getAimMaxRange()} > 0 的法术生效。
	 */
	public double getAimRadius(int level) {
		return 0.0;
	}

	/**
	 * 准星落点几何（双端一致）：眼位出发沿视向 raycast（含方块），命中取命中点；
	 * <b>未命中（准星指天/最大距离内无方块命中）返回 null</b>——落点必须在方块上（仿契灵传送）；
	 * 双端调用方都需判空：服务端拒绝施法（不耗法力/CD），客户端不显示预览。
	 */
	public static Vec3d computeAimImpact(LivingEntity caster, double maxRange) {
		Vec3d eye = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(maxRange));
		HitResult hit = caster.getWorld().raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
	}

	/**
	 * 施法前置校验（服务端权威）：当前是否允许施法。默认恒允许；
	 * 瞄准型法术可覆写（如陨火要求准星命中方块）。返回 false = 拒绝施法且不消耗法力/CD
	 * （仿契灵传送失败不消耗语义），由调用方显示红字提示。
	 */
	public boolean canCast(ServerPlayerEntity caster) {
		return true;
	}

	/**
	 * 释放魔法（服务端权威）。伤害/范围等已由调用方按耐久与单独/装书惩罚算好，通过 {@code power} 传入。
	 *
	 * @param caster 施法者（已确认装备魔法书或持有卷轴）
	 * @param power  最终威力（多为最终伤害值；buff 型法术为效果主数值，如吸收值）
	 * @param solo   是否为单独使用卷轴（部分魔法可据此微调表现，一般无需区分）
	 */
	public abstract void cast(ServerPlayerEntity caster, float power, boolean solo);

	/**
	 * 带等级释放魔法（服务端权威）：默认忽略等级直接转发三参版；需要按等级缩放速度/外观/
	 * 范围的法术（如冰锥、齐射、新星类）覆写本方法。调用方统一走本入口，避免逐法术 instanceof 特判。
	 */
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		cast(caster, power, solo);
	}

	/** 魔法名 lang key。 */
	public String getNameKey() {
		return "spell.ssc_addon." + id.getPath() + ".name";
	}

	/** 魔法描述 lang key。 */
	public String getDescKey() {
		return "spell.ssc_addon." + id.getPath() + ".description";
	}

	/** 卷轴 tooltip「装书内」文案 key（默认伤害版；buff 型法术覆写为吸收版等）。 */
	public String getInBookTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_in_book";
	}

	/** 卷轴 tooltip「单独使用」文案 key（默认伤害版；buff 型法术覆写）。 */
	public String getSoloTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_solo";
	}

	/**
	 * 魔法系别（来自 spells JSON {@code element} 字段，解析为 FormationElement）。
	 * 2026-09 起支持 fire/ice/lunar/curse/summon/void/space 七系；无系别或非法 id 返回 null（结算安全降级）。
	 */
	public FormationElement getElement() {
		return FormationElement.byId(config.element);
	}

	/**
	 * 是否为冰系魔法（历史方法：决定卷轴外观染色等。新代码请用 {@link #getElement()}）。
	 * 数值化系别来自 JSON {@code element: "ice"}；本方法 = JSON 标记为 ice 时为 true。
	 */
	public boolean isIceSpell() {
		return "ice".equals(config.element);
	}

	/**
	 * 魔法图标贴图路径（32×32 源图，GUI 内可按需最近邻缩放到任意尺寸，保持像素风）。
	 * 默认 {@code textures/gui/spell_icons/<id path>.png}；子类可覆写自定义路径。
	 * 返回 null 表示无专用图标（HUD 回落到绘制卷轴物品本身）。
	 */
	public Identifier getIconTexture() {
		return new Identifier("ssc_addon", "textures/gui/spell_icons/" + id.getPath() + ".png");
	}
}
