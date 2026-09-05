package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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

	@Override
	public double getPriority() {
		// 高于默认（0），抢在 REI 原生搬运之前接管我们的配方
		return 50d;
	}

	@Override
	public ApplicabilityResult checkApplicable(Context context) {
		// 仅接管我们自己注册的 SSCA 特殊配方卡片
		if (!(context.getDisplay() instanceof SscSpecialCraftingDisplay)) {
			return ApplicabilityResult.createNotApplicable();
		}
		// 必须在 3×3 工作台界面（物品栏 2×2 放不下 3×3 配方）
		if (!(context.getMenu() instanceof net.minecraft.screen.CraftingScreenHandler)) {
			return ApplicabilityResult.createNotApplicable();
		}
		return ApplicabilityResult.createApplicable();
	}

	@Override
	public Result handle(Context context) {
		SscSpecialCraftingDisplay display = (SscSpecialCraftingDisplay) context.getDisplay();
		MinecraftClient client = context.getMinecraft();
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
		}

		// 预检：背包必须持有全部材料（含 NBT 精确匹配），否则提示材料不足
		if (!hasAllMaterials(player, display.getRequiredPerSlot())) {
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

		// 第二步：按配方把材料逐格放入（含 NBT 的药水按真实栈精确匹配；每格多候选任选其一）
		List<List<ItemStack>> requiredPerSlot = display.getRequiredPerSlot();
		for (int i = 0; i < 9 && i < requiredPerSlot.size(); i++) {
			List<ItemStack> alternatives = requiredPerSlot.get(i);
			if (alternatives.isEmpty()) {
				continue;
			}
			int srcInv = findAnyStack(player, alternatives);
			if (srcInv < 0) {
				// 材料中途变了（预检后被移动）：已放材料留在格中（可见无损），报材料不足
				return Result.createFailed(Text.translatable("error.rei.not.enough.materials"));
			}
			int gridSlot = CRAFT_GRID_FIRST + i;
			// 右键拿起 1 个（避免整叠拿起），再放入合成格
			im.clickSlot(handler.syncId, invToHandler(srcInv), 1, SlotActionType.PICKUP, player);
			if (handler.getCursorStack().isEmpty()) {
				return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
			}
			im.clickSlot(handler.syncId, gridSlot, 0, SlotActionType.PICKUP, player);
			if (!handler.getCursorStack().isEmpty()) {
				// 合成格未接住（异常状态）：把光标残留倒回背包，终止
				dumpCursor(im, handler, player);
				return Result.createFailed(Text.translatable("error.ssc_addon.transfer.failed"));
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

	/** 背包（含热bar）里找到与候选任一完全一致的栈（Item+NBT），返回背包索引；找不到返回 -1。 */
	private int findAnyStack(ClientPlayerEntity player, List<ItemStack> alternatives) {
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) {
				continue;
			}
			for (ItemStack alt : alternatives) {
				if (ItemStack.areEqual(s, alt)) {
					return i;
				}
			}
		}
		return -1;
	}

	/** 背包里是否有能合并 want 的槽（同物同 NBT 未满叠）或空槽。 */
	private int findMergeTarget(ClientPlayerEntity player, ItemStack want) {
		PlayerInventory inv = player.getInventory();
		// 优先同栈合并槽
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && ItemStack.areEqual(s, want) && s.getCount() < s.getMaxCount()) {
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

	/** 是否持有全部材料：每格在候选中任选一科可满足，且同物品跨格合并计数。 */
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
				if (ItemStack.areEqual(n, chosen)) {
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
		// 贪心扣减背包
		for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.isEmpty()) {
				continue;
			}
			for (ItemStack n : need) {
				if (n.getCount() > 0 && ItemStack.areEqual(s, n)) {
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
