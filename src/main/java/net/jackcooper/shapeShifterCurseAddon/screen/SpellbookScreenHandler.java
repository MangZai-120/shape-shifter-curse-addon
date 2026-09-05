package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * 月尘魔法书配置界面容器（jackcooper）。卷轴槽数随书等级动态（3/5/7），
 * 通过 Fabric {@code ExtendedScreenHandler} 把槽数与等级/法力快照传给客户端。
 * 卷轴内容序列化进书自身 NBT 的 {@code Items} 列表（与 {@link SpellbookData} 同结构）。
 */
public class SpellbookScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final ItemStack bookStack;
	public final int slotCount;
	public final int bookLevel;
	public final int bookExp;
	public final int bookMana;
	public final int bookMaxMana;

	/** 服务端构造：拿到真实魔法书 stack。 */
	public SpellbookScreenHandler(int syncId, PlayerInventory playerInv, ItemStack bookStack) {
		this(syncId, playerInv, bookStack,
				SpellbookData.getSlotCount(bookStack),
				SpellbookData.getLevel(bookStack),
				SpellbookData.getExp(bookStack),
				SpellbookData.getMana(bookStack),
				SpellbookData.getMaxMana(bookStack));
	}

	/** 客户端构造：从开屏数据包读取槽数与等级/法力快照。 */
	public SpellbookScreenHandler(int syncId, PlayerInventory playerInv, PacketByteBuf buf) {
		this(syncId, playerInv, ItemStack.EMPTY,
				buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
	}

	private SpellbookScreenHandler(int syncId, PlayerInventory playerInv, ItemStack bookStack,
			int slotCount, int level, int exp, int mana, int maxMana) {
		super(SscAddon.SPELLBOOK_SCREEN_HANDLER, syncId);
		this.bookStack = bookStack;
		this.slotCount = Math.max(1, Math.min(SpellbookData.MAX_SLOTS, slotCount));
		this.bookLevel = level;
		this.bookExp = exp;
		this.bookMana = mana;
		this.bookMaxMana = maxMana;

		this.inventory = new SimpleInventory(this.slotCount) {
			@Override
			public void markDirty() {
				super.markDirty();
				SpellbookScreenHandler.this.saveToNbt();
			}

			@Override
			public int getMaxCountPerStack() {
				return 1; // 每槽一张卷轴
			}

			@Override
			public boolean isValid(int slot, ItemStack stack) {
				return ScrollData.isScroll(stack);
			}
		};

		loadFromNbt(bookStack.getNbt());
		inventory.onOpen(playerInv.player);

		// 卷轴槽横排居中（GUI 宽 176）
		int totalWidth = this.slotCount * 18;
		int startX = (176 - totalWidth) / 2 + 1;
		for (int i = 0; i < this.slotCount; ++i) {
			this.addSlot(new Slot(inventory, i, startX + i * 18, 42) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return ScrollData.isScroll(stack);
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
				this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
		}
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
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
		if (nbt != null && nbt.contains(SpellbookData.NBT_ITEMS, 9)) {
			NbtList list = nbt.getList(SpellbookData.NBT_ITEMS, 10);
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
		if (!bookStack.isEmpty()) {
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
			bookStack.getOrCreateNbt().put(SpellbookData.NBT_ITEMS, list);
		}
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		saveToNbt();
	}
}
