package net.onixary.shapeShifterCurseFabric.ssc_addon.action;

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
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;

public class SscAddonActions {

    public static void register() {
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
                         target.damage(target.getDamageSources().playerAttack((PlayerEntity)living), damageAmount);
                         target.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, 100, 0)); // 5 seconds
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
                    target.damage(target.getDamageSources().create(damageTypeKey, null, actor), amount);
                    target.setVelocity(oldVelocity);
                }
            }));
    }

    private static void registerBiEntity(ActionFactory<Pair<Entity, Entity>> actionFactory) {
        Registry.register(ApoliRegistries.BIENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
    }
    
    private static void registerEntity(ActionFactory<Entity> actionFactory) {
        Registry.register(ApoliRegistries.ENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
    }
}
