package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风灵被动「落地风涌」。
 *
 * <p>玩家显著滞空后（fallDistance ≥ 1.5）落地时，若本次滞空期间<b>未攻击命中任何敌人</b>，
 * 触发一次落地冲击：3 格半径内造成 6 点物理伤害 + 击退。5 秒内置 CD。
 * 若滞空期间命中过敌人（徒手近战 / 疾风连爪 / 弹射物命中均算），则落地不触发（鼓励空中战斗）。
 *
 * <p>机制：
 * <ul>
 *   <li>{@code ServerLivingEntityEvents.ALLOW_DAMAGE}：风灵在空中造成伤害 → 标记 hitDuringAir</li>
 *   <li>服务端 tick：onGround 下降沿 + 上一 tick fallDistance ≥ 1.5 + CD 就绪 → 触发风涌</li>
 * </ul>
 * 判定全服务端，粒子广播，多人一致。
 */
public final class WindSpiritLandingSurgeManager {

    private static final double RADIUS = 3.0;
    private static final float DAMAGE = 6.0f;
    private static final int COOLDOWN_TICKS = 100;          // 5 秒
    private static final float MIN_FALL_DISTANCE = 1.5f;     // 显著滞空阈值

    /** 每玩家上一 tick 的 onGround 状态（用于检测下降沿）。 */
    private static final Map<UUID, Boolean> PREV_ON_GROUND = new ConcurrentHashMap<>();
    /** 每玩家上一 tick 的 fallDistance（落地瞬间 MC 会清零，故需记 prev）。 */
    private static final Map<UUID, Float> PREV_FALL_DISTANCE = new ConcurrentHashMap<>();
    /** 每玩家下次可触发风涌的服务端 tick（CD 时间戳）。 */
    private static final Map<UUID, Long> NEXT_AVAILABLE_TICK = new ConcurrentHashMap<>();
    /** 本次滞空期间是否攻击命中过敌人（true 则落地不触发）。 */
    private static final Map<UUID, Boolean> HIT_DURING_AIR = new ConcurrentHashMap<>();

    private static boolean registered = false;

    private WindSpiritLandingSurgeManager() {
    }

    /** 在 mod 初始化时调用一次：注册伤害事件监听（追踪空中命中）。 */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            handleDamageDealt(entity, source);
            return true; // 不拦截伤害
        });
    }

    /** 伤害结算时：若攻击方是风灵且在空中，标记本次滞空命中过。 */
    private static void handleDamageDealt(LivingEntity target, DamageSource source) {
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity player)) return;
        if (!FormUtils.isOcelotSP(player)) return;
        if (player.isOnGround()) return; // 不在空中则忽略
        // 在空中造成伤害 → 标记
        HIT_DURING_AIR.put(player.getUuid(), Boolean.TRUE);
    }

    /** 每服务端 tick 对每个在线玩家调用（由 SscAddon tick 循环驱动）。 */
    public static void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        boolean curOnGround = player.isOnGround();
        Boolean prevOnGroundObj = PREV_ON_GROUND.get(uuid);
        float curFall = player.fallDistance;
        Float prevFallObj = PREV_FALL_DISTANCE.get(uuid);

        // 起跳瞬间（从地面到空中）：清除命中标记，开始新一轮滞空追踪
        if (prevOnGroundObj != null && prevOnGroundObj && !curOnGround) {
            HIT_DURING_AIR.remove(uuid);
        }

        // 落地下降沿：prev 在空中 → cur 在地面
        if (prevOnGroundObj != null && !prevOnGroundObj && curOnGround) {
            float prevFall = prevFallObj != null ? prevFallObj : 0f;
            if (prevFall >= MIN_FALL_DISTANCE) {
                tryTriggerSurge(player, uuid);
            }
            // 落地后清理本次滞空状态
            HIT_DURING_AIR.remove(uuid);
        }

        // 记录本 tick 状态供下一 tick 用
        PREV_ON_GROUND.put(uuid, curOnGround);
        PREV_FALL_DISTANCE.put(uuid, curFall);
    }

    /** 尝试触发落地风涌（含 CD / 空中命中检查）。 */
    private static void tryTriggerSurge(ServerPlayerEntity player, UUID uuid) {
        // 非风灵不触发
        if (!FormUtils.isOcelotSP(player)) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        // CD 检查
        Long next = NEXT_AVAILABLE_TICK.get(uuid);
        if (next != null && now < next) return;

        // 本次滞空期间命中过敌人 → 不触发（鼓励空中战斗）
        if (Boolean.TRUE.equals(HIT_DURING_AIR.get(uuid))) return;

        // 触发风涌：3 格半径 AOE
        Vec3d center = player.getPos();
        Box box = new Box(center.subtract(RADIUS, RADIUS, RADIUS), center.add(RADIUS, RADIUS, RADIUS));
        for (Entity e : world.getOtherEntities(player, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living == player) continue;
            if (WhitelistUtils.isProtected(player, living)) continue; // 默认白名单
            // 视线检查：墙后目标不命中（仿原版 PR #523 豹猫冲刺穿墙修复）
            if (!net.jackcooper.shapeShifterCurseAddon.util.LineOfSightUtils.hasLineOfSight(world, player, living)) continue;
            living.damage(player.getDamageSources().playerAttack(player), DAMAGE);
            Vec3d push = living.getPos().subtract(center);
            if (push.lengthSquared() < 1.0e-4) push = new Vec3d(0, 1, 0);
            push = push.normalize();
            living.takeKnockback(0.5, -push.x, -push.z);
        }

        // 粒子：环形冲击波 + 向上扬尘
        world.spawnParticles(ParticleTypes.POOF, center.x, center.y + 0.1, center.z,
                20, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.08);
        world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z,
                16, RADIUS * 0.6, 0.15, RADIUS * 0.6, 0.06);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 0.5, center.z,
                4, RADIUS * 0.3, 0.2, RADIUS * 0.3, 0.0);

        // 音效（全员可听）
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.PLAYERS, 0.6f, 1.3f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4f, 1.5f);

        // 设置 CD
        NEXT_AVAILABLE_TICK.put(uuid, now + COOLDOWN_TICKS);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PREV_ON_GROUND.remove(uuid);
        PREV_FALL_DISTANCE.remove(uuid);
        NEXT_AVAILABLE_TICK.remove(uuid);
        HIT_DURING_AIR.remove(uuid);
    }
}
