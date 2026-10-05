package net.jackcooper.shapeShifterCurseAddon.block;

import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationItem;
import net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
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
 * 法术研究台方块实体（jackcooper）。五槽：0=空白法阵纸、1=油墨、2=月尘（学习耗材）、3=产出、4=月尘纯晶。
 *
 * <p>抄写（按钮 C2S 驱动，服务端权威重验）：耗 纸×1 + 对应系油墨×等级 → 产出对应等级法阵；
 * 学习（同上）：耗 未加工月之尘 ×(2×等级) → 学习已记录法阵。
 * 支持漏斗：上方和侧面可入纸/墨/尘/纯晶，下方只出不进。</p>
 */
public class SpellResearchTableBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, SidedInventory {

	public static final int SLOT_PAPER = 0;
	public static final int SLOT_INK = 1;
	public static final int SLOT_MOONDUST = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SLOT_CATALYST = 4;
	public static final int SLOT_ANALYSIS = 5;
	public static final int SLOT_RESEARCH_OUTPUT = 6;
	public static final int SLOT_COUNT = 7;
	public static final int ANALYSIS_TICKS = 200;
	private int analysisTicks;
	private ItemStack analysisInput = ItemStack.EMPTY;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);

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
		// Keep the old slot indices; only move pending research products into the appended output.
		if (items.get(SLOT_OUTPUT).isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.SPELL_FORMATION)
				&& items.get(SLOT_RESEARCH_OUTPUT).isEmpty()) {
			items.set(SLOT_RESEARCH_OUTPUT, items.get(SLOT_OUTPUT));
			items.set(SLOT_OUTPUT, ItemStack.EMPTY);
		}
		analysisTicks = Math.max(0, Math.min(ANALYSIS_TICKS, nbt.getInt("AnalysisTicks")));
		analysisInput = ItemStack.fromNbt(nbt.getCompound("AnalysisInput"));
	}

	@Override
	public void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, items);
		nbt.putInt("AnalysisTicks", analysisTicks);
		nbt.put("AnalysisInput", analysisInput.writeNbt(new NbtCompound()));
	}

	public int getAnalysisTicks() { return analysisTicks; }
	public enum AnalysisStatus { EMPTY, UNSUPPORTED, NO_PAPER, OUTPUT_FULL, READY, COMPLETE }
	public static boolean acceptsAnalysisInput(ItemStack stack) {
		return stack.getItem() instanceof MagicScrollItem && ScrollData.getSpell(stack) != null
				|| stack.getItem() instanceof FormationItem && FormationData.getElement(stack) != null && ArcaneAnalysis.isUnanalyzed(stack);
	}
	public static AnalysisStatus analysisStatus(net.minecraft.inventory.Inventory inventory) {
		return analysisStatus(inventory, inventory instanceof SpellResearchTableBlockEntity table ? table.getAnalysisTicks() : 0);
	}
	public static AnalysisStatus analysisStatus(net.minecraft.inventory.Inventory inventory, int ticks) {
		ItemStack input = inventory.getStack(SLOT_ANALYSIS);
		Spell spell = ScrollData.getSpell(input);
		if (!acceptsAnalysisInput(input)) return AnalysisStatus.EMPTY;
		if (input.getCount() != 1) return AnalysisStatus.UNSUPPORTED;
		if (input.getItem() instanceof MagicScrollItem && !ArcaneAnalysis.isUnanalyzed(input)
				&& !SlottedSpellRecipes.available(SlottedSpellRecipes.get(spell.getId().getPath()), ScrollData.getLevel(input))) return AnalysisStatus.UNSUPPORTED;
		if (!inventory.getStack(SLOT_OUTPUT).isEmpty()) return AnalysisStatus.OUTPUT_FULL;
		if (ticks == ANALYSIS_TICKS) return AnalysisStatus.COMPLETE;
		if (!(inventory.getStack(SLOT_PAPER).getItem() instanceof BlankFormationPaperItem)) return AnalysisStatus.NO_PAPER;
		return AnalysisStatus.READY;
	}
	public void advanceAnalysis(WorldRuneState language) {
		ItemStack input = getStack(SLOT_ANALYSIS);
		if (input.getNbt() != null && input.getNbt().contains(RuneScheme.KEY)
				&& RuneScheme.authoritativeScroll(language, input) == null) { resetAnalysis(); return; }
		if (analysisTicks == ANALYSIS_TICKS && ItemStack.areEqual(input, analysisInput)) return;
		if (analysisStatus(this) != AnalysisStatus.READY) {
			resetAnalysis(); return;
		}
		if (!ItemStack.areEqual(input, analysisInput)) { analysisTicks = 0; analysisInput = input.copy(); }
		analysisTicks++;
		if (analysisTicks < ANALYSIS_TICKS) { if (analysisTicks % 20 == 0) markDirty(); return; }
		ItemStack result = input.getItem() instanceof MagicScrollItem
				? AnalyzedSpellDiagramItem.create(language, input) : ItemStack.EMPTY;
		boolean returnOriginal = result.isEmpty() && ArcaneAnalysis.isUnanalyzed(input);
		if (returnOriginal) { result = input.copy(); ArcaneAnalysis.identify(result); }
		if (result.isEmpty()) { resetAnalysis(); return; }
		getStack(SLOT_PAPER).decrement(1);
		if (returnOriginal) setStack(SLOT_ANALYSIS, ItemStack.EMPTY);
		else ArcaneAnalysis.identify(input);
		setStack(SLOT_OUTPUT, result);
		if (returnOriginal) resetAnalysis();
		else {
			analysisTicks = ANALYSIS_TICKS;
			analysisInput = input.copy();
		}
		markDirty();
	}
	private void resetAnalysis() {
		if (analysisTicks != 0 || !analysisInput.isEmpty()) { analysisTicks = 0; analysisInput = ItemStack.EMPTY; markDirty(); }
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
		if (slot == SLOT_ANALYSIS) resetAnalysis();
		return Inventories.splitStack(items, slot, amount);
	}

	@Override
	public ItemStack removeStack(int slot) {
		if (slot == SLOT_ANALYSIS) resetAnalysis();
		return Inventories.removeStack(items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (slot == SLOT_ANALYSIS) resetAnalysis();
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
		resetAnalysis();
		items.clear();
	}

	/** 供方块破坏时散落物品。 */
	public DefaultedList<ItemStack> getItems() {
		return items;
	}

	// ---- SidedInventory（漏斗支持） ----

	@Override
	public int[] getAvailableSlots(Direction side) {
		return side == Direction.DOWN ? new int[]{SLOT_OUTPUT, SLOT_RESEARCH_OUTPUT} : new int[]{SLOT_PAPER, SLOT_INK, SLOT_MOONDUST, SLOT_CATALYST, SLOT_ANALYSIS};
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		if (slot == SLOT_ANALYSIS) return acceptsAnalysisInput(stack);
		if (slot == SLOT_PAPER) {
			return stack.getItem() instanceof BlankFormationPaperItem;
		}
		if (slot == SLOT_INK) {
			return stack.getItem() instanceof FormationInkItem;
		}
		if (slot == SLOT_MOONDUST) {
			return stack.getItem() == RegCustomItem.UNTREATED_MOONDUST;
		}
		if (slot == SLOT_CATALYST) {
			return stack.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD);
		}
		return false; // 产出槽不可入
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return slot == SLOT_OUTPUT || slot == SLOT_RESEARCH_OUTPUT; // 只允许抽出产出
	}
}
