package net.jackcooper.shapeShifterCurseAddon.block;

import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 法术研究台方块实体（jackcooper）。四槽：0=空白法阵纸、1=油墨、2=月尘（学习耗材）、3=产出。
 *
 * <p>抄写（按钮 C2S 驱动，服务端权威重验）：耗 纸×1 + 对应系油墨×等级 → 产出对应等级法阵；
 * 学习（同上）：耗 未加工月之尘 ×(2×等级) → 学习已记录法阵。
 * 支持漏斗：上/下/侧面向均可入纸/墨/尘（0-2 槽），产出面（下方）只出不进。</p>
 */
public class SpellResearchTableBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, SidedInventory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(4, ItemStack.EMPTY);

	public static final int SLOT_PAPER = 0;
	public static final int SLOT_INK = 1;
	public static final int SLOT_MOONDUST = 2;
	public static final int SLOT_OUTPUT = 3;

	public SpellResearchTableBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.SPELL_RESEARCH_TABLE_BE, pos, state);
	}

	// ---- NamedScreenHandlerFactory ----
	@Override
	public Text getDisplayName() {
		return Text.translatable("block.ssc_addon.spell_research_table");
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SpellResearchTableScreenHandler(syncId, playerInventory, this);
	}

	// ---- NBT ----
	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		items.clear();
		Inventories.readNbt(nbt, items);
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, items);
	}

	// ---- Inventory ----
	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		return Inventories.splitStack(items, slot, amount);
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			world.updateComparators(pos, getCachedState().getBlock());
		}
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.world != null && this.world.getBlockEntity(this.pos) == this
				&& player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		items.clear();
	}

	/** 供方块破坏时散落物品。 */
	public DefaultedList<ItemStack> getItems() {
		return items;
	}

	// ---- SidedInventory（漏斗支持） ----

	@Override
	public int[] getAvailableSlots(Direction side) {
		// 下方只暴露产出槽（只出不进）；其余方向暴露耗材三槽（只进不出）
		return side == Direction.DOWN ? new int[]{SLOT_OUTPUT} : new int[]{SLOT_PAPER, SLOT_INK, SLOT_MOONDUST};
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		if (slot == SLOT_PAPER) {
			return stack.getItem() instanceof BlankFormationPaperItem;
		}
		if (slot == SLOT_INK) {
			return stack.getItem() instanceof FormationInkItem;
		}
		if (slot == SLOT_MOONDUST) {
			return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST;
		}
		return false; // 产出槽不可入
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == SLOT_OUTPUT; // 只允许抽出产出
	}
}
