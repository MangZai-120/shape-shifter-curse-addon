/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ClientResourceCache;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ManaBarPos;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 寄生果蝠形态种子量能量条 HUD 渲染。
 * 复用主模组魔力条 UI 位置配置（posType=8, offsetX=100, offsetY=-17），与雪狐 / 悦灵 / 吸血蝙蝠等能量条对齐到同一高度；
 * 该形态无魔力条，位置不与其他能量条冲突。
 */
@Environment(EnvType.CLIENT)
public final class SeedEnergyHudRenderer implements HudRenderCallback {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/bat_parasitic_fruit_seed_bar_empty.png");
    private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/bat_parasitic_fruit_seed_bar_full.png");
    /** 原图尺寸：86 像素宽 × 5 像素高，10 个圆点等距分布。 */
    private static final int TEX_WIDTH = 86;
    private static final int TEX_HEIGHT = 5;

    public static void register() {
        HudRenderCallback.EVENT.register(new SeedEnergyHudRenderer());
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (MC.options.hudHidden || MC.player == null) return;
        PlayerEntity player = MC.player;
        // 必须真正拥有该 power 才渲染，避免对其他形态/未变形玩家误显示
        if (!ClientResourceCache.has(player, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY)) return;

        int[] valMax = PowerUtils.getClientResourceValueAndMax(player, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
        int current = valMax[0];
        int max = valMax[1];
        if (max <= 0) return;
        double percent = (double) current / (double) max;

        // 与其他能量条同步使用主模组的 manaBar 位置配置
        int[] mp = ManaBarPos.get(8, 100, -17);
        int posType = mp[0];
        int offsetX = mp[1];
        int offsetY = mp[2];
        Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(posType, offsetX, offsetY);
        // X 水平居中屏幕，Y 沿用魔力条配置（与九命条一致的中心对称）
        int x = (MC.getWindow().getScaledWidth() - TEX_WIDTH) / 2;
        int y = pos.getRight();

        // 底层：空贴图
        context.drawTexture(TEX_EMPTY, x, y, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        // 顶层：满贴图按 current/max 比例横向裁剪覆盖
        if (current > 0) {
            int filledWidth = (int) Math.ceil(TEX_WIDTH * percent);
            filledWidth = Math.max(0, Math.min(TEX_WIDTH, filledWidth));
            if (filledWidth > 0) {
                context.drawTexture(TEX_FULL, x, y, 0, 0, filledWidth, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            }
        }
    }
}
