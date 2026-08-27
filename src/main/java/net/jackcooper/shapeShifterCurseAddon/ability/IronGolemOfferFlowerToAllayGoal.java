package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.EnumSet;

public class IronGolemOfferFlowerToAllayGoal extends Goal {
	private static final int OFFER_FLOWER_CHANCE = 8000;
	private static final int OFFER_FLOWER_TICKS = 400;
	private static final TargetPredicate CLOSE_ALLAY_PREDICATE = TargetPredicate.createNonAttackable()
			.setBaseMaxDistance(6.0D)
			.setPredicate(entity -> entity instanceof PlayerEntity player
					&& FormUtils.isAllaySP(player)
					&& !player.isSpectator());

	private final IronGolemEntity golem;
	private PlayerEntity targetAllay;
	private int lookCountdown;

	public IronGolemOfferFlowerToAllayGoal(IronGolemEntity golem) {
		this.golem = golem;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!this.golem.getWorld().isDay()) return false;
		if (this.golem.getRandom().nextInt(OFFER_FLOWER_CHANCE) != 0) return false;

		Box searchBox = this.golem.getBoundingBox().expand(6.0D, 2.0D, 6.0D);
		this.targetAllay = this.golem.getWorld().getClosestEntity(
				PlayerEntity.class,
				CLOSE_ALLAY_PREDICATE,
				this.golem,
				this.golem.getX(),
				this.golem.getY(),
				this.golem.getZ(),
				searchBox
		);
		return this.targetAllay != null;
	}

	@Override
	public boolean shouldContinue() {
		return this.lookCountdown > 0
				&& this.targetAllay != null
				&& this.targetAllay.isAlive()
				&& FormUtils.isAllaySP(this.targetAllay);
	}

	@Override
	public void start() {
		this.lookCountdown = this.getTickCount(OFFER_FLOWER_TICKS);
		this.golem.setLookingAtVillager(true);
	}

	@Override
	public void stop() {
		this.golem.setLookingAtVillager(false);
		this.targetAllay = null;
	}

	@Override
	public void tick() {
		if (this.targetAllay != null) {
			this.golem.getLookControl().lookAt(this.targetAllay, 30.0F, 30.0F);
		}
		--this.lookCountdown;
	}
}
