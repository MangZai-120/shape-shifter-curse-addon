package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpSoulEnergy;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 形态施法流派系统（jackcooper，2026-09-17）：把「形态能量条 ↔ 书法术值」从割裂的两套资源
 * 打通为按形态差异化的施法流派。所有结算均在服务端，多人天然一致。
 *
 * <p>书内施法的起手与扣费只使用书能量（见 {@link SpellbookData}）；形态能量须经
 * 回能法阵先实际充入书中，才能参与施法。</p>
 *
 * <p>命中与回复流派：</p>
 * <ul>
 *   <li><b>命中触发型</b>（法术命中时，各法术实体/AOE 挂点调用 {@link #onSpellHit}）：
 *       雪狐冰系命中 +1 寒霜；月织蛛月辉命中 +2 蛛 mana；金沙岚火系命中燃烧目标
 *       返 20% 耗蓝、击杀返 50%；堕灵诅咒系命中带负面效果目标返 25%；食梦魔虚无系
 *       命中返 50% 本次耗蓝；阿努比斯任意法术命中 +2 灵魂能量。全部共享每玩家
 *       1 秒防重窗（防 AOE 多目标 / 齐射多枚瞬间膨胀）。</li>
 *   <li><b>持续回复型</b>（每秒 tick，{@link #init} 注册）：朔望「月相」夜间 +1/s
 *       白天 +0.2/s；荧光幼灵/阿澪「共生」每只存活月灵 +0.5/s；吸血蝙蝠「血契」
 *       血值每 +1 同步回复 1 点书法术值（BarTrigger 挂 BAT_BLOOD）。</li>
 * </ul>
 *
 * <p>边界：书未装备 / 形态不匹配 / 资源不足 → 静默跳过不动作；血值下降不触发血契；
 * 防重窗按玩家全局共享（同秒多法术命中只结算首个返还类效果）。</p>
 */
public final class FormCastingStyle {
	private FormCastingStyle() {}

	/** 书基础自然回复（每秒，2026-09-19 用户定稿：基础 3，每升一级 +1，即 Lv1=3/Lv2=4/Lv3=5；
	 * 回息法阵每级 +20% 可叠加）。 */
	public static final float BASE_BOOK_REGEN_PER_SEC = 3.0f;

	/** 自然回复延迟（2026-09-17 用户定稿）：施法耗蓝后 7 秒（140t）内不自然回复，再次消耗重置计时。 */
	public static final int REGEN_DELAY_TICKS = 140;
	/** 每玩家最后施法耗蓝时刻（游戏 tick；服务器重启重置——重启即重新计 7 秒，可接受）。 */
	private static final Map<UUID, Long> LAST_SPEND_TICK = new ConcurrentHashMap<>();

	/** 渐进扣费及正常释放时记录时刻；充能、回能和返还不计。 */
	public static void markManaSpend(ServerPlayerEntity player) {
		if (player != null) {
			LAST_SPEND_TICK.put(player.getUuid(), player.getWorld().getTime());
		}
	}

	/** 无活动施法且距上次耗蓝已过延迟时，允许基础自然回复。 */
	private static boolean canRegenerate(ServerPlayerEntity player) {
		Long last = LAST_SPEND_TICK.get(player.getUuid());
		return SpellCastingRules.naturalRegenAllowed(SpellChannelManager.isCasting(player),
				last == null ? REGEN_DELAY_TICKS : player.getWorld().getTime() - last, REGEN_DELAY_TICKS);
	}

	/** 美西螈「潮汐」：在水中/雨中施法耗蓝 ×0.85（SP 与进化美西螈同享，用户定稿）。 */
	public static int applyTidalDiscount(net.minecraft.entity.player.PlayerEntity player, FormationElement element, int manaCost) {
		if (manaCost <= 0) {
			return manaCost;
		}
		if ((FormUtils.isForm(player, FormIdentifiers.AXOLOTL_SP)
				|| FormUtils.isForm(player, FormIdentifiers.UPGRADE_AXOLOTL))
			&& (player.isSubmergedInWater() || player.getWorld().hasRain(player.getBlockPos()))) {
			return Math.max(1, Math.round(manaCost * 0.85f));
		}
		return manaCost;
	}

	/** 蝙蝠「血契」配套：月华治愈效果 +25%（吸血蝙蝠主题；由 LunarMendSpell 调用）。 */
	public static float lunarMendBonus(ServerPlayerEntity caster, float healAmount) {
		if (FormUtils.isForm(caster, FormIdentifiers.BAT_DESMODUS)) {
			return healAmount * 1.25f;
		}
		return healAmount;
	}

	/** 每玩家命中返还防重窗（上次结算的游戏 tick；1 秒 = 20t 内不重复）。 */
	private static final Map<UUID, Long> HIT_REFUND_WINDOW = new ConcurrentHashMap<>();

	// ==================== 一次施法返还预算（阶段 B / 计划书 §7.4） ====================

	private static final SpellCastingRules.RefundLedger REFUND_BUDGET = new SpellCastingRules.RefundLedger();

	/**
	 * 正常书内施放时创建独立额度，比例返还总计最多为本次成本的 50%；solo 不创建。
	 * 同次多弹丸、范围和持续效果共享编号；后续施法不覆盖旧额度。
	 */
	public static UUID openRefundBudget(ServerPlayerEntity player, int manaCost) {
		return REFUND_BUDGET.open(player.getUuid(), manaCost, player.getServer().getOverworld().getTime());
	}

	/** 玩家退出时清理其所有未到期的返还额度。 */
	private static void clearRefundBudget(ServerPlayerEntity player) {
		if (player != null) {
			REFUND_BUDGET.clearPlayer(player.getUuid());
		}
	}

	/**
	 * 法术命中钩子：仅实际生效的伤害或状态调用；没有合法书内编号时不返还。
	 *
	 * @param caster   施法者（须为书施法；未装备书则静默跳过）
	 * @param target   被命中目标
	 * @param element  法术系别
	 * @param castId 本次书内施法编号；单独卷轴不持有编号
	 */
	public static void onSpellHit(ServerPlayerEntity caster, LivingEntity target,
	                              FormationElement element, UUID castId) {
		onSpellHit(caster, target, element, castId, target != null &&
				(element == FormationElement.FIRE ? target.getFireTicks() > 0 : hasHarmfulEffect(target)));
	}

	public static void onSpellHit(ServerPlayerEntity caster, LivingEntity target,
	                              FormationElement element, UUID castId, boolean conditionBeforeHit) {
		if (caster == null || target == null || !caster.isAlive()
				|| target instanceof net.minecraft.entity.decoration.ArmorStandEntity) {
			return;
		}
		SpellCastingRules.RefundBudget budget = REFUND_BUDGET.find(caster.getUuid(), castId,
				caster.getServer().getOverworld().getTime());
		if (budget == null) return;
		ItemStack book = SpellCastManager.getEquippedBook(caster);
		if (book == null || book.isEmpty()) {
			return;
		}
		boolean killed = !target.isAlive() || target.getHealth() <= 0f;

		// 固定值回复类（不依赖 manaCost，独立于防重窗之外的雪狐回条很温和，仍统一走窗防齐射膨胀）
		// 雪狐 SP「霜脉」：冰系命中 +1 寒霜
		if (element == FormationElement.ICE && FormUtils.isForm(caster, FormIdentifiers.SNOW_FOX_SP)) {
			if (enterWindow(caster)) {
				ResourceBars.gain(caster, BarKeys.SNOW_FOX, 1);
			}
			return;
		}
		// 月织蛛「织月」：月辉系命中 +2 原版 mana
		if (element == FormationElement.LUNAR && FormUtils.isForm(caster, FormIdentifiers.SPIDER_MOON_WEAVER)) {
			if (enterWindow(caster)) {
				ResourceBars.gain(caster, BarKeys.VANILLA_MANA, 2);
			}
			return;
		}
		// 阿努比斯「审魂」：任意法术命中 +2 灵魂能量（法术命中积魂，不消耗灵魂）
		if (FormUtils.isForm(caster, FormIdentifiers.ANUBIS_WOLF_SP)) {
			if (enterWindow(caster)) {
				AnubisWolfSpSoulEnergy.addEnergy(caster, 2);
			}
			return;
		}
		// 金沙岚「燎原」：火系命中燃烧目标返 20% 耗蓝；击杀返 50%（阶段 B：受单次预算 ≤50% 封顶）
		if (element == FormationElement.FIRE && FormUtils.isForm(caster, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			if (killed) {
				if (enterWindow(caster)) {
					SpellbookData.addMana(book, budget.grant(0.5f));
				}
			} else if (conditionBeforeHit) {
				if (enterWindow(caster)) {
					SpellbookData.addMana(book, budget.grant(0.2f));
				}
			}
			return;
		}
		// 堕灵「噬咒」：诅咒系命中已带负面效果的目标返 25% 耗蓝（配合诅咒标记连招；受单次预算封顶）
		if (element == FormationElement.CURSE && FormUtils.isForm(caster, FormIdentifiers.FALLEN_ALLAY_SP)) {
			if (conditionBeforeHit && enterWindow(caster)) {
				SpellbookData.addMana(book, budget.grant(0.25f));
			}
			return;
		}
		// 食梦魔「噬梦」：虚无系命中返 50% 本次耗蓝（用户定稿；受单次预算 ≤50% 封顶，防 AOE 多目标膨胀）
		if (element == FormationElement.VOID && FormUtils.isForm(caster, FormIdentifiers.WILD_CAT_NIGHTMARE)) {
			if (enterWindow(caster)) {
				SpellbookData.addMana(book, budget.grant(0.5f));
			}
		}
	}

	/** 目标是否带任意负面状态效果（噬咒判定用）。 */
	public static boolean hasHarmfulEffect(LivingEntity target) {
		for (var instance : target.getStatusEffects()) {
			if (instance.getEffectType().getCategory() == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL) {
				return true;
			}
		}
		return false;
	}

	/** 进入防重窗：窗口外更新时间戳返回 true；窗口内返回 false。 */
	private static boolean enterWindow(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		Long last = HIT_REFUND_WINDOW.get(player.getUuid());
		if (last != null && now - last < 20L) {
			return false;
		}
		HIT_REFUND_WINDOW.put(player.getUuid(), now);
		return true;
	}
	/**
	 * 寄生果蝠「播种」：潜行右键魔法书消耗 1 种子 = 回 10 书法术值（专属性率，不走通用 5:1；
	 * 种子条上限 10，1 种子=10 书当量与其它形态体感相当）。由 MoonDustSpellbookItem.use 调用。
	 *
	 * @return true = 已处理本次使用（消耗种子并回复）；false = 未命中条件不拦截
	 */
	public static boolean trySeedSow(ServerPlayerEntity player, ItemStack book) {
		if (!FormUtils.isForm(player, FormIdentifiers.BAT_PARASITIC_FRUIT)
				|| !player.isSneaking()
				|| !ResourceBars.has(player, BarKeys.SEED)
				|| ResourceBars.get(player, BarKeys.SEED) <= 0) {
			return false;
		}
		if (!ResourceBars.consume(player, BarKeys.SEED, 1)) {
			return false;
		}
		int added = SpellbookData.addMana(book, 10);
		if (added <= 0) {
			// 书已满：退回种子（不白耗）
			ResourceBars.gain(player, BarKeys.SEED, 1);
			return false;
		}
		// 个人操作反馈音：仅自己可闻（复用包内工具方法）
		net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkManager.playSoundToPlayer(
				player, net.minecraft.sound.SoundEvents.BLOCK_AZALEA_LEAVES_PLACE, 0.8f, 1.4f);
		player.sendMessage(net.minecraft.text.Text.translatable("message.ssc_addon.spellbook.seed_sow",
				added), true);
		return true;
	}

	/** 防重窗与预算窗口清理：玩家退出时移除（防长期驻留；ConcurrentHashMap 量级小，保留亦可，此处顺手清）。 */
	public static void clearPlayer(ServerPlayerEntity player) {
		if (player != null) {
			HIT_REFUND_WINDOW.remove(player.getUuid());
			LAST_SPEND_TICK.remove(player.getUuid());
			clearRefundBudget(player);
			LAST_HURT_TICK.remove(player.getUuid());
			LAST_BOOK_ID.remove(player.getUuid());
			CAST_BAN_UNTIL.remove(player.getUuid());
		}
	}
	/**
	 * 风灵「风行」：空间系成功施放返 15% 耗蓝（施放即返，不要求命中；施法成功处调用）。
	 */
	public static void onSpellCast(ServerPlayerEntity caster, FormationElement element, int manaCost) {
		if (element == FormationElement.SPACE && manaCost > 0
				&& FormUtils.isForm(caster, FormIdentifiers.OCELOT_SP)) {
			ItemStack book = SpellCastManager.getEquippedBook(caster);
			if (book != null && !book.isEmpty()) {
				SpellbookData.addMana(book, Math.round(manaCost * 0.15f));
			}
		}
	}

	// ==================== 换书稳定期（阶段 C / 计划书 §15.2，已拍板） ====================

	/** 受击后可自由换书窗口（tick）：窗口内换书视为「战斗中换书」。 */
	public static final int COMBAT_SWAP_WINDOW_TICKS = 100;
	/** 战斗中换书后的施法封禁期（tick）：稳定期内书无法施法（准备阶段换书不受影响）。 */
	public static final int SWAP_STABILIZE_TICKS = 60;

	/** 每玩家：受击时刻（世界时间；hurtTime 每轮询刷新）。 */
	private static final Map<UUID, Long> LAST_HURT_TICK = new ConcurrentHashMap<>();
	/** 每玩家：上次佩戴的书 UUID（换书检测）。 */
	private static final Map<UUID, java.util.UUID> LAST_BOOK_ID = new ConcurrentHashMap<>();
	/** 每玩家：施法封禁截止（世界时间；0 = 无封禁）。 */
	private static final Map<UUID, Long> CAST_BAN_UNTIL = new ConcurrentHashMap<>();

	/**
	 * 换书稳定期逻辑（每秒 tick 内调用）：检测受击（hurtTime>0 即刷新受击时刻）与换书
	 * （书 UUID 变化），战斗窗口内换书 → 封施法 SWAP_STABILIZE_TICKS。判定全部服务端，多人天然一致。
	 */
	private static void tickSwapStabilizer(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		// 受击检测：hurtTime 持续 10t，每秒轮询必命中
		if (player.hurtTime > 0) {
			LAST_HURT_TICK.put(player.getUuid(), now);
		}
		ItemStack book = SpellCastManager.getEquippedBook(player);
		java.util.UUID bookId = null;
		if (book != null && !book.isEmpty()) {
			// 书实例 UUID：首检测时生成并持久化到书 NBT（此后稳定；书挪槽不变，换书才变）
			net.minecraft.nbt.NbtCompound nbt = book.getOrCreateNbt();
			if (!nbt.containsUuid("ID")) {
				nbt.putUuid("ID", java.util.UUID.randomUUID());
			}
			bookId = nbt.getUuid("ID");
		}
		java.util.UUID prev = LAST_BOOK_ID.get(player.getUuid());
		if (prev != null && bookId != null && !prev.equals(bookId)) {
			// 换书发生：在战斗窗口内 → 封施法
			Long lastHurt = LAST_HURT_TICK.get(player.getUuid());
			boolean inCombat = lastHurt != null && now - lastHurt <= COMBAT_SWAP_WINDOW_TICKS;
			if (inCombat) {
				CAST_BAN_UNTIL.put(player.getUuid(), now + SWAP_STABILIZE_TICKS);
			player.sendMessage(net.minecraft.text.Text.translatable(
					"message.ssc_addon.spellbook.swap_stabilizing").formatted(net.minecraft.util.Formatting.YELLOW), true);
			}
		}
		if (bookId != null) {
			LAST_BOOK_ID.put(player.getUuid(), bookId);
		} else {
			LAST_BOOK_ID.remove(player.getUuid());
		}
	}

	/** 是否处于换书稳定期（施法入口调用；true = 拒绝施法）。 */
	public static boolean isSwapStabilizing(ServerPlayerEntity player) {
		Long ban = CAST_BAN_UNTIL.get(player.getUuid());
		return ban != null && player.getWorld().getTime() < ban;
	}

	// ==================== 持续回复型（每秒 tick） ====================

	/** 初始化入口（SscAddon.onInitialize 调用）：注册每秒 tick 与血契触发器。 */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) {
				return;
			}
			REFUND_BUDGET.expire(server.getOverworld().getTime());
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tickSwapStabilizer(player);
				tickSustainStyles(player, server.getTicks());
			}
		});
		// 血契：血值每 +1 → 书法术值 +1（BarTrigger 挂蝙蝠血条；下降不触发；施法期间暂停）
		BarKeys.BAT_BLOOD.addTrigger((player, oldV, newV, max) -> {
			if (newV > oldV && !SpellChannelManager.isCasting(player)) {
				ItemStack book = SpellCastManager.getEquippedBook(player);
				if (book != null && !book.isEmpty()) {
					SpellbookData.addMana(book, newV - oldV);
				}
			}
		});
		// 玩家退出：清防重窗残留
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT
				.register((handler, server) -> clearPlayer(handler.player));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
				.register(server -> REFUND_BUDGET.clear());
	}

	/**
	 * 持续流派每秒结算：
	 * <ul>
	 *   <li>朔望「月相」：夜间 +1/s、白天 +0.2/s（每 5 秒 +1）；</li>
	 *   <li>荧光幼灵/阿澪「共生」：每只存活月灵 +0.5/s（每 2 秒结算一次加只数，避免 1 只时 int 截断为 0）。</li>
	 * </ul>
	 */
	private static void tickSustainStyles(ServerPlayerEntity player, int serverTicks) {
		ItemStack book = TrinketUtils.findFirstEquipped(player, s -> s.getItem() == SscAddon.MOON_DUST_SPELLBOOK);
		if (book == null || book.isEmpty()) {
			return;
		}
		// 全书通用自然回复：基础 3/秒 + 每书等级 +1（Lv1=3/Lv2=4/Lv3=5）× 回息法阵倍率（每级 +20% 可叠加）；
		// 活动施法及耗蓝后 7 秒内不回复；月相、共生等流派回复不受此限。
		if (canRegenerate(player)) {
			float regenPerSec = BASE_BOOK_REGEN_PER_SEC + (SpellbookData.getLevel(book) - 1);
			SpellbookData.addMana(book, Math.round(regenPerSec
					* FormationData.universalRecoveryMultiplier(book)));
		}
		// 施法期间书能量不回复（2026-09-23 用户定稿，同回能法阵）：保证整次施法净消耗与报价一致
		boolean casting = SpellChannelManager.isCasting(player);
		// 月相（朔望）：书自然回复，无能量体系的纯书流派
		if (!casting && FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) {
			if (player.getWorld().isNight()) {
				SpellbookData.addMana(book, 1);                       // 夜间 1/s
			} else if (serverTicks % 100 == 0) {
				SpellbookData.addMana(book, 1);                       // 白天 0.2/s
			}
			return;
		}
		// 共生（荧光幼灵/阿澪）：每 2 秒 +存活月灵数（= 每只 0.5/s）
		// 阶段 B（§11.2）：共生回蓝计数上限 3 只——实体数与回蓝收益分别控制，
		// 多出的月灵仍能战斗，只是不产生额外回蓝
		if (!casting && (FormUtils.isForm(player, FormIdentifiers.AXOLOTL_FLUORESCENT)
				|| FormUtils.isForm(player, FormIdentifiers.AXOLOTL_ALING))
				&& serverTicks % 40 == 0
				&& player.getWorld() instanceof ServerWorld serverWorld) {
			List<LunarSpiritEntity> spirits = serverWorld.getEntitiesByClass(LunarSpiritEntity.class,
					player.getBoundingBox().expand(64), s -> s.isAlive());
			int count = 0;
			for (LunarSpiritEntity spirit : spirits) {
				if (player.getUuid().equals(spirit.getOwnerUuid())) {
					count++;
				}
			}
			count = Math.min(count, 3); // 回蓝计数上限 3 只（实体上限另计：契约容量 6）
			if (count > 0) {
				SpellbookData.addMana(book, count);
			}
		}
	}
}
