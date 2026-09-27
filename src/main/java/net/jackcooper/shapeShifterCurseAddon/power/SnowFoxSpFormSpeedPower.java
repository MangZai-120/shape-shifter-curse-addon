package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class SnowFoxSpFormSpeedPower extends Power {

	private static final UUID MELEE_SPEED_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
	private static final UUID RANGED_SPEED_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

	private final Identifier resourceId;
	private final double meleeSpeedBonus;
	private final double rangedSpeedPenalty;

	private int lastSwitchState = -1;
	// 阶段 7：balance 配置代数（重载后强制重挂修饰符，同姿态也刷新——修「重载改加成不生效」）
	private long lastBalanceRevision = Long.MIN_VALUE;

	public SnowFoxSpFormSpeedPower(PowerType<?> type, LivingEntity entity, Identifier resourceId, double meleeSpeedBonus, double rangedSpeedPenalty) {
		super(type, entity);
		this.resourceId = resourceId;
		this.meleeSpeedBonus = meleeSpeedBonus;
		this.rangedSpeedPenalty = rangedSpeedPenalty;
		this.setTicking(true);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(
				new Identifier("ssc_addon", "snow_fox_sp_form_speed"),
				new SerializableData()
						.add("resource", SerializableDataTypes.IDENTIFIER)
						.add("melee_speed_bonus", SerializableDataTypes.DOUBLE, 0.1)
						.add("ranged_speed_penalty", SerializableDataTypes.DOUBLE, -0.1),
				data -> (type, entity) -> new SnowFoxSpFormSpeedPower(
						type,
						entity,
						data.getId("resource"),
						data.getDouble("melee_speed_bonus"),
						data.getDouble("ranged_speed_penalty")
				)
		).allowCondition();
	}

	@Override
	public void tick() {
		if (entity.getWorld().isClient()) return;

		int currentState = getSwitchState();
		long balanceRevision = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.serverRevision();

		if (currentState != lastSwitchState || balanceRevision != lastBalanceRevision) {
			updateSpeedModifier(currentState);
			lastSwitchState = currentState;
			lastBalanceRevision = balanceRevision;
		}
	}

	private int getSwitchState() {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(entity);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			// Resource not found, default to melee state
		}
		return 0;
	}

	private void updateSpeedModifier(int state) {
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr == null) return;

		// Remove existing modifiers
		speedAttr.removeModifier(MELEE_SPEED_UUID);
		speedAttr.removeModifier(RANGED_SPEED_UUID);

		// 阶段 7：加成值每次从 balance 快照读取（power JSON 字段仍是构造默认）；
		// 重载后同姿态重挂即生效——tick 检测到「配置代数变化」时强制刷新（见 tick 内 revision 对比）
        var balance = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.serverSnapshot();
        double meleeBonus = balance != null && balance.overridesPower("forms.snow_fox_sp", "melee_speed_bonus")
                ? balance.getDouble("forms.snow_fox_sp", "melee_speed_bonus") : meleeSpeedBonus;
        double rangedPenalty = balance != null && balance.overridesPower("forms.snow_fox_sp", "ranged_speed_penalty")
                ? balance.getDouble("forms.snow_fox_sp", "ranged_speed_penalty") : rangedSpeedPenalty;

		if (state == 0) {
			// Melee state（快照可覆盖加成值）
			EntityAttributeModifier meleeModifier = new EntityAttributeModifier(
					MELEE_SPEED_UUID,
					"Snow Fox SP Melee Speed",
					meleeBonus,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL
			);
			speedAttr.addTemporaryModifier(meleeModifier);
		} else if (state == 1) {
			// Ranged state（快照可覆盖惩罚值）
			EntityAttributeModifier rangedModifier = new EntityAttributeModifier(
					RANGED_SPEED_UUID,
					"Snow Fox SP Ranged Speed",
					rangedPenalty,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL
			);
			speedAttr.addTemporaryModifier(rangedModifier);
		}
	}

	@Override
	public void onRemoved() {
		super.onRemoved();
		// Clean up modifiers when power is removed
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(MELEE_SPEED_UUID);
			speedAttr.removeModifier(RANGED_SPEED_UUID);
		}
	}
}
