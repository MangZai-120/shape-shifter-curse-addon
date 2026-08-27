package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.List;

/**
 * 能量汲取器方块实体（jackcooper）。
 * <p>使魔系玩家走进方块本格站立时，每 {@link #STEP_INTERVAL} tick 消耗 {@link #MANA_PER_STEP} 点 mana，
 * 按 1:1 无损耗转化为能量并注入所在能量网络（{@link EnergyNetwork}）。本方块自身仅作小容量缓冲
 * （上限 {@link #MAX_ENERGY}），能量会溢流进相邻的能量储罐。不再自行合成能量瓶——合成交由能量装瓶器。
 */
public class EnergyExtractorBlockEntity extends BlockEntity implements EnergyNetworkMember {

	/** 汲取器自身能量缓冲上限（较小，主要缓冲；储存靠储罐扩容）。 */
	public static final int MAX_ENERGY = 200;
	/** 转化节拍（每多少 tick 结算一次 mana→能量）。 */
	private static final int STEP_INTERVAL = 5;
	/** 每个节拍消耗的 mana。 */
	private static final int MANA_PER_STEP = 2;
	/** 每个节拍转化得到的能量（2026-08-27 效率提升 50%：2→3）。 */
	private static final int ENERGY_PER_STEP = 3;

	/** 当前存储能量。 */
	private int energy = 0;

	/** 所在网络成员缓存（事件驱动失效，避免每 tick 洪泛扫描）。 */
	private List<EnergyNetworkMember> networkCache;
	private boolean networkDirty = true;

	public EnergyExtractorBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.ENERGY_EXTRACTOR_BE, pos, state);
	}

	// ==================== 每 tick 逻辑（仅服务端） ====================

	public static void tick(World world, BlockPos pos, BlockState state, EnergyExtractorBlockEntity be) {
		if (world.isClient) {
			return;
		}
		if (world.getTime() % STEP_INTERVAL != 0) {
			return;
		}
		List<EnergyNetworkMember> network = be.getNetwork();
		// 网络已满则不转化（无损耗、不浪费 mana）
		if (EnergyNetwork.getTotalEnergy(network) >= EnergyNetwork.getTotalCapacity(network)) {
			return;
		}
		ServerPlayerEntity user = findFamiliarUser(world, pos);
		if (user == null || ManaUtils.getPlayerMana(user) < MANA_PER_STEP) {
			return;
		}
		ManaUtils.consumePlayerMana(user, MANA_PER_STEP);
		// 立即同步 mana 条（方块实体外部扣 mana，不走玩家 tick 的 manaTick）
		RegManaComponent.MANA.sync(user);
		EnergyNetwork.insert(network, ENERGY_PER_STEP);
		// 转化嗡鸣（降频，避免过吵）
		if (world.getTime() % 40 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.3f, 1.6f);
		}
	}

	/** 查找站在本方块格内的使魔系玩家（服务端权威，范围检测扫服务端实体）。 */
	private static ServerPlayerEntity findFamiliarUser(World world, BlockPos pos) {
		Box box = new Box(pos);
		List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class, box,
				p -> p.isAlive() && FormUtils.isFamiliarFoxFamily(p));
		return players.isEmpty() ? null : players.get(0);
	}

	// ==================== 能量网络成员 ====================

	@Override
	public int getStoredEnergy() {
		return energy;
	}

	@Override
	public void setStoredEnergy(int value) {
		energy = Math.max(0, Math.min(MAX_ENERGY, value));
		markDirty();
	}

	@Override
	public int getEnergyCapacity() {
		return MAX_ENERGY;
	}

	@Override
	public void markNetworkDirty() {
		networkDirty = true;
	}

	/** 获取所在网络成员（缓存，脏时重建）。 */
	public List<EnergyNetworkMember> getNetwork() {
		if (networkDirty || networkCache == null) {
			networkCache = EnergyNetwork.collect(this.world, this.pos);
			networkDirty = false;
		}
		return networkCache;
	}

	// ==================== NBT 持久化 ====================

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt("Energy", energy);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		energy = nbt.getInt("Energy");
	}
}
