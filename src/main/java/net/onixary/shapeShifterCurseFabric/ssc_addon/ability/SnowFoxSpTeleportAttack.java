package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.*;
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
        // This utility class should not be instantiated
    }
    
    private static final ConcurrentHashMap<UUID, TeleportAttackData> ATTACKING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>(); // 自定义CD跟踪
    
    private static final double RANGE = 10.0; // 搜索范围
    private static final int MAX_TARGETS = 3; // 最大目标数
    private static final float BASE_DAMAGE = 6.0f; // 基础伤害
    private static final float BONUS_DAMAGE = 3.0f; // 霜凝状态额外伤害
    private static final int MANA_COST_SUCCESS = 30; // 成功时霜寒值消耗
    private static final int MANA_COST_FAIL = 20; // 失败时霜寒值消耗
    private static final int TELEPORT_INTERVAL = 10; // 瞬移间隔（0.5秒 = 10tick）
    private static final float DAMAGE_REDUCTION = 0.65f; // 受伤减免
    private static final int FAIL_COOLDOWN = 100; // 失败CD（5秒 = 100tick）
    private static final int SUCCESS_COOLDOWN = 400; // 成功CD（20秒 = 400tick）
    
    private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
    private static final Identifier REGEN_COOLDOWN_ID = new Identifier("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    private static final Identifier POWER_ID = new Identifier("my_addon", "form_snow_fox_sp_melee_secondary");
    
    /**
     * 执行瞬移攻击
     */
    public static boolean execute(ServerPlayerEntity player) {
        // 检查是否已经在攻击
        if (ATTACKING_PLAYERS.containsKey(player.getUuid())) {
            return false;
        }
        
        // 检查自定义CD是否结束
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }
        
        // 检查霜寒值（需要至少20点才能尝试，失败消耗20，成功消耗30）
        int currentMana = getResourceValue(player);
        if (currentMana < MANA_COST_FAIL) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        // 搜索范围内的敌人
        List<LivingEntity> targets = findTargets(player);
        
        if (targets.isEmpty()) {
            // 没有敌人，播放灭火音效，设置5秒CD，消耗20点霜寒值
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            changeResourceValue(player, -MANA_COST_FAIL);
            // 设置回复冷却（5秒）
            setRegenCooldown(player, 100);
            // 设置失败CD（5秒 = 100tick = 5000ms）
            COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 5000L);
            return false;
        }
        
        // 成功时检查是否有足够霜寒值
        if (currentMana < MANA_COST_SUCCESS) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        // 消耗成功时的霜寒值（30点）
        changeResourceValue(player, -MANA_COST_SUCCESS);
        // 设置回复冷却（5秒）
        setRegenCooldown(player, 100);
        
        // 记录原始位置和目标
        Vec3d originalPos = player.getPos();
        Vec3d originalVelocity = player.getVelocity();
        float originalYaw = player.getYaw();
        float originalPitch = player.getPitch();
        
        TeleportAttackData data = new TeleportAttackData(
            originalPos, originalVelocity, originalYaw, originalPitch,
            targets, 0, 0
        );
        ATTACKING_PLAYERS.put(player.getUuid(), data);
        
        // 立刻执行第一次瞬移攻击
        teleportToTarget(player, data);
        
        return true;
    }
    
    /**
     * 每tick更新状态
     */
    public static void tick(ServerPlayerEntity player) {
        TeleportAttackData data = ATTACKING_PLAYERS.get(player.getUuid());
        if (data == null) return;
        
        // 检查是否被净化 - 如果有purified效果则立即返回原位并取消攻击
        if (player.hasStatusEffect(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.PURIFIED)) {
            returnToOrigin(player, data);
            ATTACKING_PLAYERS.remove(player.getUuid());
            return;
        }
        
        data.ticksSinceLastTeleport++;
        
        // 技能期间禁止移动
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        
        // 检查是否需要瞬移到下一个目标
        if (data.ticksSinceLastTeleport >= TELEPORT_INTERVAL) {
            data.currentTargetIndex++;
            data.ticksSinceLastTeleport = 0;
            
            if (data.currentTargetIndex < data.targets.size()) {
                // 瞬移到下一个目标
                teleportToTarget(player, data);
            } else {
                // 所有目标攻击完毕，回到原位
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
        
        // 检查目标是否还活着
        if (target.isDead() || target.isRemoved()) {
            return;
        }
        
        // 计算目标身后的位置
        Vec3d targetPos = target.getPos();
        Vec3d targetLookDir = target.getRotationVector().normalize();
        Vec3d behindPos = targetPos.subtract(targetLookDir.multiply(1.5)); // 目标身后1.5格
        
        // 瞬移到目标身后
        player.teleport(behindPos.x, behindPos.y, behindPos.z);
        
        // 让玩家面向目标
        Vec3d toTarget = targetPos.subtract(behindPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYaw(yaw);
        player.setPitch(0);
        
        // 播放末影人瞬移音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        
        // 生成瞬移粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
                20, 0.3, 0.5, 0.3, 0.05);
        }
        
        // 播放攻击动画（通过发送挥剑动画包）
        player.swingHand(player.getActiveHand());
        
        // 计算伤害
        float damage = BASE_DAMAGE;
        
        // 检查目标是否有霜凝效果
        StatusEffectInstance frostEffect = target.getStatusEffect(SscAddon.FROST_FREEZE);
        if (frostEffect != null) {
            damage += BONUS_DAMAGE;
        }
        
        // 造成伤害
        DamageSource source = player.getDamageSources().playerAttack(player);
        Vec3d oldVelocity = target.getVelocity();
        if (target.damage(source, damage)) {
            target.setVelocity(oldVelocity); // 保持目标位置
        }
        
        // 生成攻击粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.1);
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                1, 0, 0, 0, 0);
        }
        
        // 播放攻击音效
        player.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }
    
    /**
     * 返回原位
     */
    private static void returnToOrigin(ServerPlayerEntity player, TeleportAttackData data) {
        // 瞬移回原位
        player.teleport(data.originalPos.x, data.originalPos.y, data.originalPos.z);
        player.setYaw(data.originalYaw);
        player.setPitch(data.originalPitch);
        player.setVelocity(0, 0, 0); // 重置速度
        player.velocityModified = true;
        
        // 播放末影人瞬移音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        
        // 生成粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
                30, 0.3, 0.5, 0.3, 0.05);
        }
        
        // 设置成功CD（20秒 = 20000ms）
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
        
        // 按距离排序
        nearbyEntities.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        
        // 取最近的最多MAX_TARGETS个
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
     * 获取霜寒值
     */
    private static int getResourceValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
            // Resource not found
        }
        return 0;
    }
    
    /**
     * 修改霜寒值
     */
    private static void changeResourceValue(ServerPlayerEntity player, int change) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
                PowerHolderComponent.sync(player); // 同步到客户端
            }
        } catch (Exception e) {
            // Resource not found
        }
    }
    
    /**
     * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
     */
    public static void setRegenCooldown(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }
    
    /**
     * 设置power的cooldown
     */
    private static void setPowerCooldown(ServerPlayerEntity player, int ticks) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof CooldownPower cooldownPower) {
                cooldownPower.setCooldown(ticks);
            }
        } catch (Exception e) {
            // Power not found
        }
    }
    
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
