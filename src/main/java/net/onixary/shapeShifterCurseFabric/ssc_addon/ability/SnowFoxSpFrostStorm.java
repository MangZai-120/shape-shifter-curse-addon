package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
 * SP雪狐远程次要技能 - 冰风暴
 * 1.5秒蓄力后在准星位置释放冰风暴
 * 蓄力期间减少50%准星移动速度
 */
public class SnowFoxSpFrostStorm {

	private static final int CHARGE_TICKS = 30; // 1.5秒蓄力
	private static final double MAX_RANGE = 30.0; // 最大释放距离
	private static final int MANA_COST = 30; // 霜寒值消耗
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
	//未使用: private static final int COOLDOWN = 600;  30秒CD = 600tick
	private static final Identifier REGEN_COOLDOWN_ID = new Identifier("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
	private static final Identifier POWER_ID = new Identifier("my_addon", "form_snow_fox_sp_ranged_secondary");
	private static final Identifier CHARGE_RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_frost_charge");

	private SnowFoxSpFrostStorm() {
		throw new UnsupportedOperationException("This class cannot be instantiated.");
	}

	/**
	 * 开始蓄力（点按技能键时调用）
	 * 注意：冷却由Apoli origins:active_self power的cooldown字段管理
	 * 实际充值由JSON power处理，此方法仅验证并消耗资源
	 */
	public static boolean startCharging(ServerPlayerEntity player) {
		// 检查霜寒值
		int currentMana = getResourceValue(player);
		if (currentMana < MANA_COST) {
			player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
			return false;
		}

		// 消耗霜寒值（在蓄力开始时就消耗）
		changeResourceValue(player, -MANA_COST);
		setRegenCooldown(player, 100);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_SECONDARY_CD, 600);

		// 播放蓄力开始音效
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.5f);

		return true;
	}

	/**
	 * 释放冰风暴
	 */
	private static void releaseStorm(ServerPlayerEntity player) {
		// 霜寒值已在startCharging时消耗，CD也已设置

		// 计算准星位置（射线检测）
		Vec3d start = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = start.add(look.multiply(MAX_RANGE));

		BlockHitResult hitResult = player.getWorld().raycast(new RaycastContext(
				start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
		));

		Vec3d targetPos;
		if (hitResult.getType() == HitResult.Type.BLOCK) {
			targetPos = hitResult.getPos();
		} else {
			targetPos = end;
		}

		// 创建冰风暴实体
		FrostStormEntity storm = new FrostStormEntity(
				player.getWorld(),
				targetPos.x, targetPos.y, targetPos.z,
				player
		);
		player.getWorld().spawnEntity(storm);

		// 播放释放音效
		player.getWorld().playSound(null, targetPos.x, targetPos.y, targetPos.z,
				SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 0.8f);

		// 生成释放粒子
		if (player.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD,
					targetPos.x, targetPos.y + 1, targetPos.z,
					30, 1.5, 1.0, 1.5, 0.05);
		}
	}

	/**
	 * 获取霜寒值
	 */
	private static int getResourceValue(ServerPlayerEntity player) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			// Resource not found
		}
		return 0;
	}

	/**
	 * 修改霜寒值
	 */
	private static void changeResourceValue(ServerPlayerEntity player, int change) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
				variablePower.setValue(newValue);
				PowerHolderComponent.sync(player); // 同步到客户端
			}
		} catch (Exception e) {
			// Resource not found
		}
	}

	/**
	 * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
	 */
	private static void setRegenCooldown(ServerPlayerEntity player, int value) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(value);
				PowerHolderComponent.sync(player);
			}
		} catch (Exception e) {
			// Resource not found
		}
	}

	/**
	 * 设置power的cooldown
	 * 未使用，建议移除
	 */
	private static void setPowerCooldown(ServerPlayerEntity player, int ticks) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof CooldownPower cooldownPower) {
				cooldownPower.setCooldown(ticks);
			}
		} catch (Exception e) {
			// Power not found

		}
	}
}
