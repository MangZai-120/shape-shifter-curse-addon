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
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import java.util.List;

public class TrueInvisibilityAbilityPower extends ActiveCooldownPower {
    
    private final int effectDuration;
    private static final int COOLDOWN_TICKS = 240; // 12 seconds
    
    // Internal cooldown tracking (separate from parent class)
    private long internalCooldownEndTime = 0;
    
    private boolean wasInvisible = false;
    private boolean wasUsingItem = false;
    private boolean wasHandSwinging = false;

    public TrueInvisibilityAbilityPower(PowerType<?> type, LivingEntity entity, int cooldownAfter, int effectDuration, HudRender hudRender, Active.Key key) {
        super(type, entity, cooldownAfter, hudRender, (e) -> {});
        this.effectDuration = effectDuration;
        this.setKey(key);
        this.setTicking(true);
    }

    public int getEffectDuration() {
        return this.effectDuration;
    }

    @Override
    public void tick() {
        super.tick();
        
        if (entity == null || entity.getWorld().isClient) return;
        
        boolean isInvisible = entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY);
        boolean isPrecasting = entity.hasStatusEffect(SscAddon.PRE_INVISIBILITY);
        
        // Natural End Detection (Time expired - not from action break or key cancel)
        if (wasInvisible && !isInvisible && !isPrecasting) {
            // Effect expired naturally - apply 12s CD
            applyUniversalCooldown();
            // Play glass break sound for natural expiration
            entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            if (entity instanceof PlayerEntity player) {
                player.sendMessage(Text.of("§7隐身时间结束"), true);
            }
        }
        
        if (!isInvisible) {
            wasUsingItem = false;
            wasHandSwinging = false;
            wasInvisible = false;
            return;
        }
        
        // Particles while invisible
        if (entity.getRandom().nextFloat() < 0.15f) {
            ServerWorld serverWorld = (ServerWorld) entity.getWorld();
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SQUID_INK, 
                entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
                1, 0.3, 0.5, 0.3, 0.05);
        }
        
        // Check for actions that break invisibility
        boolean shouldBreak = false;
        
        // 1. Using item
        boolean isUsingItem = entity.isUsingItem();
        if (isUsingItem && !wasUsingItem) shouldBreak = true;
        wasUsingItem = isUsingItem;
        
        // 2. Hand swinging
        boolean isHandSwinging = entity.handSwinging;
        if (isHandSwinging && !wasHandSwinging) shouldBreak = true;
        wasHandSwinging = isHandSwinging;
        
        if (shouldBreak) {
            breakInvisibility(false); // false = action break (glass break sound)
            return;
        }
        
        wasInvisible = isInvisible; 
    }
    
    /**
     * Check if internal cooldown is ready
     */
    public boolean isInternalCooldownReady() {
        return System.currentTimeMillis() >= internalCooldownEndTime;
    }
    
    /**
     * Apply 12 second cooldown to both this power and the dash power
     */
    public void applyUniversalCooldown() {
        // Use real time for reliable cooldown
        internalCooldownEndTime = System.currentTimeMillis() + (COOLDOWN_TICKS * 50); // 50ms per tick
        
        // Also set dash ability cooldown
        List<TrueInvisibilityDashAbiltyPower> dashPowers = PowerHolderComponent.getPowers(entity, TrueInvisibilityDashAbiltyPower.class);
        for (TrueInvisibilityDashAbiltyPower dashPower : dashPowers) {
            dashPower.applyInternalCooldown();
        }
    }
    
    /**
     * Get remaining cooldown in seconds for display
     */
    public int getRemainingCooldownSeconds() {
        long remaining = internalCooldownEndTime - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 1000.0);
    }
    
    /**
     * Breaks invisibility with appropriate sound effect
     * @param byKey true if broken by pressing the key again (cat hiss), false if broken by action (glass break)
     */
    public void breakInvisibility(boolean byKey) {
        if (entity == null || entity.getWorld().isClient) return;
        
        if (!entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) return;
        
        // Remove invisibility effect
        entity.removeStatusEffect(SscAddon.TRUE_INVISIBILITY);
        wasInvisible = false;
        
        ServerWorld serverWorld = (ServerWorld) entity.getWorld();
        
        if (byKey) {
            // Key Cancel: Cat Hiss
            serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
                SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 1.0f, 1.0f);
            
            // Add Buffs: Guaranteed Crit & Speed II for 5 seconds
            entity.addStatusEffect(new StatusEffectInstance(SscAddon.GUARANTEED_CRIT, 100, 0, false, false, true));
            entity.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 100, 1, false, false, true));
            
            if (entity instanceof PlayerEntity player) {
                player.sendMessage(Text.of("§a隐身已主动解除，获得爆发增益!"), true);
            }
        } else {
            // Action Break: Glass Break
            serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            if (entity instanceof PlayerEntity player) {
                player.sendMessage(Text.of("§c隐身被打破!"), true);
            }
        }
        
        // Apply universal 12s cooldown ONLY when breaking invisibility
        applyUniversalCooldown();
    }

    @Override
    public boolean canUse() {
        // Always allow use - we handle cooldown logic in onUse
        return true;
    }

    @Override
    public void onUse() {
        if (entity == null || entity.getWorld().isClient) return;
        
        boolean isInvisible = entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY);
        boolean isPrecasting = entity.hasStatusEffect(SscAddon.PRE_INVISIBILITY);

        if (isInvisible) {
            // Already invisible - pressing key again cancels with cat hiss
            breakInvisibility(true); // true = key break (cat hiss)
        } else if (isPrecasting) {
            // Currently casting - do nothing
        } else {
            // Not invisible - try to cast
            if (isInternalCooldownReady()) {
                // Apply pre-invisibility (casting phase)
                entity.addStatusEffect(new StatusEffectInstance(SscAddon.PRE_INVISIBILITY, 20, 0, false, false, true));
                
                if (entity instanceof PlayerEntity player) {
                    player.sendMessage(Text.of("§7正在引导隐身..."), true);
                }
            } else {
                // On cooldown - show remaining time
                if (entity instanceof PlayerEntity player) {
                    int remaining = getRemainingCooldownSeconds();
                    player.sendMessage(Text.of("§c技能冷却中... " + remaining + "秒"), true);
                }
            }
        }
    }
    
    public static PowerFactory createFactory() {
        return new PowerFactory<>(new Identifier("my_addon", "true_invisibility"),
            new SerializableData()
                .add("cooldown", SerializableDataTypes.INT, COOLDOWN_TICKS)
                .add("duration", SerializableDataTypes.INT, 100)
                .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
            data ->
                (type, player) -> {
                    return new TrueInvisibilityAbilityPower(
                        type, 
                        player, 
                        data.getInt("cooldown"), 
                        data.getInt("duration"), 
                        data.get("hud_render"),
                        data.get("key")
                    );
                }
        ).allowCondition();
    }
}
