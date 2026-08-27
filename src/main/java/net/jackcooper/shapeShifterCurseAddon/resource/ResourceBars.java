package net.jackcooper.shapeShifterCurseAddon.resource;

import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

/**
 * 统一资源条门面（jackcooper）：所有资源条读写的唯一入口。
 *
 * <p>职责：clamp 到 [0,max] → 写 apoli resource（经 PowerUtils，同步不变）→ 触发该条全部
 * {@link BarTrigger}；原版 mana（{@link BarKeys#VANILLA_MANA}）直通 {@link ManaUtils} 不绕层。
 * 现有各 Manager / 技能扣费点逐步切换到本门面（迁移期两边共存，落点相同可随时回退）。
 *
 * <p>线程与端别：全部方法仅服务端调用（写路径）；{@link #get} 双端可读。
 */
public final class ResourceBars {

	private ResourceBars() {}

	// ==================== 写路径（服务端） ====================

	/** 回复 n 点（clamp 到 max），返回实际值。 */
	public static int gain(ServerPlayerEntity player, ResourceBarDef bar, int n) {
		if (bar == BarKeys.VANILLA_MANA) {
			ManaUtils.gainPlayerMana(player, n);
			return (int) ManaUtils.getPlayerMana(player);
		}
		int oldV = get(player, bar);
		int max = maxOf(player, bar);
		int newV = Math.max(0, Math.min(max, oldV + n));
		apply(player, bar, oldV, newV);
		return newV;
	}

	/**
	 * 消耗 n 点：不足返回 false 且不扣；足够则扣并返回 true。
	 */
	public static boolean consume(ServerPlayerEntity player, ResourceBarDef bar, int n) {
		if (bar == BarKeys.VANILLA_MANA) {
			if (ManaUtils.getPlayerMana(player) < n) {
				return false;
			}
			ManaUtils.consumePlayerMana(player, n);
			return true;
		}
		int oldV = get(player, bar);
		if (oldV < n) {
			return false;
		}
		apply(player, bar, oldV, oldV - n);
		return true;
	}

	/** 直接设值（clamp），触发回调。 */
	public static void set(ServerPlayerEntity player, ResourceBarDef bar, int value) {
		if (bar == BarKeys.VANILLA_MANA) {
			ManaUtils.setPlayerMana(player, value);
			return;
		}
		int oldV = get(player, bar);
		int max = maxOf(player, bar);
		int newV = Math.max(0, Math.min(max, value));
		apply(player, bar, oldV, newV);
	}

	/** 内部：写 resource + 触发回调（唯一落点，迁移期与旧直调等价）。 */
	private static void apply(ServerPlayerEntity player, ResourceBarDef bar, int oldV, int newV) {
		if (oldV != newV) {
			PowerUtils.setResourceValueAndSync(player, bar.id, newV);
			for (BarTrigger t : bar.triggers()) {
				t.onChange(player, oldV, newV, maxOf(player, bar));
			}
		}
	}

	// ==================== 读路径（双端） ====================

	/** 当前值。原版 mana 直通；客户端可读（apoli resource 自动同步）。 */
	public static int get(PlayerEntity player, ResourceBarDef bar) {
		if (bar == BarKeys.VANILLA_MANA) {
			return (int) ManaUtils.getPlayerMana(player);
		}
		if (player.getWorld().isClient()) {
			return PowerUtils.getClientResourceValue(player, bar.id);
		}
		return PowerUtils.getResourceValue((ServerPlayerEntity) player, bar.id);
	}

	/** 上限。 */
	public static int maxOf(PlayerEntity player, ResourceBarDef bar) {
		if (bar == BarKeys.VANILLA_MANA) {
			return (int) ManaUtils.getPlayerMaxMana(player);
		}
		if (player.getWorld().isClient()) {
			return PowerUtils.getClientResourceValueAndMax(player, bar.id)[1];
		}
		return PowerUtils.getResourceMax((ServerPlayerEntity) player, bar.id);
	}

	/** 百分比（0~1）。 */
	public static double percent(PlayerEntity player, ResourceBarDef bar) {
		int max = maxOf(player, bar);
		return max <= 0 ? 0d : (double) get(player, bar) / max;
	}

	// ==================== 判定 ====================

	/** 玩家是否持有该条（resource power 存在 / 原版 mana 类型存在）。 */
	public static boolean has(PlayerEntity player, ResourceBarDef bar) {
		if (bar == BarKeys.VANILLA_MANA) {
			return ManaUtils.getPlayerManaTypeID(player) != null;
		}
		return hasPowerId(player, bar.id);
	}

	/** 按 kind 判定：玩家是否有任意一条该语义的 resource 型条（不含原版直通）。 */
	public static boolean hasKind(PlayerEntity player, String kind) {
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (bar.kind.equals(kind) && has(player, bar)) {
				return true;
			}
		}
		return false;
	}

	/** 持有指定 id 的 power（PowerTypeRegistry 查表，/reload 安全）。 */
	public static boolean hasPowerId(LivingEntity entity, Identifier id) {
		try {
			PowerType<?> powerType = PowerTypeRegistry.get(id);
			return io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(entity).hasPower(powerType);
		} catch (IllegalArgumentException e) {
			return false; // power 未注册（数据包未加载/极端时序）
		}
	}

	// ==================== 统一 tick 调度（服务端） ====================

	/** 各条的独立 tick 计数（调度器用）。 */
	private static final java.util.Map<Identifier, Integer> TICKS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 服务端每 tick 调度入口（由 {@code ResourceBarsTicker} 挂 ServerTickEvents）：
	 * 对每个在线玩家持有的条，按各 RegenRule 的 interval 轮询回复/衰减，
	 * 并对命中的 ThresholdEffect 段逐 tick 施加效果。
	 */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
		TICKS.replaceAll((k, v) -> v + 1);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			for (ResourceBarDef bar : BarKeys.ALL) {
				if (!has(player, bar)) {
					continue;
				}
				int current = get(player, bar);
				int max = maxOf(player, bar);
				// 回复规则（按各自 interval）
				for (RegenRule rule : bar.regenRules()) {
					int interval = Math.max(1, rule.interval());
					int t = TICKS.getOrDefault(bar.id, 0);
					if (t % interval == 0) {
						int delta = rule.tickRegen(player, bar);
						if (delta != 0) {
							gain(player, bar, delta);
						}
					}
				}
				// 分段效果（每 tick）
				for (ThresholdEffect te : bar.thresholds()) {
					if (te.isInSegment(current, max)) {
						te.applyTick(player, current, max);
					}
				}
			}
		}
	}
}
