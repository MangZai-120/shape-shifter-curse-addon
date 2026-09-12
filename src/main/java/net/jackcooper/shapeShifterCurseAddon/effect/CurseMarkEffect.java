package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 诅咒标记（jackcooper，诅咒系法术）：标记期间受到的所有伤害加深（每级 +10%，L1=+20% … L5=+60%）。
 * 实际伤害加深由 {@code SscAddonLivingEntityMixin} 的 damage ModifyVariable 拦截实现
 * （带此效果时 amount × (1.2 + 0.1×amplifier)；amplifier 由施法时按等级写入，L1=0 … L5=4）；
 * 本类只作可视化标记（粒子 + 图标）。
 *
 * <p>可被月辉系净化（月华治愈移除负面效果时一并清除——HARMFUL 类别天然可清）。</p>
 */
public class CurseMarkEffect extends StatusEffect {

	/** 每级额外加深幅度（与 mixin 内保持一致；基础 +20% 在两者内同源）。 */
	public static final float BONUS_PER_LEVEL = 0.1f;
	/** 基础加深幅度（L1 即 +20%）。 */
	public static final float BASE_BONUS = 0.2f;

	/** 按等级计算总加深倍率（L1=1.2 … L5=1.6）。 */
	public static float multiplierForLevel(int level) {
		return 1.0f + BASE_BONUS + BONUS_PER_LEVEL * (Math.max(1, Math.min(5, level)) - 1);
	}

	public CurseMarkEffect() {
		// 暗紫主题色（与诅咒系 FormationElement.CURSE 0x7B2FBE 一致）
		super(StatusEffectCategory.HARMFUL, 0x7B2FBE);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		// 周期粒子：暗紫咒纹环绕（服务端撒，天然多人同步）
		if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld
				&& entity.age % 20 == 0) {
			serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
					entity.getX(), entity.getBodyY(0.5), entity.getZ(), 2, 0.3, 0.4, 0.3, 0.01);
		}
	}
}
