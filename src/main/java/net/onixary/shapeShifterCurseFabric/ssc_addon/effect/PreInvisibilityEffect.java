package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.TrueInvisibilityAbilityPower; // Need to make this public or accessible if not already
import java.util.List;

import net.minecraft.entity.player.PlayerEntity;

public class PreInvisibilityEffect extends StatusEffect {
    public PreInvisibilityEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x101010); // Dark color
        this.addAttributeModifier(
            EntityAttributes.GENERIC_MOVEMENT_SPEED,
            "12db6328-9844-4e20-9118-202758169972", 
            -0.5, 
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!entity.getWorld().isClient()) {
            ServerWorld serverWorld = (ServerWorld) entity.getWorld();
            
            // 1. Spawn Black Particles
            serverWorld.spawnParticles(ParticleTypes.SQUID_INK, 
                entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(), 
                20, 0.5, 0.5, 0.5, 0.1);

            // 2. Play Extinguish Sound
            serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);

            // 3. Find Power to get duration
            int duration = 100; // Default 5s
            List<TrueInvisibilityAbilityPower> powers = PowerHolderComponent.getPowers(entity, TrueInvisibilityAbilityPower.class);
            if (!powers.isEmpty()) {
                duration = powers.get(0).getEffectDuration();
            }

            // 4. Apply True Invisibility
            entity.addStatusEffect(new StatusEffectInstance(SscAddon.TRUE_INVISIBILITY, duration, 0, false, false, true));

            // 5. Notify Player
            if (entity instanceof PlayerEntity player) {
                // player.sendMessage(Text.of("§b§lInvisibility Active! (Press again to Strike)"), true);
            }
        }
    }
}
