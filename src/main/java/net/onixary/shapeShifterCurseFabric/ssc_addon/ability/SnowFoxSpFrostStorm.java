package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐远程次要技能 - 冰风暴
 * 1.5秒蓄力后在准星位置释放冰风暴
 * 蓄力期间减少50%准星移动速度
 */
public class SnowFoxSpFrostStorm {

    private SnowFoxSpFrostStorm() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }
    
    private static final ConcurrentHashMap<UUID, ChargingData> CHARGING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>(); // 自定义CD跟踪
    
    private static final int CHARGE_TICKS = 30; // 1.5秒蓄力
    private static final double MAX_RANGE = 30.0; // 最大释放距离
    private static final int MANA_COST = 30; // 霜寒值消耗
    //未使用: private static final int COOLDOWN = 600;  30秒CD = 600tick
    
    private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
    private static final Identifier REGEN_COOLDOWN_ID = new Identifier("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    private static final Identifier POWER_ID = new Identifier("my_addon", "form_snow_fox_sp_ranged_secondary");
    
    /**
     * 开始蓄力（点按技能键时调用）
     */
    public static boolean startCharging(ServerPlayerEntity player) {
        // 检查是否已经在蓄力
        if (CHARGING_PLAYERS.containsKey(player.getUuid())) {
            return false;
        }
        
        // 检查自定义CD是否结束
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }
        
        // 检查霜寒值
        int currentMana = getResourceValue(player);
        if (currentMana < MANA_COST) {
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
            return false;
        }
        
        // 消耗霜寒值（在蓄力开始时就消耗）
        changeResourceValue(player, -MANA_COST);
        // 设置回复冷却（5秒）
        setRegenCooldown(player, 100);
        // 设置技能CD（30秒 = 30000ms）
        COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + 30000L);
        
        // 开始蓄力
        CHARGING_PLAYERS.put(player.getUuid(), new ChargingData(0));
        
        // 播放蓄力开始音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.5f);
        
        return true;
    }
    
    /**
     * 每tick更新蓄力状态
     */
    public static void tick(ServerPlayerEntity player) {
        ChargingData data = CHARGING_PLAYERS.get(player.getUuid());
        if (data == null) return;
        
        data.chargeTicks++;
        
        // 生成蓄力粒子效果
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            Vec3d pos = player.getPos();
            double angle = (data.chargeTicks * 0.3) % (Math.PI * 2);
            double radius = 0.8;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE, x, pos.y + 1, z, 1, 0, 0.1, 0, 0);
        }
        
        // 蓄力完成
        if (data.chargeTicks >= CHARGE_TICKS) {
            releaseStorm(player);
            CHARGING_PLAYERS.remove(player.getUuid());
        }
    }
    
    /**
     * 释放冰风暴
     */
    private static void releaseStorm(ServerPlayerEntity player) {
        // 霜寒值已在startCharging时消耗，CD也已设置

        // 计算准星位置（射线检测）
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(MAX_RANGE));
        
        BlockHitResult hitResult = player.getWorld().raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
        ));
        
        Vec3d targetPos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            targetPos = hitResult.getPos();
        } else {
            targetPos = end;
        }
        
        // 创建冰风暴实体
        FrostStormEntity storm = new FrostStormEntity(
            player.getWorld(),
            targetPos.x, targetPos.y, targetPos.z,
            player
        );
        player.getWorld().spawnEntity(storm);
        
        // 播放释放音效
        player.getWorld().playSound(null, targetPos.x, targetPos.y, targetPos.z,
            SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 0.8f);
        
        // 生成释放粒子
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                targetPos.x, targetPos.y + 1, targetPos.z,
                30, 1.5, 1.0, 1.5, 0.05);
        }
    }
    
    /**
     * 检查玩家是否正在蓄力
     */
    public static boolean isCharging(ServerPlayerEntity player) {
        return CHARGING_PLAYERS.containsKey(player.getUuid());
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
    /* 未使用,已移除
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
     */
    
    /**
     * 蓄力数据
     */
    private static class ChargingData {
        int chargeTicks;
        
        ChargingData(int chargeTicks) {
            this.chargeTicks = chargeTicks;
        }
    }
}
