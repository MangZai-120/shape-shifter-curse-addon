package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;

public class FoxFireBurnEffect extends StatusEffect {
    public FoxFireBurnEffect() {
        super(StatusEffectCategory.HARMFUL, 0x3366FF); // Blue color
    }
    
    private PlayerEntity getOwnerFromTags(LivingEntity entity) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith("ssc_owner:")) {
                try {
                    String uuidStr = tag.substring("ssc_owner:".length());
                    UUID uuid = UUID.fromString(uuidStr);
                    return entity.getWorld().getPlayerByUuid(uuid);
                } catch (Exception e) {
                    // Ignore invalid tags
                }
            }
        }
        return null;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) {
            // Client side particles can be handled here or via separate client tick handler
            // But applyUpdateEffect runs on both usually if registered correctly.
            // For simple visuals, we might rely on server spawning particles or client side implementation.
            // StatusEffect particles are usually handled by the game automatically if color is set,
            // but we want Soul Fire particles.
            entity.getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME, 
                entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getWidth(), 
                entity.getY() + entity.getRandom().nextDouble() * entity.getHeight(), 
                entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getWidth(), 
                0, 0, 0);
        } else {
            // Server side logic
            // Damage every second (20 ticks) like vanilla fire
            if (entity.age % 20 == 0) {
                 DamageSource source = entity.getDamageSources().inFire();
                 
                 // Priority 1: Explicit owner from NBT tag (set by ssc-addon:mark_owner command)
                 PlayerEntity owner = getOwnerFromTags(entity);
                 if (owner != null) {
                     source = entity.getDamageSources().playerAttack(owner);
                 } 
                 // Priority 2: Standard vanilla attribution fallback
                 else if (entity.getLastAttacker() instanceof PlayerEntity player) {
                     source = entity.getDamageSources().playerAttack(player);
                 } else if (entity.getAttacker() instanceof PlayerEntity player) {
                     source = entity.getDamageSources().playerAttack(player);
                 }
                 
                 entity.damage(source, 1.0f);
                 
                 // Spawn explicit particles on server for everyone to see
                 if (entity.getWorld() instanceof ServerWorld serverWorld) {
                     serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
                        2, 0.3, 0.3, 0.3, 0.05);
                 }
            }
        }
    }
}
