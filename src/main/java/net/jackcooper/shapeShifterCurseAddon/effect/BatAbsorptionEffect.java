/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 寄生果蝠主技能专属「伤害吸收」buff（纯显示）。
 * 黄心预算和绝对结束时间随目标存档，由 ParasiticAbsorptionMixin 到期撤销；本效果提供状态栏图标与名字。
 * 图标 / 名字复用原版伤害吸收。
 */
public class BatAbsorptionEffect extends StatusEffect {
    public BatAbsorptionEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x2552A5);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
