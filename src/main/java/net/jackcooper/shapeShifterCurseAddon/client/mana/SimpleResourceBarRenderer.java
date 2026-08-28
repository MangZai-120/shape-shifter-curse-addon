/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.jackcooper.shapeShifterCurseAddon.util.ClientResourceCache;
import net.jackcooper.shapeShifterCurseAddon.util.ManaBarPos;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 简单资源条 HUD 渲染基类（参数化模板）。
 *
 * <p>统一「读取 resource power → 按百分比横向裁剪双层贴图」的能量条渲染模式，
 * 供悦灵/雪狐/阿努比斯/吸血蝙蝠/寄生果蝠/朔望等条复用。子类只需提供：
 * <ul>
 *   <li>{@link #resourceId()}：资源 power id；</li>
 *   <li>{@link #texFull()} / {@link #texEmpty()}：满/空两层贴图；</li>
 *   <li>{@link #width()} / {@link #height()}：贴图尺寸（默认 80×5）。</li>
 * </ul>
 * 位置默认走主模组魔力条配置（ManaBarPos.get(8, 100, -17)），如需特殊布局可覆写
 * {@link #barX(Pair, int)} / {@link #barY(Pair)}；如需「持有 power 才渲染」语义可覆写
 * {@link #requirePower()}（默认 false = 资源为空时隐藏，与旧实现一致）。</p>
 */
@Environment(EnvType.CLIENT)
public abstract class SimpleResourceBarRenderer implements net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback {
	private static final MinecraftClient MC = MinecraftClient.getInstance();

	/** 资源 power id。 */
	protected abstract Identifier resourceId();

	/** 满层贴图（按百分比横向裁剪覆盖在空层上）。 */
	protected abstract Identifier texFull();

	/** 空层贴图（底层）。 */
	protected abstract Identifier texEmpty();

	/** 贴图宽（像素），默认 80。 */
	protected int width() {
		return 80;
	}

	/** 贴图高（像素），默认 5。 */
	protected int height() {
		return 5;
	}

	/** true = 必须持有该 power 才渲染（用 ClientResourceCache.has 判定）；false = 资源为空时隐藏。 */
	protected boolean requirePower() {
		return false;
	}

	/** 魔力条位置配置参数（posType, offsetX, offsetY），默认与主模组魔力条一致。 */
	protected int[] manaBarPosArgs() {
		return ManaBarPos.get(8, 100, -17);
	}

	/** 条左上角 X（默认直接取布局结果；覆写可实现水平居中等特殊布局）。 */
	protected int barX(Pair<Integer, Integer> pos) {
		return pos.getLeft();
	}

	/** 条左上角 Y（默认直接取布局结果；覆写可实现上移/居中等特殊布局）。 */
	protected int barY(Pair<Integer, Integer> pos) {
		return pos.getRight();
	}

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		if (MC.options.hudHidden || MC.player == null) return;
		PlayerEntity player = MC.player;

		if (requirePower() && !ClientResourceCache.has(player, resourceId())) return;

		int[] valMax = PowerUtils.getClientResourceValueAndMax(player, resourceId());
		int current = valMax[0];
		int max = valMax[1];
		if (!requirePower()) {
			// 旧语义：无该资源（current=0 且 max<=1）时不渲染
			if (current <= 0 && max <= 1) return;
		}
		if (current < 0) current = 0;
		if (max <= 0) return;
		double percent = (double) current / (double) max;

		int[] mp = manaBarPosArgs();
		Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(mp[0], mp[1], mp[2]);
		int x = barX(pos);
		int y = barY(pos);

		int w = width();
		int h = height();
		// 底层：空槽
		context.drawTexture(texEmpty(), x, y, 0, 0, w, h, w, h);
		// 顶层：满层按百分比横向裁剪
		if (current > 0) {
			int filledWidth = (int) Math.ceil(w * percent);
			filledWidth = Math.max(0, Math.min(w, filledWidth));
			if (filledWidth > 0) {
				context.drawTexture(texFull(), x, y, 0, 0, filledWidth, h, w, h);
			}
		}
	}
}
