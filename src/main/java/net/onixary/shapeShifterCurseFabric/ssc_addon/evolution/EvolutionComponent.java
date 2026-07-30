package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashSet;
import java.util.Set;

/**
 * SSCA 进化加点系统 - 玩家进化数据组件（服务端权威，自动同步到客户端）。
 *
 * 框架阶段：仅承载最小数据骨架（路线 / 分支 / EXP / 已解锁节点 / 全解锁标记），
 * 具体的天赋节点、解锁规则、EXP 消耗曲线等业务逻辑「待后续设计」，由 JSON 数据驱动填充。
 *
 * 设计依据：见 附属根目录 SSCA进化加点系统_设计文档.txt
 */
public class EvolutionComponent implements AutoSyncedComponent {
    /** 玩家选择的进化路线 id（空字符串 = 未选择 / 走原版 SSC 路线）。 */
    private String route = "";
    /** 已选择的 SP 分支 id（空字符串 = 未选择）。 */
    private String branch = "";
    /** 累积的原版经验值（玩家在进化路线上获得经验时累加；进化 exp = 该值 / 2，见 {@link #getEvoLevel()}）。 */
    private int exp = 0;
    /** 已解锁的天赋节点 id 集合。 */
    private final Set<String> unlockedNodes = new HashSet<>();
    /** 管理指令强制全解锁标记（/ssc_addon evolution unlock_all）。 */
    private boolean unlockAll = false;
    /** 可用升级点数（攒点用于解锁节点）。 */
    private int points = 0;
    /** 已发放过升级点的经验等级里程碑（防重复发放）。 */
    private final Set<Integer> grantedLevels = new HashSet<>();
    /** 是否已真正变身进入过进化形态（用于「进入后再离开则重置进度」判定，避免变身动画期间误重置）。 */
    private boolean started = false;
    /**
     * 转职（灵能宝珠）进行中标志：瞬态（不写 NBT、不同步），转职变身演出期间置 true，
     * 让 {@code EvolutionManager.tickPlayer} 豁免「离开起点形态即重置」逻辑，避免动画期间清空进度。
     * 断线后自动为 false（瞬态），此时走原重置逻辑，玩家白赚不扣点（对玩家有利，非死档）。
     */
    private transient boolean jobChanging = false;
    /** 是否已迁移到内置 exp 机制（旧存档首次打开加点界面时按点数定级并置 true，防重复迁移）。 */
    private boolean migrated = false;
    /** 上次记录的原版总经验（按正增量累积进化 exp，只算获得、排除花费/死亡）。-1 = 未初始化。 */
    private int lastTotalExp = -1;

    /** 进化等级上限。 */
    public static final int EVO_LEVEL_CAP = 50;

    // ---------------- 路线 / 分支 ----------------

    public boolean isOnSscaRoute() {
        return route != null && !route.isEmpty();
    }

    public String getRoute() {
        return route == null ? "" : route;
    }

    public void setRoute(String route) {
        this.route = route == null ? "" : route;
    }

    public String getBranch() {
        return branch == null ? "" : branch;
    }

    public void setBranch(String branch) {
        this.branch = branch == null ? "" : branch;
    }

    // ---------------- EXP ----------------

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public void addExp(int delta) {
        setExp(this.exp + delta);
    }

    /**
     * 当前进化等级：由累积经验换算（每 2 点原版经验 = 1 点进化 exp，即用 exp/2 走原版经验曲线），封顶 {@link #EVO_LEVEL_CAP}。
     */
    public int getEvoLevel() {
        return Math.min(EVO_LEVEL_CAP, expToLevel(this.exp / 2));
    }

    /** 原版经验曲线：从 level 升到 level+1 需要的经验（1.20.1，与 PlayerEntity#getNextLevelExperience 一致）。 */
    private static int levelUpCost(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** 达到 level 级所需的累积经验总量（levelUpCost 前缀和）。 */
    public static int levelToTotalExp(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += levelUpCost(i);
        }
        return total;
    }

    /** 累积经验总量换算成等级（levelToTotalExp 的反函数）。 */
    public static int expToLevel(int totalExp) {
        int level = 0;
        int remaining = Math.max(0, totalExp);
        while (remaining >= levelUpCost(level)) {
            remaining -= levelUpCost(level);
            level++;
        }
        return level;
    }

    // ---------------- 升级点数 ----------------

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int delta) {
        setPoints(this.points + delta);
    }

    /** 尝试消耗点数，足够则扣除返回 true。 */
    public boolean spendPoints(int cost) {
        if (cost <= 0) return true;
        if (this.points < cost) return false;
        this.points -= cost;
        return true;
    }

    /** 该经验等级里程碑是否已发放过点数。 */
    public boolean hasGrantedLevel(int level) {
        return grantedLevels.contains(level);
    }

    public void markGrantedLevel(int level) {
        grantedLevels.add(level);
    }

    // ---------------- 进化形态驻留标志 ----------------

    /** 玩家是否已真正变身进入过进化形态。 */
    public boolean hasStarted() {
        return started;
    }

    /** 标记玩家已变身进入进化形态。 */
    public void markStarted() {
        this.started = true;
    }

    // ---------------- 解锁状态 ----------------

    public boolean isUnlockAll() {
        return unlockAll;
    }

    public void setUnlockAll(boolean unlockAll) {
        this.unlockAll = unlockAll;
    }

    public Set<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    /** 节点是否已解锁（全解锁标记开启时恒为 true）。 */
    public boolean isUnlocked(String nodeId) {
        return unlockAll || (nodeId != null && unlockedNodes.contains(nodeId));
    }

    public void unlock(String nodeId) {
        if (nodeId != null && !nodeId.isEmpty()) {
            unlockedNodes.add(nodeId);
        }
    }

    /** 清空已解锁节点（转职换形态时新技能树重开用）。 */
    public void clearUnlockedNodes() {
        unlockedNodes.clear();
    }

    /** 清空已发放里程碑记录（转职 / 迁移后按新进化等级重发用）。 */
    public void clearGrantedLevels() {
        grantedLevels.clear();
    }

    /** 已发放里程碑数量（迁移检测：旧存档 exp=0 但发过点=有里程碑记录）。 */
    public int getGrantedLevelCount() {
        return grantedLevels.size();
    }

    // ---------------- 转职 / 迁移状态 ----------------

    public boolean isJobChanging() {
        return jobChanging;
    }

    public void setJobChanging(boolean jobChanging) {
        this.jobChanging = jobChanging;
    }

    public boolean hasMigrated() {
        return migrated;
    }

    public void markMigrated() {
        this.migrated = true;
    }

    public int getLastTotalExp() {
        return lastTotalExp;
    }

    public void setLastTotalExp(int lastTotalExp) {
        this.lastTotalExp = lastTotalExp;
    }

    /** 重置全部进化数据（/ssc_addon evolution reset）。 */
    public void reset() {
        this.route = "";
        this.branch = "";
        this.exp = 0;
        this.unlockAll = false;
        this.unlockedNodes.clear();
        this.points = 0;
        this.grantedLevels.clear();
        this.started = false;
        this.jobChanging = false;
        this.lastTotalExp = -1;
        // migrated 保持：重置后仍是内置 exp 机制，无需再次迁移
    }

    // ---------------- 持久化 / 同步 ----------------

    @Override
    public void readFromNbt(NbtCompound nbt) {
        this.route = nbt.getString("route");
        this.branch = nbt.getString("branch");
        this.exp = nbt.getInt("exp");
        this.unlockAll = nbt.getBoolean("unlockAll");
        this.points = nbt.getInt("points");
        this.unlockedNodes.clear();
        NbtList list = nbt.getList("unlocked", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            this.unlockedNodes.add(list.getString(i));
        }
        this.grantedLevels.clear();
        for (int g : nbt.getIntArray("grantedLevels")) {
            this.grantedLevels.add(g);
        }
        this.started = nbt.getBoolean("started");
        this.migrated = nbt.getBoolean("migrated");
        this.lastTotalExp = nbt.contains("lastTotalExp") ? nbt.getInt("lastTotalExp") : -1;
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("route", getRoute());
        nbt.putString("branch", getBranch());
        nbt.putInt("exp", this.exp);
        nbt.putBoolean("unlockAll", this.unlockAll);
        nbt.putInt("points", this.points);
        NbtList list = new NbtList();
        for (String node : this.unlockedNodes) {
            list.add(NbtString.of(node));
        }
        nbt.put("unlocked", list);
        int[] granted = new int[this.grantedLevels.size()];
        int gi = 0;
        for (int g : this.grantedLevels) {
            granted[gi++] = g;
        }
        nbt.putIntArray("grantedLevels", granted);
        nbt.putBoolean("started", this.started);
        nbt.putBoolean("migrated", this.migrated);
        nbt.putInt("lastTotalExp", this.lastTotalExp);
    }
}
