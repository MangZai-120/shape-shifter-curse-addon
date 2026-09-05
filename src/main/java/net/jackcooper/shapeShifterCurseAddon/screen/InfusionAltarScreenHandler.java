package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 注魔台界面容器（jackcooper）。三槽：0=魔法书、1=燃料（月尘粉/纯晶）、2=催化（超级塑形核心）。
 * 充能/升级由方块实体每秒结算，界面只负责放取物品与展示书信息。
 */
public class InfusionAltarScreenHandler extends ScreenHandler {
	private final Inventory inventory;

	/** 供 C2S 升级包定位注魔台方块实体（服务端权威重验用）。 */
	public Inventory getInventory() {
		return this.inventory;
	}

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(3));
	}

	public InfusionAltarScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.INFUSION_ALTAR_SH, syncId);
		checkSize(inventory, 3);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		// 书槽（居中偏上）
		this.addSlot(new Slot(inventory, 0, 80, 20) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof MoonDustSpellbookItem;
			}

			@Override
			public int getMaxItemCount(ItemStack stack) {
				return 1;
			}
		});
		// 燃料槽（左下）
		this.addSlot(new Slot(inventory, 1, 44, 52) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST
						|| stack.getItem() == RegCustomItem.MOONDUST_CRYSTAL_SHARD;
			}
		});
		// 催化槽（右下）
		this.addSlot(new Slot(inventory, 2, 116, 52) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.SUPER_MORPHSCALE_CORE;
			}
		});

		// 玩家背包
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
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
			if (index < 3) {
				if (!this.insertItem(original, 3, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.insertItem(original, 0, 3, false)) {
				return ItemStack.EMPTY;
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
