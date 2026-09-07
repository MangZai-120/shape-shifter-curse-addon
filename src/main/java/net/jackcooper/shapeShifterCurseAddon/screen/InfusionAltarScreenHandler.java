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
 * 注魔台界面容器（jackcooper）。八槽：0=魔法书（五角星中心）、1=燃料、2=催化、3-7=五角星法阵角槽。
 * 充能/升级由方块实体每秒结算，界面只负责放取物品与展示书信息。
 *
 * <p>五角星角槽规则：书槽无书时锁定；有书时按书等级解锁角位（一级书 1 角 / 二级 3 角 / 三级 5 角），
 * 锁定角 canInsert=false（可取出不可放入）。角槽改动经 Inventory.setStack 回调三态同步书 NBT。</p>
 */
public class InfusionAltarScreenHandler extends ScreenHandler {
	private final Inventory inventory;

	/** 供 C2S 升级包定位注魔台方块实体（服务端权威重验用）。 */
	public Inventory getInventory() {
		return this.inventory;
	}

	/** 五角星五个角槽的界面坐标（数组下标 = 解锁顺序：Lv1 顶角，Lv2 +左右上，Lv3 +左右下；连线取各槽中心）。 */
	public static final int[][] PENTAGRAM_SLOT_POS = {
			{80, 18},  // 0 顶角（Lv1 解锁）
			{50, 43},  // 1 左上（Lv2 解锁）
			{110, 43}, // 2 右上（Lv2 解锁）
			{65, 79},  // 3 左下（Lv3 解锁）
			{95, 79}   // 4 右下（Lv3 解锁）
	};

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(8));
	}

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.INFUSION_ALTAR_SH, syncId);
		checkSize(inventory, 8);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		// 书槽（五角星中心）
		this.addSlot(new Slot(inventory, 0, 80, 50) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof MoonDustSpellbookItem;
			}

			@Override
			public int getMaxItemCount(ItemStack stack) {
				return 1;
			}
		});
		// 燃料槽（左上）
		this.addSlot(new Slot(inventory, 1, 16, 26) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST
						|| stack.getItem() == RegCustomItem.MOONDUST_CRYSTAL_SHARD;
			}
		});
		// 催化槽（右上）
		this.addSlot(new Slot(inventory, 2, 140, 26) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.SUPER_MORPHSCALE_CORE;
			}
		});
		// 五角星法阵角槽 3-7
		for (int i = 0; i < SpellbookData.MAX_FORMATION_SLOTS; i++) {
			final int formationSlot = i;
			this.addSlot(new Slot(inventory, 3 + i, PENTAGRAM_SLOT_POS[i][0], PENTAGRAM_SLOT_POS[i][1]) {
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
					return FormationData.isFormation(stack);
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
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 130 + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 188));
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
			if (index < 8) {
				if (!this.insertItem(original, 8, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (original.getItem() instanceof MoonDustSpellbookItem) {
				// 优先书槽
				if (!this.insertItem(original, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (FormationData.isFormation(original)) {
				// shift 法阵 → 尝试五角星角槽
				if (!this.insertItem(original, 3, 8, false)) {
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
