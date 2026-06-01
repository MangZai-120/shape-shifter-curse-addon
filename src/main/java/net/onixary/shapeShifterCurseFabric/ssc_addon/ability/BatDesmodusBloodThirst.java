package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 吸血蝙蝠 - 血渴值（Blood Thirst）系统。
 * 复用现有 Apoli 资源 my_addon:form_bat_desmodus_blood_resource (0~100)。
 *
 * 累积:
 *   - 普攻命中非白名单生物 +8 (内部冷却 0.3s)
 *   - 幽雾化形凝聚爆破命中非白名单生物 +12 / +6 / +3 (最多 3 个目标递减 50%)
 *   - 击杀 +15（由 form_bat_desmodus_blood_kill.json 触发，无叠加上限）
 *
 * 衰减:
 *   - 脱离战斗 12 秒后每秒 -4，直到再次进入战斗
 *
 * 战斗状态: 由 SscAddonLivingEntityMixin 在「玩家造成伤害」或「玩家受到伤害」时打点。
 */
public final class BatDesmodusBloodThirst {

    /** 最大值（与 JSON resource 上限一致） */
    public static final int MAX_BLOOD = 100;
    /** 普攻命中获得 +8 */
    private static final int ATTACK_HIT_GAIN = 8;
    /** 普攻命中血渴值的内部冷却（tick），0.3s */
    private static final long ATTACK_HIT_CD = 6L;
    /** 凝聚爆破第一个目标 +12，每多一个 ×0.5 (12/6/3)，最多 3 个 */
    private static final int SKILL_HIT_BASE = 12;
    private static final int SKILL_HIT_MAX_TARGETS = 3;
    /** 脱战衰减延迟（tick），12s */
    private static final long OUT_OF_COMBAT_DELAY = 240L;
    /** 衰减速率：每秒 -4 */
    private static final int DECAY_PER_SEC = 4;

    /** 玩家UUID -> 上次进入战斗的世界 tick */
    private static final Map<UUID, Long> LAST_COMBAT_TICK = new ConcurrentHashMap<>();
    /** 玩家UUID -> 上次普攻 +8 的世界 tick（用于内部CD） */
    private static final Map<UUID, Long> LAST_ATTACK_HIT_TICK = new ConcurrentHashMap<>();
    /** 玩家UUID -> 上次每秒结算（回血/衰减）tick */
    private static final Map<UUID, Long> LAST_REGEN_TICK = new ConcurrentHashMap<>();

    private BatDesmodusBloodThirst() {
    }

    /**
     * 伤害加成抑制标志：在该标志为 true 期间发生的伤害
     * 将不受 75-100 阶段 +15% 加成加成。合适使用于“被动/光环型”伤害（如血雾光环）。
     * 仅服务端使用，在伤害调用前后以 try/finally 配对设置。
     */
    public static final ThreadLocal<Boolean> SUPPRESS_OUTGOING_BUFF = ThreadLocal.withInitial(() -> false);

    // ==================== 通用 ====================

    private static boolean isBat(ServerPlayerEntity player) {
        return player != null && FormUtils.isForm(player, FormIdentifiers.BAT_DESMODUS);
    }

    /** 读取血渴值（仅蝙蝠形态有效） */
    private static int getBlood(ServerPlayerEntity player) {
        return PowerUtils.getResourceValue(player, FormIdentifiers.BAT_BLOOD_RESOURCE);
    }

    /** 设置血渴值（自动夹取 0~MAX，并同步） */
    private static void setBlood(ServerPlayerEntity player, int value) {
        int clamped = Math.max(0, Math.min(MAX_BLOOD, value));
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.BAT_BLOOD_RESOURCE, clamped);
    }

    private static void changeBlood(ServerPlayerEntity player, int delta) {
        if (delta == 0) return;
        setBlood(player, getBlood(player) + delta);
    }

    /** 当前血渴值阶段：0=0-25, 1=25-50, 2=50-75, 3=75-100 */
    public static int getStage(ServerPlayerEntity player) {
        if (!isBat(player)) return -1;
        int b = getBlood(player);
        if (b < 25) return 0;
        if (b < 50) return 1;
        if (b < 75) return 2;
        return 3;
    }

    /** 客户端版本，便于 HUD 染色 */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static int getClientStage(net.minecraft.entity.player.PlayerEntity player) {
        int[] vm = PowerUtils.getClientResourceValueAndMax(player, FormIdentifiers.BAT_BLOOD_RESOURCE);
        int b = vm[0];
        if (b < 25) return 0;
        if (b < 50) return 1;
        if (b < 75) return 2;
        return 3;
    }

    // ==================== 战斗打点 ====================

    /** 标记玩家进入战斗（仅蝙蝠形态生效） */
    public static void markCombat(ServerPlayerEntity player) {
        if (!isBat(player)) return;
        LAST_COMBAT_TICK.put(player.getUuid(), player.getWorld().getTime());
    }

    /** 是否处于战斗中（最近一次打点 ≤ OUT_OF_COMBAT_DELAY） */
    public static boolean isInCombat(ServerPlayerEntity player) {
        Long last = LAST_COMBAT_TICK.get(player.getUuid());
        if (last == null) return false;
        return player.getWorld().getTime() - last <= OUT_OF_COMBAT_DELAY;
    }

    // ==================== 命中事件 ====================

    /**
     * 蝙蝠玩家普攻命中其它生物时调用。
     * 仅当目标不在白名单内时累积 +8，并受 0.3s 内部冷却限制；
     * 无论是否命中白名单都标记战斗状态。
     */
    public static void onAttackHit(ServerPlayerEntity player, LivingEntity target) {
        if (!isBat(player) || target == null) return;
        markCombat(player);
        if (WhitelistUtils.isProtected(player, target)) return;
        long now = player.getWorld().getTime();
        Long last = LAST_ATTACK_HIT_TICK.get(player.getUuid());
        if (last != null && now - last < ATTACK_HIT_CD) return;
        LAST_ATTACK_HIT_TICK.put(player.getUuid(), now);
        changeBlood(player, ATTACK_HIT_GAIN);
    }

    /**
     * 凝聚爆破命中事件：传入实际造成伤害的目标列表（已过滤白名单）。
     * 对前 3 个非白名单目标按 12/6/3 累积，超过部分忽略。
     */
    public static void onSkillHit(ServerPlayerEntity player, List<LivingEntity> hitTargets) {
        if (!isBat(player) || hitTargets == null || hitTargets.isEmpty()) return;
        markCombat(player);
        int total = 0;
        int count = Math.min(hitTargets.size(), SKILL_HIT_MAX_TARGETS);
        int gain = SKILL_HIT_BASE;
        for (int i = 0; i < count; i++) {
            total += gain;
            gain = Math.max(1, gain / 2); // 12 -> 6 -> 3
        }
        if (total > 0) changeBlood(player, total);
    }

    // ==================== Tick ====================

    /** 每 tick 处理战斗回血与脱战衰减（仅蝙蝠形态生效） */
    public static void tick(ServerPlayerEntity player) {
        if (!isBat(player)) return;
        long now = player.getWorld().getTime();
        Long lastRegen = LAST_REGEN_TICK.get(player.getUuid());
        if (lastRegen == null) {
            LAST_REGEN_TICK.put(player.getUuid(), now);
            return;
        }
        if (now - lastRegen < 20L) return; // 每秒结算一次

        Long lastCombat = LAST_COMBAT_TICK.get(player.getUuid());
        boolean inCombat = lastCombat != null && now - lastCombat <= OUT_OF_COMBAT_DELAY;
        if (!inCombat) {
            // 脱战（含从未进入战斗）：每秒 -4，直至 0；战斗中不自动回复
            int b = getBlood(player);
            if (b > 0) {
                int decay = DECAY_PER_SEC;
                // 头顶天空可见时按昼夜节奏调整（与 sun_weak/moon_buff power 的 time_of_day 判定一致）
                net.minecraft.server.world.ServerWorld sw = player.getServerWorld();
                if (sw.isSkyVisible(player.getBlockPos())) {
                    long tod = sw.getTimeOfDay() % 24000L;
                    boolean isNight = tod >= 13000L && tod <= 23000L;
                    if (isNight) {
                        decay = 2; // 月夜亢奋：衰减减半（4 -> 2）
                    } else {
                        decay = decay + decay / 2; // 日光虚弱：4 -> 6
                    }
                }
                if (decay > 0) setBlood(player, b - decay);
            }
        }
        LAST_REGEN_TICK.put(player.getUuid(), now);
    }

    // ==================== 状态清理 ====================

    public static void clearAll() {
        LAST_COMBAT_TICK.clear();
        LAST_ATTACK_HIT_TICK.clear();
        LAST_REGEN_TICK.clear();
    }

    public static void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) return;
        LAST_COMBAT_TICK.remove(playerUuid);
        LAST_ATTACK_HIT_TICK.remove(playerUuid);
        LAST_REGEN_TICK.remove(playerUuid);
    }
}
