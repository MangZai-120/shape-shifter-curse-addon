package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastFeedback;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 月尘魔法书 HUD（jackcooper）。仅在佩戴魔法书时于屏幕左下角显示：
 * <ul>
 *   <li>魔法选择器：三槽（中间大=当前选中魔法，左=上一个，右=下一个，首尾相连）；固定三层由底到顶=空白 → 技能图标(有魔法才画) → 有东西边框；</li>
 *   <li>当前魔法名（白色，三槽下方居中）+ 剩余冷却；中间槽冷却时画由底部升起的遮罩；</li>
 *   <li>法力条：空条贴图(含金框与中心装饰)为底、满条贴图按法力比例从左裁剪叠上，显示书的当前 / 最大法力。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class SpellbookHudRenderer implements HudRenderCallback {

	// 槽位双状态贴图：_empty=空槽（无魔法时显示），_filled=「空白框」覆盖层（无魔法时叠在最上层）
	private static final Identifier TEX_SLOT_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_empty.png");
	private static final Identifier TEX_SLOT_FILLED = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_filled.png");
	private static final Identifier TEX_SLOT_BIG_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_empty.png");
	private static final Identifier TEX_SLOT_BIG_FILLED = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_filled.png");
	// 品质覆盖层（有魔法时叠在最上层，按卷轴稀有度选色；顺序与 SpellRarity 枚举一致：白/绿/蓝/紫/橙/红）
	private static final Identifier[] TEX_SLOT_RARITY = {
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_white.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_green.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_blue.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_purple.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_orange.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_rarity_red.png")
	};
	private static final Identifier[] TEX_SLOT_BIG_RARITY = {
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_white.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_green.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_blue.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_purple.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_orange.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_red.png")
	};
	// Supplied 24px rune-modified frames; side previews scale the same artwork to 18px.
	private static final Identifier[] TEX_SLOT_RARITY_MODIFIED = {
			null,
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_green_modified.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_blue_modified.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_purple_modified.png"),
			new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_rarity_orange_modified.png"),
			null
	};
	// 法力条双态贴图：_empty=空条(底,含金框与中心装饰)，_full=满条(按法力%从左裁剪叠上)
	private static final Identifier TEX_BAR_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_bar_empty.png");
	private static final Identifier TEX_BAR_FULL = new Identifier("ssc_addon", "textures/gui/spell_hud_bar_full.png");
	private static final float MANA_COST_TEXT_SCALE = 0.75f;

	/**
	 * 每 tick 数据快照（2026-09-29 性能优化）：spark 档案实测本 HUD 1.96% 中近半是
	 * 每帧 NBT 解析链（getScroll 的 NbtList 遍历 + ItemStack.fromNbt 拷贝、getMana/getMaxMana、
	 * sumCooldownMultiplier 的法阵遍历）。这些值每 tick 至多变一次（CD 剩余本就按 tick 步进显示），
	 * 按 (world 引用, book 引用, tick) 三元组缓存——服务端同步走 ItemStack 整体替换、引用必变；
	 * 本地 GUI 改动（取放卷轴/换书）发生在 tick 边界且 getEquippedBook 每 tick 重扫，引用同样会变。
	 */
	private static final class TickSnapshot {
		final int mana;
		final int maxMana;
		final int manaCost;
		final int prev;
		final int next;
		final ItemStack selScroll;
		final ItemStack prevScroll;
		final ItemStack nextScroll;

		TickSnapshot(int mana, int maxMana, int manaCost, int prev, int next,
				ItemStack selScroll, ItemStack prevScroll, ItemStack nextScroll) {
			this.mana = mana;
			this.maxMana = maxMana;
			this.manaCost = manaCost;
			this.prev = prev;
			this.next = next;
			this.selScroll = selScroll;
			this.prevScroll = prevScroll;
			this.nextScroll = nextScroll;
		}
	}

	private static Object snapWorld;
	private static ItemStack snapBook;
	private static long snapTick = Long.MIN_VALUE;
	private static int snapSlot = -1;
	private static TickSnapshot snapshot;

	private static TickSnapshot snapshotOf(MinecraftClient mc, ItemStack book, int sel) {
		if (snapWorld == mc.world && snapBook == book && snapSlot == sel
				&& snapTick == mc.world.getTime() && snapshot != null) {
			return snapshot;
		}
		int prev = SpellbookData.nextFilledSlot(book, sel, -1);
		int next = SpellbookData.nextFilledSlot(book, sel, +1);
		// 与主流程同规则：无任何卷轴时三槽都读当前选中位（-1 只作哨兵，不参与 getScroll）
		if (prev < 0 || next < 0) {
			prev = sel;
			next = sel;
		}
		ItemStack selected = SpellbookData.getScroll(book, sel);
		Spell spell = ScrollData.getSpell(selected);
		int cost = spell == null ? -1 : SpellNumbers.finalManaCost(spell, book, mc.player,
				ScrollData.getCastLevel(selected), selected);
		TickSnapshot fresh = new TickSnapshot(
				SpellbookData.getMana(book), SpellbookData.getMaxMana(book), cost,
				prev, next,
				selected, SpellbookData.getScroll(book, prev), SpellbookData.getScroll(book, next));
		snapWorld = mc.world;
		snapBook = book;
		snapTick = mc.world.getTime();
		snapSlot = sel;
		snapshot = fresh;
		return fresh;
	}

	@Override
	public void onHudRender(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.options.hudHidden) {
			return;
		}
		// 每帧全饰品扫描已收敛到 ClientTickCache 每 tick 一次（SpellcastClient.getEquippedBook 同源）
		ItemStack book = SpellcastClient.getEquippedBook();
		if (book == null || book.isEmpty()) {
			return;
		}
		int count = SpellbookData.getSlotCount(book);
		if (count <= 0) {
			return;
		}
		int sel = SpellcastClient.getSelectedSlot();
		if (sel >= count) {
			sel = 0;
		}
		// 本 tick 数据快照：mana/前后槽/三槽卷轴全部一次读齐，帧间不再走 NBT 解析链
		TickSnapshot snap = snapshotOf(mc, book, sel);
		int prev = snap.prev;
		int next = snap.next;
		boolean allEmpty = prev < 0 || next < 0;
		if (allEmpty) {
			prev = next = sel; // 全空：三槽都画当前选中位（全为空白框）
		}

		// HUD 整体位置：由 SSCAddonClientConfig 九宫格锚点 + 偏移决定（BarPositionEditorScreen 可视化编辑）
		// baseX/baseY = 单元逻辑原点；法力条/三槽/魔法名全部相对它布局。默认锚点7(左下)+偏移(16,-52)。
		SSCAddonClientConfig cfg = SSCAddonConfig.client();
		net.minecraft.util.Pair<Integer, Integer> sbAnchor = UIPositionUtils.getCorrectPosition(
				cfg.spellbookHudPosType, cfg.spellbookHudPosOffsetX, cfg.spellbookHudPosOffsetY);
		int baseX = sbAnchor.getLeft();
		int baseY = Math.max(26, Math.min(sbAnchor.getRight(), mc.getWindow().getScaledHeight() - 48));

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		// 法力条：空条(底,含金框与中心装饰)始终画满宽，满条按法力%从左裁剪叠上
		int mana = snap.mana;
		int maxMana = snap.maxMana;
		int barW = 76, barH = 10;
		int barX = baseX - 7, barY = baseY - 14; // 76px 条相对三槽(中心 baseX+31)居中：left=中心-38=baseX-7
		ctx.drawTexture(TEX_BAR_EMPTY, barX, barY, 0, 0, barW, barH, barW, barH);
		int fillW = maxMana > 0 ? (int) ((long) barW * mana / maxMana) : 0;
		if (fillW > 0) {
			ctx.drawTexture(TEX_BAR_FULL, barX, barY, 0, 0, fillW, barH, barW, barH);
		}
		int manaTextX = barX + barW + 4;
		int manaTextY = barY + 1;
		ctx.drawText(mc.textRenderer, Text.literal(mana + "/" + maxMana), manaTextX, manaTextY, 0xC8B0FF, true);
		// 当前法术耗蓝以小字号 (-N) 显示在蓝量数字下方，共用左边缘。
		if (snap.manaCost >= 0) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(manaTextX, manaTextY + mc.textRenderer.fontHeight + 2, 0);
			ctx.getMatrices().scale(MANA_COST_TEXT_SCALE, MANA_COST_TEXT_SCALE, 1.0f);
			ctx.drawText(mc.textRenderer, Text.literal("(-" + snap.manaCost + ")"), 0, 0,
					snap.manaCost > mana ? 0xFF7777 : 0xC8B0FF, true);
			ctx.getMatrices().pop();
		}

		// 三槽：中槽(22)居中，左右小槽(16)相对中槽对称分布，间隙均为 4px（卷轴读快照，帧间零 NBT）
		drawSlot(ctx, mc, book, prev, baseX, baseY + 3, 16, false, snap.prevScroll);
		drawSlot(ctx, mc, book, next, baseX + 46, baseY + 3, 16, false, snap.nextScroll);
		drawSlot(ctx, mc, book, sel, baseX + 20, baseY, 22, true, snap.selScroll);

		RenderSystem.disableBlend();

		// 当前魔法名（白色普通文字，三槽正下方居中显示，可左右超出范围）+ 剩余 cd + 施放档位
		ItemStack scroll = snap.selScroll;
		Spell spell = ScrollData.getSpell(scroll);
		if (spell != null) {
			int centerX = baseX + 31; // 选择器中心（= 中槽中心）
			int nameY = baseY + 26;
			Text name = SpellCastFeedback.spellName(spell, scroll);
			int nameW = mc.textRenderer.getWidth(name);
			int nameX = clampTextX(mc, centerX - nameW / 2, nameW);
			ctx.drawText(mc.textRenderer, name, nameX, nameY, 0xFFFFFF, true);
			int textEndX = nameX + nameW + 4;
			// 降档时名字后追加蓝色「Lv档/级」角标（低阶施放可见性，阶段 C §6.4）
			int castLevel = net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getCastLevel(scroll);
			int scrollLevel = net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getLevel(scroll);
			if (castLevel < scrollLevel) {
				String tag = "Lv" + castLevel + "/" + scrollLevel;
				ctx.drawText(mc.textRenderer, tag, textEndX, nameY, 0x2A7DFF, true);
				textEndX += mc.textRenderer.getWidth(tag) + 4;
			}
			long cdRem = net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.getEffectiveCooldownEnd(mc.player, scroll) - mc.world.getTime();
			if (cdRem > 0) {
				String cdStr = SpellCastFeedback.seconds(cdRem) + "s";
				ctx.drawText(mc.textRenderer, Text.literal(cdStr).formatted(Formatting.RED),
						textEndX, nameY, 0xFFFFFF, true);
			}
		}
	}

	private static int clampTextX(MinecraftClient mc, int x, int width) {
		return Math.max(2, Math.min(x, mc.getWindow().getScaledWidth() - width - 2));
	}

	/** 卷轴由调用方从 tick 快照传入（slot 参数仅用于日志/调试语义，不再现查 NBT）。 */
	private void drawSlot(DrawContext ctx, MinecraftClient mc, ItemStack book, int slot, int x, int y, int size, boolean big,
			ItemStack scroll) {
		Spell spell = ScrollData.getSpell(scroll);
		int fs = size + 2; // 含 1px 外框，在内容区左上外扩 1px 绘制
		// 固定三层（底→顶）：空白 → 技能图标(有魔法才画) → 有东西
		// 底层：空白空槽贴图（始终画）
		ctx.drawTexture(big ? TEX_SLOT_BIG_EMPTY : TEX_SLOT_EMPTY, x - 1, y - 1, 0, 0, fs, fs, fs, fs);
		// 中层：技能图标（有魔法才画）。优先用魔法专用 32×32 图标整张映射到槽内容区。
		// 必须用 11 参重载（显式指定源区域 32×32 → 目标 size×size）实现等比缩放（最近邻保像素风），
		// 且源区域与贴图实际尺寸必须一致（图标贴图已全部升级为 32×32）；
		// 9 参重载的最后两参是整张贴图尺寸，传错会因 UV 越界把贴图平铺成 4 张拼图。
		// 无专用图标时回落画卷轴物品本身（原 drawItem 固定 16×16）。
		boolean hasSpell = !scroll.isEmpty();
		if (hasSpell) {
			net.minecraft.util.Identifier iconTex = spell != null ? spell.getIconTexture() : null;
			if (iconTex != null) {
				ctx.drawTexture(iconTex, x, y, size, size, 0, 0, 32, 32, 32, 32);
			} else {
				int iconX = x + (size - 16) / 2;
				int iconY = y + (size - 16) / 2;
				ctx.drawItem(scroll, iconX, iconY);
			}
		}
		// 顶层：品质覆盖层 + 冷却遮罩，都要压在图标上方，故整体抬高 z 再绘制
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 260);
		// 冷却遮罩与数字共用玩家级共享 CD；同名不同等级卷轴立即显示同一截止时间。
		if (spell != null && spell.getBaseCooldownTicks() > 0) {
			long cdRem = Math.max(0L, net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.getEffectiveCooldownEnd(mc.player, scroll) - mc.world.getTime());
			if (cdRem > 0) {
				// 分母用等级后实际 CD（等级 CD 缩减后若仍用基础 CD，遮罩比例会偏小、退得比真实慢）；
			// 阶段 B：再乘法阵 CD 乘区与耐久比 ×(2−ratio)、叠加双层下限（与服务端实际写入的 CD 同式；
			// 形态亲和乘区客户端不可读，为近似值——精确值由服务端权威结算）
			int level = net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getCastLevel(scroll);
			float ratio = net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getDurabilityRatio(scroll);
			float levelCd = spell.getBaseCooldownTicks() * spell.getCooldownMultiplier(level);
			float formationCdMul = net.jackcooper.shapeShifterCurseAddon.spell.FormationData
					.sumCooldownMultiplier(book, spell.getElement());
			int totalCd = Math.max(Math.round(levelCd * (2.0f - ratio) * formationCdMul),
					Math.max(spell.getCooldownFloorTicks(),
						Math.round(levelCd * net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.RELATIVE_CD_FLOOR)));
				totalCd=net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme.modifiers(mc.player,scroll,level).cooldown(totalCd);
                int authoritativeTotal=net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.getClientCooldownTotal(spell,net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getCooldownEnd(scroll),scroll.getNbt()==null?0:scroll.getNbt().getInt("RuneCdTotal"));
                if(authoritativeTotal>0)totalCd=authoritativeTotal;
                float frac = totalCd > 0 ? Math.min(1f, cdRem / (float) totalCd) : 1f;
				int maskH = Math.round(size * frac);
				ctx.fill(x, y + size - maskH, x + size, y + size, 0x99000000);
			}
		}
		// 最上层覆盖层：有魔法→按卷轴稀有度选品质框（白/绿/蓝/紫/橙/红，中间透明露出图标）；
		// 无魔法→空白框（filled，即「技能槽 空白」贴图）
		if (hasSpell && spell != null) {
			// 有效品质按等级派生（冰锥：1白/2绿/3蓝/4紫/5橙），HUD 边框颜色一眼看出等级
			int rarity = spell.getRarity(ScrollData.getLevel(scroll)).ordinal();
			Identifier modifiedTex = TEX_SLOT_RARITY_MODIFIED[rarity];
			if (modifiedTex != null && scroll.getNbt() != null && scroll.getNbt().contains(RuneScheme.KEY, NbtElement.COMPOUND_TYPE)) {
				ctx.drawTexture(modifiedTex, x - 1, y - 1, fs, fs, 0, 0, 24, 24, 24, 24);
			} else {
				Identifier rarityTex = (big ? TEX_SLOT_BIG_RARITY : TEX_SLOT_RARITY)[rarity];
				ctx.drawTexture(rarityTex, x - 1, y - 1, 0, 0, fs, fs, fs, fs);
			}
		} else {
			ctx.drawTexture(big ? TEX_SLOT_BIG_FILLED : TEX_SLOT_FILLED, x - 1, y - 1, 0, 0, fs, fs, fs, fs);
		}
		ctx.getMatrices().pop();
	}
}
