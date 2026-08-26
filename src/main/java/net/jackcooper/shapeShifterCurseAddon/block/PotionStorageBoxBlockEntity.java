package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionStorageBoxScreenHandler;

/**
 * 药品存储箱方块实体（jackcooper）：专存压缩能量药水（feed_potion）。
 * <p>共 {@link #SLOT_COUNT} 个槽，每格最多叠放 {@link #MAX_PER_SLOT} 瓶。实现 {@link SidedInventory} 支持漏斗
 * 任意面插入 feed_potion / 抽取成品（注意：原版漏斗合并药水受物品自身 maxCount=1 限制，漏斗插入每格仅到 1，
 * GUI/手动可叠满 8）。
 */
public class PotionStorageBoxBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {

	/** 存储槽数量。 */
	public static final int SLOT_COUNT = 8;
	/** 每格最多叠放的能量瓶数。 */
	public static final int MAX_PER_SLOT = 8;
	/** 主动抽取相邻漏斗的节拍（对齐漏斗 8t 冷却）。 */
	private static final int PULL_INTERVAL = 8;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);

	/** 客户端槽位镜像（仅渲染用）：按总装填档位在柜内 4 个展示位显示药水瓶。 */
	private final DefaultedList<ItemStack> clientItems = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
	/** 上次已同步的展示档位（服务端，对比用；-1 表示尚未同步）。 */
	private int lastTier = -1;

	public PotionStorageBoxBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.POTION_STORAGE_BOX_BE, pos, state);
	}

	// ==================== 每 tick 逻辑（仅服务端） ====================

	public static void tick(World world, BlockPos pos, BlockState state, PotionStorageBoxBlockEntity be) {
		if (world.isClient) {
			return;
		}
		// 主动从「出口朝向本箱」的相邻漏斗抽取能量瓶并叠入（箱子自己插入，不受原版药水 maxCount=1 限制），
		// 从而像原版叠同类物品那样自然填满 8格×8=64。非漏斗投入的零散堆会在下次抽取时被优先补满。
		if (world.getTime() % PULL_INTERVAL == 0) {
			be.pullFromFeederHoppers(world, pos);
		}
		// 展示档位对比：总装填每跨 25% 档才发 BE 包（柜内瓶数变化但未跨档时不刷包，省带宽）
		int tier = displayTier(totalBottles(be.items));
		if (tier != be.lastTier) {
			be.lastTier = tier;
			if (!world.isClient) {
				world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** 柜内总瓶数（所有槽的堆叠数之和）。 */
	public static int totalBottles(java.util.List<ItemStack> stacks) {
		int total = 0;
		for (ItemStack s : stacks) {
			if (!s.isEmpty()) {
				total += s.getCount();
			}
		}
		return total;
	}

	/**
	 * 展示档位（0~8）：总装填每 12.5% 一档（满柜 8槽×8=64 瓶，即每 8 瓶 +1 档）；
	 * 有任何瓶子时至少 1 档，满柜 8 档。渲染器与同步触发共用本公式，保证两端一致。
	 */
	public static int displayTier(int totalBottles) {
		if (totalBottles <= 0) return 0;
		return Math.min(8, (totalBottles + 7) / 8);
	}

	/** 客户端：渲染器读取的槽位镜像。 */
	public ItemStack getClientStack(int slot) {
		return clientItems.get(slot);
	}

	/** 抽取所有「出口方向正对本箱」的相邻漏斗里的能量瓶（仅馈送漏斗，不抽取从本箱取货的漏斗）。 */
	private void pullFromFeederHoppers(World world, BlockPos boxPos) {
		for (Direction dir : Direction.values()) {
			BlockPos hpos = boxPos.offset(dir);
			BlockState hs = world.getBlockState(hpos);
			if (!hs.isOf(Blocks.HOPPER)) {
				continue;
			}
			// 仅处理出口（FACING）正对本箱的馈送漏斗；从本箱取货的漏斗出口背向本箱，不会被误抽
			if (!hpos.offset(hs.get(HopperBlock.FACING)).equals(boxPos)) {
				continue;
			}
			if (world.getBlockEntity(hpos) instanceof Inventory hopperInv) {
				pullFrom(hopperInv);
			}
		}
	}

	/** 从一个馈送漏斗里尽量抽取能量瓶并叠入本箱。 */
	private void pullFrom(Inventory hopperInv) {
		for (int i = 0; i < hopperInv.size(); i++) {
			ItemStack s = hopperInv.getStack(i);
			if (s.isEmpty() || !EnergyBottlerBlockEntity.isEnergyBottle(s)) {
				continue;
			}
			boolean moved = false;
			while (!s.isEmpty() && insertOverstackOne(s)) {
				s.decrement(1);
				moved = true;
			}
			if (s.isEmpty()) {
				hopperInv.setStack(i, ItemStack.EMPTY);
			}
			if (moved) {
				hopperInv.markDirty();
			}
		}
	}

	/** 把 1 瓶能量瓶叠入本箱：先填未满的同类堆（叠至 {@link #MAX_PER_SLOT}），再用空槽；成功返回 true。 */
	private boolean insertOverstackOne(ItemStack potion) {
		for (int i = 0; i < SLOT_COUNT; i++) {
			ItemStack a = items.get(i);
			if (!a.isEmpty() && a.getCount() < MAX_PER_SLOT && ItemStack.canCombine(a, potion)) {
				a.increment(1);
				markDirty();
				return true;
			}
		}
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (items.get(i).isEmpty()) {
				ItemStack one = potion.copy();
				one.setCount(1);
				items.set(i, one);
				markDirty();
				return true;
			}
		}
		return false;
	}

	// ==================== NBT 持久化 ====================

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, items);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		items.clear();
		Inventories.readNbt(nbt, items);
		// 客户端 BE 数据包路径：刷新柜内瓶子渲染镜像
		clientItems.clear();
		DefaultedList<ItemStack> mirror = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
		Inventories.readNbt(nbt, mirror);
		for (int i = 0; i < SLOT_COUNT && i < mirror.size(); i++) {
			clientItems.set(i, mirror.get(i));
		}
	}

	// ==================== ScreenHandlerFactory ====================

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.ssc_addon.potion_storage_box");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new PotionStorageBoxScreenHandler(syncId, playerInventory, this);
	}

	// ==================== 客户端渲染同步（柜内动态瓶子） ====================

	/** BE 更新包：客户端收到后走 readNbt 刷新柜内瓶子镜像。 */
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	/** 区块数据包：进存档/重进世界时客户端也能拿到槽位内容渲染柜内瓶子。 */
	@Override
	public NbtCompound toInitialChunkDataNbt() {
		NbtCompound nbt = super.toInitialChunkDataNbt();
		Inventories.writeNbt(nbt, items);
		return nbt;
	}

	// ==================== SidedInventory（漏斗互通） ====================

	@Override
	public int[] getAvailableSlots(Direction side) {
		int[] slots = new int[SLOT_COUNT];
		for (int i = 0; i < SLOT_COUNT; i++) {
			slots[i] = i;
		}
		return slots;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return EnergyBottlerBlockEntity.isEnergyBottle(stack);
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return true;
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
		if (stack.getCount() > MAX_PER_SLOT) {
			stack.setCount(MAX_PER_SLOT);
		}
		markDirty();
	}

	@Override
	public int getMaxCountPerStack() {
		return MAX_PER_SLOT;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return EnergyBottlerBlockEntity.isEnergyBottle(stack);
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
