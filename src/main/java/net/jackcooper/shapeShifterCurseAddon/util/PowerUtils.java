package net.jackcooper.shapeShifterCurseAddon.util;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowerUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(PowerUtils.class);

	private PowerUtils() {
	}

	/**
	 * 从Apoli VariableIntPower读取当前值（客户端侧）
	 * 用于HUD渲染器等只能在客户端读取资源的场景
	 */
	@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
	public static int getClientResourceValue(PlayerEntity player, Identifier resourceId) {
		// 走每 tick 缓存，避免每帧遍历玩家全部 power
		return ClientResourceCache.getValue(player, resourceId);
	}

	/**
	 * 获取客户端资源值和最大值（用于HUD渲染百分比计算）
	 *
	 * @return int[2] {current, max}，失败返回 {0, 1}
	 */
	@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
	public static int[] getClientResourceValueAndMax(PlayerEntity player, Identifier resourceId) {
		// 走每 tick 缓存，避免每帧每个 HUD 条都遍历玩家全部 power
		return ClientResourceCache.getValueAndMax(player, resourceId);
	}

	public static int getResourceValue(ServerPlayerEntity player, Identifier resourceId) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			LOGGER.error("getResourceValue 失败: resourceId={}", resourceId, e);
		}
		return 0;
	}

	public static void setResourceValue(ServerPlayerEntity player, Identifier resourceId, int value) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(value);
			}
		} catch (Exception e) {
			LOGGER.error("setResourceValue 失败: resourceId={}, value={}", resourceId, value, e);
		}
	}

	public static void changeResourceValue(ServerPlayerEntity player, Identifier resourceId, int change) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				// 使用资源自身的 max 值而非硬编码 100
				int max = variablePower.getMax();
				int newValue = Math.max(0, Math.min(max, variablePower.getValue() + change));
				variablePower.setValue(newValue);
			}
		} catch (Exception e) {
			LOGGER.error("changeResourceValue 失败: resourceId={}, change={}", resourceId, change, e);
		}
	}

	public static void syncPower(ServerPlayerEntity player, Identifier powerId) {
		try {
			// 只同步单个 power（而非全量 sync），避免战斗中频繁重发玩家全部 powers 大包占用带宽
			PowerHolderComponent.syncPower(player, PowerTypeRegistry.get(powerId));
		} catch (Exception e) {
			LOGGER.error("syncPower 失败: powerId={}", powerId, e);
		}
	}

	public static void setResourceValueAndSync(ServerPlayerEntity player, Identifier resourceId, int value) {
		// 值未变则跳过：省掉一次 setValue + 一个同步包（Apoli 走可靠 TCP，客户端已持有该值）
		if (getResourceValue(player, resourceId) == value) return;
		setResourceValue(player, resourceId, value);
		syncPower(player, resourceId);
	}

	public static void changeResourceValueAndSync(ServerPlayerEntity player, Identifier resourceId, int change) {
		// 无变化则跳过同步包（change==0 恒不改值）
		if (change == 0) return;
		changeResourceValue(player, resourceId, change);
		syncPower(player, resourceId);
	}

	/**
	 * 获取Apoli资源最大值（服务端）
	 */
	public static int getResourceMax(ServerPlayerEntity player, Identifier resourceId) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getMax();
			}
		} catch (Exception e) {
			LOGGER.error("getResourceMax 失败: resourceId={}", resourceId, e);
		}
		return 0;
	}

	public static boolean hasResource(ServerPlayerEntity player, Identifier resourceId, int required) {
		return getResourceValue(player, resourceId) >= required;
	}

	/**
	 * 检查玩家是否处于 SP Allay 形态
	 */
	public static boolean isSpAllay(ServerPlayerEntity player) {
		try {
			return PowerHolderComponent.KEY.get(player).getPowers().stream()
					.anyMatch(p -> p.getType().getIdentifier().getNamespace().equals("my_addon")
							&& p.getType().getIdentifier().getPath().contains("form_allay_sp"));
		} catch (Exception e) {
			LOGGER.error("isSpAllay 检测失败", e);
			return false;
		}
	}

	/**
	 * 重启某个 Apoli 冷却型能力（active_self 等 CooldownPower）的冷却到满。
	 * 用于让“水矛合成冷却”从水矛消失那刻重新起算，避免持矛期间冷却走完后扛出即可秒合成。
	 */
	public static void resetCooldown(ServerPlayerEntity player, Identifier cooldownPowerId) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(cooldownPowerId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof io.github.apace100.apoli.power.CooldownPower cooldownPower) {
				cooldownPower.use();
				// 只同步该冷却 power 自身，避免全量重发全部 powers
				PowerHolderComponent.syncPower(player, powerType);
			}
		} catch (Exception e) {
			LOGGER.error("resetCooldown 失败: cooldownPowerId={}", cooldownPowerId, e);
		}
	}

}
