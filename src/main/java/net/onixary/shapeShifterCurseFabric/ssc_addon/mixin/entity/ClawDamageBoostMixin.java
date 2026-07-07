package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 风灵「疾风连爪」+ 副技能：缩放风灵**徒手/形态**近战伤害。
 *
 * 从**受击侧**（{@code LivingEntity.damage}）拦截，兼容整合包里 BetterCombat 等改攻击流程的 mod。
 * 仅当：攻击者是风灵形态玩家 + 伤害类型是玩家近战(player_attack) + 主手为空(徒手) 时，
 * 按 {@code WindSpiritClawManager.getNormalMeleeMultiplier} 缩放：
 * - 过热回复期 → 0~90%（弱普攻）；
 * - 副技能 buff 期 → ×1.5（疾风连爪 / 普攻都吃）。
 *
 * 这样同时覆盖：连续爪击伤害（爪击期倍率=1×buff）、回复期弱普攻、副技能增伤。
 * 不含弓箭/药水/手持武器（主手非空即跳过）。
 */
@Mixin(LivingEntity.class)
public class ClawDamageBoostMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ssc_addon$scaleWindSpiritMelee(float value, DamageSource source, float amount) {
        if (source != null
                && source.getAttacker() instanceof ServerPlayerEntity p
                && source.isOf(DamageTypes.PLAYER_ATTACK)
                && p.getMainHandStack().isEmpty()
                && FormUtils.isOcelotSP(p)) {
            float mult = WindSpiritClawManager.getNormalMeleeMultiplier(p);
            if (mult != 1.0f) {
                return value * mult;
            }
        }
        return value;
    }
}
