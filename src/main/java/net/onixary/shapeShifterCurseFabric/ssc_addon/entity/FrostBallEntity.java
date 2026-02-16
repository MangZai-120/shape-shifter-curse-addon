package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/**
 * SP雪狐远程主要技能 - 法术冰球
 * 无下坠，飞行速度10b/s，最多飞行25格
 * 命中施加霜降效果4秒
 */
public class FrostBallEntity extends ProjectileEntity implements FlyingItemEntity {
    
    private static final double SPEED = 0.75; // 15格/秒 = 0.75格/tick（原10格/秒增加1.5倍）
    private static final double MAX_DISTANCE = 50.0; // 最大飞行距离（原25格增加2倍）
    private static final int FROST_FALL_DURATION = 80; // 霜降持续4秒
    
    private Vec3d startPos;
    private int ticksAlive = 0;
    
    // 追踪相关
    private LivingEntity trackingTarget;
    private boolean isChild = false;

    public FrostBallEntity(EntityType<? extends FrostBallEntity> entityType, World world) {
        super(entityType, world);
        this.startPos = this.getPos();
    }
    
    public FrostBallEntity(World world, LivingEntity owner) {
        super(SscAddon.FROST_BALL_ENTITY, world);
        this.setOwner(owner);
        this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        this.startPos = this.getPos();
    }
    
    public void setTrackingTarget(LivingEntity target) {
        this.trackingTarget = target;
    }

    public void setIsChild(boolean isChild) {
        this.isChild = isChild;
    }
    
    /**
     * 设置冰球的飞行方向
     */
    public void setDirection(Vec3d direction) {
        Vec3d velocity = direction.normalize().multiply(SPEED);
        this.setVelocity(velocity.x, velocity.y, velocity.z);
    }
    
    @Override
    protected void initDataTracker() {
        // 不需要额外的数据追踪器
    }
    
    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        
        // 追踪逻辑
        if (this.trackingTarget != null && this.trackingTarget.isAlive()) {
            Vec3d targetPos = this.trackingTarget.getEyePos();
            Vec3d currentPos = this.getPos();
            Vec3d direction = targetPos.subtract(currentPos).normalize();
            
            // 简单的追踪转向
            Vec3d currentVel = this.getVelocity().normalize();
            // 0.2f 的转向系数
            Vec3d newVel = currentVel.add(direction.multiply(0.2)).normalize().multiply(SPEED);
            
            this.setVelocity(newVel.x, newVel.y, newVel.z);
        }

        // 无重力移动
        Vec3d velocity = this.getVelocity();
        this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
        
        // 检测碰撞
        HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onCollision(hitResult);
        }

        // 如果在碰撞处理中实体被移除了（比如撞到了什么），就不再移动
        if (this.isRemoved()) return;
        
        // 检查是否超过最大飞行距离
        if (startPos != null && this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
            this.discard();
            return;
        }
        
        // 超时检查（防止无限飞行）
        if (ticksAlive > 100) { // 5秒超时
            this.discard();
            return;
        }
        
        // 生成粒子效果
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY(), this.getZ(),
                2, 0.1, 0.1, 0.1, 0.02);
        }
    }
    
    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        
        if (!this.getWorld().isClient) {
            // 播放击中音效
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
            
            // 生成爆炸粒子
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                    this.getX(), this.getY(), this.getZ(),
                    15, 0.3, 0.3, 0.3, 0.1);
            }
            
            this.discard();
        }
    }
    
    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        
        Entity target = entityHitResult.getEntity();
        if (target instanceof LivingEntity livingTarget && !this.getWorld().isClient) {
            // 施加霜降效果
            livingTarget.addStatusEffect(new StatusEffectInstance(
                SscAddon.FROST_FALL,
                FROST_FALL_DURATION,
                0,
                false,
                true,
                true
            ));
            
            // 播放击中音效
            this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 1.2f);

            // 霜之护符逻辑：如果是玩家发射的，且不是分裂的子弹，且装备了护符
            if (this.getOwner() instanceof LivingEntity owner && !this.isChild) {
                 boolean hasAmulet = TrinketsApi.getTrinketComponent(owner).map(
                    c -> c.isEquipped(SscAddon.FROST_AMULET)
                 ).orElse(false);

                 if (hasAmulet) {
                     spawnTrackingShards(owner, livingTarget);
                 }
            }
        }
    }

    private void spawnTrackingShards(LivingEntity owner, LivingEntity hitTarget) {
        // 直径10格 = 半径5格
        double radius = 5.0;
        Box box = hitTarget.getBoundingBox().expand(radius);
        List<LivingEntity> nearby = this.getWorld().getEntitiesByClass(LivingEntity.class, box,
            e -> e != owner && e != hitTarget && e.isAlive() && !e.isSpectator());
        
        if (nearby.isEmpty()) return;
        
        // 按距离排序
        nearby.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(hitTarget)));
        
        int count = 0;
        int maxShards = 2; // 发射两个
        
        for (LivingEntity target : nearby) {
            if (count >= maxShards) break;
            
            FrostBallEntity shard = new FrostBallEntity(this.getWorld(), owner);
            // 从被命中者位置发射
            shard.setPosition(hitTarget.getX(), hitTarget.getEyeY(), hitTarget.getZ());
            shard.setTrackingTarget(target);
            shard.setIsChild(true); // 标记为子弹，防止递归爆炸
            
            // 初始朝向目标
            Vec3d direction = target.getEyePos().subtract(hitTarget.getEyePos()).normalize();
            shard.setDirection(direction);
            
            this.getWorld().spawnEntity(shard);
            count++;
        }
    }
    
    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
    }
    
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("StartX")) {
            this.startPos = new Vec3d(
                nbt.getDouble("StartX"),
                nbt.getDouble("StartY"),
                nbt.getDouble("StartZ")
            );
        }
        if (nbt.contains("IsChild")) {
            this.setIsChild(nbt.getBoolean("IsChild"));
        }
    }
    
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (startPos != null) {
            nbt.putDouble("StartX", startPos.x);
            nbt.putDouble("StartY", startPos.y);
            nbt.putDouble("StartZ", startPos.z);
        }
        nbt.putBoolean("IsChild", this.isChild);
    }
    
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
    
    /**
     * FlyingItemEntity接口实现 - 返回雪球作为显示物品
     */
    @Override
    public ItemStack getStack() {
        return new ItemStack(Items.SNOWBALL);
    }
}
