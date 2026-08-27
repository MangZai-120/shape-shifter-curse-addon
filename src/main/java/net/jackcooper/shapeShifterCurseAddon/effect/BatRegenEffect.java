/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 寄生果蝠专属「生命恢复」buff。
 * 回血在施加瞬间由调用方一次性完成（{@code target.heal}，触发血条回血动画），
 * 本效果不周期回血（canApplyUpdateEffect 恒 false），仅提供状态栏图标与名字显示，
 * 因此即使带持续时间也「只回一次」。图标/名字复用原版生命恢复。
 */
public class BatRegenEffect extends StatusEffect {
    public BatRegenEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xCD5CAB);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
