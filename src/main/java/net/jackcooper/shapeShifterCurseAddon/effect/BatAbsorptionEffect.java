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
 * 黄心的累加 / 持续 / 到期清除由 {@code ParasiticAbsorptionManager} 管理，本效果仅提供状态栏图标与名字。
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
