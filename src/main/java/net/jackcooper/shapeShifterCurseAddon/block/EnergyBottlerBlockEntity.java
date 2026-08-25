package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkConsumer;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;
import net.jackcooper.shapeShifterCurseAddon.screen.EnergyBottlerScreenHandler;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;

import java.util.Collections;
import java.util.List;

/**
 * 能量装瓶器方块实体（jackcooper）：类炼药台的三线并行装瓶机。
 * <p>从相邻能量网络（{@link EnergyNetwork}，除正下方外任一面相邻的储罐/汲取器）抽取能量，
 * 每条线独立地以 {@link #ENERGY_PER_BOTTLE} 能量 + 1 个空玻璃瓶、经 {@link #CRAFT_TIME} tick 合成 1 瓶
 * 压缩能量药水（feed_potion）。三条线并行；手动/自动为总开关。输出槽每格可叠 {@link #OUTPUT_MAX} 瓶。
 * 支持漏斗：顶/侧插入空玻璃瓶到输入槽，底部抽取成品。
 */
public class EnergyBottlerBlockEntity extends BlockEntity
		implements SidedInventory, NamedScreenHandlerFactory, EnergyNetworkConsumer {

	/** 合成一瓶消耗的能量。 */
	public static final int ENERGY_PER_BOTTLE = 25;
	/** 合成一瓶所需时间（tick），3 秒。 */
	public static final int CRAFT_TIME = 60;
	/** 并行合成线数量。 */
	public static final int LINES = 3;
	/** 每个输出槽最多叠放的能量瓶数。 */
	public static final int OUTPUT_MAX = 8;

	/** 输入槽（空玻璃瓶）索引 0~2；输出槽（能量瓶）索引 3~5。 */
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(LINES * 2, ItemStack.EMPTY);

	/** 每条线的合成进度。 */
	private final int[] progress = new int[LINES];
	/** 手动模式下每条线的单次合成请求（完成一瓶后清除）。 */
	private final boolean[] manualRequested = new boolean[LINES];
	/** 是否自动合成（总开关，作用于全部三条线）。 */
	private boolean autoMode = false;

	/** 相邻能量网络缓存（消费者：事件驱动失效，非每 tick 洪泛）。 */
	private List<EnergyNetworkMember> networkCache;
	private boolean networkDirty = true;

	/** 同步给客户端 GUI 的属性：0=网络能量 1=网络上限 2/3/4=三线进度 5=是否自动。 */
	private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> EnergyNetwork.getTotalEnergy(getEnergyNetwork());
				case 1 -> EnergyNetwork.getTotalCapacity(getEnergyNetwork());
				case 2 -> progress[0];
				case 3 -> progress[1];
				case 4 -> progress[2];
				case 5 -> autoMode ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 2 -> progress[0] = value;
				case 3 -> progress[1] = value;
				case 4 -> progress[2] = value;
				case 5 -> autoMode = value != 0;
				default -> {
				}
			}
		}

		@Override
		public int size() {
			return 6;
		}
	};

	public EnergyBottlerBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.ENERGY_BOTTLER_BE, pos, state);
	}

	public PropertyDelegate getPropertyDelegate() {
		return propertyDelegate;
	}

	// ==================== 每 tick 逻辑（仅服务端） ====================

	public static void tick(World world, BlockPos pos, BlockState state, EnergyBottlerBlockEntity be) {
		if (world.isClient) {
			return;
		}
		boolean dirty = false;
		for (int i = 0; i < LINES; i++) {
			if (be.canCraft(i) && (be.autoMode || be.manualRequested[i])) {
				be.progress[i]++;
				if (be.progress[i] >= CRAFT_TIME) {
					be.craftOne(i);
					be.progress[i] = 0;
					if (!be.autoMode) {
						be.manualRequested[i] = false;
					}
					world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.6f, 1.0f);
				}
				dirty = true;
			} else if (be.progress[i] != 0) {
				be.progress[i] = 0;
				dirty = true;
			} else if (be.manualRequested[i] && !be.canCraft(i)) {
				be.manualRequested[i] = false;
				dirty = true;
			}
		}
		if (dirty) {
			be.markDirty();
		}
	}

	/** 第 i 条线是否可合成：网络能量≥25、该输入槽有空玻璃瓶、该输出槽能容纳一瓶能量瓶。 */
	public boolean canCraft(int line) {
		if (EnergyNetwork.getTotalEnergy(getEnergyNetwork()) < ENERGY_PER_BOTTLE) {
			return false;
		}
		if (!items.get(line).isOf(Items.GLASS_BOTTLE)) {
			return false;
		}
		ItemStack out = items.get(LINES + line);
		return out.isEmpty() || (isEnergyBottle(out) && out.getCount() < OUTPUT_MAX);
	}

	/** 结算第 i 条线一瓶：从网络抽 25 能量、耗 1 空瓶、输出槽 +1 瓶能量瓶。 */
	private void craftOne(int line) {
		EnergyNetwork.extract(getEnergyNetwork(), ENERGY_PER_BOTTLE);
		items.get(line).decrement(1);
		ItemStack out = items.get(LINES + line);
		if (out.isEmpty()) {
			items.set(LINES + line, makeEnergyBottle());
		} else {
			out.increment(1);
		}
	}

	/** 能量瓶 = 原版药水 + 压缩能量药水（feed_potion）药水效果。 */
	public static ItemStack makeEnergyBottle() {
		return PotionUtil.setPotion(new ItemStack(Items.POTION), RegCustomPotions.FEED_POTION);
	}

	/** 是否为压缩能量药水瓶。 */
	public static boolean isEnergyBottle(ItemStack stack) {
		if (!stack.isOf(Items.POTION)) {
			return false;
		}
		Potion potion = PotionUtil.getPotion(stack);
		return potion == RegCustomPotions.FEED_POTION;
	}

	// ==================== 能量网络消费者 ====================

	@Override
	public void markNetworkDirty() {
		networkDirty = true;
	}

	/** 定位并缓存相邻能量网络（除正下方外任一面相邻的能量源，取其整个共享网络）。 */
	public List<EnergyNetworkMember> getEnergyNetwork() {
		if (networkDirty || networkCache == null) {
			networkCache = findAdjacentNetwork();
			networkDirty = false;
		}
		return networkCache;
	}

	private List<EnergyNetworkMember> findAdjacentNetwork() {
		if (world == null) {
			return Collections.emptyList();
		}
		for (Direction dir : Direction.values()) {
			if (dir == Direction.DOWN) {
				continue; // 除正下方外任一面相邻
			}
			BlockEntity be = world.getBlockEntity(pos.offset(dir));
			if (be instanceof EnergyNetworkMember) {
				return EnergyNetwork.collect(world, pos.offset(dir));
			}
		}
		return Collections.emptyList();
	}

	// ==================== GUI 按钮回调（服务端） ====================

	/** 切换手动/自动总开关。 */
	public void toggleAutoMode() {
		autoMode = !autoMode;
		if (!autoMode) {
			for (int i = 0; i < LINES; i++) {
				manualRequested[i] = false;
			}
		}
		markDirty();
	}

	/** 手动模式下：对全部三条线各请求一次合成。 */
	public void requestManualCraft() {
		if (!autoMode) {
			for (int i = 0; i < LINES; i++) {
				manualRequested[i] = true;
			}
			markDirty();
		}
	}

	// ==================== NBT 持久化 ====================

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, items);
		nbt.putIntArray("Progress", progress.clone());
		nbt.putBoolean("AutoMode", autoMode);
		int mask = 0;
		for (int i = 0; i < LINES; i++) {
			if (manualRequested[i]) {
				mask |= (1 << i);
			}
		}
		nbt.putInt("ManualMask", mask);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		items.clear();
		Inventories.readNbt(nbt, items);
		int[] p = nbt.getIntArray("Progress");
		for (int i = 0; i < LINES; i++) {
			progress[i] = i < p.length ? p[i] : 0;
		}
		autoMode = nbt.getBoolean("AutoMode");
		int mask = nbt.getInt("ManualMask");
		for (int i = 0; i < LINES; i++) {
			manualRequested[i] = (mask & (1 << i)) != 0;
		}
	}

	// ==================== ScreenHandlerFactory ====================

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.ssc_addon.energy_bottler");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new EnergyBottlerScreenHandler(syncId, playerInventory, this, propertyDelegate);
	}

	// ==================== SidedInventory（漏斗互通） ====================

	@Override
	public int[] getAvailableSlots(Direction side) {
		int[] slots = new int[LINES * 2];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = i;
		}
		return slots;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		// 仅允许往输入槽插入空玻璃瓶
		return slot < LINES && stack.isOf(Items.GLASS_BOTTLE);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		// 仅允许从输出槽抽取
		return slot >= LINES;
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
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
		int max = slot < LINES ? 64 : OUTPUT_MAX;
		if (stack.getCount() > max) {
			stack.setCount(max);
		}
		markDirty();
	}

	@Override
	public int getMaxCountPerStack() {
		return OUTPUT_MAX;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return slot < LINES && stack.isOf(Items.GLASS_BOTTLE);
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		if (this.world == null || this.world.getBlockEntity(this.pos) != this) {
			return false;
		}
		return player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		items.clear();
	}
}
