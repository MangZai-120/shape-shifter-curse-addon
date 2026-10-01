package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 注魔台界面容器。九槽：0=中央魔法书、1=燃料、2=催化、3-8=六芒星法阵角槽。
 * 充能由方块实体每秒结算，升级需主动点击；界面负责放取物品与展示书信息。
 *
 * <p>六芒星角槽规则：书槽无书时锁定；有书时按书等级解锁角位（一级书 1 角 / 二级 3 角 / 三级 6 角），
 * 锁定角 canInsert=false（可取出不可放入）。角槽改动经 Inventory.setStack 回调三态同步书 NBT。</p>
 */
public class InfusionAltarScreenHandler extends ScreenHandler {
	public static final int GUI_WIDTH = 312, GUI_HEIGHT = 232;
	public static final int ALTAR_SLOT_COUNT = 3 + SpellbookData.MAX_FORMATION_SLOTS;
	public static final int INVENTORY_X = 12, INVENTORY_Y = 148, HOTBAR_Y = 206;
	public static final int BOOK_X = 88, BOOK_Y = 63;
	public static final int FUEL_X = 212, CATALYST_X = 264, MATERIAL_Y = 110;
	private final Inventory inventory;

	/** 供 C2S 升级包定位注魔台方块实体（服务端权威重验用）。 */
	public Inventory getInventory() {
		return this.inventory;
	}

	/** 正六芒星角槽：中心 (96,71)，半径 34；左右半宽取 34×sqrt(3)/2 的整数近似。 */
	public static final int[][] HEXAGRAM_SLOT_POS = {
			{88, 29},  // 0 顶角（Lv1 解锁）
			{59, 46},  // 1 左上（Lv2 解锁）
			{117, 46}, // 2 右上（Lv2 解锁）
			{59, 80},  // 3 左下（Lv3 解锁）
			{117, 80}, // 4 右下（Lv3 解锁）
			{88, 97}   // 5 底角（Lv3 解锁；保留原有五槽的 NBT 编号）
	};

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(ALTAR_SLOT_COUNT));
	}

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.INFUSION_ALTAR_SH, syncId);
		checkSize(inventory, ALTAR_SLOT_COUNT);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		// 书槽（六芒星中心）
		this.addSlot(new Slot(inventory, 0, BOOK_X, BOOK_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof MoonDustSpellbookItem;
			}

			@Override
			public int getMaxItemCount(ItemStack stack) {
				return 1;
			}
		});
		// 材料区：燃料也提供升级所需的纯晶。
		this.addSlot(new Slot(inventory, 1, FUEL_X, MATERIAL_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST
						|| stack.getItem() == RegCustomItem.MOONDUST_CRYSTAL_SHARD;
			}
		});
		// 催化槽：超级塑形核心。
		this.addSlot(new Slot(inventory, 2, CATALYST_X, MATERIAL_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.SUPER_MORPHSCALE_CORE;
			}
		});
		// 六芒星法阵角槽 3-8
		for (int i = 0; i < SpellbookData.MAX_FORMATION_SLOTS; i++) {
			final int formationSlot = i;
			this.addSlot(new Slot(inventory, 3 + i, HEXAGRAM_SLOT_POS[i][0], HEXAGRAM_SLOT_POS[i][1]) {
				@Override
				public boolean canInsert(ItemStack stack) {
					// 无书或角位未解锁 → 锁定；有书且解锁 → 只收法阵
					ItemStack book = inventory.getStack(0);
					if (!(book.getItem() instanceof MoonDustSpellbookItem) || book.isEmpty()) {
						return false;
					}
					if (!SpellbookData.isFormationSlotUnlocked(book, formationSlot)) {
						return false;
					}
					return FormationData.isFormation(stack) && !net.jackcooper.shapeShifterCurseAddon.spell.ArcaneAnalysis.isUnanalyzed(stack);
				}

				@Override
				public int getMaxItemCount(ItemStack stack) {
					return 1;
				}
			});
		}

		// 玩家背包
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			newStack = original.copy();
			if (index < ALTAR_SLOT_COUNT) {
				if (!this.insertItem(original, ALTAR_SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (original.getItem() instanceof MoonDustSpellbookItem) {
				// 优先书槽
				if (!this.insertItem(original, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (FormationData.isFormation(original)) {
				// shift 法阵 → 尝试六芒星角槽
				if (!this.insertItem(original, 3, ALTAR_SLOT_COUNT, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (!this.insertItem(original, 1, 3, false)) {
					return ItemStack.EMPTY;
				}
			}
			if (original.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return newStack;
	}
}
