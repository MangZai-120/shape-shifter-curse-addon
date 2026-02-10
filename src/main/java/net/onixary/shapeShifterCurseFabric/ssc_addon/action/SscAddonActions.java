package net.onixary.shapeShifterCurseFabric.ssc_addon.action;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.EntityPose;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.minecraft.entity.effect.StatusEffects;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SscAddonActions {
    
    // 冰球自定义CD跟踪
    private static final ConcurrentHashMap<UUID, Long> FROST_BALL_COOLDOWN = new ConcurrentHashMap<>();

    public static void register() {
            registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "item_cooldown"),
            new SerializableData()
                .add("item", SerializableDataTypes.ITEM)
                .add("duration", SerializableDataTypes.INT),
            (data, entity) -> {
                if(entity instanceof PlayerEntity player) {
                    player.getItemCooldownManager().set((net.minecraft.item.Item)data.get("item"), data.getInt("duration"));
                }
            }));

        registerEntity(new ActionFactory<>(new Identifier("my_addon", "fire_breath"),
            new SerializableData()
                .add("distance", SerializableDataTypes.FLOAT)
                .add("damage", SerializableDataTypes.FLOAT),
            (data, entity) -> {
                if (!(entity instanceof LivingEntity living)) return;
                
                float distance = data.getFloat("distance");
                float damageAmount = data.getFloat("damage");
                
                Vec3d eyePos = living.getEyePos();
                Vec3d lookVec = living.getRotationVec(1.0F);
                Vec3d targetPos = eyePos.add(lookVec.multiply(distance));
                
                Box box = living.getBoundingBox().expand(distance).stretch(lookVec.multiply(distance));
                
                living.getWorld().getEntitiesByClass(LivingEntity.class, box, target -> target != living).forEach(target -> {
                    Vec3d targetVec = target.getPos().add(0, target.getHeight() / 2, 0).subtract(eyePos).normalize();
                    double dot = lookVec.dotProduct(targetVec);
                    double distSq = living.squaredDistanceTo(target);
                    
                    if (dot > 0.8 && distSq < distance * distance) {
                         Vec3d oldVelocity = target.getVelocity();
                         // Use INDIRECT_MAGIC damage type sourced from player (bypasses armor often, but counts as player kill)
                         // If we want it to be just MAGIC but from player, we construct it via damageSources
                         if (target.damage(target.getDamageSources().magic(), damageAmount)) {
                             // Restore velocity to prevent knockback
                             target.setVelocity(oldVelocity);
                             // Manually setting attacker to player because .magic() source usually doesn't carry attacker info by default in some mappings,
                             // or we can use create(DamageTypes.MAGIC, player, player)
                         }
                         
                         // To properly attribute damage to player for kill credit while keeping it MAGIC:
                         // We should use a custom damage source or existing one that fits
                         // Let's try playerAttack first but just override the damage type if possible, OR
                         // use new DamageSource(target.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.MAGIC), living, living)
                         
                         // Re-implementation for "Magic Damage from Player without Knockback":
                         RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
                         if (target.damage(target.getDamageSources().create(magicKey, living, living), damageAmount)) {
                              target.setVelocity(oldVelocity);
                         }

                         target.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, 100, 0)); // 5 seconds
                         
                         if (living instanceof PlayerEntity player && target instanceof SscIgnitedEntityAccessor accessor) {
                             accessor.sscAddon$setIgniterUuid(player.getUuid());
                         }
                    }
                });
            }));

        registerBiEntity(new ActionFactory<>(new Identifier("my_addon", "set_on_fire_attributed"),
            new SerializableData()
                .add("duration", SerializableDataTypes.INT),
            (data, pair) -> {
                Entity actor = pair.getLeft();
                Entity target = pair.getRight();
                if (actor == null || target == null) return;
                if (target.getWorld().isClient()) return;
                
                int duration = data.getInt("duration");
                // target.setOnFireFor(duration); // Replaced with custom effect
                
                if (target instanceof LivingEntity livingTarget) {
                    livingTarget.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, duration * 20, 0));
                }
                
                if (actor instanceof PlayerEntity player && target instanceof SscIgnitedEntityAccessor accessor) {
                    accessor.sscAddon$setIgniterUuid(player.getUuid());
                }
            }));

        registerBiEntity(new ActionFactory<>(new Identifier("my_addon", "damage_target_from_actor"),
            new SerializableData()
                .add("amount", SerializableDataTypes.FLOAT)
                .add("damage_type", SerializableDataTypes.IDENTIFIER),
            (data, pair) -> {
                Entity actor = pair.getLeft();
                Entity target = pair.getRight();
                if (actor == null || target == null) return;
                
                float amount = data.getFloat("amount");
                Identifier damageTypeId = data.getId("damage_type");
                
                if (target instanceof LivingEntity) {
                    RegistryKey<DamageType> damageTypeKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, damageTypeId);
                    Vec3d oldVelocity = target.getVelocity();
                    if (target.damage(target.getDamageSources().create(damageTypeKey, null, actor), amount)) {
                        target.setVelocity(oldVelocity);
                    }
                }
            }));
            
        registerEntity(new ActionFactory<>(new Identifier("my_addon", "force_pose"),
            new SerializableData()
                .add("pose", SerializableDataTypes.STRING),
            (data, entity) -> {
                String poseName = data.getString("pose");
                try {
                    EntityPose pose = EntityPose.valueOf(poseName.toUpperCase());
                    entity.setPose(pose);
                    if (pose == EntityPose.SWIMMING) {
                        entity.setSwimming(true);
                    }
                } catch (IllegalArgumentException ignored) {}
            }));

        registerEntity(new ActionFactory<>(new Identifier("my_addon", "adaptive_water_jump"),
            new SerializableData()
                .add("base_y", SerializableDataTypes.FLOAT, 0.4f)
                .add("horizontal_momentum", SerializableDataTypes.FLOAT, 1.2f)
                .add("vertical_conversion", SerializableDataTypes.FLOAT, 0.5f),
            (data, entity) -> {
                if (entity instanceof LivingEntity living) {
                    float baseY = data.getFloat("base_y");
                    float hMom = data.getFloat("horizontal_momentum");
                    float vConv = data.getFloat("vertical_conversion");

                    Vec3d velocity = living.getVelocity();
                    double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
                    
                    // New Y: Base jump + portion of horizontal speed converted to lift + existing vertical velocity
                    double newY = baseY + (hSpeed * vConv) + (velocity.y > 0 ? velocity.y : 0);
                    
                    // New Horizontal: maintain and boost momentum
                    double newX = velocity.x * hMom;
                    double newZ = velocity.z * hMom;

                    living.setVelocity(newX, newY, newZ);
                    living.velocityModified = true;
                }
            }));

        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "clear_aggro"),
            new SerializableData()
                .add("radius", SerializableDataTypes.DOUBLE, 64.0),
            (data, entity) -> {
                System.out.println("SSC ADDON DEBUG: Play Dead / Clear Aggro Triggered!");
                double radius = data.getDouble("radius");
                Box box = entity.getBoundingBox().expand(radius);
                entity.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, box, mob -> mob.getTarget() == entity).forEach(mob -> {
                    mob.setTarget(null);
                    mob.setAttacker(null);
                });
            }));

        // SP雪狐 - 雪刺冲刺技能
        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_dash"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    SnowFoxSpMeleeAbility.execute(player);
                }
            }));

        // SP雪狐 - 瞬移攻击技能
        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_teleport_attack"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    SnowFoxSpTeleportAttack.execute(player);
                }
            }));

        // SP雪狐 - 法术冰球技能
        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_frost_ball"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    // 检查自定义CD是否结束
                    Long cdEndTime = FROST_BALL_COOLDOWN.get(player.getUuid());
                    if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
                        return;
                    }
                    
                    // 检查并消耗霜寒值
                    int currentMana = getResourceValue(player, "my_addon:form_snow_fox_sp_resource");
                    int manaCost = 15;
                    if (currentMana < manaCost) {
                        player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
                        return;
                    }
                    changeResourceValue(player, "my_addon:form_snow_fox_sp_resource", -manaCost);
                    // 设置回复冷却（5秒）
                    setRegenCooldown(player, 100);
                    // 设置技能CD（5秒 = 5000ms）
                    FROST_BALL_COOLDOWN.put(player.getUuid(), System.currentTimeMillis() + 5000L);
                    
                    // 创建并发射冰球
                    FrostBallEntity frostBall = new FrostBallEntity(player.getWorld(), player);
                    Vec3d lookVec = player.getRotationVec(1.0F);
                    frostBall.setDirection(lookVec);
                    player.getWorld().spawnEntity(frostBall);
                    
                    // 播放发射音效
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
                }
            }));

        // SP雪狐 - 冰风暴技能（点按开始蓄力）
        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_frost_storm"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    SnowFoxSpFrostStorm.startCharging(player);
                }
            }));

        registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "trigger_play_dead"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof LivingEntity living) {
                    // 1. Effects
                    // Duration 6s = 120 ticks
                    int duration = 120;
                    // visible=false to hide icon
                    living.addStatusEffect(new StatusEffectInstance(SscAddon.PLAYING_DEAD, duration, 0, false, false, false));
                    living.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, duration, 2, false, true));
                    living.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false));
                    living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 10, false, false));
                    
                    // 2. Clear Aggro
                    double radius = 64.0;
                    Box box = living.getBoundingBox().expand(radius);
                    living.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, box, mob -> mob.getTarget() == living).forEach(mob -> {
                        mob.setTarget(null);
                        mob.setAttacker(null);
                    });
                    
                    // 3. Force Pose
                    living.setPose(EntityPose.SLEEPING);
                    
                    System.out.println("SSC ADDON: Triggered Play Dead (Composite Java Action)");
                }
            }));
            
        registerEntity(new ActionFactory<>(new Identifier("my_addon", "adaptive_water_jump"),
            new SerializableData().add("multiplier", SerializableDataTypes.FLOAT, 2.0F),
            (data, entity) -> {
                // Must be in swimming pose (sprinting in water) to trigger
                if (entity.isSwimming()) {
                    Vec3d velocity = entity.getVelocity();
                    double vy = velocity.y;
                    
                    // Only boost if moving upwards
                    // AND looking up (Pitch < -20) for the special "Jump Out" mechanics
                    if (vy > 0) {
                         double newVy = vy;
                         double newVx = velocity.x;
                         double newVz = velocity.z;
                         
                         // Special Jump Boost: Only when looking up (Pitch < -20)
                         // Triggers explosive jump out of water
                         if (entity.getPitch() < -20.0f) {
                             newVy = vy * data.getFloat("multiplier");
                             
                             // Height limit constraints (Considering Air Resistance 0.98 and Gravity 0.08)
                             // Min: 7 Blocks Height -> requires ~1.1 velocity
                             // Max: 12 Blocks Height -> requires ~1.5 velocity
                             double minVy = 1.1; 
                             double maxVy = 1.7;
                             
                             if (newVy < minVy) newVy = minVy;
                             if (newVy > maxVy) newVy = maxVy;
                             
                             // Maintain Horizontal Acceleration (Fix "stutter/stop" when looking up)
                             newVx = velocity.x * 1.5;
                             newVz = velocity.z * 1.5;
                         } else {
                             // Normal Swimming Leap (Flat/Looking Down):
                             // Vertical speed (Rising/Sinking) 1.5x boost
                             newVy = vy * 1.5;
                             // Horizontal boost (1.5x) to create composite vector acceleration
                             newVx = velocity.x * 1.5;
                             newVz = velocity.z * 1.5;
                         }

                         // Always apply velocity to preserve momentum against water exit drag
                         // This ensures smooth transition for both cases
                         entity.setVelocity(newVx, newVy, newVz);
                         entity.velocityModified = true;
                    }
                }
            }));
    }

    private static void registerBiEntity(ActionFactory<Pair<Entity, Entity>> actionFactory) {
        if (!ApoliRegistries.BIENTITY_ACTION.containsId(actionFactory.getSerializerId())) {
             Registry.register(ApoliRegistries.BIENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
        }
    }
    
    private static void registerEntity(ActionFactory<Entity> actionFactory) {
        if (!ApoliRegistries.ENTITY_ACTION.containsId(actionFactory.getSerializerId())) {
            Registry.register(ApoliRegistries.ENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
        }
    }
    
    /**
     * 获取Resource值
     */
    private static int getResourceValue(ServerPlayerEntity player, String resourceId) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(new Identifier(resourceId));
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
     * 修改Resource值
     */
    private static void changeResourceValue(ServerPlayerEntity player, String resourceId, int change) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(new Identifier(resourceId));
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
            PowerType<?> powerType = PowerTypeRegistry.get(new Identifier("my_addon:form_snow_fox_sp_frost_regen_cooldown_resource"));
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }
}
