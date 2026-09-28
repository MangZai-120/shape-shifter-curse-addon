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
        public ResolvedConfig config; // 档位型释放前可 retune 改写
        public final long beginTick;    // 建档时刻（超时兜底用）
        public int phase = PHASE_CHARGING;
        public boolean settled = false;
        /** on_cast 模式下已先行起算的冷却标记（失败改写用）。 */
        public boolean cooldownStarted = false;
        public UUID persistentEntity;

        Cast(long castId, UUID playerId, String skillId, ResolvedConfig config, long beginTick) {
            this.castId = castId;
            this.playerId = playerId;
            this.skillId = skillId;
            this.config = config;
            this.beginTick = beginTick;
        }

        /** 档位型改写（未结算时有效）。 */
        void retune(int actualCooldown) {
            config = new ResolvedConfig(actualCooldown, config.failCooldown, config.cooldownStart);
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
            this.cooldownStart = cooldownStart == null ? START_ON_CAST : cooldownStart;
        }
    }

    /** 单技能冷却状态（持久化单元）。 */
    public static final class CooldownState {
        public long endTick;        // 冷却结束的绝对服务器 tick（0=无冷却）
        public int totalTicks;      // 本次冷却总长（HUD 分母；失败档用实际值）
        public long lastCastId;
        public int pausedRemaining = -1;

        public CooldownState() {
        }

        public boolean isActive(long now) {
            return remaining(now) > 0;
        }

        public int remaining(long now) {
            return pausedRemaining >= 0 ? pausedRemaining : (int) Math.min(Integer.MAX_VALUE, Math.max(0, endTick - now));
        }
    }

    // ===== 存储 =====
    private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Cast> activeCasts = new ConcurrentHashMap<>();

    private static volatile SkillCastManager instance;
    private static long castIdSeq = 0;
    private long lastWorldTick;
    private boolean needsClockRestore;
    private final java.util.Set<UUID> offline = new java.util.HashSet<>();

    /** 获取服务端实例（overworld PersistentState 挂载；每个逻辑服一份）。 */
    public static SkillCastManager get(ServerWorld world) {
        SkillCastManager mgr = world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(SkillCastManager::fromNbt, SkillCastManager::new, KEY);
        instance = mgr;
        mgr.onServerStarted(world.getServer().getOverworld().getTime());
        mgr.lastWorldTick = world.getServer().getOverworld().getTime();
        return mgr;
    }

    /** 统一时钟：overworld 世界时间（随存档持久化，重启不归零；
     * 修 P1-4：原 server.getTicks() 重启归零会让存下的绝对 endTick 变成天文数字 → 永久锁死）。 */
    public static long now(ServerPlayerEntity player) {
        ServerWorld ow = player.getServer().getOverworld();
        return ow != null ? ow.getTime() : player.getServer().getTicks();
    }

    public static SkillCastManager instanceOrNull() {
        return instance;
    }

    public static String domain(String skill) {
        return skill.equals("my_addon:form_upgrade_familiar_fox_spark")
                ? "my_addon:form_upgrade_familiar_fox_fire_ring" : skill;
    }

    private static boolean persistentEffect(Cast cast) {
        return cast.phase == PHASE_ACTIVE && (cast.persistentEntity != null
                || cast.skillId.equals("my_addon:form_snow_fox_sp_ranged_secondary"));
    }

    public void bindEntity(long castId, UUID entityId) {
        Cast cast = findCast(castId);
        if (cast != null) { cast.persistentEntity = entityId; markDirty(); }
    }

    public void onPlayerJoined(UUID player, long now) {
        lastWorldTick = now;
        offline.remove(player);
        String prefix = player + "\u0000";
        cooldowns.forEach((k, cd) -> {
            if (k.startsWith(prefix) && cd.pausedRemaining >= 0) {
                cd.endTick = now + cd.pausedRemaining;
                cd.pausedRemaining = -1;
            }
        });
        markDirty();
    }

    private static String key(UUID player, String skillId) {
        return player + "\u0000" + domain(skillId);
    }

    // ===== 拟议接口（计划书 §4.2）=====

    /**
     * 接受一次施放：建立 cast 记录后返回 castId。
     * on_cast 模式立即起算正常 CD；其余模式等待 released/finish。
     * 冷却未完 / 已在施放中 → 返回 -1（rejected）。
     */
    public long begin(ServerPlayerEntity player, String skillId, ResolvedConfig config) {
        return begin(player.getUuid(), skillId, config, now(player));
    }

    public long begin(UUID player, String skillId, ResolvedConfig config, long now) {
        String k = key(player, skillId);
        CooldownState cd = cooldowns.get(k);
        if (cd != null && cd.isActive(now)) return -1;
        if (activeCasts.containsKey(k)) return -1;

        // lastWorldTick 只在真实变更时推进：被拒绝的 begin 是只读查询，
        // 不能把 savedAt 推过最后一次写入，否则重启冻结的剩余 CD 会凭空减少。
        lastWorldTick = now;
        Cast cast = new Cast(nextCastId(), player, skillId, config, now);
        activeCasts.put(k, cast);
        cooldowns.computeIfAbsent(k, ignored -> new CooldownState());
        markDirty();

        if (START_ON_CAST.equals(config.cooldownStart)) {
            startCooldown(player, skillId, config.cooldown, cast.castId, now);
            cast.cooldownStarted = true;
        }
        return cast.castId;
    }

    /**
     * 确认实际释放成功（蓄力完成/效果提交）。
     * on_release 模式此时起算正常 CD；on_cast 已起算保持；on_end 等待 finish。
     */
    public void released(ServerPlayerEntity player, long castId) {
        released(castId, now(player));
    }

    public void released(long castId, long now) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled || cast.phase == PHASE_ACTIVE) return;
        if (START_ON_RELEASE.equals(cast.config.cooldownStart)) {
            startCooldown(cast.playerId, cast.skillId, cast.config.cooldown, castId, now);
            cast.cooldownStarted = true;
        }
        cast.phase = PHASE_ACTIVE;
        markDirty();
    }

    /**
     * 结束有效生命周期（效果结束/主动关闭/自然到期）。
     * on_end 模式此时起算正常 CD；瞬发技能同 tick released+finish 依次结算。
     */
    public void complete(long castId, long now) {
        released(castId, now);
        finish(castId, now);
    }

    public void finish(ServerPlayerEntity player, long castId) {
        finish(castId, now(player));
    }

    public void finish(long castId, long now) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        if (START_ON_END.equals(cast.config.cooldownStart) && !cast.cooldownStarted) {
            startCooldown(cast.playerId, cast.skillId, cast.config.cooldown, castId, now);
        }
        cast.settled = true;
        activeCasts.remove(key(cast.playerId, cast.skillId), cast);
        markDirty();
    }

    /**
     * 确认释放失败：按 fail_cooldown 结算（不受 cooldown_start 延迟，计划书 §3.3）。
     * 修 P1-6：fail_cooldown=0 且 on_cast 已先行起算时，清除已启动的正常冷却（零失败档=免费）。
     */
    public void fail(ServerPlayerEntity player, long castId) {
        fail(castId, now(player));
    }

    public void fail(long castId, long now) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        if (cast.config.failCooldown > 0) {
            startCooldown(cast.playerId, cast.skillId, cast.config.failCooldown, castId, now);
        } else if (cast.cooldownStarted) {
            CooldownState cd = cooldowns.get(key(cast.playerId, cast.skillId));
            if (cd != null) {
                cd.endTick = 0;
                cd.totalTicks = 0;
                if (cd.pausedRemaining >= 0) cd.pausedRemaining = 0;
                markDirty();
            }
        }
        cast.settled = true;
        activeCasts.remove(key(cast.playerId, cast.skillId), cast);
        markDirty();
    }

    /** 中断时按实际阶段结算；重复清理与旧回调均不重置冷却。 */
    public void interrupt(long castId, long now) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        if (cast.phase == PHASE_ACTIVE) finish(castId, now);
        else fail(castId, now);
    }

    /** 显式免费撤销；普通死亡、变形和断线应使用 interrupt。 */
    public void abort(ServerPlayerEntity player, long castId) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        cast.settled = true;
        activeCasts.remove(key(cast.playerId, cast.skillId), cast);
        markDirty();
    }

    /** 不经施放流程直接上 CD（外部联动用）。 */
    public void force(UUID player, String skillId, int ticks, long now) {
        startCooldown(player, skillId, Math.max(0, ticks), 0, now);
    }

    /** 清除冷却（击杀刷新等）。 */
    public void reset(UUID player, String skillId) {
        CooldownState cd = cooldowns.get(key(player, skillId));
        if (cd == null) return;
        cd.endTick = 0;
        cd.totalTicks = 0;
                if (cd.pausedRemaining >= 0) cd.pausedRemaining = 0;
        markDirty();
    }

    public java.util.List<Cast> activeCasts() {
        return java.util.List.copyOf(activeCasts.values());
    }

    /** 二次操作（关环/引爆/断丝等）：不新建施放，返回进行中 cast 供控制（§4.1）。 */
    public Cast control(UUID player, String skillId) {
        return activeCasts.get(key(player, skillId));
    }

    /** 档位型技能释放前改写本次 cast 的冷却值（tier 档位/动态档用；仅未结算 cast 可改）。 */
    public void retune(long castId, int actualCooldown) {
        Cast cast = findCast(castId);
        if (cast == null || cast.settled) return;
        int ticks = Math.max(0, actualCooldown);
        cast.retune(ticks);
        markDirty();
        CooldownState cd = cooldowns.get(key(cast.playerId, cast.skillId));
        if (cast.cooldownStarted && cd != null && cd.lastCastId == castId) {
            if (cd.pausedRemaining >= 0) cd.pausedRemaining = Math.max(0, cd.pausedRemaining - cd.totalTicks + ticks);
            cd.endTick = cd.endTick - cd.totalTicks + ticks;
            cd.totalTicks = ticks;
            markDirty();
        }
    }

    /** 查询冷却（服务端权威；HUD/同步用）。 */
    public CooldownState cooldown(UUID player, String skillId) {
        return cooldowns.get(key(player, skillId));
    }

    public record View(String skill, int phase, int remaining, int total) {}

    public java.util.List<View> snapshot(UUID player, long now) {
        String prefix = player + "\u0000";
        java.util.List<View> result = new java.util.ArrayList<>();
        cooldowns.forEach((k, cd) -> {
            if (!k.startsWith(prefix)) return;
            Cast cast = activeCasts.get(k);
            result.add(new View(k.substring(prefix.length()), cast == null ? PHASE_IDLE : cast.phase,
                    cd.remaining(now), cd.totalTicks));
        });
        result.sort(java.util.Comparator.comparing(View::skill));
        return result;
    }

    /** 是否允许开始新施放（门禁统一入口）。 */
    public boolean canBegin(ServerPlayerEntity player, String skillId) {
        long now = now(player);
        CooldownState cd = cooldowns.get(key(player.getUuid(), skillId));
        return !activeCasts.containsKey(key(player.getUuid(), skillId))
                && (cd == null || !cd.isActive(now));
    }

    /** 玩家断线（修审查#4：不再免费绕过）：
     *  - on_cast 已起算 / on_release 已结算的：冷却已在 cooldowns 表，持久化自然保留；
     *  - on_end 尚未起算的效果（断线即视为效果结束）：立即按 cooldown 结算正常 CD；
     *  - CHARGING 蓄力中未释放的：视为失败，按 fail_cooldown 结算（fail=0 免费）。 */
    public void onPlayerRemoved(UUID player, long now, java.util.function.Function<UUID, net.minecraft.server.network.ServerPlayerEntity> playerLookup) {
        for (Cast cast : activeCasts()) {
            if (cast.playerId.equals(player) && !persistentEffect(cast)) interrupt(cast.castId, now);
        }
        lastWorldTick = now;
        offline.add(player);
        String prefix = player + "\u0000";
        cooldowns.forEach((k, cd) -> {
            if (k.startsWith(prefix)) cd.pausedRemaining = cd.remaining(now);
        });
        markDirty();
    }

    /** 冷启动：清已过期冷却（不重置未到期冷却；存 endTick 绝对值，离线不扣减自动成立）。 */
    public void onServerStarted(long currentTick) {
        if (!needsClockRestore) return;
        cooldowns.forEach((k, cd) -> {
            if (cd.pausedRemaining < 0) cd.pausedRemaining = cd.remaining(currentTick);
            offline.add(UUID.fromString(k.substring(0, k.indexOf('\u0000'))));
        });
        lastWorldTick = currentTick;
        needsClockRestore = false;
        markDirty();
    }

    // ===== 内部 =====

    private void startCooldown(UUID player, String skillId, int ticks, long castId, long now) {
        CooldownState cd = cooldowns.computeIfAbsent(key(player, skillId), k -> new CooldownState());
        lastWorldTick = now;
        cd.endTick = now + ticks;
        cd.pausedRemaining = offline.contains(player) ? ticks : -1;
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

    static SkillCastManager fromNbt(NbtCompound nbt) {
        SkillCastManager mgr = new SkillCastManager();
        NbtCompound map = nbt.getCompound("cooldowns");
        for (String k : map.getKeys()) {
            NbtCompound entry = map.getCompound(k);
            CooldownState cd = new CooldownState();
            cd.totalTicks = entry.getInt("total");
            cd.endTick = entry.getLong("end");
            cd.lastCastId = entry.getLong("cast");
            cd.pausedRemaining = entry.contains("paused") ? entry.getInt("paused") : -1;
            UUID owner = UUID.fromString(k.substring(0, k.indexOf('\u0000')));
            String canonical = key(owner, k.substring(k.indexOf('\u0000') + 1));
            mgr.cooldowns.merge(canonical, cd, (a, b) ->
                    a.remaining(nbt.getLong("savedAt")) >= b.remaining(nbt.getLong("savedAt")) ? a : b);
            if (cd.pausedRemaining >= 0) mgr.offline.add(owner);
        }
        castIdSeq = Math.max(castIdSeq, nbt.getLong("sequence"));
        NbtCompound active = nbt.getCompound("active");
        for (String k : active.getKeys()) {
            NbtCompound entry = active.getCompound(k);
            Cast cast = new Cast(entry.getLong("cast"), entry.getUuid("owner"), entry.getString("skill"),
                    new ResolvedConfig(entry.getInt("normal"), entry.getInt("failed"), entry.getString("start")), entry.getLong("begin"));
            cast.phase = entry.getInt("phase");
            cast.cooldownStarted = entry.getBoolean("started");
            castIdSeq = Math.max(castIdSeq, cast.castId);
            // 重启后进行中的效果已不存在：已放出按正常 CD、未放出按失败 CD 结算
            if (entry.containsUuid("entity")) cast.persistentEntity = entry.getUuid("entity");
            if (persistentEffect(cast)) {
                mgr.activeCasts.put(key(cast.playerId, cast.skillId), cast);
                mgr.cooldowns.computeIfAbsent(key(cast.playerId, cast.skillId), ignored -> new CooldownState());
                mgr.offline.add(cast.playerId);
                continue;
            }
            mgr.offline.add(cast.playerId);
            if (cast.phase != PHASE_ACTIVE || !cast.cooldownStarted) {
                mgr.startCooldown(cast.playerId, cast.skillId,
                        cast.phase == PHASE_ACTIVE ? cast.config.cooldown : cast.config.failCooldown,
                        cast.castId, nbt.getLong("savedAt"));
            }
        }
        mgr.needsClockRestore = true;
        return mgr;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound map = new NbtCompound();
        for (Map.Entry<String, CooldownState> e : cooldowns.entrySet()) {
            if (e.getValue().endTick == 0 && e.getValue().pausedRemaining < 0) continue;
            NbtCompound entry = new NbtCompound();
            entry.putInt("total", e.getValue().totalTicks);
            entry.putLong("end", e.getValue().endTick);
            entry.putLong("cast", e.getValue().lastCastId);
            entry.putInt("paused", e.getValue().pausedRemaining);
            map.put(e.getKey(), entry);
        }
        nbt.put("cooldowns", map);
        nbt.putLong("sequence", castIdSeq);
        nbt.putLong("savedAt", lastWorldTick);
        NbtCompound active = new NbtCompound();
        activeCasts.forEach((k, cast) -> {
            NbtCompound entry = new NbtCompound();
            entry.putLong("cast", cast.castId); entry.putUuid("owner", cast.playerId);
            entry.putString("skill", cast.skillId); entry.putInt("normal", cast.config.cooldown);
            entry.putInt("failed", cast.config.failCooldown); entry.putString("start", cast.config.cooldownStart);
            entry.putLong("begin", cast.beginTick); entry.putInt("phase", cast.phase);
            if (cast.persistentEntity != null) entry.putUuid("entity", cast.persistentEntity);
            entry.putBoolean("started", cast.cooldownStarted); active.put(k, entry);
        });
        nbt.put("active", active);
        return nbt;
    }
}
