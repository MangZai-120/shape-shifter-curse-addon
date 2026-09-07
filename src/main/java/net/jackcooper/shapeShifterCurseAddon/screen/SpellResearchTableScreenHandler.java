package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 法术研究台界面容器（jackcooper）。四槽：0=空白法阵纸、1=油墨、2=月尘、3=产出。
 * 抄写/学习由页签按钮 C2S 包驱动（服务端权威重验后扣材料），界面只负责放取物品。
 */
public class SpellResearchTableScreenHandler extends ScreenHandler {
	private final Inventory inventory;

	/** 供 C2S 包定位研究台方块实体（服务端权威重验用）。 */
	public Inventory getInventory() {
		return this.inventory;
	}

	public SpellResearchTableScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(4));
	}

	public SpellResearchTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.SPELL_RESEARCH_TABLE_SH, syncId);
		checkSize(inventory, 4);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		// 纸槽（左上）
		this.addSlot(new Slot(inventory, 0, 14, 25) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof BlankFormationPaperItem;
			}
		});
		// 油墨槽（左中）
		this.addSlot(new Slot(inventory, 1, 14, 50) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof FormationInkItem;
			}
		});
		// 月尘槽（左下）
		this.addSlot(new Slot(inventory, 2, 14, 75) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST;
			}
		});
		// 产出槽（面板下方中间）
		this.addSlot(new Slot(inventory, 3, 84, 99) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return false; // 产出槽只取不放
			}
		});

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
			if (index < 4) {
				if (!this.insertItem(original, 4, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// 按物品类型路由到对应耗材槽（产出槽不可入）
				boolean routed = this.insertItem(original, 0, 1, false)   // 纸
						|| this.insertItem(original, 1, 2, false)          // 墨
						|| this.insertItem(original, 2, 3, false);         // 尘
				if (!routed) {
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
