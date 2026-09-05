package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
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

	// 槽位双状态贴图：_empty=空槽（无魔法时显示），_filled=「有东西」边框（有魔法时叠在图标上方，中间透明露出图标）
	private static final Identifier TEX_SLOT_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_empty.png");
	private static final Identifier TEX_SLOT_FILLED = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_filled.png");
	private static final Identifier TEX_SLOT_BIG_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_empty.png");
	private static final Identifier TEX_SLOT_BIG_FILLED = new Identifier("ssc_addon", "textures/gui/spell_hud_slot_big_filled.png");
	// 法力条双态贴图：_empty=空条(底,含金框与中心装饰)，_full=满条(按法力%从左裁剪叠上)
	private static final Identifier TEX_BAR_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_hud_bar_empty.png");
	private static final Identifier TEX_BAR_FULL = new Identifier("ssc_addon", "textures/gui/spell_hud_bar_full.png");

	@Override
	public void onHudRender(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.options.hudHidden) {
			return;
		}
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
		// 左右槽 = 沿非空槽推导的上一/下一技能（与 cycleSelected 同逻辑，空槽不参与循环）
		int prev = SpellbookData.nextFilledSlot(book, sel, -1);
		int next = SpellbookData.nextFilledSlot(book, sel, +1);
		if (prev < 0 || next < 0) {
			return; // 全空：不画选择器
		}

		// HUD 整体位置：由 SSCAddonClientConfig 九宫格锚点 + 偏移决定（BarPositionEditorScreen 可视化编辑）
		// baseX/baseY = 单元逻辑原点；法力条/三槽/魔法名全部相对它布局。默认锚点7(左下)+偏移(16,-52)。
		SSCAddonClientConfig cfg = SSCAddonConfig.client();
		net.minecraft.util.Pair<Integer, Integer> sbAnchor = UIPositionUtils.getCorrectPosition(
				cfg.spellbookHudPosType, cfg.spellbookHudPosOffsetX, cfg.spellbookHudPosOffsetY);
		int baseX = sbAnchor.getLeft();
		int baseY = sbAnchor.getRight();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		// 法力条：空条(底,含金框与中心装饰)始终画满宽，满条按法力%从左裁剪叠上
		int mana = SpellbookData.getMana(book);
		int maxMana = SpellbookData.getMaxMana(book);
		int barW = 76, barH = 10;
		int barX = baseX - 7, barY = baseY - 14; // 76px 条相对三槽(中心 baseX+31)居中：left=中心-38=baseX-7
		ctx.drawTexture(TEX_BAR_EMPTY, barX, barY, 0, 0, barW, barH, barW, barH);
		int fillW = maxMana > 0 ? (int) ((long) barW * mana / maxMana) : 0;
		if (fillW > 0) {
			ctx.drawTexture(TEX_BAR_FULL, barX, barY, 0, 0, fillW, barH, barW, barH);
		}
		ctx.drawText(mc.textRenderer, Text.literal(mana + "/" + maxMana), barX + barW + 4, barY + 1, 0xC8B0FF, true);

		// 三槽：中槽(22)居中，左右小槽(16)相对中槽对称分布，间隙均为 4px
		drawSlot(ctx, mc, book, prev, baseX, baseY + 3, 16, false);
		drawSlot(ctx, mc, book, next, baseX + 46, baseY + 3, 16, false);
		drawSlot(ctx, mc, book, sel, baseX + 20, baseY, 22, true);

		RenderSystem.disableBlend();

		// 当前魔法名（白色普通文字，三槽正下方居中显示，可左右超出范围）+ 剩余 cd
		ItemStack scroll = SpellbookData.getScroll(book, sel);
		Spell spell = ScrollData.getSpell(scroll);
		if (spell != null) {
			int centerX = baseX + 31; // 选择器中心（= 中槽中心）
			int nameY = baseY + 26;
			Text name = Text.translatable(spell.getNameKey());
			int nameW = mc.textRenderer.getWidth(name);
			int nameX = centerX - nameW / 2; // 居中锚点，长名向左右自然溢出、不裁剪
			ctx.drawText(mc.textRenderer, name, nameX, nameY, 0xFFFFFF, true);
			long cdRem = SpellbookData.getCooldownRemaining(book, sel, mc.world);
			if (cdRem > 0) {
				String cdStr = String.format("%.1fs", cdRem / 20.0);
				ctx.drawText(mc.textRenderer, Text.literal(cdStr).formatted(Formatting.RED),
						nameX + nameW + 4, nameY, 0xFFFFFF, true);
			}
		}
	}

	private void drawSlot(DrawContext ctx, MinecraftClient mc, ItemStack book, int slot, int x, int y, int size, boolean big) {
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		Spell spell = ScrollData.getSpell(scroll);
		int fs = size + 2; // 含 1px 外框，在内容区左上外扩 1px 绘制
		// 固定三层（底→顶）：空白 → 技能图标(有魔法才画) → 有东西
		// 底层：空白空槽贴图（始终画）
		ctx.drawTexture(big ? TEX_SLOT_BIG_EMPTY : TEX_SLOT_EMPTY, x - 1, y - 1, 0, 0, fs, fs, fs, fs);
		// 中层：技能图标（有魔法才画）。优先用魔法专用 16×16 图标整张映射到槽内容区。
		// 必须用 11 参重载（显式指定源区域 16×16 → 目标 size×size）实现等比放大（最近邻保像素风）；
		// 9 参重载的最后两参是整张贴图尺寸，传错会因 UV 越界把 16×16 平铺成 4 张拼图。
		// 无专用图标时回落画卷轴物品本身（原 drawItem 固定 16×16）。
		boolean hasSpell = !scroll.isEmpty();
		if (hasSpell) {
			net.minecraft.util.Identifier iconTex = spell != null ? spell.getIconTexture() : null;
			if (iconTex != null) {
				ctx.drawTexture(iconTex, x, y, size, size, 0, 0, 16, 16, 16, 16);
			} else {
				int iconX = x + (size - 16) / 2;
				int iconY = y + (size - 16) / 2;
				ctx.drawItem(scroll, iconX, iconY);
			}
		}
		// 顶层：有东西边框（始终画）+ 冷却遮罩，都要压在图标上方，故整体抬高 z 再绘制
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 260);
		// 冷却遮罩：三个槽各自独立显示（冷却是按槽存书 NBT 的，切槽后原槽 cd 仍在走，
		// 切回/切走都应能看到对应槽的剩余冷却从上往下退去）
		if (spell != null && spell.getBaseCooldownTicks() > 0) {
			long cdRem = SpellbookData.getCooldownRemaining(book, slot, mc.world);
			if (cdRem > 0) {
				float frac = Math.min(1f, cdRem / (float) spell.getBaseCooldownTicks());
				int maskH = Math.round(size * frac);
				ctx.fill(x, y + size - maskH, x + size, y + size, 0x99000000);
			}
		}
		// 有东西边框叠在最上层（边框中间透明，露出下方的图标 / 空白）
		ctx.drawTexture(big ? TEX_SLOT_BIG_FILLED : TEX_SLOT_FILLED, x - 1, y - 1, 0, 0, fs, fs, fs, fs);
		ctx.getMatrices().pop();
	}
}
