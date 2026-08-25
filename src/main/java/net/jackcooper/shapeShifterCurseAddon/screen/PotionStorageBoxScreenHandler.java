package net.jackcooper.shapeShifterCurseAddon.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.PotionStorageBoxBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;

/**
 * 药品存储箱容器（jackcooper）：8 个存储槽（单排），仅接受压缩能量药水（feed_potion），每格叠放上限
 * {@link PotionStorageBoxBlockEntity#MAX_PER_SLOT}。
 */
public class PotionStorageBoxScreenHandler extends ScreenHandler {

	private static final int CONTAINER_SLOTS = PotionStorageBoxBlockEntity.SLOT_COUNT;

	private final Inventory inventory;

	/** 客户端构造。 */
	public PotionStorageBoxScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(CONTAINER_SLOTS));
	}

	/** 服务端构造。 */
	public PotionStorageBoxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.POTION_STORAGE_BOX_SH, syncId);
		checkSize(inventory, CONTAINER_SLOTS);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		// 8 个存储槽（单排，居中）：坐标对齐 GUI 贴图槽位内部（贴图内边框起点 x=16/y=20，物品区从 x=17/y=21 起）
		for (int i = 0; i < CONTAINER_SLOTS; i++) {
			this.addSlot(new Slot(inventory, i, 17 + i * 18, 21) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return EnergyBottlerBlockEntity.isEnergyBottle(stack);
				}

				@Override
				public int getMaxItemCount() {
					return PotionStorageBoxBlockEntity.MAX_PER_SLOT;
				}

				@Override
				public int getMaxItemCount(ItemStack stack) {
					return PotionStorageBoxBlockEntity.MAX_PER_SLOT;
				}
			});
		}

		// 玩家背包（3 行）：对齐贴图内部 y=50/68/86
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
			}
		}
		// 快捷栏：对齐贴图内部 y=108
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 108));
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
			ItemStack originalStack = slot.getStack();
			newStack = originalStack.copy();
			if (index < CONTAINER_SLOTS) {
				// 存储箱 → 玩家背包
				if (!this.insertItem(originalStack, CONTAINER_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (EnergyBottlerBlockEntity.isEnergyBottle(originalStack)) {
				// 玩家背包 → 存储箱（仅能量瓶）
				if (!this.insertItem(originalStack, 0, CONTAINER_SLOTS, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}

			if (originalStack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return newStack;
	}
}
