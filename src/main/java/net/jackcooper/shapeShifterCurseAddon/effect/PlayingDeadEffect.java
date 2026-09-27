package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;

public class PlayingDeadEffect extends StatusEffect {
	// 装死回血：每 10 tick 结算一次，6 秒(120t)≈ 12 次
	// 默认：总回 15 颗心(30HP) → 每次 2.5 HP
	// 戴活珊瑚项链：总回 5 颗心(10HP) + 20 颗黄心(40吸收HP) → 每次 0.834HP + 黄心累积 3.34 封顶 40
	private static final float DEFAULT_HEAL_PER_TICK = 30.0f / 12.0f;
	private static final float NECKLACE_HEAL_PER_TICK = 10.0f / 12.0f;
	private static final float NECKLACE_ABSORB_PER_TICK = 40.0f / 12.0f;
	private static final float NECKLACE_ABSORB_MAX = 40.0f;

	// 阶段 5：运行时快照读取（abilities.playing_dead；快照未初始化回退默认常量）。
	// 默认值与 float 常量逐位一致（2.5 / 0.8333333 / 3.3333333 / 40.0）。
	private static final BalanceReader BAL = new BalanceReader("abilities.playing_dead");

	public PlayingDeadEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0x586e7c);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity == null || entity.isDead()) {
			return;
		}
		// Use SWIMMING pose which forces the model to lie flat (crawl animation) on client side
		entity.setPose(EntityPose.SWIMMING);
		entity.setSwimming(true);
		entity.setVelocity(0, entity.getVelocity().y, 0);
		entity.velocityModified = true;

		// 回血结算（仅服务端，每 10 tick）
		if (entity.getWorld().isClient() || entity.age % 10 != 0) {
			return;
		}
		boolean hasNecklace = false;
		if (entity instanceof PlayerEntity) {
			hasNecklace = TrinketUtils.isWearing(entity,
					net.jackcooper.shapeShifterCurseAddon.SscAddon.ACTIVE_CORAL_NECKLACE);
		}
		if (hasNecklace) {
			entity.heal((float) BAL.d("necklace_heal_per_tick", NECKLACE_HEAL_PER_TICK));
			float cur = entity.getAbsorptionAmount();
			float next = Math.min((float) BAL.d("necklace_absorb_max", NECKLACE_ABSORB_MAX), cur + (float) BAL.d("necklace_absorb_per_tick", NECKLACE_ABSORB_PER_TICK));
			entity.setAbsorptionAmount(next);
			// 记录「装死给的黄心」增量，供 30s 存留后衰减（仅这部分会衰减，其它来源不动）
			if (entity instanceof PlayerEntity p) {
				net.jackcooper.shapeShifterCurseAddon.ability.PlayDeadAbsorptionManager.addAbsorption(p, next - cur);
			}
		} else {
			entity.heal((float) BAL.d("heal_per_tick", DEFAULT_HEAL_PER_TICK));
		}
	}
}
