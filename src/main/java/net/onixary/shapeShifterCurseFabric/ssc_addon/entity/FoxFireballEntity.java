package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.List;

/**
 * red（红堕落使魔）次要技能 - 狐火火球。
 * 直线飞行（无抛物线、不穿墙）：0~12 格速度 20→2 b/s 线性衰减（约 1.5 秒，期间穿过生物造成 8 魔法穿透 + 额外爆破）；
 * 12 格后固定 2 格/s 飞行至多 3 秒（达 18 格上限）；撞墙立即爆炸，12 格后碍生物也爆炸，未命中则泡泡裂开消散。
 * 爆炸：6 格球范围 6 物理（不穿墙） + 仅直接碰到火球者腰部火环向 2 格内连锁 4 物理（不再二次连锁）。
 */
public class FoxFireballEntity extends ProjectileEntity implements net.minecraft.entity.FlyingItemEntity {

    private static final double ARM_DISTANCE = 12.0;   // 12 格后才进入杀伤（碰墙/生物爆炸）
    private static final double HIT_RADIUS = 2.0;      // 命中/穿透判定球半径
    private static final double EXPLODE_RADIUS = 6.0;  // 爆炸球半径
    private static final double CHAIN_RADIUS = 2.0;    // 连锁半径
    private static final float PIERCE_DAMAGE = 8.0f;   // 前 12 格穿透（魔法）
    private static final float EXPLODE_DAMAGE = 6.0f;  // 爆炸（物理）
    private static final float CHAIN_DAMAGE = 4.0f;    // 连锁（物理）
    private static final int RING_DURATION = 7;        // 腰部火环扩散动画帧数（半径 0→2）
    private static final double PHASE2_SPEED = 2.0;    // 12 格后固定 2 格/s
    private static final int PHASE2_DURATION = 60;     // 12 格后最多飞 3 秒（60 tick → 18 格上限）

    private Vec3d direction = new Vec3d(0, 0, 1);
    private double distanceTraveled = 0;
    private int ticksAlive = 0;
    private int phase2Tick = 0;                  // 12 格后已飞 tick 数
    private boolean exploded = false;
    private boolean exploding = false;           // 爆炸后停止移动、等待所有火环播完即消失
    private final java.util.List<float[]> activeRings = new java.util.ArrayList<>();   // 活跃火环 [cx,cy,cz,已播放tick]
    private final java.util.Set<java.util.UUID> piercedEntities = new java.util.HashSet<>();

    public FoxFireballEntity(EntityType<? extends FoxFireballEntity> type, World world) {
        super(type, world);
    }

    public FoxFireballEntity(World world, LivingEntity owner) {
        super(SscAddon.FOX_FIREBALL_ENTITY, world);
        this.setOwner(owner);
        this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    public void setDirection(Vec3d dir) {
        this.direction = dir.normalize();
        this.setVelocity(this.direction);   // 用速度把方向编进 spawn 包，供客户端预测移动
    }

    /** 速度曲线（格/tick = b/s ÷ 20）：0~12 格按距离 20→2 线性递减（约 1.5 秒走完），12 格后固定 2 格/s。 */
    private double speedPerTick(double d) {
        double bps;
        if (d < ARM_DISTANCE) {
            bps = 20.0 - (20.0 - 2.0) * (d / ARM_DISTANCE);  // 20 → 2 线性递减（按距离）
        } else {
            bps = PHASE2_SPEED;                              // 12 格后固定 2 格/s
        }
        return bps / 20.0;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        if (this.getWorld().isClient) {
            // 客户端确定性预测移动（与服务端同一速度曲线），渲染插值平滑，避免只靠 tracker 同步的卡顿
            Vec3d v = this.getVelocity();
            Vec3d dir = v.lengthSquared() > 1.0e-6 ? v.normalize() : direction;
            double speed = speedPerTick(distanceTraveled);
            this.setPosition(this.getX() + dir.x * speed, this.getY() + dir.y * speed, this.getZ() + dir.z * speed);
            distanceTraveled += speed;
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // 每帧推进并绘制所有活跃火环（穿透/爆炸触发的额外爆炸动画）
        updateActiveRings(sw);

        if (exploding) {
            // 已爆炸：原地等待所有火环播完即消失
            if (activeRings.isEmpty()) this.discard();
            return;
        }

        boolean armed = distanceTraveled >= ARM_DISTANCE;  // 12 格后才有杀伤
        double speed = speedPerTick(distanceTraveled);
        Vec3d velocity = direction.multiply(speed);
        Vec3d from = this.getPos();
        Vec3d to = from.add(velocity);

        // 墙壁碰撞：火球不穿墙，撞墙立即触发爆破（任何距离）
        BlockHitResult blockHit = sw.raycast(new RaycastContext(
                from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3d hp = blockHit.getPos();
            this.setPosition(hp.x, hp.y, hp.z);
            explode(sw, null);
            return;
        }

        this.setPosition(to.x, to.y, to.z);
        distanceTraveled += speed;

        if (armed) {
            // 12 格后：2 格球命中非白名单生物即爆炸
            LivingEntity target = findTarget(sw);
            if (target != null) {
                explode(sw, target);
                return;
            }
        } else {
            // 前 12 格：穿过生物造成穿透伤害 + 额外爆炸（火球不灭继续飞）
            pierceTargets(sw);
        }
        spawnTrail(sw);

        // 12 格后最多再飞 3 秒（达 18 格），未命中则原地触发一次爆炸后消失
        if (distanceTraveled >= ARM_DISTANCE) {
            phase2Tick++;
            if (phase2Tick >= PHASE2_DURATION) {
                explode(sw, null);   // 未命中飞到射程上限：原地触发一次爆炸（无连锁）后消失
                return;
            }
        }
        if (ticksAlive > 200) this.discard();
    }

    private LivingEntity findTarget(ServerWorld world) {
        Box box = this.getBoundingBox().expand(HIT_RADIUS);
        Vec3d c = this.getPos();
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != this.getOwner() && e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(c.x, c.y, c.z) <= HIT_RADIUS * HIT_RADIUS);
        for (LivingEntity e : list) {
            if (this.getOwner() instanceof ServerPlayerEntity op && WhitelistUtils.isProtected(op, e)) continue;
            return e;
        }
        return null;
    }

    /** 前 12 格穿透：对 2 格球内每个非白名单生物造成一次 8 魔法穿透伤害（去重，火球不灭）。 */
    private void pierceTargets(ServerWorld world) {
        Box box = this.getBoundingBox().expand(HIT_RADIUS);
        Vec3d c = this.getPos();
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != this.getOwner() && e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(c.x, c.y, c.z) <= HIT_RADIUS * HIT_RADIUS
                        && !piercedEntities.contains(e.getUuid()));
        for (LivingEntity e : list) {
            if (this.getOwner() instanceof ServerPlayerEntity op && WhitelistUtils.isProtected(op, e)) continue;
            e.damage(magicSource(e, owner), PIERCE_DAMAGE);
            piercedEntities.add(e.getUuid());
            applyFoxFireBurn(e);
            double ex = e.getX(), ey = e.getY() + e.getHeight() * 0.5, ez = e.getZ();
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, ex, ey, ez, 8, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.FLAME, ex, ey, ez, 6, 0.25, 0.25, 0.25, 0.02);
            world.playSound(null, ex, ey, ez, SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.4f, 1.6f);
            triggerExtraExplosion(world, owner, e);   // 穿透段也触发额外爆炸（腰部火环 + 连锁）
        }
    }

    /** 火球本体：2 格直径（半径 1）火焰球 + 短拖尾（双火焰 + 稀疏烟雾）。 */
    private void spawnTrail(ServerWorld w) {
        double x = this.getX(), y = this.getY(), z = this.getZ();
        Random rnd = this.random;
        for (int i = 0; i < 10; i++) {
            Vec3d p = randomInSphere(1.0, rnd);
            w.spawnParticles(ParticleTypes.FLAME, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.01);
        }
        for (int i = 0; i < 7; i++) {
            Vec3d p = randomInSphere(1.0, rnd);
            w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.01);
        }
        Vec3d back = direction.multiply(-0.5);
        w.spawnParticles(ParticleTypes.FLAME, x + back.x, y + back.y, z + back.z, 2, 0.15, 0.15, 0.15, 0.0);
        w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x + back.x, y + back.y, z + back.z, 1, 0.12, 0.12, 0.12, 0.0);
        w.spawnParticles(ParticleTypes.SMOKE, x + back.x * 1.5, y + back.y * 1.5, z + back.z * 1.5, 1, 0.1, 0.1, 0.1, 0.0);
    }

    private void explode(ServerWorld w, LivingEntity directTarget) {
        if (exploded) return;
        exploded = true;
        double x = this.getX(), y = this.getY(), z = this.getZ();
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;

        // 爆炸粒子 + 音效
        spawnExplosionParticles(w, x, y, z);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.4f);

        // 6 格球范围物理伤害；视线被方块阻挡的目标不受伤（爆炸不穿墙）
        Box box = new Box(x - EXPLODE_RADIUS, y - EXPLODE_RADIUS, z - EXPLODE_RADIUS,
                x + EXPLODE_RADIUS, y + EXPLODE_RADIUS, z + EXPLODE_RADIUS);
        List<LivingEntity> affected = w.getEntitiesByClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(x, y, z) <= EXPLODE_RADIUS * EXPLODE_RADIUS
                        && !(owner instanceof ServerPlayerEntity op && WhitelistUtils.isProtected(op, e))
                        && hasLineOfSight(w, x, y, z, e));
        for (LivingEntity e : affected) {
            // 爆破伤害按距离衰减：≤2 格全额，2~6 格线性衰减至最低 1
            double dist = Math.sqrt(e.squaredDistanceTo(x, y, z));
            float dmg;
            if (dist <= 2.0) {
                dmg = EXPLODE_DAMAGE;
            } else {
                dmg = (float) (EXPLODE_DAMAGE - (EXPLODE_DAMAGE - 1.0) * (dist - 2.0) / (EXPLODE_RADIUS - 2.0));
                if (dmg < 1.0f) dmg = 1.0f;
            }
            e.damage(physicalSource(e, owner), dmg);
            applyFoxFireBurn(e);
        }
        // 仅“直接碰到火球”的生物触发额外爆破（腰部火环 + 2 格连锁）；主爆炸范围内其他生物不触发；碰墙无直接目标则不触发
        if (directTarget != null && directTarget.isAlive()) {
            triggerExtraExplosion(w, owner, directTarget);
        }
        // 爆炸后停止移动，由 tick 等所有火环播完即消失
        exploding = true;
    }

    /** 爆炸视线检测：爆炸中心（略沿来向回退避免贴墙误判）到目标若被方块碰撞箱阻挡则不可达（爆炸不穿墙）。 */
    private boolean hasLineOfSight(ServerWorld w, double x, double y, double z, LivingEntity e) {
        Vec3d from = new Vec3d(x, y, z).subtract(direction.multiply(0.3));
        Vec3d to = new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
        BlockHitResult hit = w.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** 额外爆炸：被影响者腰部火环扩散 + 2 格内连锁伤害（不再二次连锁）；穿透段与爆炸段共用。 */
    private void triggerExtraExplosion(ServerWorld w, LivingEntity owner, LivingEntity center) {
        double cx = center.getX(), cy = center.getY() + center.getHeight() * 0.5, cz = center.getZ();
        activeRings.add(new float[]{(float) cx, (float) cy, (float) cz, 0f});   // 腰部火环，由 tick 逐帧播放
        Box box = new Box(cx - CHAIN_RADIUS, cy - CHAIN_RADIUS, cz - CHAIN_RADIUS,
                cx + CHAIN_RADIUS, cy + CHAIN_RADIUS, cz + CHAIN_RADIUS);
        List<LivingEntity> chained = w.getEntitiesByClass(LivingEntity.class, box,
                e -> e != owner && e != center && e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(cx, cy, cz) <= CHAIN_RADIUS * CHAIN_RADIUS
                        && !(owner instanceof ServerPlayerEntity op && WhitelistUtils.isProtected(op, e)));
        for (LivingEntity e : chained) {
            e.damage(physicalSource(e, owner), CHAIN_DAMAGE);
            applyFoxFireBurn(e);
        }
    }

    /** 火球命中附加狐火灼烧 5 秒（每秒掉血），并打上施法者归属 tag。 */
    private void applyFoxFireBurn(LivingEntity target) {
        target.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, 100, 0, false, true, true));
        if (this.getOwner() != null) {
            target.addCommandTag("ssc_owner:" + this.getOwner().getUuid());
        }
    }

    /** 推进并绘制所有活跃火环（穿透/爆炸触发），播完移除。 */
    private void updateActiveRings(ServerWorld w) {
        java.util.Iterator<float[]> it = activeRings.iterator();
        while (it.hasNext()) {
            float[] r = it.next();
            r[3] += 1f;
            float progress = r[3] / (float) RING_DURATION;
            spawnWaistRing(w, r[0], r[1], r[2], progress);
            if (r[3] >= RING_DURATION) it.remove();
        }
    }

    /** 6 格球爆炸粒子：80% 红 dust + 20% 狐火，附加少量 lava 黑渣。 */
    private void spawnExplosionParticles(ServerWorld w, double x, double y, double z) {
        Random rnd = this.random;
        DustParticleEffect red = new DustParticleEffect(new Vector3f(0.85f, 0.1f, 0.05f), 1.3f);
        for (int i = 0; i < 130; i++) {
            Vec3d p = randomInSphere(EXPLODE_RADIUS, rnd);
            double px = x + p.x, py = y + p.y, pz = z + p.z;
            if (rnd.nextDouble() < 0.8) {
                w.spawnParticles(red, px, py, pz, 1, 0, 0, 0, 0);
            } else {
                w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0, 0, 0.01);
            }
        }
        for (int i = 0; i < 16; i++) {
            Vec3d p = randomInSphere(EXPLODE_RADIUS * 0.6, rnd);
            w.spawnParticles(ParticleTypes.LAVA, x + p.x, y + p.y + 1.0, z + p.z, 1, 0, 0, 0, 0);
        }
        // === RC4 奥术手雷爆炸特效（放大 1.5 倍版：扩散范围与 dust 尺寸 ×1.5，数量与速度不变） ===
        w.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 3, 0.3, 0.3, 0.3, 1.0);
        w.spawnParticles(ParticleTypes.LAVA, x, y + 0.8, z, 20, 0.45, 0.45, 0.45, 0.2);
        // 紫红→暗红渐变粉尘（dust 尺寸 1→1.5）
        DustColorTransitionParticleEffect arcaneDust = new DustColorTransitionParticleEffect(
                new Vector3f(0.322f, 0.0f, 0.149f), new Vector3f(0.149f, 0.012f, 0.039f), 1.5f);
        w.spawnParticles(arcaneDust, x, y + 0.3, z, 300, 1.05, 1.8, 1.05, 0.01);
        w.spawnParticles(ParticleTypes.FLAME, x, y + 0.3, z, 80, 0.75, 1.2, 0.75, 0.1);
        w.spawnParticles(ParticleTypes.SQUID_INK, x, y + 0.3, z, 5, 0.45, 0.45, 0.45, 0.1);
        w.spawnParticles(ParticleTypes.FALLING_LAVA, x, y + 0.1, z, 125, 1.5, 0.75, 1.5, 0.2);
    }

    /** 腰部火环扩散动画：每帧画半径递增的环（0→CHAIN_RADIUS），仅火焰 + 灵魂火粒子（无红色粉尘）。 */
    private void spawnWaistRing(ServerWorld w, double x, double y, double z, float progress) {
        double radius = Math.max(0.1, CHAIN_RADIUS * progress);
        int pts = Math.max(2, (int) ((12 + 26 * progress) * 0.2));  // 粒子量减至原 20%
        double rot = progress * 0.6;                                // 轻微旋转更灵动
        double upward = 0.02 + progress * 0.04;                     // 火苗向上飘
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts + rot;
            double px = x + radius * Math.cos(a);
            double pz = z + radius * Math.sin(a);
            w.spawnParticles(ParticleTypes.FLAME, px, y, pz, 1, 0, upward, 0, 0.0);
            if (i % 2 == 0) {
                w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.05, pz, 1, 0, upward, 0, 0.01);
            }
        }
    }

    private Vec3d randomInSphere(double r, Random rnd) {
        double rr = r * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * 2 * Math.PI;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vec3d(rr * sinPhi * Math.cos(theta), rr * Math.cos(phi), rr * sinPhi * Math.sin(theta));
    }

    private DamageSource magicSource(LivingEntity target, LivingEntity owner) {
        RegistryKey<DamageType> key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
        return target.getDamageSources().create(key, owner, owner);
    }

    private DamageSource physicalSource(LivingEntity target, LivingEntity owner) {
        RegistryKey<DamageType> key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "mob_attack"));
        return target.getDamageSources().create(key, owner, owner);
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("DirX")) {
            this.direction = new Vec3d(nbt.getDouble("DirX"), nbt.getDouble("DirY"), nbt.getDouble("DirZ"));
        }
        this.distanceTraveled = nbt.getDouble("Dist");
        this.exploded = nbt.getBoolean("Exploded");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putDouble("DirX", direction.x);
        nbt.putDouble("DirY", direction.y);
        nbt.putDouble("DirZ", direction.z);
        nbt.putDouble("Dist", distanceTraveled);
        nbt.putBoolean("Exploded", exploded);
    }

    @Override
    public net.minecraft.item.ItemStack getStack() {
        return new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIRE_CHARGE);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
