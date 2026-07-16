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
 * 朔望「九命」剩余命数 HUD 渲染（9 格圆点条，仿寄生果蝠种子条分层贴图）。
 * 素材由种子条裁剪得到（86px/10 格 → 77px/9 格）。位置复用主模组魔力条配置，与其它能量条对齐同一高度。
 */
@Environment(EnvType.CLIENT)
public final class NineLivesHudRenderer implements HudRenderCallback {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/form_ocelot_nova_nine_lives_empty.png");
    private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/form_ocelot_nova_nine_lives_full.png");
    /** 命数条尺寸：76 像素宽 × 5 像素高（与实际贴图一致，居中按此宽度计算）。 */
    private static final int TEX_WIDTH = 76;
    private static final int TEX_HEIGHT = 5;

    public static void register() {
        HudRenderCallback.EVENT.register(new NineLivesHudRenderer());
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (MC.options.hudHidden || MC.player == null) return;
        PlayerEntity player = MC.player;
        if (!ClientResourceCache.has(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES)) return;

        int[] valMax = PowerUtils.getClientResourceValueAndMax(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES);
        int current = valMax[0];
        int max = valMax[1];
        if (max <= 0) return;
        double percent = (double) current / (double) max;

        // Y 沿用主模组魔力条配置（与其它能量条同高）；X 水平居中屏幕（修正左右不对称）
        int[] mp = ManaBarPos.get(8, 100, -17);
        int posType = mp[0];
        int offsetX = mp[1];
        int offsetY = mp[2];
        Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(posType, offsetX, offsetY);
        int x = (MC.getWindow().getScaledWidth() - TEX_WIDTH) / 2;
        int y = pos.getRight();

        // 底层：空条（9 格空圆点）
        context.drawTexture(TEX_EMPTY, x, y, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        // 顶层：满条按 current/max 比例横向裁剪覆盖
        if (current > 0) {
            int filledWidth = (int) Math.ceil(TEX_WIDTH * percent);
            filledWidth = Math.max(0, Math.min(TEX_WIDTH, filledWidth));
            if (filledWidth > 0) {
                context.drawTexture(TEX_FULL, x, y, 0, 0, filledWidth, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            }
        }
    }
}
