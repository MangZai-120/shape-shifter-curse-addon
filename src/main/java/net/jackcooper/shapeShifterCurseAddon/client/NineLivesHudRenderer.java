/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.jackcooper.shapeShifterCurseAddon.client.mana.SimpleResourceBarRenderer;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

/**
 * 朔望「九命」剩余命数 HUD 渲染（9 格圆点条，仿寄生果蝠种子条分层贴图）。
 * 素材由种子条裁剪得到（86px/10 格 → 77px/9 格）。Y 复用主模组魔力条配置与其它能量条同高，X 水平居中屏幕。
 */
@Environment(EnvType.CLIENT)
public final class NineLivesHudRenderer extends SimpleResourceBarRenderer {
    private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/form_ocelot_nova_nine_lives_empty.png");
    private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/form_ocelot_nova_nine_lives_full.png");
    /** 命数条尺寸：76 像素宽 × 5 像素高（与实际贴图一致，居中按此宽度计算）。 */
    private static final int TEX_WIDTH = 76;

    public static void register() {
        HudRenderCallback.EVENT.register(new NineLivesHudRenderer());
    }

    @Override
    protected Identifier resourceId() {
        return FormIdentifiers.OCELOT_NOVA_NINE_LIVES;
    }

    @Override
    protected Identifier texFull() {
        return TEX_FULL;
    }

    @Override
    protected Identifier texEmpty() {
        return TEX_EMPTY;
    }

    @Override
    protected int width() {
        return TEX_WIDTH;
    }

    /** 必须真正拥有该 power 才渲染。 */
    @Override
    protected boolean requirePower() {
        return true;
    }

    /** Y 沿用魔力条布局；X 水平居中屏幕（修正左右不对称）。 */
    @Override
    protected int barX(Pair<Integer, Integer> pos) {
        return (MinecraftClient.getInstance().getWindow().getScaledWidth() - TEX_WIDTH) / 2;
    }
}
