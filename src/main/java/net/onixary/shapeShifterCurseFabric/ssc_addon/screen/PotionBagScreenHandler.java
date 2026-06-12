package net.onixary.shapeShifterCurseFabric.ssc_addon.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem;

public class PotionBagScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final ItemStack bagStack;
	private final PlayerEntity player;

	public PotionBagScreenHandler(int syncId, PlayerInventory playerInventory, ItemStack bagStack) {
		super(SscAddon.POTION_BAG_SCREEN_HANDLER, syncId);
		this.bagStack = bagStack;
		this.player = playerInventory.player;
		// 1 Row x 9 Columns (Standard Single Chest Layout) = 9 Slots
		this.inventory = new SimpleInventory(9) {
			@Override
			public void markDirty() {
				super.markDirty();
				PotionBagScreenHandler.this.saveToNbt();
			}

			@Override
			public int getMaxCountPerStack() {
				return 8; // Max stack size: 8
			}

			@Override
			public boolean isValid(int slot, ItemStack stack) {
				return stack.getItem() instanceof PotionItem ||
						stack.getItem() instanceof SplashPotionItem ||
						stack.getItem() instanceof LingeringPotionItem ||
						stack.getItem() instanceof InfiniteEnergyPotionItem;
			}
		};

		// Load NBT
		loadFromNbt(bagStack.getNbt());
		// 清理已充满的无限药水空瓶标记，使打开时袋内显示正常名称
		cleanRechargedInfinitePotions();

		inventory.onOpen(playerInventory.player);

		// 1 Row x 9 Columns for Potion Bag
		// Standard start X=8, Y=18
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 18) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.getItem() instanceof PotionItem ||
							stack.getItem() instanceof SplashPotionItem ||
							stack.getItem() instanceof LingeringPotionItem ||
							stack.getItem() instanceof InfiniteEnergyPotionItem;
				}

				@Override
				public int getMaxItemCount(ItemStack stack) {
					// 无限压缩能量药水每瓶独立自充能，不可叠加（每格仅 1 个）
					if (stack.getItem() instanceof InfiniteEnergyPotionItem) {
						return 1;
					}
					if (stack.getItem() instanceof PotionItem ||
							stack.getItem() instanceof SplashPotionItem ||
							stack.getItem() instanceof LingeringPotionItem) {
						return 8;
					}
					return super.getMaxItemCount(stack);
				}
			});
		}

		// Player Inventory
		// Standard single chest GUI (1 row) places player inventory at Y=51 approx (actually 17 + 1*18 + 14 = 49)
		// With standard generic_54 texture, single row implies rows=1
		// Let's assume standard positioning:
		// Top chest rows end at Y = 17 + rows*18. Padding = 14. So player inv Y = 17 + 18 + 14 = 49
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
			}
		}

		// Hotbar (Y = Player Inv End + 4 = 49 + 54 + 4 = 107) -> 108 usually for 1 row chest
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 108));
		}
	}

	// Client Constructor
	public PotionBagScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ItemStack.EMPTY);
	}


	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player); // Simple close if too far
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack originalStack = slot.getStack();
			newStack = originalStack.copy();
			if (index < this.inventory.size()) {
				if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
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

	private void loadFromNbt(NbtCompound nbt) {
		if (nbt != null && nbt.contains("Items", 9)) {
			NbtList list = nbt.getList("Items", 10);
			for (int i = 0; i < list.size(); ++i) {
				NbtCompound itemTag = list.getCompound(i);
				int slot = itemTag.getByte("Slot") & 255;
				if (slot >= 0 && slot < inventory.size()) {
					inventory.setStack(slot, ItemStack.fromNbt(itemTag));
				}
			}
		}
	}

	private void saveToNbt() {
		if (!bagStack.isEmpty()) {
			NbtList list = new NbtList();
			for (int i = 0; i < inventory.size(); ++i) {
				ItemStack stack = inventory.getStack(i);
				if (!stack.isEmpty()) {
					NbtCompound itemTag = new NbtCompound();
					itemTag.putByte("Slot", (byte) i);
					stack.writeNbt(itemTag);
					list.add(itemTag);
				}
			}
			bagStack.getOrCreateNbt().put("Items", list);
		}
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		saveToNbt(); // Ensure save on close
	}

	/**
	 * 同步内容前先清理已充满的无限药水空瓶标记，使打开期间充满的药水实时恢复正常名称（仅服务端）。
	 */
	@Override
	public void sendContentUpdates() {
		cleanRechargedInfinitePotions();
		super.sendContentUpdates();
	}

	/**
	 * 清除袋内已充满的无限压缩能量药水的空瓶标记（仅服务端）。
	 * 药水袋存储物不走物品 inventoryTick，需在此主动清理，否则充满后仍显示「（空）」。
	 */
	private void cleanRechargedInfinitePotions() {
		if (player == null || player.getWorld() == null || player.getWorld().isClient) {
			return;
		}
		net.minecraft.world.World world = player.getWorld();
		for (int i = 0; i < inventory.size(); ++i) {
			ItemStack s = inventory.getStack(i);
			if (s.getItem() instanceof InfiniteEnergyPotionItem
					&& InfiniteEnergyPotionItem.clearRechargeMarkIfDone(s, world)) {
				inventory.markDirty(); // 触发写回药水袋 NBT
			}
		}
	}

	/**
	 * 读取药水包指定槽位存储的物品（NBT 结构与 {@link #saveToNbt} 一致）。
	 * 供 PotionBagItem 的快捷投放栏（槽位 0）在不打开 GUI 时直接读取，多人下读的是服务端同步过来的手持物 NBT。
	 *
	 * @return 该槽位物品；不存在时返回 {@link ItemStack#EMPTY}
	 */
	public static ItemStack getStoredStack(ItemStack bagStack, int slot) {
		NbtCompound nbt = bagStack.getNbt();
		if (nbt == null || !nbt.contains("Items", 9)) {
			return ItemStack.EMPTY;
		}
		NbtList list = nbt.getList("Items", 10);
		for (int i = 0; i < list.size(); ++i) {
			NbtCompound itemTag = list.getCompound(i);
			if ((itemTag.getByte("Slot") & 255) == slot) {
				return ItemStack.fromNbt(itemTag);
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 写回药水包指定槽位的物品（{@code stack} 为空则移除该槽位条目）。
	 * 供快捷投放栏消耗药水后更新存储，与 GUI 的 {@link #saveToNbt} 使用同一 NBT 结构。
	 */
	public static void setStoredStack(ItemStack bagStack, int slot, ItemStack stack) {
		NbtCompound nbt = bagStack.getOrCreateNbt();
		NbtList list = nbt.contains("Items", 9) ? nbt.getList("Items", 10) : new NbtList();
		for (int i = list.size() - 1; i >= 0; --i) {
			if ((list.getCompound(i).getByte("Slot") & 255) == slot) {
				list.remove(i);
			}
		}
		if (!stack.isEmpty()) {
			NbtCompound itemTag = new NbtCompound();
			itemTag.putByte("Slot", (byte) slot);
			stack.writeNbt(itemTag);
			list.add(itemTag);
		}
		nbt.put("Items", list);
	}

}
