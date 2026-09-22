package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端每 tick 数据缓存（jackcooper，2026-09-23）：把「不逐帧变化、但被 HUD 每帧查询」
 * 的三类重查询收敛到 END_CLIENT_TICK 刷新一次，渲染帧只读缓存值：
 * <ul>
 *   <li>佩戴魔法书 ItemStack（{@link #equippedBook()}，含 null 负缓存）——原每帧全饰品扫描 +
 *       Curios 反射链（{@link SpellcastClient#getEquippedBook} 同源复用）；</li>
 *   <li>指定饰品物品的佩戴布尔（{@link #isWearing}）——护符/吊坠等装备判定；</li>
 *   <li>寒棘狐法阵进度与环绕冰锥存在性（{@link #frostForgeProgress} / {@link #hasHoverThorn}）
 *       ——原每帧实体盒扫描。</li>
 * </ul>
 * 粒度为 1 tick（20 次/秒）：换装/装备变化后 HUD 最多延迟 1 tick（50ms）反映，无感；
 * 进度条为 1t 步进（寒棘狐蓄力条 0-100t，5%/步，视觉平滑度与帧率解耦后依旧连贯）。
 * 本地玩家为空/换世界时自动清空。
 */
@Environment(EnvType.CLIENT)
public final class ClientTickCache {
	private ClientTickCache() {}

	/** 缓存锚定的客户端 tick（world.getTime()）。 */
	private static long cachedTick = Long.MIN_VALUE;
	@Nullable
	private static ItemStack equippedBook;
	private static final Map<Item, Boolean> WEARING = new ConcurrentHashMap<>();
	private static float frostForgeProgress = -1f;
	private static boolean hasHoverThorn;

	/** 注册 tick 刷新（客户端初始化时调用一次）。 */
	public static void register() {
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ClientTickCache::refresh);
	}

	private static void refresh(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			invalidate();
			return;
		}
		long now = client.world.getTime();
		if (now == cachedTick) return; // 同一 tick 多次回调只刷一次
		cachedTick = now;
		// 佩戴魔法书（含 null 负缓存：未佩戴玩家不做二次扫描）
		ItemStack book = TrinketUtils.findFirstEquipped(client.player,
				stack -> stack.getItem() == net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK);
		equippedBook = book == null || book.isEmpty() ? null : book;
		// 荧光幼灵海晶吊坠 / 使魔蓝火护符（固定两项，避免动态增删）
		WEARING.clear();
		WEARING.put(net.jackcooper.shapeShifterCurseAddon.SscAddon.SEA_CRYSTAL_PENDANT,
				TrinketUtils.isWearing(client.player, net.jackcooper.shapeShifterCurseAddon.SscAddon.SEA_CRYSTAL_PENDANT));
		WEARING.put(net.jackcooper.shapeShifterCurseAddon.SscAddon.BLUE_FIRE_AMULET,
				TrinketUtils.isWearing(client.player, net.jackcooper.shapeShifterCurseAddon.SscAddon.BLUE_FIRE_AMULET));
		// 寒棘狐：法阵实体进度（-1 无实体）与环绕冰锥存在性（一次扫描同时取两者）
		frostForgeProgress = -1f;
		hasHoverThorn = false;
		var arrays = client.world.getEntitiesByClass(net.jackcooper.shapeShifterCurseAddon.entity.FrostArrayEntity.class,
				client.player.getBoundingBox().expand(4.0),
				a -> a.getTrackedOwnerId() == client.player.getId());
		if (!arrays.isEmpty()) {
			frostForgeProgress = Math.max(0f, Math.min(1f, arrays.get(0).getProgress() / 100f));
		}
		hasHoverThorn = !client.world.getEntitiesByClass(net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity.class,
				client.player.getBoundingBox().expand(3.0),
				t -> t.isHover() && client.player.getUuid().equals(t.getOwnerUuid().orElse(null))).isEmpty();
	}

	private static void invalidate() {
		cachedTick = Long.MIN_VALUE;
		equippedBook = null;
		WEARING.clear();
		frostForgeProgress = -1f;
		hasHoverThorn = false;
	}

	/** 当前佩戴的魔法书（未佩戴返回 null；本 tick 未刷新时同步兜底刷新一次）。 */
	@Nullable
	public static ItemStack equippedBook() {
		ensureFresh();
		return equippedBook;
	}

	/** 指定饰品物品是否佩戴（本 tick 未刷新时同步兜底刷新一次；未登记的物品返回 false）。 */
	public static boolean isWearing(Item item) {
		ensureFresh();
		return Boolean.TRUE.equals(WEARING.getOrDefault(item, false));
	}

	/** 寒棘狐法阵蓄力进度（0-1；无实体 -1）。 */
	public static float frostForgeProgress() {
		ensureFresh();
		return frostForgeProgress;
	}

	/** 寒棘狐环绕冰锥是否存在（凝棘门槛）。 */
	public static boolean hasHoverThorn() {
		ensureFresh();
		return hasHoverThorn;
	}

	/** 世界时间锚已过期（如换世界后 HUD 先于 tick 回调执行）时兜底同步刷新。 */
	private static void ensureFresh() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			invalidate();
			return;
		}
		if (cachedTick != client.world.getTime()) refresh(client);
	}
}
