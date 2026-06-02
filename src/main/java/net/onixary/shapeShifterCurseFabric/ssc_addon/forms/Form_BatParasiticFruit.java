/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Bat3;

/**
 * 寄生果蝠 - 蝙蝠的进化石进化形态。
 * 继承原版三阶段蝙蝠，复用其模型、动画与飞行/倒挂等基础行为，
 * 主要玩法围绕将灵果种子附着在友方或敌方身上提供自适应辅助效果。
 */
public class Form_BatParasiticFruit extends Form_Bat3 {
    public Form_BatParasiticFruit(Identifier formID) {
        super(formID);
    }
}