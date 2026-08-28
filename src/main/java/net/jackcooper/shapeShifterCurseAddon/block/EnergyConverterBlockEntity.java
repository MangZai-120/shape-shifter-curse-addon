package net.jackcooper.shapeShifterCurseAddon.block;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkConsumer;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;
import team.reborn.energy.api.EnergyStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能量转变器方块实体（jackcooper）：SSCA 能量 → Team Reborn Energy(E) 的实时结算转换枢纽。
 * <p><b>无缓冲架构（2026-08-27 v3）</b>：废除 E 预扣缓冲——「预扣进缓冲再供给」会导致
 * 设备得电与储罐扣电时序脱节（设备狂吃缓冲、储罐不扣/延迟扣、重启后才扣等怪象）。
 * 现改为<b>拉取时实时结算</b>：电缆/机器每次 extract，同一事务内即时从相邻 SSCA 网络按
 * {@link #RATIO}（1:16）抽取对应能量；事务中止自动全额归还（CloseCallback 回滚），
 * 设备得电与储罐扣电严格同 tick 锁定。
 * <ul>
 *   <li><b>拉取路径</b>：{@link #exposed} 注册于 {@code EnergyStorage.SIDED}，extract 实时结算；</li>
 *   <li><b>推送路径</b>：tick 主动向相邻可接收储能插入，插入成功后同 tick 从网络扣费，
 *       插多少扣多少（事务保证失败不扣）。</li>
 * </ul>
 * 两路共享 {@link #MAX_E_PER_TICK}（512 E/t）预算。
 */
public class EnergyConverterBlockEntity extends BlockEntity implements EnergyNetworkConsumer {

/** SSCA 能量 → E 的换算比（1 SSCA = 16 E）。 */
public static final int RATIO = 16;
/** 每 tick 最大 E 输出（拉取 + 推送合计）。 */
public static final int MAX_E_PER_TICK = 512;

/** 对外暴露的 E 储能（注册于 {@code EnergyStorage.SIDED}，供 TR 等电缆/机器拉取）。 */
private final EnergyStorage exposed = new EnergyStorage() {
@Override
public boolean supportsInsertion() {
return false; // 单向：只出不进
}

@Override
public long insert(long maxAmount, TransactionContext transaction) {
return 0;
}

@Override
public boolean supportsExtraction() {
// 只由激活态决定：缓冲概念已废除，网络无电时 extract 返回 0 即可（API 允许存疑返回 true）。
// 不随存量翻转可避免电缆把本转换器从电源名单剔除后不重扫的停摆问题。
return isActive();
}

@Override
public long extract(long maxAmount, TransactionContext transaction) {
if (!isActive() || world == null || world.isClient) {
return 0;
}
refreshTickBudget();
long budget = MAX_E_PER_TICK - outputUsedThisTick;
long allowed = Math.min(maxAmount, budget);
if (allowed <= 0) {
return 0;
}
// 实时结算：按网络当前存量限制可供给 E
List<EnergyNetworkMember> network = getEnergyNetwork();
int availSSCA = EnergyNetwork.getTotalEnergy(network);
long supplyableE = Math.min(allowed, (long) availSSCA * RATIO);
if (supplyableE <= 0) {
return 0;
}
int needSSCA = (int) ((supplyableE + RATIO - 1) / RATIO);
// 逐成员抽取并记录明细，供事务中止时全额归还
Map<EnergyNetworkMember, Integer> takenFrom = new LinkedHashMap<>();
int remaining = needSSCA;
for (EnergyNetworkMember m : network) {
if (remaining <= 0) {
break;
}
int avail = m.getStoredEnergy();
if (avail > 0) {
int take = Math.min(avail, remaining);
m.setStoredEnergy(avail - take);
takenFrom.put(m, take);
remaining -= take;
}
}
int takenTotal = needSSCA - remaining;
if (takenTotal <= 0) {
return 0;
}
final long grantedE = Math.min(supplyableE, (long) takenTotal * RATIO);
final List<EnergyNetworkMember> net = network;
// 事务关闭回调：中止 → 全额归还；提交 → 计数 + 刷新储罐液面显示
transaction.addCloseCallback((tx, result) -> {
if (result == TransactionContext.Result.ABORTED) {
for (Map.Entry<EnergyNetworkMember, Integer> e : takenFrom.entrySet()) {
e.getKey().setStoredEnergy(e.getKey().getStoredEnergy() + e.getValue());
}
} else {
outputUsedThisTick += grantedE;
convertingThisTick = true;
EnergyNetwork.refreshTankDisplays(net);
}
});
return grantedE;
}

@Override
public long getAmount() {
// 对外显示：网络当前可供给的 E（钳到单 tick 上限，供电缆/机器 UI 预估）
if (world == null || world.isClient || !isActive()) {
return 0;
}
return Math.min(MAX_E_PER_TICK, (long) EnergyNetwork.getTotalEnergy(getEnergyNetwork()) * RATIO);
}

@Override
public long getCapacity() {
return MAX_E_PER_TICK;
}
};

/** 相邻能量网络缓存（消费者：事件驱动失效 + 邻居更新标脏）。 */
private List<EnergyNetworkMember> networkCache;
private boolean networkDirty = true;

/** 6 面 TB EnergyStorage 缓存（可接收 E 的推送目标；与网络缓存一同失效）。 */
private List<DirectionalStorage> storageCache;

/** 每 tick 输出预算记账（推+拉共享；按 world time 惰性重置）。 */
private long lastTickTime = -1;
private long outputUsedThisTick = 0;
private boolean convertingThisTick = false;
/** 音效状态（上升沿播一次）。 */
private boolean soundConverting = false;
/** 停摆自愈计数：激活但本 tick 无人取电的连续 tick 数。 */
private int stallCounter = 0;

public EnergyConverterBlockEntity(BlockPos pos, BlockState state) {
super(RegAddonBlockEntities.ENERGY_CONVERTER_BE, pos, state);
}

private record DirectionalStorage(Direction dir, EnergyStorage storage) {}

/** 对外暴露的 E 储能（由 {@code EnergyStorage.SIDED} 注册引用）。 */
public EnergyStorage getExposedStorage() {
return exposed;
}

/** 是否处于激活态。 */
private boolean isActive() {
BlockState s = getCachedState();
return s.contains(EnergyConverterBlock.ACTIVE) && s.get(EnergyConverterBlock.ACTIVE);
}

/** 新 tick 时重置输出预算与转化标记（推/拉两路径都会先调用，保证一致）。 */
private void refreshTickBudget() {
if (world != null && world.getTime() != lastTickTime) {
lastTickTime = world.getTime();
outputUsedThisTick = 0;
convertingThisTick = false;
}
}

// ==================== 每 tick 逻辑（仅服务端：推送路径 + 自愈） ====================

public static void tick(World world, BlockPos pos, BlockState state, EnergyConverterBlockEntity be) {
if (world.isClient) {
return;
}
be.refreshTickBudget();
if (!state.get(EnergyConverterBlock.ACTIVE)) {
be.tickFeedback(world, pos, false);
return;
}
// 推送路径：向相邻可接收储能主动插入；插多少、同 tick 从网络扣多少（实时结算，无缓冲）
be.refreshTickBudget();
long eBudget = MAX_E_PER_TICK - be.outputUsedThisTick;
if (eBudget > 0) {
List<EnergyNetworkMember> network = be.getEnergyNetwork();
int availSSCA = EnergyNetwork.getTotalEnergy(network);
long supplyableE = Math.min(eBudget, (long) availSSCA * RATIO);
if (supplyableE > 0) {
for (DirectionalStorage target : be.resolveTargets()) {
if (eBudget <= 0 || supplyableE <= 0) {
break;
}
long tryAmt = Math.min(supplyableE, eBudget);
try (Transaction tx = Transaction.openOuter()) {
long inserted = target.storage().insert(tryAmt, tx);
if (inserted > 0) {
// 同事务内按实际插入量扣 SSCA（向上取整，多耗 ≤1 SSCA/t 封顶不超发）
int needSSCA = (int) ((inserted + RATIO - 1) / RATIO);
int taken = EnergyNetwork.extract(network, Math.min(needSSCA, availSSCA));
if (taken > 0) {
tx.commit();
be.outputUsedThisTick += inserted;
eBudget -= inserted;
supplyableE -= inserted;
availSSCA -= taken;
be.convertingThisTick = true;
}
// taken == 0（网络刚好被拉空）：不 commit 回滚插入
}
// inserted == 0：不 commit 自动回滚
}
}
}
}
be.tickFeedback(world, pos, be.convertingThisTick);
// 停摆自愈：激活但本 tick 无人取电时，每 10 tick 广播邻居更新，
// 唤醒 TR 等模组电缆的连接缓存重扫（TB Energy 官方建议的能力变化通知方式）。
if (be.outputUsedThisTick == 0) {
be.stallCounter++;
if (be.stallCounter >= 10) {
be.stallCounter = 0;
world.updateNeighborsAlways(pos, state.getBlock());
}
} else {
be.stallCounter = 0;
}
}

/** 转化状态上升沿播一次嗡鸣（不做持续音，避免吵）。 */
private void tickFeedback(World world, BlockPos pos, boolean converting) {
if (converting != soundConverting) {
soundConverting = converting;
if (converting) {
world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.25f, 1.8f);
}
}
}

// ==================== 相邻定位与缓存 ====================

/** 解析 6 面可接收 E 的 TB EnergyStorage（缓存；只含 supportsInsertion 的目标）。 */
private List<DirectionalStorage> resolveTargets() {
if (storageCache == null) {
List<DirectionalStorage> list = new ArrayList<>();
if (world != null) {
for (Direction dir : Direction.values()) {
EnergyStorage es = EnergyStorage.SIDED.find(world, pos.offset(dir), dir.getOpposite());
if (es != null && es.supportsInsertion()) {
list.add(new DirectionalStorage(dir, es));
}
}
}
storageCache = list;
}
return storageCache;
}

// ==================== 能量网络消费者 ====================

@Override
public void markNetworkDirty() {
networkDirty = true;
storageCache = null;
}

/** 定位并缓存相邻能量网络（任一面相邻的能量源，含正下方；取其整个共享网络）。 */
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
BlockEntity be = world.getBlockEntity(pos.offset(dir));
if (be instanceof EnergyNetworkMember) {
return EnergyNetwork.collect(world, pos.offset(dir));
}
}
return Collections.emptyList();
}

// ==================== NBT 持久化（无缓冲后无能量字段） ====================

@Override
protected void writeNbt(NbtCompound nbt) {
super.writeNbt(nbt);
}

@Override
public void readNbt(NbtCompound nbt) {
super.readNbt(nbt);
}
}