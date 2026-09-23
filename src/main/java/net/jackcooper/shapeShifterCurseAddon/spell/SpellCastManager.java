package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 月尘魔法书施法服务端核心（jackcooper）。服务端权威：验证佩戴魔法书、读书内卷轴、判冷却/法力、
 * 按卷轴耐久比缩放伤害与冷却（满次数=正常，用过越多越弱），执行魔法、写冷却、累积经验。
 */
public final class SpellCastManager {
	private SpellCastManager() {
	}

	/** 玩家当前佩戴的魔法书（未装备返回 null）。 */
	public static ItemStack getEquippedBook(ServerPlayerEntity player) {
		return TrinketUtils.findFirstEquipped(player, s -> s.getItem() == SscAddon.MOON_DUST_SPELLBOOK);
	}

	/** 释放书内指定槽的魔法（正常档）。 */
	public static void cast(ServerPlayerEntity player, int slot) {
		cast(player, slot, 0);
	}

	public static void cast(ServerPlayerEntity player, int slot, int token) {
		castInternal(player, slot, 0, 1.0f, token);
	}

	/**
	 * 临时降档施放（法力不足三连击，用户 2026-09-18 定稿）：自动降到付得起的最高档，
	 * 本次 CD ×1.2 惩罚；不写卷轴 NBT，下次施法仍按原档。全部档都付不起 → 维持红字提示。
	 */
	public static void castDowngraded(ServerPlayerEntity player, int slot) {
		castDowngraded(player, slot, 0);
	}

	public static void castDowngraded(ServerPlayerEntity player, int slot, int token) {
		if (SpellChannelManager.isCasting(player)) return;
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty()) {
			return;
		}
		int count = SpellbookData.getSlotCount(book);
		if (slot < 0 || slot >= count) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		if (scroll.isEmpty()) {
			return;
		}
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return;
		}
		if (FormCastingStyle.isSwapStabilizing(player)) {
			SpellChannelManager.playNoManaSound(player); // 换书稳定期/CD 类拒绝：火焰熄灭音（2026-09-19 用户定稿）
			return;
		}
		if (ScrollData.isOnCooldown(scroll, player.getWorld())
				|| SharedSpellCooldowns.isOnSharedCooldown(player, spell)) {
			SpellChannelManager.playNoManaSound(player); // CD 中施放：火焰熄灭音
			return;
		}
		int top = ScrollData.getCastLevel(scroll);
		// 客户端预检后可能刚好回能；原档已付得起时正常施放，不错误附加降档惩罚。
		if (SpellbookData.canPayMana(book, SpellNumbers.finalManaCost(spell, book, player, top))) {
			castInternal(player, slot, 0, 1.0f, token);
			return;
		}
		// 红色稀有度（单档）法术不得降档（2026-09-24 用户定稿）：与已是最低档同路处理。
		// 服务端防御：防止伪造降档包或未来红色法术配多条 levels 时绕过客户端守卫。
		if (top <= 1 || spell.getMaxLevel() <= 1) {
			SpellChannelManager.playNoManaSound(player); // 法力不足：火焰熄灭音
			// 已是最低档仍不足：与正常施放同样提示（保持行为一致）
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
			return;
		}
		// 从高到低找付得起的最高档（与正式结算同式，含法阵/亲和/潮汐）
		int payable = SpellNumbers.highestAffordableLevel(spell, book, player, top - 1);
		if (payable <= 0) {
			SpellChannelManager.playNoManaSound(player); // 全档位都付不起：熄灭音
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
			return;
		}
		castInternal(player, slot, payable, 1.2f, token);
	}

	/** 内部统一施法：forcedLevel>0 用指定档（临时降档），否则用卷轴档位；cdPenalty 为本次 CD 惩罚倍率。 */
	private static void castInternal(ServerPlayerEntity player, int slot, int forcedLevel, float cdPenalty, int token) {
		if (SpellChannelManager.isCasting(player)) return;
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty()) {
			return;
		}
		int count = SpellbookData.getSlotCount(book);
		if (slot < 0 || slot >= count) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		if (scroll.isEmpty()) {
			return;
		}
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return;
		}
		World world = player.getWorld();
		// 换书稳定期（阶段 C §15.2，已拍板）：战斗中换书后短期内禁止施法（防多书满蓝连用）
		if (FormCastingStyle.isSwapStabilizing(player)) {
			SpellChannelManager.playNoManaSound(player); // 换书稳定期拒绝：火焰熄灭音
			return;
		}
		// 阶段 B（§15.2）：卷轴 NBT 与玩家共享表双源判定——换槽/换书/同法术第二张卷轴均不能绕 CD。
		// 被共享表拦截时把共享结束时刻回写卷轴 NBT（经饰品同步）：新换入的卷轴 HUD 遮罩也能正确显示
		if (ScrollData.isOnCooldown(scroll, world)
				|| SharedSpellCooldowns.isOnSharedCooldown(player, spell)) {
			SpellChannelManager.playNoManaSound(player); // CD 中施放：火焰熄灭音
			if (!ScrollData.isOnCooldown(scroll, world)) {
				long sharedEnd = SharedSpellCooldowns.getCooldownEndOf(player, spell);
				if (sharedEnd > ScrollData.getCooldownEnd(scroll)) {
					ScrollData.setCooldownEnd(scroll, sharedEnd);
					SpellbookData.setScroll(book, slot, scroll);
				}
			}
			return;
		}
		// 施放档位（低阶选档，§6.4；缺省=卷轴档位；三连击降档由 forcedLevel 指定临时档）
		int level = Math.max(1, Math.min(spell.getMaxLevel(), forcedLevel > 0 ? forcedLevel : ScrollData.getCastLevel(scroll)));
		// 法阵加成：耗蓝倍率（全魔法每级 +10%）+ 形态亲和耗蓝乘区（使魔系 ×0.85）+ 每级耗蓝倍率
		// （耗蓝按施放档位算——低阶施放省蓝；召唤亲和 +1 只加强施法效果，不推高耗蓝）
		int manaCost = SpellNumbers.finalManaCost(spell, book, player, level);
		// 书内法术只按 HUD 书能量判定；形态能量不能把书不足的施法放行。
		if (!SpellbookData.canPayMana(book, manaCost)) {
			SpellChannelManager.playNoManaSound(player); // 法力不足：火焰熄灭音
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
			return;
		}

		// 施法前置校验（如陨火要求准星命中方块）：失败拒绝施法、不耗法力/CD（仿契灵传送失败不消耗）
		if (spell.getCastingMode() == SpellCastingRules.Mode.AUTOMATIC && !spell.canCast(player)) {
			SpellChannelManager.playFailureSound(player);
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_target").formatted(Formatting.RED), true);
			return;
		}

		FormationElement spellElement = spell.getElement();
		float ratio = ScrollData.getDurabilityRatio(scroll);   // 1=满次数, 越低惩罚越大
		// 形态亲和等级加成（荧光幼灵/阿澪：召唤系 +1，上限 5）——叠加在施放档位上
		// （耗蓝已按档位结算完毕；亲和只加强效果等级，不推高耗蓝，语义与旧版一致）
		level = FormAffinity.bonusSpellLevel(player, spellElement, level);
		// 法阵加成：同系伤 +12%/级、对立系伤 -12%/级；同系 cd -5%/级；空间法阵只缩空间系 cd
		// 形态亲和：伤害/耗蓝/CD 三乘区（与法阵独立叠加）
		float damage = spell.getBaseDamage() * ratio * spell.getDamageMultiplier(level)
				* FormationData.sumDamageMultiplier(book, spellElement)
				* FormAffinity.damageMultiplier(player, spellElement);
		// 阶段 B（§6.2）：统一冷却公式——多乘区 + 双层下限（绝对 floor + 0.2×等级基准），
		// 与单独使用/客户端 HUD 共用同一实现（SpellNumbers）
		float formationCdMul = FormationData.sumCooldownMultiplier(book, spellElement);
		float affinityCdMul = FormAffinity.cooldownMultiplier(player, spellElement);
		int cd = SpellNumbers.finalCooldownTicks(spell, level, ratio, formationCdMul, affinityCdMul);
		if (cdPenalty != 1.0f) {
			cd = Math.round(cd * cdPenalty); // 三连击降档惩罚：本次 CD ×1.2（用户定稿）
		}

		net.minecraft.nbt.NbtCompound previousNbt = scroll.getNbt() == null ? null : scroll.getNbt().copy();
		if (!spell.prepareScroll(player, scroll)) {
			SpellChannelManager.playFailureSound(player);
			return;
		}
		if (!java.util.Objects.equals(previousNbt, scroll.getNbt())) {
			SpellbookData.setScroll(book, slot, scroll);
		}
		final int castLevel = level;
		final int castMana = manaCost;
		// 起手一次性全额扣除（2026-09-23 用户定稿）：预检通过即锁定报价，通道建立成功后
		// 立即全额结算——读条期间零扣费（渐进扣蓝已废），中断不返还已扣部分。
		boolean started = SpellChannelManager.start(player, spell, scroll, level, false, token, manaCost, cd,
				() -> getEquippedBook(player) == book && ItemStack.areEqual(SpellbookData.getScroll(book, slot), scroll),
				target -> finishCast(player, book, scroll, spell, damage, castLevel, castMana, forcedLevel, target),
				duration -> {
					ItemStack current = SpellbookData.getScroll(book, slot);
					long end = player.getWorld().getTime() + duration;
					boolean unchanged = ItemStack.areEqual(current, scroll);
					ScrollData.setCooldownEnd(scroll, end);
					if (unchanged) SpellbookData.setScroll(book, slot, scroll);
					SharedSpellCooldowns.record(player, spell, end);
				});
		if (started) {
				// 通道建立后才结算（start 失败如落点失效不扣）；预检已确保付得起，此处必成
				SpellbookData.consumeMana(book, manaCost);
				FormCastingStyle.markManaSpend(player);
			}
	}

	private static void finishCast(ServerPlayerEntity player, ItemStack book, ItemStack scroll, Spell spell,
			float damage, int level, int manaCost, int forcedLevel, net.minecraft.util.math.Vec3d target) {
		FormationElement spellElement = spell.getElement();
		// 记录耗蓝时刻：自然回复 7 秒延迟从此起算（再次施法重置）
		FormCastingStyle.markManaSpend(player);
		// 一次施法返还预算（阶段 B §7.4）：本次耗蓝 ×50% 为上限，多弹丸/AOE 共享
		spell.ssc_addon$setRefundCastId(FormCastingStyle.openRefundBudget(player, manaCost));
		// 经验机制（exp_mode，2026-09-15）——基数按「法术基础成本（基础耗蓝×等级倍率）× 有效技能等级」
		// （阶段 C 对齐计划书 §16：不用最终耗蓝，防堆加耗蓝法阵变成刷级装备），
		// （蓝色品质中位 6.0 exp/次），再乘经验法阵（通用系 exp 变体）倍率：每级 +10%（Lv5=×1.5）：
		//   0 = 释放即得全额；1 = 命中才得（释放时 0，全额挂起）；2 = 释放得 20%、命中补 80%。
		// 挂起部分经 Spell 桥在 cast 调用前装入：弹射物法术在 cast 内取走存进实体（NBT 持久化，
		// 命中结算时发放）；AOE 法术在 damage 成功后取走发放（首目标取全额、后续取 0，天然幂等）。
		int expMode = spell.getExpMode();
		int baseExpTen = Math.round(spell.getManaCost()
				* spell.getConfig().manaCostMultiplier(level)) * level;
		int bestExpFormation = FormationData.getBestUniversalVariantLevel(book, FormationData.VARIANT_EXP);
		if (bestExpFormation > 0) {
			baseExpTen = Math.round(baseExpTen * FormationData.universalExpMultiplier(bestExpFormation));
		}
		int pendingTen;
		switch (expMode) {
			case 1 -> pendingTen = baseExpTen;                // 命中才得：释放时全额挂起
			case 2 -> pendingTen = baseExpTen * 8 / 10;       // 释放 20% + 命中补 80%（整数截断）
			default -> pendingTen = 0;                        // 释放即得：无挂起
		}
		if (pendingTen > 0) {
			spell.ssc_addon$setPendingExp(pendingTen);
		}
		// 统一四参入口：法术内部自行决定是否按等级缩放速度/外观/范围（无 instanceof 特判）
		try {
			spell.castAtTarget(player, damage, false, level, scroll, target);
		} finally {
			spell.ssc_addon$clearPendingExp();
			spell.ssc_addon$clearRefundCastId();
		}
		// 风灵「风行」等施放即返类流派（不要求命中）
		FormCastingStyle.onSpellCast(player, spellElement, manaCost);
		// 降档施放告知（临时档不写卷轴，玩家需要知道本次按了几档）
		if (forcedLevel > 0) {
			player.sendMessage(Text.translatable("message.ssc_addon.spell.downgrade_cast",
					Text.translatable(spell.getNameKey()), forcedLevel), true);
		}
		SpellbookData.addExpTen(book, baseExpTen - pendingTen);
		if (SpellNumbers.isAtFloor(spell, level, ScrollData.getDurabilityRatio(scroll),
				FormationData.sumCooldownMultiplier(book, spellElement), FormAffinity.cooldownMultiplier(player, spellElement))) {
			player.sendMessage(Text.translatable("message.ssc_addon.spell.cd_floor")
					.formatted(Formatting.GRAY), true);
		}
	}

	/** 更新当前选中槽（存书 NBT，持久化 + 服务端一致）。 */
	public static void setSelected(ServerPlayerEntity player, int slot) {
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty()) {
			return;
		}
		if (slot < 0 || slot >= SpellbookData.getSlotCount(book)) return;
		SpellChannelManager.cancelSelf(player);
		SpellbookData.setSelectedSlot(book, slot);
	}

	/**
	 * 设置卷轴施放档位（低阶选档，阶段 C / 计划书 §6.4；服务端权威）。
	 * castLevel=0 表示重置为跟随卷轴等级；1-5 由 {@link ScrollData#setCastLevel} 夹到 [0, 卷轴等级]。
	 * 各等级共享 CD、耗蓝/伤害/CD 按档位结算（施法处已实现）；写回书 NBT 经饰品同步客户端。
	 */
	public static void setCastLevel(ServerPlayerEntity player, int slot, int castLevel) {
		if (SpellChannelManager.isCasting(player)) return;
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty() || slot < 0 || slot >= SpellbookData.getSlotCount(book)) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return;
		}
		ScrollData.setCastLevel(scroll, castLevel);
		SpellbookData.setScroll(book, slot, scroll);
		player.sendMessage(Text.translatable("message.ssc_addon.spell.cast_level_set",
				Text.translatable(spell.getNameKey()),
				ScrollData.getCastLevel(scroll)), true);
	}
}
