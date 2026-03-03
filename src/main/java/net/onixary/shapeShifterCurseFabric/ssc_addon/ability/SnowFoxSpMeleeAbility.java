package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

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

	private SnowFoxSpMeleeAbility() {
    }

    private static final ConcurrentHashMap<UUID, DashingPlayerData> DASHING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>();
    
    private static final double DASH_DISTANCE = 8.0;
    private static final double DASH_SPEED = 1.5;
    private static final float DAMAGE = 8.0f;
    private static final int FROST_FREEZE_DURATION = 60;
    private static final int MANA_COST = 15;
    
    // ==== NEW CODE: 使用FormIdentifiers ====
    private static final Identifier RESOURCE_ID = FormIdentifiers.SNOW_FOX_RESOURCE;
    private static final Identifier REGEN_COOLDOWN_ID = FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN;
    private static final Identifier POWER_ID = FormIdentifiers.SNOW_FOX_MELEE_PRIMARY;
    private static final int COOLDOWN = 120;
    
    /**
     * 执行雪刺冲刺
     */
    public static boolean execute(ServerPlayerEntity player) {
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }
        
        int currentMana = PowerUtils.getResourceValue(player, RESOURCE_ID);
        
        if (currentMana < MANA_COST) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        if (DASHING_PLAYERS.containsKey(player.getUuid())) {
            return false;
        }
        
        PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST);
        PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, 100);
        COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 6000L);
        
        Vec3d lookDir = player.getRotationVector().normalize();
        Vec3d startPos = player.getPos();
        
        DashingPlayerData data = new DashingPlayerData(startPos, lookDir, 0);
        DASHING_PLAYERS.put(player.getUuid(), data);
        
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
        
        double distanceMoved = data.ticksElapsed * DASH_SPEED;
        
        if (distanceMoved >= DASH_DISTANCE || player.horizontalCollision || player.verticalCollision) {
            DASHING_PLAYERS.remove(player.getUuid());
            return;
        }
        
        Vec3d velocity = data.direction.multiply(DASH_SPEED);
        player.setVelocity(velocity);
        player.velocityModified = true;
        
        Box hitbox = player.getBoundingBox().expand(0.5);
        List<Entity> nearbyEntities = player.getWorld().getOtherEntities(player, hitbox, 
            entity -> entity instanceof LivingEntity && !data.hitEntities.contains(entity.getUuid()));
        
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity target) {
                if (WhitelistUtils.isProtected(player, target)) {
                    data.hitEntities.add(entity.getUuid());
                    continue;
                }
                data.hitEntities.add(entity.getUuid());
                
                DamageSource source = player.getDamageSources().playerAttack(player);
                target.damage(source, DAMAGE);
                
                target.addStatusEffect(new StatusEffectInstance(
                    SscAddon.FROST_FREEZE,
                    FROST_FREEZE_DURATION,
                    0,
                    false,
                    true,
                    true
                ));
                
                // 使用ParticleUtils
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                    ParticleUtils.spawnHitParticles(serverWorld, new Vec3d(target.getX(), target.getY() + target.getHeight() / 2, target.getZ()));
                }
                
                player.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
        }
        
        // 使用ParticleUtils
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            ParticleUtils.spawnSnowflakeParticles(serverWorld, new Vec3d(player.getX(), player.getY() + 0.5, player.getZ()));
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
     * 玩家断线/死亡时清理所有状态，防止内存泄漏和重连后状态错乱
     */
    public static void clearPlayer(java.util.UUID uuid) {
        DASHING_PLAYERS.remove(uuid);
        COOLDOWN_PLAYERS.remove(uuid);
    }
    
    /*
    // 旧代码 (保留参考) 已移至PowerUtils
    
    private static final Identifier RESOURCE_ID_OLD = new Identifier("my_addon", "form_snow_fox_sp_resource");
    private static final Identifier REGEN_COOLDOWN_ID_OLD = new Identifier("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    private static final Identifier POWER_ID_OLD = new Identifier("my_addon", "form_snow_fox_sp_melee_primary");
    
    private static int getResourceValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID_OLD);
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
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID_OLD);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    private static void setRegenCooldown(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID_OLD);
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
            PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID_OLD);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof CooldownPower cooldownPower) {
                cooldownPower.setCooldown(ticks);
            }
        } catch (Exception e) {
        }
    }
    */
    
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
