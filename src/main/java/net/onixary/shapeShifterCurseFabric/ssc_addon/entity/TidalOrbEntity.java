package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 荧光幼灵（Axolotl Fluorescent）主要技能「潮汐波动」粒子球实体。
 *
 * <p>状态机：
 * <ul>
 *   <li>FLYING：4 格/s 向前飞行，每 tick 缓慢偏向主人准星方向（陶氏导弹式，最大 ~3°/tick）；最多飞 8 秒。</li>
 *   <li>DECELERATING：飞行中再次按键 / 飞满 8 秒触发，0.75 秒内速度线性衰减至 0（保持当前方向，不再转向）。</li>
 *   <li>ATTRACTING（潮汐束缚 / 拴人）：落点瞬间捕获半径 6 格内所有非白名单目标；持续 8.5 秒把它们「拴」在落点，
 *       6 格内自由活动，超出 6 格按超出距离线性拉回锚点，绝不允许超过 8 格。纯控制、无伤害。</li>
 *   <li>DELAY：拴人时长结束，延迟 0.35 秒后泡泡破裂消失。</li>
 * </ul>
 * 不消耗潮湿度；CD 在球完全消失后由技能管理器起算；可被净化（PURIFIED）直接消除。
 *
 * <p>渲染：实现 {@link net.minecraft.entity.FlyingItemEntity} 以潮涌（Conduit）方块作为可见核心
 * （由 FlyingItemEntityRenderer 渲染，对齐 red 火球标准），外层附以富的水/荧光拖尾粒子。
 */
public class TidalOrbEntity extends Entity implements net.minecraft.entity.FlyingItemEntity {

    // ===== 飞行参数 =====
    private static final double FLY_SPEED = 0.2;            // 4 格/s = 0.2 格/tick
    private static final int MAX_FLY_TICKS = 160;           // 8 秒后自动进入吸附
    private static final double MAX_TURN_RAD = Math.toRadians(3.0); // 每 tick 最多转向 3°（幅度小）

    // ===== 减速参数 =====
    private static final int DECEL_TICKS = 15;              // 0.75 秒减速到 0

    // ===== 拴人参数（潮汐束缚）=====
    private static final double TETHER_CATCH_RADIUS = 6.0;    // 落点瞬间：捕获半径 6 格内所有目标
    private static final double TETHER_SOFT_RADIUS = 6.0;     // 软边界：6 格内自由活动
    private static final double TETHER_HARD_RADIUS = 8.0;     // 硬边界：绝不允许超过 8 格
    private static final int TETHER_DURATION_TICKS = 170;     // 拴住时长 8.5 秒
    private static final double TETHER_PULL_PER_BLOCK = 0.30; // 超出 6 格后：每超 1 格给 0.30 速度线性拉回
    private static final double TETHER_VERTICAL_DAMP = 0.4;   // 垂直拉力衰减，避免上下猛拽

    // ===== 消失延迟 =====
    private static final int POP_DELAY_TICKS = 7;           // 0.35 秒后破裂

    private enum Phase { FLYING, DECELERATING, ATTRACTING, DELAY }

    /** 客户端同步：是否处于拴人（激活）状态，用于把潮涌核心切换为激活态渲染。 */
    private static final net.minecraft.entity.data.TrackedData<Boolean> TETHER_ACTIVE =
            net.minecraft.entity.data.DataTracker.registerData(TidalOrbEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);

    private Phase phase = Phase.FLYING;
    private int ticksAlive = 0;
    private int phaseTicks = 0;
    private UUID ownerUuid;
    // 阿澪(axolotl_aling)差异化：飞行/拴人时长 +20%，拴人期间造成伤害
    private boolean isAling = false;
    private int maxFlyTicks = MAX_FLY_TICKS;
    private int tetherDuration = TETHER_DURATION_TICKS;
    private Vec3d flyDir = new Vec3d(0, 0, 1);   // FLYING / DECEL 用
    private double currentSpeed = FLY_SPEED;     // DECEL 时衰减
    private Vec3d tetherCenter = null;           // 落点锚点（拴人中心）
    private final Set<UUID> tetheredTargets = new HashSet<>(); // 落点瞬间捕获、被拴住的目标 UUID

    // ===== 粒子配色（青/蓝/淡蓝/白：水 + 荧光） =====
    private static final DustParticleEffect CYAN_DUST =
            new DustParticleEffect(new Vector3f(0.20f, 0.78f, 0.92f), 1.5f);
    private static final DustParticleEffect BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.25f, 0.45f, 0.95f), 1.5f);
    private static final DustParticleEffect LIGHT_BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.55f, 0.80f, 1.0f), 1.4f);

    public TidalOrbEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public TidalOrbEntity(World world, ServerPlayerEntity owner) {
        super(SscAddon.TIDAL_ORB_ENTITY, world);
        this.ownerUuid = owner.getUuid();
        // 从玩家眼部前方生成，方向取准星
        Vec3d look = owner.getRotationVec(1.0f);
        this.flyDir = look.normalize();
        this.setPosition(owner.getX() + look.x * 0.8, owner.getEyeY() - 0.1 + look.y * 0.8, owner.getZ() + look.z * 0.8);
        this.currentSpeed = FLY_SPEED;
        this.noClip = true;
        // 阿澪：飞行/拴人时长 +20%（拴人伤害在 tickAttracting 结算）
        this.isAling = FormUtils.isForm(owner, FormIdentifiers.AXOLOTL_ALING);
        if (this.isAling) {
            this.maxFlyTicks = (int) Math.round(MAX_FLY_TICKS * 1.2);            // 160 -> 192
            this.tetherDuration = (int) Math.round(TETHER_DURATION_TICKS * 1.2); // 170 -> 204
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(TETHER_ACTIVE, false);
    }

    /** 飞行阶段：触发减速（玩家再次按键 / 飞满 8 秒自动）。 */
    public void triggerDecelerate() {
        if (phase == Phase.FLYING) {
            phase = Phase.DECELERATING;
            phaseTicks = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        phaseTicks++;

        if (this.getWorld().isClient) {
            // 客户端：在实体（渲染核心）位置生成拖尾粒子，紧跟核心无网络延迟
            spawnTrailParticlesClient();
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // 净化消除：主人受到 PURIFIED 时球立即破裂
        if (isOwnerPurified(sw)) {
            pop(sw);
            return;
        }

        switch (phase) {
            case FLYING -> tickFlying(sw);
            case DECELERATING -> tickDecelerating(sw);
            case ATTRACTING -> tickAttracting(sw);
            case DELAY -> tickDelay(sw);
        }

        // 全局保底：超过 30 秒强制消失，防卡死
        if (ticksAlive > 600) {
            pop(sw);
        }
    }

    // ==================== FLYING ====================
    private void tickFlying(ServerWorld sw) {
        ServerPlayerEntity owner = getOwner(sw);
        // 向主人当前准星方向缓慢偏转（陶氏式追踪）
        if (owner != null) {
            Vec3d look = owner.getRotationVec(1.0f);
            if (look.lengthSquared() > 1.0e-6) {
                flyDir = turnToward(flyDir, look.normalize(), MAX_TURN_RAD);
            }
        }
        // 移动（撞墙则停下并进入吸附，不再穿墙）
        if (moveWithWallCheck(sw, flyDir.x * currentSpeed, flyDir.y * currentSpeed, flyDir.z * currentSpeed)) {
            enterAttractPhase(sw);
            return;
        }
        // 飞行环境音（每 10 tick 一次轻水波）
        if (ticksAlive % 10 == 0) {
            sw.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENTITY_AXOLOTL_SWIM, SoundCategory.PLAYERS, 0.6f, 1.1f);
        }

        if (ticksAlive >= maxFlyTicks) {
            triggerDecelerate();   // 飞满时长自动进入减速（阿澪 +20%）
        }
    }

    // ==================== DECELERATING ====================
    private void tickDecelerating(ServerWorld sw) {
        // 0.75 秒线性减速到 0，保持当前方向不转向
        double t = (double) phaseTicks / DECEL_TICKS;
        currentSpeed = FLY_SPEED * (1.0 - MathHelper.clamp(t, 0.0, 1.0));
        if (moveWithWallCheck(sw, flyDir.x * currentSpeed, flyDir.y * currentSpeed, flyDir.z * currentSpeed)) {
            enterAttractPhase(sw);
            return;
        }
        if (phaseTicks >= DECEL_TICKS) {
            enterAttractPhase(sw);
        }
    }

    /** 撞墙检测移动：撞墙返回 true 并停在墙前（回退 0.25 格防嵌入）。 */
    private boolean moveWithWallCheck(ServerWorld sw, double dx, double dy, double dz) {
        Vec3d from = getPos();
        Vec3d to = from.add(dx, dy, dz);
        net.minecraft.util.hit.BlockHitResult hit = sw.raycast(new net.minecraft.world.RaycastContext(
                from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            Vec3d hp = hit.getPos();
            Vec3d back = from.subtract(to);
            if (back.lengthSquared() > 1.0e-6) back = back.normalize().multiply(0.25);
            else back = Vec3d.ZERO;
            setPosition(hp.x + back.x, hp.y + back.y, hp.z + back.z);
            return true;
        }
        setPosition(to.x, to.y, to.z);
        return false;
    }

    /** 落点：进入「拴人」阶段（减速停下 / 撞墙 / 飞满时长）。记录锚点并捕获 6 格内目标。 */
    private void enterAttractPhase(ServerWorld sw) {
        currentSpeed = 0.0;
        phase = Phase.ATTRACTING;
        this.dataTracker.set(TETHER_ACTIVE, true); // 潮涌核心切激活态
        phaseTicks = 0;
        tetherCenter = getPos();
        // 落点瞬间捕获半径 6 格内的所有合法目标（之后再进入范围的不算）
        tetheredTargets.clear();
        ServerPlayerEntity owner = getOwner(sw);
        double cx = tetherCenter.x, cy = tetherCenter.y, cz = tetherCenter.z;
        Box box = new Box(cx - TETHER_CATCH_RADIUS, cy - TETHER_CATCH_RADIUS, cz - TETHER_CATCH_RADIUS,
                cx + TETHER_CATCH_RADIUS, cy + TETHER_CATCH_RADIUS, cz + TETHER_CATCH_RADIUS);
        List<LivingEntity> found = sw.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(cx, cy, cz) <= TETHER_CATCH_RADIUS * TETHER_CATCH_RADIUS);
        for (LivingEntity t : found) {
            if (WhitelistUtils.isProtected(ownerUuid, sw, t)) continue; // 默认白名单豁免（owner 离线也保守保护玩家/宠物）
            if (t.getUuid().equals(ownerUuid)) continue;                          // 主人自己不被拴
            tetheredTargets.add(t.getUuid());
        }
        // 落点「潮汐降临」：一次清晰潮涌音（球位置 + 主人位置，不重叠）
        sw.playSound(null, cx, cy, cz,
                SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.1f);
        if (owner != null) {
            sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.1f);
        }
        // 落点水柱爆开 + 6 格束缚环
        sw.spawnParticles(ParticleTypes.SPLASH, cx, cy, cz, 50, 0.8, 0.5, 0.8, 0.4);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, cx, cy - 0.3, cz, 30, 0.6, 0.2, 0.6, 0.3);
        spawnTetherRing(sw, 40);
    }

    // ==================== ATTRACTING（潮汐束缚 / 拴人）====================
    private void tickAttracting(ServerWorld sw) {
        // 锚点悬停粒子 + 6 格束缚环
        spawnHoverParticles(sw);
        applyTether(sw);
        // 阿澪：拴人期间每 2 秒(40t)对被拴目标造成 2 点物理伤害
        if (isAling && phaseTicks > 0 && phaseTicks % 40 == 0) {
            tickTetherDamage(sw);
        }
        // 每 10 tick 把被拴目标同步给客机，用于渲染守卫者激光
        if (phaseTicks % 10 == 1) {
            syncTetherToClients(sw);
        }
        if (phaseTicks >= tetherDuration) {
            phase = Phase.DELAY;
            phaseTicks = 0;
        }
    }

    /** 阿澪专属：对被拴目标造成物理伤害（走默认白名单豁免，服务端判定）。 */
    private void tickTetherDamage(ServerWorld sw) {
        if (tetheredTargets.isEmpty()) return;
        ServerPlayerEntity owner = getOwner(sw);
        for (UUID id : tetheredTargets) {
            Entity e = sw.getEntity(id);
            if (!(e instanceof LivingEntity t) || !t.isAlive() || t.isSpectator()) continue;
            if (WhitelistUtils.isProtected(ownerUuid, sw, t)) continue;
            if (owner != null) {
                t.damage(t.getDamageSources().playerAttack(owner), 2.0f);
            } else {
                t.damage(t.getDamageSources().magic(), 2.0f);
            }
        }
    }

    /**
     * 拴人：对落点捕获的每个目标，6 格内自由；超出 6 格按「超出距离」线性拉回锚点，
     * 绝不允许超过 8 格（硬封）。纯控制、无伤害。全服务端判定。
     */
    private void applyTether(ServerWorld sw) {
        if (tetherCenter == null || tetheredTargets.isEmpty()) return;
        double cx = tetherCenter.x, cy = tetherCenter.y, cz = tetherCenter.z;
        java.util.Iterator<UUID> it = tetheredTargets.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = sw.getEntity(id);
            if (!(e instanceof LivingEntity t) || !t.isAlive() || t.isSpectator()) {
                it.remove(); // 死亡 / 离线 / 无效 → 移除，不再处理
                continue;
            }
            if (WhitelistUtils.isProtected(ownerUuid, sw, t)) continue; // 白名单豁免（owner 离线也保守保护玩家/宠物）
            double tx = t.getX(), ty = t.getY() + t.getHeight() * 0.5, tz = t.getZ();
            double dx = tx - cx, dy = ty - cy, dz = tz - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= TETHER_SOFT_RADIUS || dist < 1.0e-4) continue; // 6 格内自由活动
            double inv = 1.0 / dist;
            double over = dist - TETHER_SOFT_RADIUS;
            double mag = TETHER_PULL_PER_BLOCK * over;                 // 线性拉力：超得越多、拉得越强
            t.setVelocity(t.getVelocity().add(
                    -dx * inv * mag,
                    -dy * inv * mag * TETHER_VERTICAL_DAMP,
                    -dz * inv * mag));
            t.velocityModified = true;
            // 硬封：一旦超过 8 格，直接拉回到 8 格球面并抵消向外冲量
            if (dist > TETHER_HARD_RADIUS) {
                double k = TETHER_HARD_RADIUS * inv;
                double nx = cx + dx * k;
                double ny = cy + dy * k - t.getHeight() * 0.5;
                double nz = cz + dz * k;
                t.requestTeleport(nx, ny, nz);
                t.setVelocity(t.getVelocity().multiply(0.15, 0.6, 0.15));
                t.velocityModified = true;
            }
            spawnAffectedFootParticles(sw, t); // 被拴目标脚底提示
        }
    }

    /** 把当前被拴目标的 entityId 打包发给所有正在追踪本球的客户端（守卫者激光渲染用）。 */
    private void syncTetherToClients(ServerWorld sw) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (UUID id : tetheredTargets) {
            Entity e = sw.getEntity(id);
            if (e != null && e.isAlive()) ids.add(e.getId());
        }
        var tracking = net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this);
        if (tracking.isEmpty()) return;
        for (ServerPlayerEntity p : tracking) {
            net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeVarInt(this.getId());
            buf.writeVarInt(ids.size());
            for (int i : ids) buf.writeVarInt(i);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    p, net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_TIDAL_TETHER, buf);
        }
    }

    // ==================== DELAY ====================
    private void tickDelay(ServerWorld sw) {
        spawnHoverParticles(sw);
        if (phaseTicks >= POP_DELAY_TICKS) {
            pop(sw);
        }
    }

    // ==================== 破裂消失 ====================
    private void pop(ServerWorld sw) {
        double x = getX(), y = getY(), z = getZ();
        // 泡泡破裂：大量水/气泡/淡蓝 dust 向外迸射
        for (int i = 0; i < 80; i++) {
            Vec3d p = randomInSphere(1.8, random);
            sw.spawnParticles(ParticleTypes.BUBBLE, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.03);
        }
        for (int i = 0; i < 50; i++) {
            Vec3d p = randomInSphere(1.4, random);
            sw.spawnParticles(ParticleTypes.SPLASH, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.06);
        }
        sw.spawnParticles(CYAN_DUST, x, y, z, 60, 1.2, 1.2, 1.2, 0.0);
        sw.spawnParticles(LIGHT_BLUE_DUST, x, y, z, 60, 1.2, 1.2, 1.2, 0.0);
        sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.5, 0.5, 0.5, 0.15);
        sw.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 3, 0.3, 0.3, 0.3, 0.0);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y - 0.3, z, 40, 0.8, 0.3, 0.8, 0.4);
        // 破裂：单一清晰水灵音（球位置 + 主人位置，不重叠）
        sw.playSound(null, x, y, z, SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.3f, 0.9f);
        ServerPlayerEntity owner = getOwner(sw);
        if (owner != null) {
            sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 0.6f, 0.9f);
        }
        // 通知技能管理器：球已消失，可开始 CD
        notifyManagerBallEnded();
        this.discard();
    }

    // ==================== 粒子 ====================
    private void spawnTrailParticlesClient() {
        double x = getX(), y = getY(), z = getZ();
        World w = this.getWorld();
        // 1. 核心发光（END_ROD + GLOW）
        w.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        w.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        if (ticksAlive % 2 == 0) w.addParticle(ParticleTypes.GLOW, x, y, z, 0, 0, 0);
        // 2. 旋转水环（6 点环绕，随时间旋转）
        double rot = ticksAlive * 0.35;
        for (int i = 0; i < 6; i++) {
            double a = rot + i * (Math.PI * 2 / 6);
            double r = 0.5;
            double px = x + Math.cos(a) * r;
            double pz = z + Math.sin(a) * r;
            double py = y + Math.sin(ticksAlive * 0.25 + i) * 0.12;
            w.addParticle((i % 2 == 0) ? CYAN_DUST : LIGHT_BLUE_DUST, px, py, pz, 0, 0, 0);
        }
        // 3. 球体水粒子（气泡 + 水花）
        for (int i = 0; i < 5; i++) {
            Vec3d p = randomInSphere(0.5, random);
            w.addParticle(ParticleTypes.BUBBLE, x + p.x, y + p.y, z + p.z, 0, 0.01, 0);
        }
        if (random.nextFloat() < 0.5f) {
            Vec3d p = randomInSphere(0.45, random);
            w.addParticle(ParticleTypes.SPLASH, x + p.x, y + p.y, z + p.z, 0, 0.02, 0);
        }
        // 4. 蓝色 dust 包围
        for (int i = 0; i < 3; i++) {
            Vec3d p = randomInSphere(0.35, random);
            w.addParticle(LIGHT_BLUE_DUST, x + p.x, y + p.y, z + p.z, 0, 0, 0);
        }
        // 5. 偶发鹦鹉螺
        if (random.nextFloat() < 0.35f) {
            w.addParticle(ParticleTypes.NAUTILUS, x, y, z, 0, 0, 0);
        }
    }

    private void spawnHoverParticles(ServerWorld sw) {
        double x = getX(), y = getY(), z = getZ();
        // 三个小粒子球绕锚点旋转（120° 均布，各用一种水系配色）
        double rot = ticksAlive * 0.12;
        double orbitR = 1.1;
        for (int k = 0; k < 3; k++) {
            double a = rot + k * (Math.PI * 2 / 3);
            double ox = x + Math.cos(a) * orbitR;
            double oz = z + Math.sin(a) * orbitR;
            double oy = y + Math.sin(ticksAlive * 0.15 + k * 2.0) * 0.25; // 上下轻微起伏
            DustParticleEffect col = (k == 0) ? CYAN_DUST : (k == 1) ? BLUE_DUST : LIGHT_BLUE_DUST;
            for (int i = 0; i < 4; i++) {
                Vec3d p = randomInSphere(0.22, random);
                sw.spawnParticles(col, ox + p.x, oy + p.y, oz + p.z, 1, 0, 0, 0, 0.0);
            }
            if (random.nextFloat() < 0.4f) {
                sw.spawnParticles(ParticleTypes.BUBBLE, ox, oy, oz, 1, 0.04, 0.04, 0.04, 0.0);
            }
        }
        // 核心发光
        sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.08, 0.1, 0.08, 0.0);
        // 中间生成、随重力慢慢下落的水滴
        if (ticksAlive % 2 == 0) {
            double dx = (random.nextDouble() - 0.5) * 0.5;
            double dz = (random.nextDouble() - 0.5) * 0.5;
            sw.spawnParticles(ParticleTypes.FALLING_WATER, x + dx, y + 0.15, z + dz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 6 格范围提示：公转粒子球（与中央三球同一套视觉语言）
        spawnBoundaryOrbs(sw);
    }

    /** 在 6 格软边界处描一圈 dust（落点瞬间一次性闪现，展示拴人范围）。 */
    private void spawnTetherRing(ServerWorld sw, int count) {
        double x = getX(), y = getY(), z = getZ();
        for (int i = 0; i < count; i++) {
            double ang = i * (Math.PI * 2 / count);
            sw.spawnParticles(BLUE_DUST,
                    x + Math.cos(ang) * TETHER_SOFT_RADIUS, y + 0.1, z + Math.sin(ang) * TETHER_SOFT_RADIUS,
                    1, 0, 0, 0, 0.0);
        }
    }

    /** 6 格范围持续提示：几个小粒子球绕 6 格边界公转（与中央三球同一视觉语言）。 */
    private void spawnBoundaryOrbs(ServerWorld sw) {
        double x = getX(), y = getY(), z = getZ();
        double rot = ticksAlive * 0.06;   // 慢速公转
        int n = 6;                          // 6 个球均布在 6 格边界
        for (int k = 0; k < n; k++) {
            double a = rot + k * (Math.PI * 2 / n);
            double ox = x + Math.cos(a) * TETHER_SOFT_RADIUS;
            double oz = z + Math.sin(a) * TETHER_SOFT_RADIUS;
            double oy = y + Math.sin(ticksAlive * 0.12 + k) * 0.3; // 上下起伏
            DustParticleEffect col = (k % 3 == 0) ? CYAN_DUST : (k % 3 == 1) ? BLUE_DUST : LIGHT_BLUE_DUST;
            for (int i = 0; i < 3; i++) {
                Vec3d p = randomInSphere(0.2, random);
                sw.spawnParticles(col, ox + p.x, oy + p.y, oz + p.z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    private void spawnAffectedFootParticles(ServerWorld sw, LivingEntity t) {
        double fx = t.getX(), fy = t.getY() + 0.05, fz = t.getZ();
        // 脚底青色 + 蓝色粒子（提示被吸附影响）
        for (int i = 0; i < 6; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * t.getWidth() * 0.6;
            sw.spawnParticles(CYAN_DUST, fx + Math.cos(ang) * r, fy, fz + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.0);
        }
        for (int i = 0; i < 4; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * t.getWidth() * 0.6;
            sw.spawnParticles(BLUE_DUST, fx + Math.cos(ang) * r, fy, fz + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.0);
        }
    }

    // ==================== 工具 ====================
    /** 将 from 方向朝 to 方向转最多 maxRad 弧度，返回新单位向量。 */
    private static Vec3d turnToward(Vec3d from, Vec3d to, double maxRad) {
        Vec3d f = from.normalize();
        Vec3d tt = to.normalize();
        double dot = MathHelper.clamp(f.dotProduct(tt), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 1.0e-4) return f;
        double turn = Math.min(angle, maxRad);
        double t = turn / angle;
        // 球面线性插值（slerp）
        Vec3d cross = f.crossProduct(tt);
        if (cross.lengthSquared() < 1.0e-8) return tt; // 共线
        // 用四元数思路简化：f * sin((1-t)a)/sin(a) + to * sin(ta)/sin(a)
        double sa = Math.sin(angle);
        double w1 = Math.sin((1 - t) * angle) / sa;
        double w2 = Math.sin(t * angle) / sa;
        return new Vec3d(f.x * w1 + tt.x * w2, f.y * w1 + tt.y * w2, f.z * w1 + tt.z * w2).normalize();
    }

    private Vec3d randomInSphere(double r, net.minecraft.util.math.random.Random rnd) {
        double rr = r * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * 2 * Math.PI;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vec3d(rr * sinPhi * Math.cos(theta), rr * Math.cos(phi), rr * sinPhi * Math.sin(theta));
    }

    private ServerPlayerEntity getOwner(ServerWorld sw) {
        if (ownerUuid == null) return null;
        var server = sw.getServer();
        return server == null ? null : server.getPlayerManager().getPlayer(ownerUuid);
    }

    private boolean isOwnerPurified(ServerWorld sw) {
        ServerPlayerEntity owner = getOwner(sw);
        return owner != null && owner.hasStatusEffect(SscAddon.PURIFIED);
    }

    /** 球消失时回调技能管理器，让其开始 CD。 */
    private void notifyManagerBallEnded() {
        if (ownerUuid != null) {
            net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentTidalManager.onBallRemoved(ownerUuid);
        }
    }

    // ==================== NBT / 同步 ====================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Phase", phase.ordinal());
        nbt.putInt("TicksAlive", ticksAlive);
        nbt.putInt("PhaseTicks", phaseTicks);
        nbt.putDouble("DirX", flyDir.x);
        nbt.putDouble("DirY", flyDir.y);
        nbt.putDouble("DirZ", flyDir.z);
        nbt.putDouble("Speed", currentSpeed);
        if (tetherCenter != null) {
            nbt.putDouble("TetherX", tetherCenter.x);
            nbt.putDouble("TetherY", tetherCenter.y);
            nbt.putDouble("TetherZ", tetherCenter.z);
        }
        NbtList tl = new NbtList();
        for (UUID id : tetheredTargets) tl.add(net.minecraft.nbt.NbtHelper.fromUuid(id));
        nbt.put("Tethered", tl);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        int p = nbt.contains("Phase") ? nbt.getInt("Phase") : 0;
        phase = Phase.values()[MathHelper.clamp(p, 0, Phase.values().length - 1)];
        ticksAlive = nbt.contains("TicksAlive") ? nbt.getInt("TicksAlive") : 0;
        phaseTicks = nbt.contains("PhaseTicks") ? nbt.getInt("PhaseTicks") : 0;
        double dx = nbt.contains("DirX") ? nbt.getDouble("DirX") : 0;
        double dy = nbt.contains("DirY") ? nbt.getDouble("DirY") : 0;
        double dz = nbt.contains("DirZ") ? nbt.getDouble("DirZ") : 1;
        flyDir = new Vec3d(dx, dy, dz).normalize();
        currentSpeed = nbt.contains("Speed") ? nbt.getDouble("Speed") : FLY_SPEED;
        if (nbt.contains("TetherX")) {
            tetherCenter = new Vec3d(nbt.getDouble("TetherX"), nbt.getDouble("TetherY"), nbt.getDouble("TetherZ"));
        }
        tetheredTargets.clear();
        if (nbt.contains("Tethered")) {
            NbtList tl = nbt.getList("Tethered", NbtElement.INT_ARRAY_TYPE);
            for (int i = 0; i < tl.size(); i++) {
                tetheredTargets.add(net.minecraft.nbt.NbtHelper.toUuid(tl.get(i)));
            }
        }
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }

    /** 渲染为潮涌（Conduit）方块作为发光核心。 */
    @Override
    public net.minecraft.item.ItemStack getStack() {
        return new net.minecraft.item.ItemStack(net.minecraft.item.Items.CONDUIT);
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    /** 主人是否仍持有该球（供管理器查询）。 */
    public boolean isAliveOrActive() {
        return !this.isRemoved();
    }

    /** 客户端查询：是否处于拴人激活状态（潮涌核心切激活态渲染）。 */
    public boolean isTetherActive() {
        return this.dataTracker.get(TETHER_ACTIVE);
    }
}