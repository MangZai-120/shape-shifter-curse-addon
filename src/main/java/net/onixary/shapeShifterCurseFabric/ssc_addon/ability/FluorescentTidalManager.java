package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.TidalOrbEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荧光幼灵主要技能「潮汐波动」管理器（服务端状态机）。
 *
 * <p>按键语义（客户端边沿触发发网络包）：
 * <ul>
 *   <li>空闲(STATE=0) → 按键：开始蓄力（1.25s=25t），STATE=1。</li>
 *   <li>蓄力中(STATE=1) → 满 25t：发射粒子球，STATE=2。</li>
 *   <li>球飞行中(STATE=2) → 再次按键：触发球减速（进入吸附流程），STATE 保持 2 直到球消失。</li>
 *   <li>球消失 → STATE=0，开始 CD（8s=160t）。CD 期间按键无效。</li>
 * </ul>
 * 蓄力期间可移动但减速 50%；受伤不打断；被净化打断蓄力并清除球。
 * 技能不消耗任何资源（潮湿度）。
 */
public final class FluorescentTidalManager {

    /** 客户端同步状态：0=空闲，1=蓄力中，2=球飞行中。 */
    public static final net.minecraft.util.Identifier TIDAL_STATE =
            new net.minecraft.util.Identifier("my_addon", "form_axolotl_fluorescent_tidal_state");

    private static final int CHARGE_TICKS = 25;       // 1.25 秒蓄力
    private static final int CD_TICKS = 160;          // 8 秒 CD（球消失后起算）
    private static final double CHARGE_SPEED_PENALTY = -0.5;  // 蓄力期间移动 -50%

    private static final UUID CHARGE_SPEED_UUID = UUID.fromString("9d2b3c4d-5e6f-7081-92a3-b4c5d6e7f819");

    private enum State { IDLE, CHARGING, FLYING }

    private static final class Session {
        State state = State.IDLE;
        int chargeTicks = 0;
        TidalOrbEntity orb = null;
        boolean pendingCd = false;   // 球消失后等待在主线程补设 CD（回调拿不到 player 引用）
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private FluorescentTidalManager() {
    }

    /** 客户端「按下主要技能键」时调用（由网络包触发）。 */
    public static void onKeyPress(ServerPlayerEntity player) {
        Session s = SESSIONS.computeIfAbsent(player.getUuid(), k -> new Session());
        if (!FormUtils.isAxolotlFluorescent(player)) {
            return;
        }
        switch (s.state) {
            case IDLE -> startCharge(player, s);
            case CHARGING -> { /* 蓄力中再按：忽略，必须等蓄力完成自动发射 */ }
            case FLYING -> triggerOrbDecel(player, s);
        }
    }

    private static void startCharge(ServerPlayerEntity player, Session s) {
        // CD 中不可用（潮汐现为副技能，用 SP_SECONDARY_CD）
        if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return;
        s.state = State.CHARGING;
        s.chargeTicks = 0;
        PowerUtils.setResourceValueAndSync(player, TIDAL_STATE, 1);
        // 蓄力减速 modifier
        applyChargeSpeed(player, true);
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 起手：多层音效（潮涌充能 + 海守诅咒 + 水花）
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 0.7f, 1.4f);
        sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.15);
        sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
    }

    /** 飞行中再次按键：让球开始减速。 */
    private static void triggerOrbDecel(ServerPlayerEntity player, Session s) {
        if (s.orb != null && s.orb.isAliveOrActive()) {
            s.orb.triggerDecelerate();
        }
    }

    /** 每服务端 tick 对每个在线玩家调用（由 SscAddon tick 注册）。 */
    public static void tick(ServerPlayerEntity player) {
        Session s = SESSIONS.get(player.getUuid());
        if (s == null) return;
        // 形态丢失 / 死亡 → 清理
        if (player.isDead() || !FormUtils.isAxolotlFluorescent(player)) {
            cleanup(player, s);
            return;
        }
        // 蓄力阶段推进
        if (s.state == State.CHARGING) {
            // 被净化打断
            if (player.hasStatusEffect(SscAddon.PURIFIED)) {
                cancelCharge(player, s);
                return;
            }
            s.chargeTicks++;
            // 蓄力音效：音高随进度上升（每 5 tick）
            if (s.chargeTicks % 5 == 0) {
                ServerWorld sw = (ServerWorld) player.getWorld();
                float prog = s.chargeTicks / (float) CHARGE_TICKS;
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.4f, 0.8f + prog * 0.8f);
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.5f, 0.8f + prog * 0.6f);
            }
            // 蓄力粒子（围绕玩家收缩的水环）
            if (s.chargeTicks % 2 == 0) {
                ServerWorld sw = (ServerWorld) player.getWorld();
                double ang = s.chargeTicks * 0.5;
                for (int i = 0; i < 4; i++) {
                    double a = ang + i * (Math.PI / 2);
                    double r = 1.2 - (s.chargeTicks / (double) CHARGE_TICKS) * 0.8;
                    sw.spawnParticles(ParticleTypes.BUBBLE,
                            player.getX() + Math.cos(a) * r, player.getY() + 1.0, player.getZ() + Math.sin(a) * r,
                            1, 0, 0.05, 0, 0.0);
                }
            }
            if (s.chargeTicks >= CHARGE_TICKS) {
                releaseOrb(player, s);
            }
        }
        // 飞行阶段：球实体自行 tick，管理器只等 onBallRemoved 回调
    }

    private static void releaseOrb(ServerPlayerEntity player, Session s) {
        // 移除蓄力减速
        applyChargeSpeed(player, false);
        s.state = State.FLYING;
        PowerUtils.setResourceValueAndSync(player, TIDAL_STATE, 2);
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 生成粒子球
        TidalOrbEntity orb = new TidalOrbEntity(sw, player);
        sw.spawnEntity(orb);
        s.orb = orb;
        // 发射：清爽的水灵发射音（溺尸投掷 + 潮涌激活，两种不重叠）
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_DROWNED_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.3f);
        sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.8, 0.6, 0.3);
        sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.5, 0.5, 0.25);
    }

    /** 球实体消失时回调：标记 pendingCd，由 tickPendingCd 在主线程补设 CD（回调拿不到 player 引用）。 */
    public static void onBallRemoved(UUID ownerUuid) {
        Session s = SESSIONS.get(ownerUuid);
        if (s == null) return;
        s.state = State.IDLE;
        s.orb = null;
        s.chargeTicks = 0;
        s.pendingCd = true;
    }

    /** 取消蓄力（被净化打断）：不发射，但进入 60% CD（返还 40%）。 */
    private static void cancelCharge(ServerPlayerEntity player, Session s) {
        applyChargeSpeed(player, false);
        s.state = State.IDLE;
        s.chargeTicks = 0;
        PowerUtils.setResourceValueAndSync(player, TIDAL_STATE, 0);
        // 被净化打断：返还 40% CD（进 60% CD = 160 × 0.6 = 96t = 4.8 秒）
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, (int)(CD_TICKS * 0.6));
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private static void cleanup(ServerPlayerEntity player, Session s) {
        applyChargeSpeed(player, false);
        if (s.orb != null && s.orb.isAliveOrActive()) {
            s.orb.discard();
        }
        s.state = State.IDLE;
        s.orb = null;
        s.chargeTicks = 0;
        PowerUtils.setResourceValueAndSync(player, TIDAL_STATE, 0);
    }

    /** 蓄力期间移动速度 -50%。 */
    private static void applyChargeSpeed(ServerPlayerEntity player, boolean apply) {
        var attr = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(CHARGE_SPEED_UUID);
        if (apply) {
            attr.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    CHARGE_SPEED_UUID, "Tidal Charge Slow", CHARGE_SPEED_PENALTY,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 由 SscAddon 在 END_SERVER_TICK 中调用：为待 CD 的 session 补设 CD 资源。 */
    public static void tickPendingCd(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            Session s = SESSIONS.get(p.getUuid());
            if (s == null || !s.pendingCd) continue;
            s.pendingCd = false;
            PowerUtils.setResourceValueAndSync(p, FormIdentifiers.SP_SECONDARY_CD, CD_TICKS);
            PowerUtils.setResourceValueAndSync(p, TIDAL_STATE, 0);
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        Session s = SESSIONS.remove(uuid);
        if (s != null && s.orb != null) s.orb.discard();
    }

    public static void clearAll() {
        for (Session s : SESSIONS.values()) {
            if (s.orb != null) s.orb.discard();
        }
        SESSIONS.clear();
    }
}