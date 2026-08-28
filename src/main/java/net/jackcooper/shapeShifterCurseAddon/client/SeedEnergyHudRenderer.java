/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.client.mana.SimpleResourceBarRenderer;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

/**
 * 寄生果蝠形态种子量能量条 HUD 渲染。
 * 复用主模组魔力条 UI 位置配置，与雪狐 / 悦灵 / 吸血蝙蝠等能量条对齐到同一高度；
 * 该形态无魔力条，位置不与其他能量条冲突。
 */
@Environment(EnvType.CLIENT)
public final class SeedEnergyHudRenderer extends SimpleResourceBarRenderer {
    private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/bat_parasitic_fruit_seed_bar_empty.png");
    private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/bat_parasitic_fruit_seed_bar_full.png");
    /** 原图尺寸：86 像素宽 × 5 像素高，10 个圆点等距分布。 */
    private static final int TEX_WIDTH = 86;

    public static void register() {
        HudRenderCallback.EVENT.register(new SeedEnergyHudRenderer());
    }

    @Override
    protected Identifier resourceId() {
        return FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY;
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

    /** 必须真正拥有该 power 才渲染，避免对其他形态/未变形玩家误显示。 */
    @Override
    protected boolean requirePower() {
        return true;
    }
}
