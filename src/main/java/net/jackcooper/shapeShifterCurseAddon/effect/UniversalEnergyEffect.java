package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem;
import org.jetbrains.annotations.Nullable;

/**
 * 通用能量药水的瞬时效果载体（jackcooper）：仅用于喷溅 / 滞留型的原版投掷药水弹射物。
 *
 * <p>饮用型不走效果（直接 finishUsing 服务端结算）；喷溅 / 滞留型投掷时生成携带本效果的原版
 * {@code PotionEntity}，落点 AOE 逐实体触发 {@link #applyInstantEffect}，距离衰减后按
 * {@link UniversalEnergyPotionItem#canRestore}/{@code restoreMana} 同源逻辑回复。
 * 判定与数值完全复用饮用型静态方法，保证喷溅 / 滞留 / 饮用三型行为一致。
 * 实现参照原版 SSC {@code FeedEffect}（isInstant + applyInstantEffect + proximity 衰减）。
 */
public class UniversalEnergyEffect extends StatusEffect {

	/** 瓶身 / 粒子着色：与饮用型贴图的青蓝色系一致 */
	public static final int POTION_COLOR = 0x3FD8D0;

	public UniversalEnergyEffect() {
		super(StatusEffectCategory.BENEFICIAL, POTION_COLOR);
	}

	@Override
	public boolean isInstant() {
		return true;
	}

	@Override
	public void applyInstantEffect(@Nullable Entity source, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
		// 仅服务端玩家可吸收能量（喷溅/滞留 AOE 由原版 PotionEntity 在服务端触发；与饮用型只认玩家同源）
		if (!(target instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
			return;
		}
		if (!UniversalEnergyPotionItem.canRestore(player)) {
			return;
		}
		// 距离衰减：爆炸中心 1.0 → 边缘 0.0，下限 0.5（与原版 FeedEffect 同参）
		double distanceMultiplier = Math.max(0.5, proximity);
		double restore = UniversalEnergyPotionItem.MANA_RESTORE * distanceMultiplier;
		UniversalEnergyPotionItem.restoreManaScaled(player, restore);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		// 瞬时效果：不随 tick 结算（滞留云会以 instant 方式周期性调用 applyInstantEffect）
		return false;
	}
}
