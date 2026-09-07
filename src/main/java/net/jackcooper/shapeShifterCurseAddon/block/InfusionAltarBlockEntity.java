package net.jackcooper.shapeShifterCurseAddon.block;

import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import org.jetbrains.annotations.Nullable;

/**
 * 注魔台方块实体（jackcooper）。八槽：0=魔法书、1=燃料（月尘粉/月尘纯晶）、2=催化（超级塑形核心）、
 * 3-7=五角星法阵槽（书放入中心时动态显示书内已装备法阵）。
 *
 * <p>充能/升级逻辑同前：每秒结算一次充能；材料齐备可升级时暂停充能等玩家点「升级」按钮。</p>
 *
 * <p><b>五角星双向同步（三态快照）</b>：以「上一次同步进书的内容」为基准对比——
 * 槽变（玩家在角槽放/取法阵）→ 把槽内容写回书 NBT；书变（玩家直接换了书）→ 把新书 NBT 加载进角槽。
 * 拿走书后角槽清空（法阵已随书带走），下一本书放入时重新加载。</p>
 */
public class InfusionAltarBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(8, ItemStack.EMPTY);
	/** 上一次同步进书的五角星内容快照（null = 从未同步，需要初始加载）。 */
	@Nullable
	private NbtCompound lastSyncedFormations;

	public InfusionAltarBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.INFUSION_ALTAR_BE, pos, state);
	}

	/** 升级条件是否齐备（经验够 + 催化槽超核 + 燃料槽纯晶）。 */
	private boolean upgradeReady() {
		ItemStack book = items.get(0);
		ItemStack fuel = items.get(1);
		ItemStack catalyst = items.get(2);
		return book.getItem() instanceof MoonDustSpellbookItem
				&& SpellbookData.canLevelUp(book)
				&& catalyst.getItem() == RegCustomItem.SUPER_MORPHSCALE_CORE && !catalyst.isEmpty()
				&& fuel.getItem() == RegCustomItem.MOONDUST_CRYSTAL_SHARD && !fuel.isEmpty();
	}

	/**
	 * 玩家点击界面「升级」按钮（C2S 包服务端重验后调用）：扣材料、书 +1 级、经验清零、法力补满。
	 * 升级会解锁更多五角星角位——已有角内法阵保留（书 NBT 不动），重置快照重新加载显示。
	 */
	public void tryUpgrade(PlayerEntity player) {
		World world = getWorld();
		if (world == null || world.isClient || !upgradeReady()) {
			return;
		}
		items.get(2).decrement(1); // 催化槽超核
		items.get(1).decrement(1); // 燃料槽月尘纯晶
		ItemStack book = items.get(0);
		SpellbookData.setLevel(book, SpellbookData.getLevel(book) + 1);
		SpellbookData.setExp(book, 0);
		SpellbookData.setMana(book, SpellbookData.getMaxMana(book)); // 升级补满法力
		// 升级可能解锁新角位：重置快照强制重新加载（书变分支），角槽显示书内当前法阵
		this.lastSyncedFormations = null;
		syncFormations();
		markDirty();
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.4f);
		// 强制把书的新 NBT 同步给开着界面的玩家（书物品不变、仅 NBT 变化，默认增量同步不会重发）
		if (player instanceof ServerPlayerEntity sp && sp.currentScreenHandler != null) {
			sp.currentScreenHandler.sendContentUpdates();
			sp.currentScreenHandler.updateToClient();
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, InfusionAltarBlockEntity be) {
		if (world.isClient || world.getTime() % 20 != 0) {
			return;
		}
		// 五角星双向同步（每秒结算一次，覆盖「无 GUI 直接放书/漏斗放书」场景）
		be.syncFormations();
		ItemStack book = be.items.get(0);
		if (!(book.getItem() instanceof MoonDustSpellbookItem)) {
			return;
		}
		// 材料齐备可升级时暂停自动充能，避免把升级用的纯晶当普通燃料吃掉，等玩家点「升级」按钮
		if (be.upgradeReady()) {
			return;
		}
		ItemStack fuel = be.items.get(1);

		Item untreated = RegCustomItem.UNTREATED_MOONDUST;
		Item crystal = RegCustomItem.MOONDUST_CRYSTAL_SHARD;

		// 充能：法力未满 + 有燃料
		int mana = SpellbookData.getMana(book);
		int maxMana = SpellbookData.getMaxMana(book);
		if (mana < maxMana && !fuel.isEmpty()) {
			int add = 0;
			if (fuel.getItem() == untreated) {
				add = 10;
			} else if (fuel.getItem() == crystal) {
				add = 80;
			}
			if (add > 0) {
				fuel.decrement(1);
				SpellbookData.addMana(book, add);
				be.markDirty();
				world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 1.2f);
			}
		}
	}

	// ---- 五角星三态同步 ----

	/**
	 * 五角星双向同步：把角槽(3-7)当前内容与 {@link #lastSyncedFormations} 对比：
	 * <ul>
	 *   <li><b>无书</b>：清空快照与角槽显示（法阵已随书带走）；</li>
	 *   <li><b>初始</b>（快照为 null）：书 NBT → 角槽；</li>
	 *   <li><b>槽变</b>（角槽内容与快照不一致）：角槽 → 书 NBT；</li>
	 *   <li><b>书变</b>（书 NBT 的 Formations 与快照不一致）：书 NBT → 角槽。</li>
	 * </ul>
	 * 书与槽同时变（极小概率）时以槽变优先（玩家刚操作完的界面状态为准）。
	 */
	public void syncFormations() {
		if (world == null || world.isClient) {
			return;
		}
		ItemStack book = items.get(0);
		if (!(book.getItem() instanceof MoonDustSpellbookItem) || book.isEmpty()) {
			// 无书：清快照 + 清空角槽显示
			this.lastSyncedFormations = null;
			for (int i = 3; i < 8; i++) {
				items.set(i, ItemStack.EMPTY);
			}
			return;
		}
		if (this.lastSyncedFormations == null) {
			// 初始：书 → 槽
			loadFromBook();
			return;
		}
		NbtCompound slotsSnapshot = slotsSnapshotOf();
		NbtCompound bookSnapshot = bookSnapshotOf(book);
		boolean slotsChanged = !slotsSnapshot.equals(this.lastSyncedFormations);
		boolean bookChanged = !bookSnapshot.equals(this.lastSyncedFormations);
		if (slotsChanged) {
			// 槽 → 书（玩家在角槽放/取了法阵）
			for (int slot = 0; slot < SpellbookData.MAX_FORMATION_SLOTS; slot++) {
				ItemStack stack = items.get(3 + slot);
				SpellbookData.setFormation(book, slot,
						(!stack.isEmpty() && FormationData.isFormation(stack)) ? stack : ItemStack.EMPTY);
			}
			this.lastSyncedFormations = bookSnapshotOf(book);
			markDirty();
			world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.3f);
		} else if (bookChanged) {
			// 书 → 槽（换书 / 书 NBT 外部变化）
			loadFromBook();
		}
	}

	/** 书 → 槽加载，并刷新快照。 */
	private void loadFromBook() {
		ItemStack book = items.get(0);
		for (int slot = 0; slot < SpellbookData.MAX_FORMATION_SLOTS; slot++) {
			items.set(3 + slot, SpellbookData.getFormation(book, slot).copy());
		}
		this.lastSyncedFormations = bookSnapshotOf(book);
		markDirty();
	}

	/** 角槽(3-7)内容快照（key=角位 0-4）。 */
	private NbtCompound slotsSnapshotOf() {
		NbtCompound snapshot = new NbtCompound();
		for (int slot = 0; slot < SpellbookData.MAX_FORMATION_SLOTS; slot++) {
			ItemStack stack = items.get(3 + slot);
			if (!stack.isEmpty() && FormationData.isFormation(stack)) {
				NbtCompound tag = new NbtCompound();
				tag.putByte("Slot", (byte) slot);
				stack.writeNbt(tag);
				snapshot.put(String.valueOf(slot), tag);
			}
		}
		return snapshot;
	}

	/** 书 NBT Formations 的快照（供对比）。 */
	private static NbtCompound bookSnapshotOf(ItemStack book) {
		NbtCompound snapshot = new NbtCompound();
		for (int slot = 0; slot < SpellbookData.MAX_FORMATION_SLOTS; slot++) {
			ItemStack stack = SpellbookData.getFormation(book, slot);
			if (!stack.isEmpty()) {
				NbtCompound tag = new NbtCompound();
				tag.putByte("Slot", (byte) slot);
				stack.writeNbt(tag);
				snapshot.put(String.valueOf(slot), tag);
			}
		}
		return snapshot;
	}

	/** 玩家在 GUI 里改动角槽后由 ScreenHandler 调用（不等 1 秒 tick，操作即时生效）。 */
	public void onFormationSlotsChanged() {
		syncFormations();
	}

	// ---- NamedScreenHandlerFactory ----
	@Override
	public Text getDisplayName() {
		return Text.translatable("block.ssc_addon.infusion_altar");
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new InfusionAltarScreenHandler(syncId, playerInventory, this);
	}

	// ---- NBT ----
	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		items.clear();
		Inventories.readNbt(nbt, items);
		// 读档后快照失效，首次 tick 重新加载
		this.lastSyncedFormations = null;
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
		ItemStack result = Inventories.splitStack(items, slot, amount);
		if (!result.isEmpty()) {
			markDirty();
			syncFormations();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack result = Inventories.removeStack(items, slot);
		if (!result.isEmpty()) {
			markDirty();
			syncFormations();
		}
		return result;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
		syncFormations();
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
}
