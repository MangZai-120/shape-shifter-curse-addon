package net.jackcooper.shapeShifterCurseAddon.cooldown;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 形态技能统一施放与冷却权威服务（计划书 §4.2 拟议接口的落地实现）。
 *
 * <p>职责：
 * <ul>
 *   <li>管理 UUID + skillId → 施放阶段（IDLE/CHARGING/ACTIVE）与冷却阶段（未开始/倒计时/完成）；</li>
 *   <li>每次施放分配唯一 castId，延迟回调（蓄力完成/落地/实体消失）凭 castId 结算，防旧回调误伤新施放；</li>
 *   <li>三种起算模式（on_cast / on_release / on_end）：on_end 生效期间不倒计时，结束后才起算完整 CD；</li>
 *   <li>失败结算（failCooldown）独立于起算模式，服务器确认失败瞬间起算；</li>
 *   <li>冷却持久化（PersistentState 挂 overworld）：重启/断线不清 CD，按服务器时钟恢复剩余；</li>
 *   <li>幂等结束：同一 cast 只结算一次。</li>
 * </ul></p>
 *
 * <p>线程模型：服务端主线程调用；ConcurrentHashMap 仅作防御。</p>
 *
 * <p>skillId 约定：使用 power JSON 的注册路径（如 "my_addon:form_snow_fox_sp_melee_primary"），
 * 保证与 Apoli power 一一对应、可直接反查 HUD 槽位。</p>
 */
public class SkillCastManager extends PersistentState {

    private static final String KEY = "ssca_skill_cooldowns";

    // ===== 施放阶段（计划书 §4.1 状态模型）=====
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_CHARGING = 1;
    public static final int PHASE_ACTIVE = 2;

    // ===== 起算模式（power JSON cooldown_start 字段）=====
    public static final String START_ON_CAST = "on_cast";
    public static final String START_ON_RELEASE = "on_release";
    public static final String START_ON_END = "on_end";

    /** 单次施放记录（内存态；持久化只存冷却结果）。 */
    public static final class Cast {
        public final long castId;
        public final UUID playerId;
        public final String skillId;
        public final ResolvedConfig config;
        public int phase = PHASE_CHARGING;
        public boolean settled = false;
        /** on_cast 模式下已先行起算的冷却标记（失败改写用）。 */
        public boolean cooldownStarted = false;

        Cast(long castId, UUID playerId, String skillId, ResolvedConfig config) {
            this.castId = castId;
            this.playerId = playerId;
            this.skillId = skillId;
            this.config = config;
        }
    }

    /** 从 power JSON 解析并冻结的配置快照（reload 只影响下一次施放，计划书 §4.3）。 */
    public static final class ResolvedConfig {
        public final int cooldown;
        public final int failCooldown;
        public final String cooldownStart;

        public ResolvedConfig(int cooldown, int failCooldown, String cooldownStart) {
            this.cooldown = Math.max(0, cooldown);
            this.failCooldown = Math.max(0, failCooldown);
            this.cooldownStart = cooldownStart;
        }
    }

    /** 单技能冷却状态（持久化单元）。 */
    public static final class CooldownState {
        public long endTick;        // 冷却结束的绝对服务器 tick（0=无冷却）
        public int totalTicks;      // 本次冷却总长（HUD 分母；失败档用实际值）
        public long lastCastId;

        public CooldownState() {
        }

        public boolean isActive(long now) {
            return endTick > now;
        }

        public int remaining(long now) {
            return (int) Math.max(0, endTick - now);
        }
    }

    // ===== 存储 =====
    private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Cast> activeCasts = new ConcurrentHashMap<>();

    private static volatile SkillCastManager instance;
    private static long castIdSeq = 0;

    /** 获取服务端实例（overworld PersistentState 挂载；每个逻辑服一份）。 */
    public static SkillCastManager get(ServerWorld world) {
        SkillCastManager mgr = world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(SkillCastManager::fromNbt, SkillCastManager::new, KEY);
        instance = mgr;
        return mgr;
    }

    /** 服务器时钟便捷入口（MinecraftServer.getTicks，全维度统一权威时钟）。 */
    public static long now(ServerPlayerEntity player) {
        return player.getServer().getTicks();
    }

    public static SkillCastManager instanceOrNull() {
        return instance;
    }

    private static String key(UUID player, String skillId) {
        return player + "\u0000" + skillId;
    }

    // ===== 拟议接口（计划书 §4.2）=====

    /**
     * 接受一次施放：建立 cast 记录后返回 castId。
     * on_cast 模式立即起算正常 CD；其余模式等待 released/finish。
     * 冷却未完 / 已在施放中 → 返回 -1（rejected）。
     */
    public long begin(ServerPlayerEntity player, String skillId, ResolvedConfig config) {
        long now = now(player);
        String k = key(player.getUuid(), skillId);
        CooldownState cd = cooldowns.get(k);
        if (cd != null && cd.isActive(now)) return -1;
        if (activeCasts.containsKey(k)) return -1;

        Cast cast = new Cast(nextCastId(), player.getUuid(), skillId, config);
        activeCasts.put(k, cast);

        if (START_ON_CAST.equals(config.cooldownStart)) {
            startCooldown(player.getUuid(), skillId, config.cooldown, cast.castId, now);
            cast.cooldownStarted = true;
        }
        return cast.castId;
    }

    /**
     * 确认实际释放成功（蓄力完成/效果提交）。
     * on_release 模式此时起算正常 CD；on_cast 已起算保持；on_end 等待 finish。
     */
    public void released(ServerPlayerEntity player, long castId) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        long now = now(player);
        if (START_ON_RELEASE.equals(cast.config.cooldownStart)) {
            startCooldown(cast.playerId, cast.skillId, cast.config.cooldown, castId, now);
        }
        cast.phase = PHASE_ACTIVE;
    }

    /**
     * 结束有效生命周期（效果结束/主动关闭/自然到期）。
     * on_end 模式此时起算正常 CD；瞬发技能同 tick released+finish 依次结算。
     */
    public void finish(ServerPlayerEntity player, long castId) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        long now = now(player);
        if (START_ON_END.equals(cast.config.cooldownStart) && !cast.cooldownStarted) {
            startCooldown(cast.playerId, cast.skillId, cast.config.cooldown, castId, now);
        }
        cast.settled = true;
        activeCasts.remove(key(cast.playerId, cast.skillId), cast);
    }

    /**
     * 确认释放失败：立即按 fail_cooldown 结算（不受 cooldown_start 延迟，计划书 §3.3）。
     * on_cast 已先行起算的，从失败时刻改写为 fail_cooldown（§4.2：不保留错误的成功冷却）。
     */
    public void fail(ServerPlayerEntity player, long castId) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        long now = now(player);
        startCooldown(cast.playerId, cast.skillId, cast.config.failCooldown, castId, now);
        cast.settled = true;
        activeCasts.remove(key(cast.playerId, cast.skillId), cast);
    }

    /** 二次操作（关环/引爆/断丝等）：不新建施放，返回进行中 cast 供控制（§4.1）。 */
    public Cast control(UUID player, String skillId) {
        return activeCasts.get(key(player, skillId));
    }

    /** 查询冷却（服务端权威；HUD/同步用）。 */
    public CooldownState cooldown(UUID player, String skillId) {
        return cooldowns.get(key(player, skillId));
    }

    /** 是否允许开始新施放（门禁统一入口）。 */
    public boolean canBegin(ServerPlayerEntity player, String skillId) {
        long now = now(player);
        CooldownState cd = cooldowns.get(key(player.getUuid(), skillId));
        return !activeCasts.containsKey(key(player.getUuid(), skillId))
                && (cd == null || !cd.isActive(now));
    }

    /** 玩家断线：冷却持久化保留；进行中施放记录清（效果由各 Manager 按旧规则处理）。 */
    public void onPlayerRemoved(UUID player) {
        activeCasts.keySet().removeIf(k -> k.startsWith(player + "\u0000"));
    }

    /** 冷启动：清已过期冷却（不重置未到期冷却；存 endTick 绝对值，离线不扣减自动成立）。 */
    public void onServerStarted(long currentTick) {
        cooldowns.values().removeIf(cd -> cd.endTick != 0 && cd.endTick <= currentTick);
        markDirty();
    }

    // ===== 内部 =====

    private void startCooldown(UUID player, String skillId, int ticks, long castId, long now) {
        if (ticks <= 0) return;
        CooldownState cd = cooldowns.computeIfAbsent(key(player, skillId), k -> new CooldownState());
        cd.endTick = now + ticks;
        cd.totalTicks = ticks;
        cd.lastCastId = castId;
        markDirty();
    }

    private Cast findCast(long castId) {
        for (Cast c : activeCasts.values()) {
            if (c.castId == castId) return c;
        }
        return null;
    }

    private static long nextCastId() {
        return ++castIdSeq;
    }

    // ===== 持久化 =====

    private static SkillCastManager fromNbt(NbtCompound nbt) {
        SkillCastManager mgr = new SkillCastManager();
        NbtCompound map = nbt.getCompound("cooldowns");
        for (String k : map.getKeys()) {
            NbtCompound entry = map.getCompound(k);
            CooldownState cd = new CooldownState();
            cd.totalTicks = entry.getInt("total");
            cd.endTick = entry.getLong("end");
            cd.lastCastId = entry.getLong("cast");
            if (cd.endTick > 0) mgr.cooldowns.put(k, cd);
        }
        return mgr;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound map = new NbtCompound();
        for (Map.Entry<String, CooldownState> e : cooldowns.entrySet()) {
            if (e.getValue().endTick == 0) continue;
            NbtCompound entry = new NbtCompound();
            entry.putInt("total", e.getValue().totalTicks);
            entry.putLong("end", e.getValue().endTick);
            entry.putLong("cast", e.getValue().lastCastId);
            map.put(e.getKey(), entry);
        }
        nbt.put("cooldowns", map);
        return nbt;
    }
}
