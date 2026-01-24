package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import io.github.apace100.apoli.power.Active;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import java.util.List;

public class TrueInvisibilityDashAbiltyPower extends ActiveCooldownPower {
    
    private int ticksSinceDash = 0;
    private boolean isWaitingForStun = false;
    private static final int COOLDOWN_TICKS = 240; // 12 seconds
    private static final int STUN_DELAY_TICKS = 20; // 1 second
    
    // Internal cooldown tracking (same as TrueInvisibilityAbilityPower)
    private long internalCooldownEndTime = 0;

    public TrueInvisibilityDashAbiltyPower(PowerType<?> type, LivingEntity entity, int cooldownAfter, HudRender hudRender, Active.Key key) {
        super(type, entity, cooldownAfter, hudRender, (e) -> {});
        this.setKey(key);
        this.setTicking(true);
    }
    
    /**
     * Check if internal cooldown is ready
     */
    public boolean isInternalCooldownReady() {
        return System.currentTimeMillis() >= internalCooldownEndTime;
    }
    
    /**
     * Apply internal cooldown (called from TrueInvisibilityAbilityPower)
     */
    public void applyInternalCooldown() {
        internalCooldownEndTime = System.currentTimeMillis() + (COOLDOWN_TICKS * 50); // 50ms per tick
    }
    
    /**
     * Get remaining cooldown in seconds for display
     */
    public int getRemainingCooldownSeconds() {
        long remaining = internalCooldownEndTime - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 1000.0);
    }

    @Override
    public boolean canUse() {
        // Can only use when invisible
        return entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY);
    }

    @Override
    public void onUse() {
        if (entity == null || entity.getWorld().isClient) return;

        // Double check: MUST be invisible to use this
        // Note: Apoli might call onUse without checking canUse() in some network contexts
        if (!this.canUse()) {
            return;
        }
        
        // Remove invisibility immediately
        entity.removeStatusEffect(SscAddon.TRUE_INVISIBILITY);

        // Apply 50% slow (Slowness III = -45%, close enough) for 1 second
        entity.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 20, 2, false, false, false));
        
        // Play Cat Hiss Sound (周围人能听见)
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
                SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 1.0f, 1.0f);
        
        // Dash forward 5 blocks
        float yaw = entity.getYaw();
        float f = -net.minecraft.util.math.MathHelper.sin(yaw * 0.017453292F);
        float g = net.minecraft.util.math.MathHelper.cos(yaw * 0.017453292F);
        entity.addVelocity(f * 1.5, 0.5, g * 1.5);
        entity.velocityModified = true;
        
        // Start waiting for 1 second to apply stun
        isWaitingForStun = true;
        ticksSinceDash = 0;
        
        // Set both cooldowns to 12 seconds
        applyInternalCooldown();
        
        // Also set the main ability cooldown
        List<TrueInvisibilityAbilityPower> mainPowers = PowerHolderComponent.getPowers(entity, TrueInvisibilityAbilityPower.class);
        for (TrueInvisibilityAbilityPower mainPower : mainPowers) {
            mainPower.applyUniversalCooldown();
        }
        
        if (entity instanceof PlayerEntity player) {
            player.sendMessage(Text.of("§6震慑冲刺!"), true);
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (entity == null || entity.getWorld().isClient) return;
        
        if (isWaitingForStun) {
            ticksSinceDash++;
            
            // Apply stun exactly 1 second (20 ticks) after dash
            if (ticksSinceDash >= STUN_DELAY_TICKS) {
                performStunEffect();
            }
        }
    }

    private void performStunEffect() {
        ServerWorld world = (ServerWorld) entity.getWorld();
        
        // Apply stun to nearby entities (5 blocks radius for 10 blocks diameter sphere)
        net.minecraft.util.math.Box box = entity.getBoundingBox().expand(5.0, 5.0, 5.0);
        world.getEntitiesByClass(LivingEntity.class, box, (e) -> {
            // Filter Logic:
            // 1. Not self
            if (e == entity) return false;
            
            // 2. Not other "Wild Cats" - check if they have the TrueInvisibility power
            // This includes all wild cats, whether visible or not
            if (PowerHolderComponent.getPowers(e, TrueInvisibilityAbilityPower.class).size() > 0) return false;
            if (PowerHolderComponent.getPowers(e, TrueInvisibilityDashAbiltyPower.class).size() > 0) return false;

            return e.distanceTo(entity) <= 5.0;
        })
        .forEach(target -> {
            // Apply Stun: 1.5s = 30 ticks
            target.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, 30, 0, false, false, true));
        });
        
        // Particle effect
        world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, 
            entity.getX(), entity.getY(), entity.getZ(), 15, 0.8, 0.2, 0.8, 0.1);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, 
            entity.getX(), entity.getY(), entity.getZ(), 10, 0.5, 0.1, 0.5, 0.05);
        
        // Play additional sound when stun triggers
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5f, 1.5f);
        
        if (entity instanceof PlayerEntity player) {
            player.sendMessage(Text.of("§e震慑波动!"), true);
        }
        
        isWaitingForStun = false;
        ticksSinceDash = 0;
    }

    public static PowerFactory createFactory() {
        return new PowerFactory<>(new Identifier("my_addon", "true_invisibility_dash"),
            new SerializableData()
                .add("cooldown", SerializableDataTypes.INT, COOLDOWN_TICKS)
                .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
            data ->
                (type, player) -> {
                    return new TrueInvisibilityDashAbiltyPower(
                        type, 
                        player, 
                        data.getInt("cooldown"), 
                        data.get("hud_render"),
                        data.get("key")
                    );
                }
        ).allowCondition();
    }
}
