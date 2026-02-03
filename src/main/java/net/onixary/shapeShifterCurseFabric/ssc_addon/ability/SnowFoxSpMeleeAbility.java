package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐近战主要技能 - 雪刺冲刺
 * 向准星方向冲刺8格，碰撞敌人造成6点魔法伤害并施加霜凝效果3秒
 */
public class SnowFoxSpMeleeAbility {
    
    // 存储正在冲刺的玩家和已经击中的敌人
    private static final ConcurrentHashMap<UUID, DashingPlayerData> DASHING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>(); // 自定义CD跟踪
    
    private static final double DASH_DISTANCE = 8.0; // 冲刺距离（格）
    private static final double DASH_SPEED = 1.5; // 冲刺速度（每tick移动的格数）
    private static final float DAMAGE = 6.0f; // 伤害
    private static final int FROST_FREEZE_DURATION = 60; // 霜凝持续时间（3秒 = 60tick）
    private static final int MANA_COST = 15; // 霜寒值消耗
    
    private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
    private static final Identifier REGEN_COOLDOWN_ID = new Identifier("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    private static final Identifier POWER_ID = new Identifier("my_addon", "form_snow_fox_sp_melee_primary");
    private static final int COOLDOWN = 120; // 6秒CD = 120tick
    
    /**
     * 执行雪刺冲刺
     */
    public static boolean execute(ServerPlayerEntity player) {
        System.out.println("[SnowFoxSpDash] execute() called for player: " + player.getName().getString());
        
        // 检查自定义CD是否结束
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }
        
        // 检查霜寒值是否足够
        int currentMana = getResourceValue(player);
        System.out.println("[SnowFoxSpDash] Current mana: " + currentMana + ", required: " + MANA_COST);
        
        if (currentMana < MANA_COST) {
            // 霜寒值不足，播放失败音效
            System.out.println("[SnowFoxSpDash] Not enough mana, failing");
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        // 检查是否已经在冲刺
        if (DASHING_PLAYERS.containsKey(player.getUuid())) {
            return false;
        }
        
        // 消耗霜寒值
        changeResourceValue(player, -MANA_COST);
        // 设置回复冷却（5秒）
        setRegenCooldown(player, 100);
        // 设置技能CD（6秒 = 6000ms）
        COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 6000L);
        
        // 获取冲刺方向（玩家视线方向）
        Vec3d lookDir = player.getRotationVector().normalize();
        Vec3d startPos = player.getPos();
        
        // 开始冲刺
        DashingPlayerData data = new DashingPlayerData(startPos, lookDir, 0);
        DASHING_PLAYERS.put(player.getUuid(), data);
        
        // 播放冲刺开始音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 1.0f, 1.2f);
        
        return true;
    }
    
    /**
     * 每tick更新冲刺状态
     */
    public static void tick(ServerPlayerEntity player) {
        DashingPlayerData data = DASHING_PLAYERS.get(player.getUuid());
        if (data == null) return;
        
        // 计算已经移动的距离
        double distanceMoved = data.ticksElapsed * DASH_SPEED;
        
        if (distanceMoved >= DASH_DISTANCE || player.horizontalCollision || player.verticalCollision) {
            // 冲刺结束
            DASHING_PLAYERS.remove(player.getUuid());
            return;
        }
        
        // 移动玩家
        Vec3d velocity = data.direction.multiply(DASH_SPEED);
        player.setVelocity(velocity);
        player.velocityModified = true;
        
        // 检测碰撞的敌人
        Box hitbox = player.getBoundingBox().expand(0.5);
        List<Entity> nearbyEntities = player.getWorld().getOtherEntities(player, hitbox, 
            entity -> entity instanceof LivingEntity && !data.hitEntities.contains(entity.getUuid()));
        
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity target) {
                // 标记已击中
                data.hitEntities.add(entity.getUuid());
                
                // 造成魔法伤害
                DamageSource source = player.getDamageSources().magic();
                target.damage(source, DAMAGE);
                
                // 施加霜凝效果
                target.addStatusEffect(new StatusEffectInstance(
                    SscAddon.FROST_FREEZE,
                    FROST_FREEZE_DURATION,
                    0,
                    false,
                    true,
                    true
                ));
                
                // 播放击中音效和粒子
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1);
                    serverWorld.spawnParticles(ParticleTypes.CLOUD,
                        target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                        10, 0.3, 0.3, 0.3, 0.05);
                }
                
                player.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
        }
        
        // 生成冲刺粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 0.5, player.getZ(),
                5, 0.2, 0.2, 0.2, 0.02);
        }
        
        data.ticksElapsed++;
    }
    
    /**
     * 检查玩家是否正在冲刺
     */
    public static boolean isDashing(PlayerEntity player) {
        return DASHING_PLAYERS.containsKey(player.getUuid());
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
    private static void setRegenCooldown(ServerPlayerEntity player, int value) {
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
     * 冲刺中玩家数据
     */
    private static class DashingPlayerData {
        final Vec3d startPos;
        final Vec3d direction;
        final Set<UUID> hitEntities;
        int ticksElapsed;
        
        DashingPlayerData(Vec3d startPos, Vec3d direction, int ticksElapsed) {
            this.startPos = startPos;
            this.direction = direction;
            this.hitEntities = new HashSet<>();
            this.ticksElapsed = ticksElapsed;
        }
    }
}
