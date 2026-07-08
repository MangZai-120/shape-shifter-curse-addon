package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风灵「风之冲刺」主技能（sp_primary）—— 四阶段状态机。
 *
 * <p>第 1 次按主技能键：
 * <ul>
 *   <li><b>RISE 起飞</b>：垂直上升至 5 格高。0-3 格保持较快速度（≈0.45 格/tick），
 *       3-5 格线性减速到 0。脚下灰烟（CAMPFIRE_COSY_SMOKE）+ 烟花上升粒子（FIREWORK）。
 *       上方有方块则顶头停在方块下方（不穿墙）。</li>
 *   <li><b>HOVER 悬浮</b>：到顶后定住 3 秒，脚下持续灰烟；同时客户端显示绿色落点指示器
 *       （准星水平方向最远 16 格，半径 3 格）。</li>
 * </ul>
 * <p>悬浮中第 2 次按主技能键：
 * <ul>
 *   <li><b>DASH 冲刺</b>：直线快速冲到落点中心。起点反方向冒白粒子（CLOUD），
 *       路径白粒子拖尾。冲刺过程中不产生灰烟。</li>
 *   <li><b>LAND 落地</b>：落点 3 格半径内 12 点物理伤害 + 方块视觉浮动（粒子模拟），
 *       进入 12 秒 CD。</li>
 * </ul>
 * <p>悬浮 3 秒超时未按键：<b>FALL 缓慢落下</b>（3 格/秒），落地无伤害但进入 CD。
 *
 * <p>所有判定在服务端，粒子通过 ServerWorld 广播，落点预览纯客户端各自计算。
 * 伤害 AOE 默认白名单（{@code my_addon:not_actor_whitelisted} 对应 WhitelistUtils）。
 */
public final class WindDashManager {

    // ===== 常量 =====
    private static final float TARGET_HEIGHT = 5.0f;
    private static final double RISE_FAST_SPEED = 0.45;      // 0-3 格阶段速度（格/tick）
    private static final int RISE_FAST_TICKS = 7;            // 0-3 格约 7 tick
    private static final int RISE_SLOW_TICKS = 10;           // 3-5 格减速阶段约 10 tick
    private static final int HOVER_TICKS = 60;               // 悬浮 3 秒
    private static final double DASH_SPEED = 1.5;            // 冲刺速度（格/tick，极快）
    private static final double MAX_DASH_RANGE = 16.0;
    private static final double LANDING_RADIUS = 3.0;
    private static final float LANDING_DAMAGE = 12.0f;
    private static final int COOLDOWN_TICKS = 240;           // 12 秒
    private static final double FALL_SPEED = 0.15;           // 3 格/秒 ≈ 0.15 格/tick

    // ===== 阶段 =====
    public static final int PHASE_NONE = 0;
    public static final int PHASE_RISE = 1;
    public static final int PHASE_HOVER = 2;
    public static final int PHASE_DASH = 3;
    public static final int PHASE_FALL = 4;

    private static final Map<UUID, DashState> STATES = new ConcurrentHashMap<>();

    public static final class DashState {
        int phase = PHASE_NONE;
        double startY;          // 起飞前脚部 Y
        double targetY;         // 目标悬浮 Y（可能因顶头而低于 startY+5）
        int riseTick = 0;       // 起飞阶段 tick 计数
        int hoverTick = 0;      // 悬浮已持续 tick
        Vec3d dashTarget = null; // 冲刺落点
        double dashTraveled = 0.0;
        double dashTotal = 0.0;
        Vec3d dashStart = null;
        Vec3d dashDir = null;
        boolean dashed = false; // 是否已进入过冲刺（区分"冲刺落地"与"超时落下落地"）
    }

    private WindDashManager() {
    }

    /** 客户端按主技能键（无 payload）。服务端根据当前阶段分支。 */
    public static void onKeyPress(ServerPlayerEntity player) {
        if (!FormUtils.isOcelotSP(player)) {
            clear(player);
            return;
        }
        DashState s = STATES.get(player.getUuid());
        if (s == null || s.phase == PHASE_NONE) {
            // CD 中不能起飞
            if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return;
            startRise(player);
        } else if (s.phase == PHASE_HOVER) {
            // 悬浮中第 2 次按键 → 冲刺
            startDash(player, s);
        }
        // RISE/DASH/FALL 期间按键忽略
    }

    /** 开始起飞。 */
    private static void startRise(ServerPlayerEntity player) {
        DashState s = new DashState();
        s.phase = PHASE_RISE;
        s.startY = player.getY();
        s.targetY = s.startY + TARGET_HEIGHT;
        // 顶头检测：从脚部向上 raycast 5.5 格，碰到的方块下方即为可达最高点
        double headClear = checkVerticalClearance(player, TARGET_HEIGHT + 0.5);
        if (headClear < TARGET_HEIGHT) {
            // 上方有方块阻挡，目标降为碰顶处（方块下方）
            s.targetY = s.startY + Math.max(0.5, headClear - 0.05);
        }
        STATES.put(player.getUuid(), s);
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 起飞音效（全员可听）：用短促上升音，避免循环/长音在落地后残留 1-2 秒
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.9f, 0.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.5f, 1.8f);
        // 同步阶段给客户端（驱动落点预览/音效）
        SscAddonNetworking.syncDashState(player, s.phase, s.targetY);
    }

    /** 悬浮中触发冲刺：3D 准星射线找落点（瞄近处地面=近落点，瞄远处=远落点，看天空退到16格）。 */
    private static void startDash(ServerPlayerEntity player, DashState s) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector().normalize();
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 3D 准星射线找落点（与客户端预览 computeHorizontalLanding 完全一致，保证所见即所得）
        s.dashTarget = computeDashLanding(sw, eye, look, player);
        s.dashStart = new Vec3d(player.getX(), player.getY(), player.getZ());
        s.dashDir = s.dashTarget.subtract(s.dashStart).normalize();
        s.dashTotal = Math.max(1.0, s.dashStart.distanceTo(s.dashTarget));
        s.dashTraveled = 0.0;
        s.phase = PHASE_DASH;
        s.dashed = true;

        // 起点反方向白粒子（向冲刺反方向喷出）
        Vec3d back = s.dashDir.multiply(-1);
        sw.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(),
                25, 0.2, 0.3, 0.2, 0.02);
        // 给反方向粒子一个速度（用 spawnParticles 的速度分量近似）
        for (int i = 0; i < 15; i++) {
            sw.spawnParticles(ParticleTypes.CLOUD,
                    player.getX() + back.x * 0.5, player.getY() + 1.0, player.getZ() + back.z * 0.5,
                    1, 0.1, 0.1, 0.1, 0.0);
        }
        // 冲刺音效
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.8f);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.6f, 1.4f);
        SscAddonNetworking.syncDashState(player, s.phase, 0);
    }

    /** 每服务端 tick 对每个在线玩家调用。 */
    public static void tick(ServerPlayerEntity player) {
        DashState s = STATES.get(player.getUuid());
        if (s == null) return;

        // 死亡 / 非风灵 → 清理并让玩家自然落下
        if (player.isDead() || !FormUtils.isOcelotSP(player)) {
            clear(player);
            return;
        }

        switch (s.phase) {
            case PHASE_RISE -> tickRise(player, s);
            case PHASE_HOVER -> tickHover(player, s);
            case PHASE_DASH -> tickDash(player, s);
            case PHASE_FALL -> tickFall(player, s);
            default -> {}
        }
    }

    private static void tickRise(ServerPlayerEntity player, DashState s) {
        s.riseTick++;
        ServerWorld sw = (ServerWorld) player.getWorld();

        // 计算本 tick 应到达的 Y
        double curY = player.getY();
        double dy = s.targetY - curY;
        double stepY;
        if (dy <= 0) {
            // 已到顶
            enterHover(player, s);
            return;
        }
        double traveledFromStart = curY - s.startY;
        if (traveledFromStart < 3.0) {
            // 0-3 格快速
            stepY = Math.min(RISE_FAST_SPEED, dy);
        } else {
            // 3-5 格线性减速：从 RISE_FAST_SPEED 线性到 0
            float t = (float) MathHelper.clamp((traveledFromStart - 3.0) / 2.0, 0.0, 1.0);
            stepY = RISE_FAST_SPEED * (1.0f - t);
            stepY = Math.min(stepY, dy);
            if (stepY < 0.02) stepY = dy; // 收尾，避免抖动
        }

        // 应用位移（垂直，保持 x/z）
        player.setVelocity(0, stepY, 0);
        player.velocityModified = true;
        player.setPosition(player.getX(), curY + stepY, player.getZ());

        // 脚下灰烟 + 烟花上升粒子
        spawnRiseParticles(sw, player);

        if (curY + stepY >= s.targetY - 0.05 || s.riseTick > RISE_FAST_TICKS + RISE_SLOW_TICKS + 5) {
            enterHover(player, s);
        }
    }

    private static void enterHover(ServerPlayerEntity player, DashState s) {
        // 精确落到 targetY
        player.setPosition(player.getX(), s.targetY, player.getZ());
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        s.phase = PHASE_HOVER;
        s.hoverTick = 0;
        SscAddonNetworking.syncDashState(player, s.phase, s.targetY);
    }

    private static void tickHover(ServerPlayerEntity player, DashState s) {
        s.hoverTick++;
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 定住位置（对抗重力）
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        // 脚下持续灰烟
        sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() - 0.1, player.getZ(),
                3, 0.25, 0.0, 0.25, 0.005);

        if (s.hoverTick >= HOVER_TICKS) {
            // 超时 → 缓慢落下
            s.phase = PHASE_FALL;
            SscAddonNetworking.syncDashState(player, s.phase, 0);
        }
    }

    private static void tickDash(ServerPlayerEntity player, DashState s) {
        ServerWorld sw = (ServerWorld) player.getWorld();
        double step = Math.min(DASH_SPEED, s.dashTotal - s.dashTraveled);
        if (step <= 0) {
            enterLand(player, s, true);
            return;
        }
        s.dashTraveled += step;
        Vec3d next = s.dashStart.add(s.dashDir.multiply(s.dashTraveled));
        // 直线冲刺中途碰撞检测：若中途遇墙，提前落地
        if (!isClearPath(sw, new Vec3d(player.getX(), player.getY(), player.getZ()), next)) {
            enterLand(player, s, true);
            return;
        }
        player.setVelocity(s.dashDir.x * DASH_SPEED, s.dashDir.y * DASH_SPEED, s.dashDir.z * DASH_SPEED);
        player.velocityModified = true;
        player.setPosition(next.x, next.y, next.z);

        // 路径白粒子拖尾（冲刺中无灰烟）
        sw.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(),
                4, 0.15, 0.15, 0.15, 0.0);
        sw.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.2, player.getZ(),
                3, 0.1, 0.1, 0.1, 0.0);

        if (s.dashTraveled >= s.dashTotal - 0.05) {
            enterLand(player, s, true);
        }
    }

    private static void tickFall(ServerPlayerEntity player, DashState s) {
        ServerWorld sw = (ServerWorld) player.getWorld();
        // 3 格/秒 = 0.15 格/tick 缓慢下落
        double stepY = FALL_SPEED;
        double curY = player.getY();
        // 检测脚下方块，落地则结束
        double groundY = findGroundY(sw, player.getX(), player.getZ(), curY);
        if (curY - stepY <= groundY + 0.01) {
            // 落地（超时落下：无伤害但有 CD）
            player.setPosition(player.getX(), groundY, player.getZ());
            player.setVelocity(0, 0, 0);
            player.velocityModified = true;
            // 落地小粒子
            sw.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
                    10, 0.3, 0.0, 0.3, 0.02);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_SAND_PLACE, SoundCategory.PLAYERS, 0.5f, 1.2f);
            enterCooldown(player);
            clear(player);
            return;
        }
        player.setVelocity(0, -stepY, 0);
        player.velocityModified = true;
        player.setPosition(player.getX(), curY - stepY, player.getZ());
        // 下落时脚下灰烟
        sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() - 0.1, player.getZ(),
                2, 0.2, 0.0, 0.2, 0.005);
    }

    /** 冲刺落地：造成伤害 + 方块视觉浮动 + 进 CD。 */
    private static void enterLand(ServerPlayerEntity player, DashState s, boolean withDamage) {
        ServerWorld sw = (ServerWorld) player.getWorld();
        Vec3d land = s.dashTarget != null ? s.dashTarget : player.getPos();
        player.setPosition(land.x, land.y, land.z);
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;

        // 落地音效（全员可听）
        sw.playSound(null, land.x, land.y, land.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        sw.playSound(null, land.x, land.y, land.z,
                SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.PLAYERS, 0.8f, 0.8f);

        if (withDamage) {
            // AOE 伤害（3 格半径，默认白名单）
            Box box = new Box(land.subtract(LANDING_RADIUS, LANDING_RADIUS, LANDING_RADIUS),
                    land.add(LANDING_RADIUS, LANDING_RADIUS, LANDING_RADIUS));
            for (Entity e : sw.getOtherEntities(player, box)) {
                if (!(e instanceof LivingEntity living)) continue;
                if (WhitelistUtils.isProtected(player, living)) continue; // 默认白名单
                living.damage(player.getDamageSources().playerAttack(player), LANDING_DAMAGE);
                Vec3d push = living.getPos().subtract(land);
                if (push.lengthSquared() < 1.0e-4) push = new Vec3d(0, 1, 0);
                push = push.normalize();
                living.takeKnockback(0.6, -push.x, -push.z);
            }
            // 落地爆发粒子（用快消/瞬消粒子，避免白雾长时间残留）
            sw.spawnParticles(ParticleTypes.POOF, land.x, land.y + 0.2, land.z,
                    25, LANDING_RADIUS * 0.5, 0.1, LANDING_RADIUS * 0.5, 0.08);
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, land.x, land.y + 0.5, land.z,
                    10, LANDING_RADIUS * 0.4, 0.3, LANDING_RADIUS * 0.4, 0.0);
            sw.spawnParticles(ParticleTypes.EXPLOSION, land.x, land.y + 0.3, land.z,
                    2, LANDING_RADIUS * 0.3, 0.1, LANDING_RADIUS * 0.3, 0.0);
            // 方块视觉浮动（粒子模拟）：落点半径内方块按远近冒破坏粒子
            spawnBlockFloatParticles(sw, land, LANDING_RADIUS);
        }

        enterCooldown(player);
        clear(player);
    }

    /** 进入 CD（12 秒）。超时落下也进 CD。 */
    private static void enterCooldown(ServerPlayerEntity player) {
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, COOLDOWN_TICKS);
    }

    /** 起飞阶段脚下粒子：灰烟 + 烟花上升。 */
    private static void spawnRiseParticles(ServerWorld sw, ServerPlayerEntity player) {
        // 灰烟（脚下）
        sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() - 0.1, player.getZ(),
                4, 0.3, 0.0, 0.3, 0.01);
        // 烟花同款上升粒子（FIREWORK，带向上速度）
        sw.spawnParticles(ParticleTypes.FIREWORK,
                player.getX(), player.getY() - 0.2, player.getZ(),
                3, 0.25, 0.0, 0.25, 0.05);
    }

    /**
     * 方块视觉浮动（粒子模拟）：落地半径 3 格内方块，按距落点远近冒方块破坏粒子，
     * 近处密集、远处稀疏，呈现"被冲击波震起"的视觉。不动真实方块，避免兼容问题。
     */
    private static void spawnBlockFloatParticles(ServerWorld sw, Vec3d land, double radius) {
        BlockPos center = BlockPos.ofFloored(land.x, land.y, land.z);
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                // 找该列地表方块（从落点高度向下找第一个实心）
                for (int dy = 0; dy >= -2; dy--) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState st = sw.getBlockState(pos);
                    if (st.isAir()) continue;
                    // 近处粒子多、远处少
                    float intensity = (float) (1.0 - dist / radius);
                    int count = Math.max(1, (int) (4 * intensity));
                    sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(
                            net.minecraft.particle.ParticleTypes.BLOCK, st),
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                            count, 0.3, 0.05, 0.3, 0.1);
                    break;
                }
            }
        }
    }

    // ===== 工具方法 =====

    /** 垂直向上射线，返回从脚部到上方第一个实心方块底面的距离（无阻挡返回 maxCheck）。 */
    private static double checkVerticalClearance(ServerPlayerEntity player, double maxCheck) {
        ServerWorld sw = (ServerWorld) player.getWorld();
        Vec3d feet = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d top = feet.add(0, maxCheck, 0);
        BlockHitResult bhr = sw.raycast(new RaycastContext(feet, top,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (bhr.getType() == HitResult.Type.MISS) return maxCheck;
        return bhr.getPos().y - feet.y;
    }

    /**
     * 计算冲刺落点：用完整 3D 准星射线（含俯仰角），命中方块则取该处地面（水平距离≤MAX_DASH_RANGE）；
     * 打空（看天空）才退到水平方向最远 MAX_DASH_RANGE 处的地面。这样玩家瞄近处地面=近落点，瞄远处=远落点。
     */
    private static Vec3d computeDashLanding(ServerWorld world, Vec3d eye, Vec3d look, ServerPlayerEntity player) {
        Vec3d flatLook = new Vec3d(look.x, 0, look.z);
        double flatLen = flatLook.length();
        // 3D 射线长度上限：保证水平投影不超过 MAX_DASH_RANGE
        double max3D = flatLen > 1.0e-4 ? MAX_DASH_RANGE / flatLen : MAX_DASH_RANGE;
        Vec3d end3D = eye.add(look.multiply(max3D));
        BlockHitResult bhr = world.raycast(new RaycastContext(eye, end3D,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (bhr.getType() != HitResult.Type.MISS) {
            Vec3d hit = bhr.getPos();
            double groundY = findGroundY(world, hit.x, hit.z, Math.min(hit.y, player.getY()));
            return new Vec3d(hit.x, groundY, hit.z);
        }
        // 打空：退到水平方向最远 MAX_DASH_RANGE 处的地面
        Vec3d flatDir;
        if (flatLen > 1.0e-4) {
            flatDir = flatLook.normalize();
        } else {
            float yaw = player.getYaw();
            flatDir = new Vec3d(-MathHelper.sin(yaw * 0.017453292f), 0, MathHelper.cos(yaw * 0.017453292f));
        }
        double hx = player.getX() + flatDir.x * MAX_DASH_RANGE;
        double hz = player.getZ() + flatDir.z * MAX_DASH_RANGE;
        double gy = findGroundY(world, hx, hz, player.getY());
        return new Vec3d(hx, gy, hz);
    }

    /** 从空中水平点 (x,z) 向下找地面站立面 Y。fromY 为起始高度。 */
    private static double findGroundY(ServerWorld world, double x, double z, double fromY) {
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        int startY = (int) Math.floor(fromY) + 1;
        for (int y = startY; y >= startY - 20 && y >= world.getBottomY(); y--) {
            mpos.set(x, y, z);
            BlockState st = world.getBlockState(mpos);
            if (!st.isAir() && !st.getCollisionShape(world, mpos).isEmpty()) {
                return y + 1; // 方块顶面
            }
        }
        return fromY; // 找不到地面，维持原高度
    }

    /** 直线路径是否畅通（无实心方块阻挡）。 */
    private static boolean isClearPath(ServerWorld world, Vec3d from, Vec3d to) {
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.5));
        Vec3d step = to.subtract(from).multiply(1.0 / steps);
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        for (int i = 1; i < steps; i++) {
            Vec3d p = from.add(step.multiply(i));
            mpos.set(p.x, p.y, p.z);
            BlockState st = world.getBlockState(mpos);
            if (!st.isAir() && !st.getCollisionShape(world, mpos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 彻底清理状态并通知客户端。 */
    public static void clear(ServerPlayerEntity player) {
        if (STATES.remove(player.getUuid()) != null) {
            SscAddonNetworking.syncDashState(player, PHASE_NONE, 0);
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        STATES.remove(player.getUuid());
    }
}
