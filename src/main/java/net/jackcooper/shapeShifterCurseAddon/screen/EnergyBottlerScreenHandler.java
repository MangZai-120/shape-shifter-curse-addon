package net.jackcooper.shapeShifterCurseAddon.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;

/**
 * 能量装瓶器容器（jackcooper）。
 * <p>三条独立合成线：左 3 个空玻璃瓶输入槽、右 3 个能量瓶输出槽（每格叠放上限
 * {@link EnergyBottlerBlockEntity#OUTPUT_MAX}）。网络能量 / 三线进度 / 手动自动经 {@link PropertyDelegate} 同步。
 * GUI 按钮经 {@link #onButtonClick} 服务端处理（id 0=切总开关，1=手动对三线各请求一次合成）。
 */
public class EnergyBottlerScreenHandler extends ScreenHandler {

	/** 按钮 id：切换手动/自动总开关。 */
	public static final int BUTTON_TOGGLE_MODE = 0;
	/** 按钮 id：手动请求三线各合成一次。 */
	public static final int BUTTON_MANUAL_CRAFT = 1;

	private static final int CONTAINER_SLOTS = EnergyBottlerBlockEntity.LINES * 2;

	private final Inventory inventory;
	private final PropertyDelegate propertyDelegate;

	/** 客户端构造。 */
	public EnergyBottlerScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(CONTAINER_SLOTS), new ArrayPropertyDelegate(6));
	}

	/** 服务端构造。 */
	public EnergyBottlerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
		super(RegAddonBlockEntities.ENERGY_BOTTLER_SH, syncId);
		checkSize(inventory, CONTAINER_SLOTS);
		this.inventory = inventory;
		this.propertyDelegate = propertyDelegate;
		inventory.onOpen(playerInventory.player);
		this.addProperties(propertyDelegate);

		int lines = EnergyBottlerBlockEntity.LINES;
		// 三条线：输入槽（空玻璃瓶）与输出槽（能量瓶）
		for (int i = 0; i < lines; i++) {
			int y = 18 + i * 24;
			// 输入槽
			this.addSlot(new Slot(inventory, i, 44, y) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.isOf(Items.GLASS_BOTTLE);
				}

				@Override
				public int getMaxItemCount() {
					return 64;
				}
			});
			// 输出槽（仅取出，可叠 OUTPUT_MAX）
			this.addSlot(new Slot(inventory, lines + i, 108, y) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return false;
				}

				@Override
				public int getMaxItemCount() {
					return EnergyBottlerBlockEntity.OUTPUT_MAX;
				}

				@Override
				public int getMaxItemCount(ItemStack stack) {
					return EnergyBottlerBlockEntity.OUTPUT_MAX;
				}
			});
		}

		// 玩家背包（3 行）
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 160));
		}
	}

	public int getEnergy() {
		return propertyDelegate.get(0);
	}

	public int getCapacity() {
		return propertyDelegate.get(1);
	}

	public int getProgress(int line) {
		return propertyDelegate.get(2 + line);
	}

	public boolean isAutoMode() {
		return propertyDelegate.get(5) != 0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (inventory instanceof EnergyBottlerBlockEntity be) {
			if (id == BUTTON_TOGGLE_MODE) {
				be.toggleAutoMode();
				return true;
			}
			if (id == BUTTON_MANUAL_CRAFT) {
				be.requestManualCraft();
				return true;
			}
		}
		return false;
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
				// 容器 → 玩家背包
				if (!this.insertItem(originalStack, CONTAINER_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (originalStack.isOf(Items.GLASS_BOTTLE)) {
				// 玩家背包 → 输入槽（仅空玻璃瓶，索引 0~2）
				if (!this.insertItem(originalStack, 0, EnergyBottlerBlockEntity.LINES, false)) {
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
