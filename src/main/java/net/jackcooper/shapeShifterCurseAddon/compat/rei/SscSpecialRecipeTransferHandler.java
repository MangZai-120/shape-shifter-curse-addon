package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 特殊配方的 REI 快速转移处理器（客户端模拟原版点击）。
 * <p>
 * 背景：REI 原生转移（MOVE_ITEMS_NEW_PACKET）在服务端用「裸物品 id」重建材料栈
 * （InputSlotCrafter.acceptAlignedInput → RecipeFinder.getStackFromId，无 NBT），
 * 再用 ItemStack.areItemsEqual 严格比对 NBT——带 Potion NBT 的药水永远匹配不上
 * 裸栈（NBT=null），导致压缩能量药水/剧毒药水永远搬不进工作台格。
 * <p>
 * 修复：命中我们的配方卡片时改走原版 ClickSlot 模拟点击路径：
 * ClickSlot 包携带完整物品栈（含 NBT），服务端按真实栈处理，天然支持 NBT 材料。
 * 仅客户端逻辑，服务端全程走原版校验，多人环境安全。
 */
public class SscSpecialRecipeTransferHandler implements TransferHandler {

	/** 3×3 工作台中 9 个合成格在 ScreenHandler 中的槽序号范围（1..9，0 为输出）。 */
	private static final int CRAFT_GRID_FIRST = 1;
	private static final int CRAFT_GRID_LAST = 9;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ssc-addon");

	/** 失败统一出口：打日志 + 返回失败。 */
	private Result fail(String reason) {
		LOG.warn("[SSCA] REI转移失败: {}", reason);
		return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
	}

	@Override
	public double getPriority() {
		// 高于默认（0），抢在 REI 原生搬运之前接管我们的配方
		return 50d;
	}

	@Override
	public ApplicabilityResult checkApplicable(Context context) {
		// 三重识别（从精确到宽泛）：① 我们手工注册的卡片类；② REI 自动生成且携带配方对象的卡片；
		// ③ 任意合成类卡片但输出是我们的特殊产物（display 可能被 REI 序列化重建，配方 Optional 为空，
		// 只能按输出内容匹配——这是最宽泛的兜底）。
		Display display = context.getDisplay();
		boolean ours = display instanceof SscSpecialCraftingDisplay;
		if (!ours && display instanceof DefaultCraftingDisplay<?> dd
				&& dd.getOptionalRecipe().isPresent()
				&& SSCA_REIPlugin.isSscSpecialRecipe(dd.getOptionalRecipe().get())) {
			ours = true;
		}
		if (!ours && hasOurOutput(display)) {
			ours = true;
		}
		if (!ours) {
			return ApplicabilityResult.createNotApplicable();
		}
		// 必须在 3×3 工作台界面（物品栏 2×2 放不下 3×3 配方）
		if (!(context.getMenu() instanceof net.minecraft.screen.CraftingScreenHandler)) {
			return ApplicabilityResult.createNotApplicable();
		}
		return ApplicabilityResult.createApplicable();
	}

	/** 卡片输出是否含我们的特殊产物（无限压缩能量药水 / 毒液腺体）。 */
	private static boolean hasOurOutput(Display display) {
		if (!(display instanceof BasicDisplay bd)) {
			return false;
		}
		try {
			for (EntryIngredient out : bd.getOutputEntries()) {
				for (me.shedaniel.rei.api.common.entry.EntryStack<?> stack : out) {
					Object v = stack.getValue();
					if (v instanceof ItemStack is && (is.isOf(SscAddon.INFINITE_ENERGY_POTION)
							|| is.isOf(SscAddon.VENOM_GLAND))) {
						return true;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 按输出物品选网格（无限压缩能量药水 / 毒液腺体）。 */
	private static ItemStack[][] outputGrid(Display display) {
		if (!(display instanceof BasicDisplay bd)) {
			return null;
		}
		try {
			for (EntryIngredient out : bd.getOutputEntries()) {
				for (me.shedaniel.rei.api.common.entry.EntryStack<?> stack : out) {
					Object v = stack.getValue();
					if (v instanceof ItemStack is && is.isOf(SscAddon.INFINITE_ENERGY_POTION)) {
						return SSCA_REIPlugin.infiniteEnergyPotionGrid();
					}
					if (v instanceof ItemStack is2 && is2.isOf(SscAddon.VENOM_GLAND)) {
						return SSCA_REIPlugin.venomGlandGrid();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	@Override
	public Result handle(Context context) {
		// 统一解析「每格材料需求」：我们的卡片直接取；其余按配方/输出内容重建网格
		List<List<ItemStack>> requiredPerSlot;
		if (context.getDisplay() instanceof SscSpecialCraftingDisplay our) {
			requiredPerSlot = our.getRequiredPerSlot();
		} else if (context.getDisplay() instanceof DefaultCraftingDisplay<?> dd
				&& dd.getOptionalRecipe().isPresent()
				&& SSCA_REIPlugin.gridFor(dd.getOptionalRecipe().get()) != null) {
			requiredPerSlot = SscSpecialCraftingDisplay.requiredOf(
					SSCA_REIPlugin.gridFor(dd.getOptionalRecipe().get()));
		} else {
			// 输出内容匹配的宽泛路径：按输出物品选网格
			ItemStack[][] grid = outputGrid(context.getDisplay());
			if (grid == null) {
				return fail("无法从卡片解析材料网格 " + context.getDisplay().getClass().getName());
			}
			requiredPerSlot = SscSpecialCraftingDisplay.requiredOf(grid);
		}
		MinecraftClient client = context.getMinecraft();
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return fail("玩家为 null");
		}

		// 预检仅用于悬浮提示（未持齐材料时按钮提示不足）；实际点击不拦截——逐格尽力放置，
		// 缺的格子跳过，服务端工作台配方校验兜底。
		if (!hasAllMaterials(player, requiredPerSlot) && !context.isActuallyCrafting()) {
			return Result.createFailed(Text.translatable("error.rei.not.enough.materials"));
		}

		// 仅预检（鼠标悬浮预览）阶段不实际执行
		if (!context.isActuallyCrafting()) {
			return Result.createSuccessful();
		}
		client.setScreen(context.getContainerScreen());
		ScreenHandler handler = player.currentScreenHandler;
		ClientPlayerInteractionManager im = client.interactionManager;
		if (im == null) {
			return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
		}

		// 光标上有物品时状态太复杂，直接失败（用户先自行放下）
		if (!handler.getCursorStack().isEmpty()) {
			return Result.createFailed(Text.translatable("error.ssc_addon.transfer.cursor_not_empty"));
		}

		// 第一步：把合成格里的现有物品搬回背包（拿起到光标 → 放入背包槽，残留循环倾倒）
		for (int i = CRAFT_GRID_FIRST; i <= CRAFT_GRID_LAST; i++) {
			Slot slot = handler.slots.get(i);
			if (!slot.getStack().isEmpty()) {
				im.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, player);
				if (!dumpCursor(im, handler, player)) {
					return Result.createFailed(Text.translatable("error.ssc_addon.transfer.no_room"));
				}
			}
		}

		// 第二步：按配方把材料逐格放入（含 NBT 的药水按真实栈精确匹配；每格多候选任选其一）。
		// 放 1 个的正确三步：① 左键整堆拿起；② 右键点合成格（vanilla 右键=放入 1 个）；
		// ③ 光标若还有剩余，左键点回原背包槽（原槽放回，不打乱背包物品位置/药水排布）。
		for (int i = 0; i < 9 && i < requiredPerSlot.size(); i++) {
			List<ItemStack> alternatives = requiredPerSlot.get(i);
			if (alternatives.isEmpty()) {
				continue;
			}
			int srcHandlerSlot = -1;
			int srcInv = findAnyStack(player, alternatives);
			if (srcInv >= 0) {
				srcHandlerSlot = invToHandler(srcInv);
			} else {
				// 背包找不到：尝试直接在合成格里找（上一轮已放过的同材料格可复用，蜘蛛眼 8 格场景）
				boolean inGrid = false;
				for (ItemStack alt : alternatives) {
					for (int g = CRAFT_GRID_FIRST; g <= CRAFT_GRID_LAST; g++) {
						if (stackMatches(handler.slots.get(g).getStack(), alt) && g != CRAFT_GRID_FIRST + i) {
							inGrid = true;
							break;
						}
					}
					if (inGrid) break;
				}
				if (!inGrid) {
					continue;
				}
				// 从合成格已有堆里匀 1 个过来：拿起该格 → 右键放入目标格 → 剩余放回原格
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
					continue;
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
				return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
			}
			// ② 右键点合成格 → 精确放入 1 个
			im.clickSlot(handler.syncId, gridSlot, 1, SlotActionType.PICKUP, player);
			// ③ 剩余放回原背包槽（同物同 NBT 可叠回；药水 count=1 时光标已空，天然不打乱）
			if (!handler.getCursorStack().isEmpty()) {
				im.clickSlot(handler.syncId, srcHandlerSlot, 0, SlotActionType.PICKUP, player);
				if (!handler.getCursorStack().isEmpty()) {
					// 原槽意外放不回（被同步竞态占了）：兜底找合并槽/空槽
					dumpCursor(im, handler, player);
				}
			}
		}

		return Result.createSuccessful();
	}

	/** 把光标上的物品尽数放回背包（合并槽/空槽；放不下返回 false，光标残留由服务端权威回滚）。 */
	private boolean dumpCursor(ClientPlayerInteractionManager im, ScreenHandler handler, ClientPlayerEntity player) {
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
	private int invToHandler(int invIndex) {
		return invIndex < 9 ? 37 + invIndex : 1 + invIndex;
	}

/** 背包（含热bar）里找到与候选任一匹配的栈，返回背包索引；找不到返回 -1。 */
	private int findAnyStack(ClientPlayerEntity player, List<ItemStack> alternatives) {
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
	 * 药水类退化为「同物品 + 药水类型(Potion id)相等」——玩家实物常带额外 NBT。
	 * 注意：不能用 areEqual——它还比较 count（反编译确认：count 不等直接 false），
	 * display 候选恒 count=1，背包堆叠材料永远匹配不上。 */
	private static boolean stackMatches(ItemStack have, ItemStack want) {
		if (ItemStack.canCombine(have, want)) {
			return true;
		}
		if (have.getItem() != want.getItem()) {
			return false;
		}
		if (isPotionBottle(have) && isPotionBottle(want)) {
			return net.minecraft.potion.PotionUtil.getPotion(have).equals(net.minecraft.potion.PotionUtil.getPotion(want));
		}
		return false;
	}

	private static boolean isPotionBottle(ItemStack s) {
		return s.isOf(Items.POTION) || s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION);
	}

	/** 背包里是否有能合并 want 的槽（同物同 NBT 未满叠，忽略数量）或空槽。 */
	private int findMergeTarget(ClientPlayerEntity player, ItemStack want) {
		PlayerInventory inv = player.getInventory();
		// 优先同栈合并槽
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && ItemStack.canCombine(s, want) && s.getCount() < s.getMaxCount()) {
				return i;
			}
		}
		// 其次空槽
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			if (inv.getStack(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** 是否持有全部材料：每格在候选中任选一科可满足，且同物品跨格合并计数。
	 * <p>需求合并与贪心扣减均用 {@link #stackMatches} 语义匹配（与取物阶段一致），
	 * 避免玩家实物带额外 NBT 时被严格 areEqual 误判缺料。 */
	private boolean hasAllMaterials(ClientPlayerEntity player, List<List<ItemStack>> requiredPerSlot) {
		// 需求展开：Item+NBT → 总数（同格候选互斥只计一份，不同格分开计）
		List<ItemStack> need = new ArrayList<>();
		for (List<ItemStack> alts : requiredPerSlot) {
			if (alts.isEmpty()) {
				continue;
			}
			// 该格选「背包里已有的那个候选」；若都没有则直接缺料
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
				ItemStack one = chosen.copy();
				need.add(one);
			}
		}
		if (need.isEmpty()) {
			return true;
		}
		// 贪心扣减背包（语义匹配）
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
