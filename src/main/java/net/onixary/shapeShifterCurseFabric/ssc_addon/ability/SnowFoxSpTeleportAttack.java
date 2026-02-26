package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐近战次要技能 - 瞬移攻击
 * 瞬移到10格范围内最多3个敌人身后攻击
 */
public class SnowFoxSpTeleportAttack {

    private SnowFoxSpTeleportAttack() {
    }
    
    private static final ConcurrentHashMap<UUID, TeleportAttackData> ATTACKING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>();
    
    private static final double RANGE = 10.0;
    private static final int MAX_TARGETS = 3;
    private static final float BASE_DAMAGE = 6.0f;
    private static final float BONUS_DAMAGE = 3.0f;
    private static final int MANA_COST_SUCCESS = 30;
    private static final int MANA_COST_FAIL = 20;
    private static final int TELEPORT_INTERVAL = 10;
    private static final float DAMAGE_REDUCTION = 0.65f;
    private static final int FAIL_COOLDOWN = 100;
    private static final int SUCCESS_COOLDOWN = 400;
    
    // ==== NEW CODE: 使用FormIdentifiers ====
    private static final Identifier RESOURCE_ID = FormIdentifiers.SNOW_FOX_RESOURCE;
    private static final Identifier REGEN_COOLDOWN_ID = FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN;
    private static final Identifier POWER_ID = FormIdentifiers.SNOW_FOX_MELEE_SECONDARY;
    
    /**
     * 执行瞬移攻击
     */
    public static boolean execute(ServerPlayerEntity player) {
        if (ATTACKING_PLAYERS.containsKey(player.getUuid())) {
            return false;
        }
        
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }
        
        int currentMana = PowerUtils.getResourceValue(player, RESOURCE_ID);
        if (currentMana < MANA_COST_FAIL) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        List<LivingEntity> targets = findTargets(player);
        
        if (targets.isEmpty()) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST_FAIL);
            setRegenCooldown(player, 100);
            COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 5000L);
            return false;
        }
        
        if (currentMana < MANA_COST_SUCCESS) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST_SUCCESS);
        setRegenCooldown(player, 100);
        
        Vec3d originalPos = player.getPos();
        Vec3d originalVelocity = player.getVelocity();
        float originalYaw = player.getYaw();
        float originalPitch = player.getPitch();
        
        TeleportAttackData data = new TeleportAttackData(
            originalPos, originalVelocity, originalYaw, originalPitch,
            targets, 0, 0
        );
        ATTACKING_PLAYERS.put(player.getUuid(), data);
        
        teleportToTarget(player, data);
        
        return true;
    }
    
    /**
     * 每tick更新状态
     */
    public static void tick(ServerPlayerEntity player) {
        TeleportAttackData data = ATTACKING_PLAYERS.get(player.getUuid());
        if (data == null) return;
        
        if (player.hasStatusEffect(SscAddon.PURIFIED)) {
            returnToOrigin(player, data);
            ATTACKING_PLAYERS.remove(player.getUuid());
            return;
        }
        
        data.ticksSinceLastTeleport++;
        
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        
        if (data.ticksSinceLastTeleport >= TELEPORT_INTERVAL) {
            data.currentTargetIndex++;
            data.ticksSinceLastTeleport = 0;
            
            if (data.currentTargetIndex < data.targets.size()) {
                teleportToTarget(player, data);
            } else {
                returnToOrigin(player, data);
                ATTACKING_PLAYERS.remove(player.getUuid());
            }
        }
    }
    
    /**
     * 瞬移到目标身后并攻击
     */
    private static void teleportToTarget(ServerPlayerEntity player, TeleportAttackData data) {
        if (data.currentTargetIndex >= data.targets.size()) return;
        
        LivingEntity target = data.targets.get(data.currentTargetIndex);
        
        if (target.isDead() || target.isRemoved()) {
            return;
        }
        
        Vec3d targetPos = target.getPos();
        Vec3d targetLookDir = target.getRotationVector().normalize();
        Vec3d behindPos = targetPos.subtract(targetLookDir.multiply(1.5));
        
        player.teleport(behindPos.x, behindPos.y, behindPos.z);
        
        Vec3d toTarget = targetPos.subtract(behindPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYaw(yaw);
        player.setPitch(0);
        
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
                20, 0.3, 0.5, 0.3, 0.05);
        }
        
        player.swingHand(player.getActiveHand());
        
        float damage = BASE_DAMAGE;
        
        StatusEffectInstance frostEffect = target.getStatusEffect(SscAddon.FROST_FREEZE);
        if (frostEffect != null) {
            damage += BONUS_DAMAGE;
        }
        
        DamageSource source = player.getDamageSources().playerAttack(player);
        Vec3d oldVelocity = target.getVelocity();
        if (target.damage(source, damage)) {
            target.setVelocity(oldVelocity);
        }
        
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.1);
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                1, 0, 0, 0, 0);
        }
        
        player.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }
    
    /**
     * 返回原位
     */
    private static void returnToOrigin(ServerPlayerEntity player, TeleportAttackData data) {
        player.teleport(data.originalPos.x, data.originalPos.y, data.originalPos.z);
        player.setYaw(data.originalYaw);
        player.setPitch(data.originalPitch);
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
                30, 0.3, 0.5, 0.3, 0.05);
        }
        
        COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 20000L);
    }
    
    /**
     * 查找范围内的目标
     */
    private static List<LivingEntity> findTargets(ServerPlayerEntity player) {
        List<LivingEntity> result = new ArrayList<>();
        Box searchBox = player.getBoundingBox().expand(RANGE);
        
        List<LivingEntity> nearbyEntities = player.getWorld().getEntitiesByClass(
            LivingEntity.class, searchBox,
            entity -> entity != player && 
                      !entity.isSpectator() && 
                      entity.isAlive() &&
                      player.squaredDistanceTo(entity) <= RANGE * RANGE
        );
        
        nearbyEntities.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        
        for (int i = 0; i < Math.min(MAX_TARGETS, nearbyEntities.size()); i++) {
            result.add(nearbyEntities.get(i));
        }
        
        return result;
    }
    
    /**
     * 检查玩家是否正在瞬移攻击
     */
    public static boolean isAttacking(ServerPlayerEntity player) {
        return ATTACKING_PLAYERS.containsKey(player.getUuid());
    }
    
    /**
     * 获取伤害减免系数（用于Mixin）
     */
    public static float getDamageReduction(ServerPlayerEntity player) {
        if (isAttacking(player)) {
            return DAMAGE_REDUCTION;
        }
        return 0.0f;
    }
    
    /**
     * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
     * ==== NEW CODE: 使用PowerUtils ====
     */
    public static void setRegenCooldown(ServerPlayerEntity player, int value) {
        PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, value);
    }
    
    /*
    // 旧代码 (保留参考) 已移至PowerUtils
    
    private static int getResourceValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
        }
        return 0;
    }
    
    private static void changeResourceValue(ServerPlayerEntity player, int change) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    public static void setRegenCooldownOld(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    private static void setPowerCooldown(ServerPlayerEntity player, int ticks) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof CooldownPower cooldownPower) {
                cooldownPower.setCooldown(ticks);
            }
        } catch (Exception e) {
        }
    }
    */
    
    /**
     * 瞬移攻击数据
     */
    private static class TeleportAttackData {
        final Vec3d originalPos;
        final Vec3d originalVelocity;
        final float originalYaw;
        final float originalPitch;
        final List<LivingEntity> targets;
        int currentTargetIndex;
        int ticksSinceLastTeleport;
        
        TeleportAttackData(Vec3d originalPos, Vec3d originalVelocity, 
                          float originalYaw, float originalPitch,
                          List<LivingEntity> targets, 
                          int currentTargetIndex, int ticksSinceLastTeleport) {
            this.originalPos = originalPos;
            this.originalVelocity = originalVelocity;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.targets = targets;
            this.currentTargetIndex = currentTargetIndex;
            this.ticksSinceLastTeleport = ticksSinceLastTeleport;
        }
    }
}

