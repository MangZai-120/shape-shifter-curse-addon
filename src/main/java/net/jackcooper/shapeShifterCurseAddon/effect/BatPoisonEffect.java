/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

import java.util.UUID;

/**
 * 寄生果蝠次要技能专属「中毒」buff。
 * 效果与原版中毒一致：周期性扣血（amplifier 越高越频繁，最低保留 1 血不致死），
 * 图标/名字复用原版中毒。
 */
public class BatPoisonEffect extends StatusEffect {
    public interface OwnerAccess {
        UUID ssca$getBatPoisonOwner();
        void ssca$setBatPoisonOwner(UUID owner);
    }

    public BatPoisonEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4E9331);
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!entity.getWorld().isClient && entity.getHealth() > 1.0f) {
            UUID owner = ((OwnerAccess) entity).ssca$getBatPoisonOwner();
            var caster = owner == null ? null : entity.getWorld().getPlayerByUuid(owner);
            var sources = entity.getDamageSources();
            entity.damage(caster == null ? sources.magic() : sources.create(DamageTypes.MAGIC, null, caster), 1.0f);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        int i = 25 >> amplifier;
        if (i > 0) {
            return duration % i == 0;
        }
        return true;
    }
}
