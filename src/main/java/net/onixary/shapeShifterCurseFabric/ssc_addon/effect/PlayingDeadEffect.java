package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;

public class PlayingDeadEffect extends StatusEffect {
	// 装死回血：每 10 tick 结算一次，6 秒(120t)≈ 12 次
	// 默认：总回 15 颗心(30HP) → 每次 2.5 HP
	// 戴活珊瑚项链：总回 5 颗心(10HP) + 20 颗黄心(40吸收HP) → 每次 0.834HP + 黄心累积 3.34 封顶 40
	private static final float DEFAULT_HEAL_PER_TICK = 30.0f / 12.0f;
	private static final float NECKLACE_HEAL_PER_TICK = 10.0f / 12.0f;
	private static final float NECKLACE_ABSORB_PER_TICK = 40.0f / 12.0f;
	private static final float NECKLACE_ABSORB_MAX = 40.0f;

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
		if (entity instanceof PlayerEntity player) {
			try {
				hasNecklace = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player)
						.map(c -> c.isEquipped(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.ACTIVE_CORAL_NECKLACE))
						.orElse(false);
			} catch (Throwable ignored) {
			}
		}
		if (hasNecklace) {
			entity.heal(NECKLACE_HEAL_PER_TICK);
			float cur = entity.getAbsorptionAmount();
			float next = Math.min(NECKLACE_ABSORB_MAX, cur + NECKLACE_ABSORB_PER_TICK);
			entity.setAbsorptionAmount(next);
			// 记录「装死给的黄心」增量，供 30s 存留后衰减（仅这部分会衰减，其它来源不动）
			if (entity instanceof PlayerEntity p) {
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.PlayDeadAbsorptionManager.addAbsorption(p, next - cur);
			}
		} else {
			entity.heal(DEFAULT_HEAL_PER_TICK);
		}
	}
}
