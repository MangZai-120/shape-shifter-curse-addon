package net.jackcooper.shapeShifterCurseAddon.block;

import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
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
 * 注魔台方块实体（jackcooper）。三槽：0=魔法书、1=燃料（月尘粉/月尘纯晶）、2=催化（超级塑形核心）。
 * <p>每秒结算一次充能：书法力未满 + 燃料 → 消耗燃料充法力（未加工月之尘 +10 / 月尘纯晶 +80）；
 * 但材料齐备可升级（经验够 + 纯晶 + 超核）时暂停充能，等玩家在界面点「升级」按钮才升级（见 {@link #tryUpgrade}）。</p>
 */
public class InfusionAltarBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(3, ItemStack.EMPTY);

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
		}
		return result;
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
