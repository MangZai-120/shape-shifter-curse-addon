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

	public int getMaxLevel() {
		return rarity == SpellRarity.RED ? 1 : ScrollData.MAX_SPELL_LEVEL;
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

	public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity caster, int level, boolean solo) {
		return config.spellTier == SpellCastingRules.Tier.CUSTOM
				? getCustomCastingProfile(caster, level, solo) : config.spellTier.profile;
	}

	protected SpellCastingRules.Profile getCustomCastingProfile(ServerPlayerEntity caster, int level, boolean solo) {
		return new SpellCastingRules.Profile(getBaseCastTimeTicks() > 0 ? getBaseCastTimeTicks() : 20, 0.8, false);
	}

	public SpellCastingRules.Mode getCastingMode() {
		return getAimMaxRange() > 0 ? SpellCastingRules.Mode.RELEASE : SpellCastingRules.Mode.AUTOMATIC;
	}

	/** Select locally first; the server captures the target before starting the paid channel. */
	public boolean requiresTargetBeforeChannel() { return false; }

	public Vec3d captureCastTarget(ServerPlayerEntity caster, int level) {
		return getAimMaxRange() > 0 ? computeAimImpact(caster, getAimMaxRange()) : null;
	}

	private Vec3d lockedCastTarget;

	protected Vec3d getCastTarget(ServerPlayerEntity caster, int level) {
		return lockedCastTarget == null ? captureCastTarget(caster, level) : lockedCastTarget;
	}

	public void castAtTarget(ServerPlayerEntity caster, float power, boolean solo, int level,
				net.minecraft.item.ItemStack scroll, Vec3d target) {
		if (target != null && DomainManager.blocksPath(caster.getWorld(), caster.getPos(), target, 0)) return;
		Vec3d previousTarget = this.lockedCastTarget;
		var previousSource = DomainManager.setEffectSource(caster);
		this.lockedCastTarget = target;
		try {
			cast(caster, power, solo, level, scroll);
		} finally {
			this.lockedCastTarget = previousTarget;
			DomainManager.setEffectSource(previousSource);
		}
	}

	public boolean readyToRelease(ServerPlayerEntity caster, net.minecraft.item.ItemStack scroll) {
		return true;
	}

	public boolean canContinueCasting(ServerPlayerEntity caster, net.minecraft.item.ItemStack scroll) {
		return true;
	}

	public void onChannelStarted(ServerPlayerEntity caster) {}

	public void onChannelStarted(ServerPlayerEntity caster, Vec3d target) { onChannelStarted(caster); }

	public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {}

	/** Called during preparation, before the effect starts (including release-mode spells). */
	public void tickChannel(ServerPlayerEntity caster, int level, net.minecraft.item.ItemStack scroll, int ticks) {}

	public int getInterruptedCooldown(int cooldown) {
		return SpellCastingRules.interruptedCooldown(cooldown);
	}

	/** 施法锁定点（2026-09-23 用户定稿：领域壳开始扩张后不可打断，必须释放）：
	 * 返回 true 时伤害/主动取消/长按取消/位移等一切「可放弃」打断均失效，读条必须走完释放。
	 * 默认 false（无锁定点）；死亡/断线/停服/载体失效等强制终止不在此列，仍会安全结束。 */
	public boolean isLockedIn(ServerPlayerEntity caster) {
		return false;
	}

	/** 施法锁定转换 tick（2026-09-24 新增，客户端 HUD 本地预测用）：返回进入锁定态的读条 tick，
	 * -1 = 无锁定点。服务端判定仍走 isLockedIn（玩家/世界状态），此值仅供客户端在
	 * 校准包时滞/单点补发丢失时按本地推进 elapsed 兜底红显（如领域 200t、爆裂 682t）。 */
	public int getLockInTick() {
		return -1;
	}

	public boolean tickContinuousCast(ServerPlayerEntity caster, int level, net.minecraft.item.ItemStack scroll, int ticks) {
		return false;
	}

	public void endContinuousCast(ServerPlayerEntity caster, int level, boolean interrupted) {}

	/** 冷却绝对下限（tick；0 = 无绝对下限，仅受相对下限 0.2×C_L 约束。阶段 B / 计划书 §6.2）。 */
	public int getCooldownFloorTicks() {
		return config.cooldownFloorTicks;
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
		if (hit.getType() == HitResult.Type.MISS) return null;
		// 领域隔离：命中点与施法者分属任一领域壳内外（跨界）→ 视为无合法落点
		// （与指天拒绝同语义：客户端不显示指针/预览，服务端拒施放不耗蓝不进 CD）
		if (DomainManager.blocksAimBoundary(caster.getWorld(), caster.getPos(), hit.getPos())) return null;
		return hit.getPos();
	}

	/**
	 * 施法前置校验（服务端权威）：当前是否允许施法。默认恒允许；
	 * 瞄准型法术可覆写（如陨火要求准星命中方块）。返回 false = 拒绝施法且不消耗法力/CD
	 * （仿契灵传送失败不消耗语义），由调用方显示红字提示。
	 */
	public boolean canCast(ServerPlayerEntity caster) {
		return true;
	}

	public boolean prepareScroll(ServerPlayerEntity caster, net.minecraft.item.ItemStack scroll) {
		return true;
	}

	public void onCooldownStarted(ServerPlayerEntity caster, Runnable cancelRefund) {}

	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level, net.minecraft.item.ItemStack scroll) {
		cast(caster, power, solo, level);
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

	// ---- 经验机制桥（2026-09-15：exp_mode 0=释放即得 / 1=命中才得 / 2=释放 20%+命中补到 100%） ----

	/**
	 * 本次施法待补发的经验（×10 整数）。服务端逻辑单线程：SpellCastManager 在调用
	 * {@code cast()} 前设置、同一次调用栈内由法术实体/局部变量取走，调用返回后由施法管理器清零——
	 * set→read 之间不会插入其它玩家的施法，无竞态。solo 使用不进本体系（恒 0）。
	 */
	private int pendingExpTen = 0;

	/** 经验机制模式（JSON {@code exp_mode}；缺省 0）。 */
	public int getExpMode() {
		return config.expMode;
	}

	/** 施法管理器专用：设置本次施法的待补发经验（仅服务端调用栈内有效）。 */
	public void ssc_addon$setPendingExp(int expTen) {
		this.pendingExpTen = expTen;
	}

	/** 法术实体 / AOE 循环取走并清零待补发经验（取走后再调用返回 0）。 */
	public int ssc_addon$takePendingExp() {
		int v = pendingExpTen;
		pendingExpTen = 0;
		return v;
	}

	/** 施法管理器专用：cast 返回后强制清桥（防子类忘取走导致泄漏到下次施法）。 */
	public void ssc_addon$clearPendingExp() {
		pendingExpTen = 0;
	}

	// ---- 施法编号桥：同次弹丸、范围和持续效果共用服务端返还额度 ----

	/** 施法管理器在同步 cast 调用前装入、返回后清除；异步效果保存编号，solo 为 null。 */
	private java.util.UUID refundCastId;

	/** 施法管理器专用：设置本次书内施法编号。 */
	public void ssc_addon$setRefundCastId(java.util.UUID castId) {
		this.refundCastId = castId;
	}

	/** 读取但不清除编号，确保同次施法的所有效果共享额度。 */
	public java.util.UUID ssc_addon$getRefundCastId() {
		return refundCastId;
	}

	/** 施法管理器专用：cast 返回后强制清桥（防泄漏到下次施法）。 */
	public void ssc_addon$clearRefundCastId() {
		refundCastId = null;
	}
}
