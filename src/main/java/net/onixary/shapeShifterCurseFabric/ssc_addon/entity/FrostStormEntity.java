package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;
import java.util.UUID;

/**
 * SP雪狐远程次要技能 - 冰风暴实体
 * 持续10秒，伤害半径3.5格，每秒2点魔法伤害
 * 吸附速度2b/s（6格内），6-10格吸附减弱
 */
public class FrostStormEntity extends Entity {
    
    private static final int DURATION = 200; // 10秒
    private static final double DAMAGE_RADIUS = 3.5;
    private static final double PULL_RADIUS_STRONG = 6.0;
    private static final double PULL_RADIUS_WEAK = 10.0;
    private static final float DAMAGE_PER_SECOND = 2.0f;
    private static final double PULL_SPEED = 0.1; // 2格/秒 = 0.1格/tick
    
    private int ticksAlive = 0;
    private UUID ownerUuid;
    
    public FrostStormEntity(EntityType<?> entityType, World world) {
        super(entityType, world);
        this.noClip = true;
    }
    
    public FrostStormEntity(World world, double x, double y, double z, PlayerEntity owner) {
        super(SscAddon.FROST_STORM_ENTITY, world);
        this.setPosition(x, y, z);
        this.ownerUuid = owner.getUuid();
        this.noClip = true;
    }
    
    @Override
    protected void initDataTracker() {
        // 暂时不需要初始化数据跟踪器
    }
    
    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        
        if (ticksAlive > DURATION) {
            this.discard();
            return;
        }
        
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
            // 每0.5秒造成一次伤害（每10tick）
            if (ticksAlive % 10 == 0) {
                dealDamage(serverWorld);
            }
            
            // 每tick吸附敌人
            pullEntities();
            
            // 生成粒子效果
            spawnParticles(serverWorld);
            
            // 播放环境音效
            if (ticksAlive % 40 == 0) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_SNOW_GOLEM_AMBIENT, SoundCategory.HOSTILE, 0.5f, 0.5f);
            }
        }
    }
    
    private void dealDamage(ServerWorld world) {
        Box damageBox = new Box(
            this.getX() - DAMAGE_RADIUS, this.getY() - 1, this.getZ() - DAMAGE_RADIUS,
            this.getX() + DAMAGE_RADIUS, this.getY() + 3, this.getZ() + DAMAGE_RADIUS
        );
        
        List<LivingEntity> targets = world.getEntitiesByClass(
            LivingEntity.class, damageBox,
            entity -> entity.getUuid() != ownerUuid && entity.isAlive()
        );
        
        PlayerEntity owner = ownerUuid != null ? world.getPlayerByUuid(ownerUuid) : null;
        
        for (LivingEntity target : targets) {
            double dist = this.squaredDistanceTo(target.getX(), this.getY(), target.getZ());
            if (dist <= DAMAGE_RADIUS * DAMAGE_RADIUS) {
                DamageSource source = owner != null 
                    ? target.getDamageSources().playerAttack(owner)
                    : target.getDamageSources().magic();
                target.damage(source, DAMAGE_PER_SECOND);
            }
        }
    }
    
    private void pullEntities() {
        Box pullBox = new Box(
            this.getX() - PULL_RADIUS_WEAK, this.getY() - 2, this.getZ() - PULL_RADIUS_WEAK,
            this.getX() + PULL_RADIUS_WEAK, this.getY() + 4, this.getZ() + PULL_RADIUS_WEAK
        );
        
        List<LivingEntity> targets = this.getWorld().getEntitiesByClass(
            LivingEntity.class, pullBox,
            entity -> entity.getUuid() != ownerUuid && entity.isAlive()
        );
        
        Vec3d center = new Vec3d(this.getX(), this.getY(), this.getZ());
        
        for (LivingEntity target : targets) {
            Vec3d targetPos = target.getPos();
            double dist = Math.sqrt(target.squaredDistanceTo(this.getX(), this.getY(), this.getZ()));
            
            if (dist > PULL_RADIUS_WEAK) continue;
            if (dist < 0.5) continue; // 太近不吸附
            
            // 计算吸附速度
            double pullStrength;
            if (dist <= PULL_RADIUS_STRONG) {
                pullStrength = PULL_SPEED; // 正常吸附速度
            } else {
                // 6-10格，吸附减弱
                double factor = 1.0 - ((dist - PULL_RADIUS_STRONG) / (PULL_RADIUS_WEAK - PULL_RADIUS_STRONG));
                pullStrength = PULL_SPEED * factor * 0.3; // 骤减吸附
            }
            
            // 计算吸附方向
            Vec3d direction = center.subtract(targetPos).normalize();
            Vec3d pullVelocity = direction.multiply(pullStrength);
            
            // 应用吸附
            Vec3d newVelocity = target.getVelocity().add(pullVelocity);
            target.setVelocity(newVelocity);
            target.velocityModified = true;
        }
    }
    
    private void spawnParticles(ServerWorld world) {
        // 风暴粒子
        for (int i = 0; i < 5; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * DAMAGE_RADIUS;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + Math.random() * 2;
            
            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0, 0, 0, 0.05);
        }
        
        // 旋转粒子效果
        double rotAngle = (ticksAlive * 0.2) % (Math.PI * 2);
        for (int i = 0; i < 3; i++) {
            double angle = rotAngle + (i * Math.PI * 2 / 3);
            double x = this.getX() + Math.cos(angle) * 2;
            double z = this.getZ() + Math.sin(angle) * 2;
            world.spawnParticles(ParticleTypes.CLOUD, x, this.getY() + 1, z, 1, 0, 0.1, 0, 0);
        }
    }
    
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        this.ticksAlive = nbt.getInt("TicksAlive");
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
    }
    
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("TicksAlive", this.ticksAlive);
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
    }
    
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
    
    @Override
    public boolean isCollidable() {
        return false;
    }
}
