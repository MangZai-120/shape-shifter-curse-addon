package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.BindingAnkletItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 契灵·绑定脚环灵气效果：
 * 当某只生物被劫掠阵营 NPC（{@link BindingAnkletItem#isRaiderFaction}）攻击，
 * 且攻击者 16 格范围内存在装备绑定脚环的契灵玩家时，将本次伤害放大 1.2 倍。
 *
 * 在 LivingEntity#damage 的 HEAD 处 ModifyVariable 修改 amount 参数；
 * 服务端线程才会判定（{@link BindingAnkletItem#hasAnkletAuraNearby} 会过滤客户端），
 * 多人环境下伤害结算在服务器进行，客户端不会重复触发。
 */
@Mixin(LivingEntity.class)
public abstract class BindingAnkletAuraMixin {

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float ssc_addon$bindingAnkletBoost(float amount, DamageSource source) {
		if (amount <= 0.0F) return amount;
		Entity raw = source.getAttacker();
		if (!(raw instanceof LivingEntity attacker)) return amount;
		if (!BindingAnkletItem.isRaiderFaction(attacker)) return amount;
		if (!BindingAnkletItem.hasAnkletAuraNearby(attacker)) return amount;
		return amount * BindingAnkletItem.DAMAGE_MULTIPLIER;
	}
}
