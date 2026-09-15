package net.jackcooper.shapeShifterCurseAddon.compat.jei;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 特殊配方的点击模拟搬运工具（REI / JEI 共用）。
 * <p>
 * 背景：配方查看器原生转移（REI MOVE_ITEMS 包 / JEI RecipeTransferServerUtil）
 * 在服务端用「裸物品 id」或 Ingredient 重建材料，无法携带药水 NBT；
 * 带 Potion NBT 的药水（压缩能量/剧毒）永远搬不进工作台格。
 * <p>
 * 方案：客户端模拟原版 ClickSlot 点击（包本身携带完整 NBT 栈，服务端按原版规则
 * 校验），把材料逐格放入 3×3 合成格。仅客户端逻辑，多人安全。
 */
public final class SscGridClickTransfer {

	private SscGridClickTransfer() {
	}

	/** 3×3 工作台中 9 个合成格在 ScreenHandler 中的槽序号范围（1..9，0 为输出）。 */
	private static final int CRAFT_GRID_FIRST = 1;
	private static final int CRAFT_GRID_LAST = 9;

	/** 单格材料候选（含 NBT 真实栈；空列表 = 该格留空）。 */
	public record GridSpec(List<List<ItemStack>> perSlot) {
		/** 无限压缩能量药水：上中月髓环，中间行 苹果/药水(三瓶型)/苹果，其余空。 */
		public static GridSpec infinitePotion() {
			ItemStack[] feedAlts = {
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.POTION), RegCustomPotions.FEED_POTION),
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.SPLASH_POTION), RegCustomPotions.FEED_POTION),
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.LINGERING_POTION), RegCustomPotions.FEED_POTION)};
			ItemStack[] moonRing = {new ItemStack(SscAddon.SP_UPGRADE_THING)};
			ItemStack[] apple = {new ItemStack(net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE)};
			List<List<ItemStack>> grid = new ArrayList<>(9);
			grid.add(List.of());            // 0
			grid.add(List.of(moonRing));    // 1
			grid.add(List.of());            // 2
			grid.add(List.of(apple));       // 3
			grid.add(List.of(feedAlts));    // 4
			grid.add(List.of(apple));       // 5
			grid.add(List.of());            // 6
			grid.add(List.of());            // 7
			grid.add(List.of());            // 8
			return new GridSpec(grid);
		}

		/** 毒液腺体：8 蜘蛛眼环绕 + 中心剧毒药水（三瓶型）。 */
		public static GridSpec venomGland() {
			ItemStack[] poisonAlts = {
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.POTION), Potions.POISON),
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.SPLASH_POTION), Potions.POISON),
					PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.LINGERING_POTION), Potions.POISON)};
			ItemStack[] eye = {new ItemStack(net.minecraft.item.Items.SPIDER_EYE)};
			List<List<ItemStack>> grid = new ArrayList<>(9);
			for (int i = 0; i < 9; i++) {
				grid.add(i == 4 ? List.of(poisonAlts) : List.of(eye));
			}
			return new GridSpec(grid);
		}
	}

	/** 搬运结果。 */
	public enum Outcome {
		SUCCESS, NOT_ENOUGH, NO_ROOM, CURSOR_NOT_EMPTY, FAILED
	}

	/**
	 * 执行点击模拟搬运：清空合成格 → 按网格逐格放入材料。
	 *
	 * @param checkOnly true=仅预检（悬浮提示用），不执行点击
	 */
	public static Outcome transfer(MinecraftClient client, GridSpec spec, boolean checkOnly) {
		ClientPlayerEntity player = client.player;
		ClientPlayerInteractionManager im = client.interactionManager;
		if (player == null || im == null) {
			return Outcome.FAILED;
		}
		if (!(player.currentScreenHandler instanceof CraftingScreenHandler)) {
			return Outcome.FAILED;
		}

		// 预检仅用于悬浮提示；实际点击不拦截——逐格尽力放置，缺的格子跳过，
		// 服务端工作台配方校验兑底（与 REI 侧同策略）。
		if (!hasAllMaterials(player, spec.perSlot())) {
			return checkOnly ? Outcome.NOT_ENOUGH : Outcome.SUCCESS;
		}
		if (checkOnly) {
			return Outcome.SUCCESS;
		}

		ScreenHandler handler = player.currentScreenHandler;

		// 光标上有物品时状态太复杂，直接失败（用户先自行放下）
		if (!handler.getCursorStack().isEmpty()) {
			return Outcome.CURSOR_NOT_EMPTY;
		}

		// 第一步：把合成格里的现有物品搬回背包（拿起到光标 → 放入背包槽，残留循环倾倒）
		for (int i = CRAFT_GRID_FIRST; i <= CRAFT_GRID_LAST; i++) {
			Slot slot = handler.slots.get(i);
			if (!slot.getStack().isEmpty()) {
				im.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, player);
				if (!dumpCursor(im, handler, player)) {
					return Outcome.NO_ROOM;
				}
			}
		}

		// 第二步：按网格逐格放入。放 1 个的正确三步：① 左键整堆拿起；② 右键点合成格
		//（vanilla 右键=放入 1 个）；③ 光标若还有剩余，左键点回原背包槽（原槽放回不打乱背包）。
		for (int i = 0; i < 9 && i < spec.perSlot().size(); i++) {
			List<ItemStack> alternatives = spec.perSlot().get(i);
			if (alternatives.isEmpty()) {
				continue;
			}
			int srcHandlerSlot = -1;
			int srcInv = findAnyStack(player, alternatives);
			if (srcInv >= 0) {
				srcHandlerSlot = invToHandler(srcInv);
			} else {
				// 背包找不到：从合成格已有同材料堆里与 1 个过来（蜘蛛眼 8 格场景）
				for (int g = CRAFT_GRID_FIRST; g <= CRAFT_GRID_LAST && srcHandlerSlot < 0; g++) {
					if (g == CRAFT_GRID_FIRST + i) continue;
					for (ItemStack alt : alternatives) {
						if (stackMatches(handler.slots.get(g).getStack(), alt)) {
							srcHandlerSlot = g;
							break;
						}
					}
				}
				if (srcHandlerSlot < 0) {
					continue; // 缺料跳过，服务端配方校验兑底
				}
				im.clickSlot(handler.syncId, srcHandlerSlot, 0, SlotActionType.PICKUP, player);
				im.clickSlot(handler.syncId, CRAFT_GRID_FIRST + i, 1, SlotActionType.PICKUP, player);
				if (!handler.getCursorStack().isEmpty()) {
					im.clickSlot(handler.syncId, srcHandlerSlot, 0, SlotActionType.PICKUP, player);
				}
				continue;
			}
			int gridSlot = CRAFT_GRID_FIRST + i;
			// 合成格已有该格所需材料（复用已放内容，避免重复消耗）
			boolean gridHas = alternatives.stream()
					.anyMatch(a -> stackMatches(handler.slots.get(gridSlot).getStack(), a));
			if (gridHas) {
				continue;
			}
			// ① 左键整堆拿起
			im.clickSlot(handler.syncId, srcHandlerSlot, 0, SlotActionType.PICKUP, player);
			if (handler.getCursorStack().isEmpty()) {
				return Outcome.FAILED;
			}
			// ② 右键点合成格 → 精确放入 1 个
			im.clickSlot(handler.syncId, gridSlot, 1, SlotActionType.PICKUP, player);
			// ③ 剩余放回原背包槽（同物同 NBT 可叠回；药水 count=1 时光标已空）
			if (!handler.getCursorStack().isEmpty()) {
				im.clickSlot(handler.syncId, srcHandlerSlot, 0, SlotActionType.PICKUP, player);
				if (!handler.getCursorStack().isEmpty()) {
					// 原槽意外放不回：兑底找合并槽/空槽
					dumpCursor(im, handler, player);
				}
			}
		}
		return Outcome.SUCCESS;
	}

	/** 把光标上的物品尽数放回背包（合并槽/空槽；放不下返回 false）。 */
	private static boolean dumpCursor(ClientPlayerInteractionManager im, ScreenHandler handler, ClientPlayerEntity player) {
		int guard = 0;
		while (!handler.getCursorStack().isEmpty()) {
			int target = findMergeTarget(player, handler.getCursorStack());
			if (target < 0) {
				return false;
			}
			im.clickSlot(handler.syncId, invToHandler(target), 0, SlotActionType.PICKUP, player);
			if (++guard > 40) {
				return false;
			}
		}
		return true;
	}

	/**
	 * PlayerInventory 索引 → CraftingScreenHandler 槽序号。
	 * <p>
	 * 原版 3×3 工作台 handler 槽序：0=输出，1..9=合成格，10..36=主背包(inv 9..35)，37..45=热bar(inv 0..8)。
	 */
	private static int invToHandler(int invIndex) {
		return invIndex < 9 ? 37 + invIndex : 1 + invIndex;
	}

	/** 背包（含热bar）里找到与候选任一匹配的栈，返回背包索引；找不到返回 -1。 */
	private static int findAnyStack(ClientPlayerEntity player, List<ItemStack> alternatives) {
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) {
				continue;
			}
			for (ItemStack alt : alternatives) {
				if (stackMatches(s, alt)) {
					return i;
				}
			}
		}
		return -1;
	}

	/** 语义匹配（与配方 matches 一致，忽略数量）：物品同 + NBT 同（canCombine 不含 count）；
	 * 药水类退化为「同物品 + 药水类型(Potion id)相等」。
	 * 注意：不能用 areEqual——它还比较 count，display 候选恒 count=1，
	 * 背包堆叠材料永远匹配不上（REI 侧踩过的坑）。 */
	private static boolean stackMatches(ItemStack have, ItemStack want) {
		if (ItemStack.canCombine(have, want)) {
			return true;
		}
		if (have.getItem() != want.getItem()) {
			return false;
		}
		if (isPotionBottle(have) && isPotionBottle(want)) {
			return PotionUtil.getPotion(have).equals(PotionUtil.getPotion(want));
		}
		return false;
	}

	private static boolean isPotionBottle(ItemStack s) {
		return s.isOf(net.minecraft.item.Items.POTION)
				|| s.isOf(net.minecraft.item.Items.SPLASH_POTION)
				|| s.isOf(net.minecraft.item.Items.LINGERING_POTION);
	}

	/** 背包里能合并 want 的槽（同物同 NBT 未满叠，忽略数量）或空槽；无则 -1。 */
	private static int findMergeTarget(ClientPlayerEntity player, ItemStack want) {
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && ItemStack.canCombine(s, want) && s.getCount() < s.getMaxCount()) {
				return i;
			}
		}
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			if (inv.getStack(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** 是否持有全部材料：每格取背包已有的候选（语义匹配，忽略数量），同物品跨格合并计数。 */
	private static boolean hasAllMaterials(ClientPlayerEntity player, List<List<ItemStack>> perSlot) {
		List<ItemStack> need = new ArrayList<>();
		for (List<ItemStack> alts : perSlot) {
			if (alts.isEmpty()) {
				continue;
			}
			int src = findAnyStack(player, alts);
			if (src < 0) {
				return false;
			}
			ItemStack chosen = player.getInventory().getStack(src).copy();
			chosen.setCount(1);
			boolean merged = false;
			for (ItemStack n : need) {
				if (stackMatches(n, chosen)) {
					n.increment(1);
					merged = true;
					break;
				}
			}
			if (!merged) {
				need.add(chosen);
			}
		}
		if (need.isEmpty()) {
			return true;
		}
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.isEmpty()) {
				continue;
			}
			for (ItemStack n : need) {
				if (n.getCount() > 0 && stackMatches(s, n)) {
					int take = Math.min(n.getCount(), s.getCount());
					n.decrement(take);
				}
			}
		}
		for (ItemStack n : need) {
			if (n.getCount() > 0) {
				return false;
			}
		}
		return true;
	}
}
