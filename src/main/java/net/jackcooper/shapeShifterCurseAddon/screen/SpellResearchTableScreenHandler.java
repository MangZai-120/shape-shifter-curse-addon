package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 法术研究台界面容器（jackcooper）。五槽：0=空白法阵纸、1=油墨、2=月尘、3=产出、4=月尘纯晶。
 * 抄写/学习由页签按钮 C2S 包驱动（服务端权威重验后扣材料），界面只负责放取物品。
 */
public class SpellResearchTableScreenHandler extends ScreenHandler {
	public static final int GUI_WIDTH = 312;
	public static final int GUI_HEIGHT = 212;
	public static final int INVENTORY_X = 76;
	private final Inventory inventory;

	/** 供 C2S 包定位研究台方块实体（服务端权威重验用）。 */
	public Inventory getInventory() {
		return this.inventory;
	}

	public SpellResearchTableScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(SpellResearchTableBlockEntity.SLOT_COUNT));
	}

	public SpellResearchTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
		super(RegAddonBlockEntities.SPELL_RESEARCH_TABLE_SH, syncId);
		checkSize(inventory, SpellResearchTableBlockEntity.SLOT_COUNT);
		this.inventory = inventory;
		inventory.onOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 154, 72) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof BlankFormationPaperItem;
			}
		});
		this.addSlot(new Slot(inventory, 1, 182, 72) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof FormationInkItem;
			}
		});
		this.addSlot(new Slot(inventory, 2, 210, 72) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST;
			}
		});
		this.addSlot(new Slot(inventory, 3, 282, 72) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof MagicScrollItem && ScrollData.getSpell(stack) != null;
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		this.addSlot(new Slot(inventory, SpellResearchTableBlockEntity.SLOT_CATALYST, 240, 72) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD);
			}
		});

		// 玩家背包
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, 130 + row * 18));
			}
		}
		// 快捷栏
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, 188));
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
			if (index < SpellResearchTableBlockEntity.SLOT_COUNT) {
				if (!this.insertItem(original, SpellResearchTableBlockEntity.SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				boolean routed = this.insertItem(original, 3, 4, false)
						|| this.insertItem(original, SpellResearchTableBlockEntity.SLOT_CATALYST, SpellResearchTableBlockEntity.SLOT_COUNT, false)
						|| this.insertItem(original, 0, 1, false)   // 纸
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
