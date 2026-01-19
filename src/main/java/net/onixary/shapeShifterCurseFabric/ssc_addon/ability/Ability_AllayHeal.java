package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SpAllayMana;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class Ability_AllayHeal {

    // Using Maps instead of NBT for runtime data storage to avoid NBT/Mixin complexity
    private static final Map<UUID, Long> LAST_ACTIVE_TICK = new HashMap<>();
    private static final Map<UUID, Long> START_TICK = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    public static void onHold(ServerPlayerEntity player) {
        long currentTick = player.getWorld().getTime();
        UUID uuid = player.getUuid();
        
        // Update last active tick
        LAST_ACTIVE_TICK.put(uuid, currentTick);

        // Check initialization
        if (!START_TICK.containsKey(uuid)) {
            // Check cooldown
            long cooldown = COOLDOWN_UNTIL.getOrDefault(uuid, 0L);
            if (currentTick < cooldown) {
                // Cooldown active, do nothing
                return;
            }

            // Start ability
            START_TICK.put(uuid, currentTick);
            player.sendMessage(Text.translatable("message.ssc_addon.allay_heal_on").setStyle(Style.EMPTY.withColor(Formatting.GREEN)), true);
        }
    }

    public static void tick(ServerPlayerEntity player) {
        long currentTick = player.getWorld().getTime();
        UUID uuid = player.getUuid();

        long lastActiveTick = LAST_ACTIVE_TICK.getOrDefault(uuid, 0L);
        long startTick = START_TICK.getOrDefault(uuid, 0L);
        
        // If startTick is 0 (or not present), ability is not active
        if (startTick == 0) {
            return;
        }

        // Check if button was released (no signal for > 2 ticks)
        if (currentTick - lastActiveTick > 2) {
             // Stop ability
            player.sendMessage(Text.translatable("message.ssc_addon.allay_heal_end").setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
            START_TICK.remove(uuid);
            COOLDOWN_UNTIL.put(uuid, currentTick + 60); // 3 seconds cooldown
            return;
        }

        // Check duration (7 seconds = 140 ticks)
        if (currentTick - startTick > 140) {
            // Stop ability (Time's up)
            player.sendMessage(Text.translatable("message.ssc_addon.allay_heal_off").setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
            START_TICK.remove(uuid);
            COOLDOWN_UNTIL.put(uuid, currentTick + 60); // 3 seconds cooldown
            return;
        }

        // Logic every 7 ticks (0.35s)
        if (currentTick % 7 == 0) {
            ManaComponent manaComponent = ManaUtils.getManaComponent(player);
            if (manaComponent.getMana() < 5) {
               // Not enough mana, stop
               player.sendMessage(Text.translatable("message.ssc_addon.not_enough_mana").setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
               START_TICK.remove(uuid);
               return; 
            }
            manaComponent.consumeMana(5.0);

            // Healing logic
            double radius = 25.0;
            Box box = new Box(player.getBlockPos()).expand(radius);
            List<PlayerEntity> nearbyPlayers = player.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p.distanceTo(player) <= radius);

            // Whitelist check
            // PlayerFormBase form = FormAbilityManager.getForm(player); // Corrected method name
            // Set<String> whitelist = form != null ? form.getNullableWhitelist() : null; // Whitelist not available in PlayerFormBase

            for (PlayerEntity target : nearbyPlayers) {
                if (target == player) continue;
                // if (whitelist != null && !whitelist.contains(target.getName().getString())) continue;

                target.heal(1.0f);
            }
            
            // Particles
             if (player.getWorld() instanceof ServerWorld serverWorld) {
                // Spawn sphere particles
                for (int i = 0; i < 50; i++) {
                     double phi = Math.random() * 2 * Math.PI;
                     double costheta = Math.random() * 2 - 1;
                     // double u = Math.random();
                     double theta = Math.acos(costheta);
                     // double r = radius * Math.cbrt(u);
                     
                     // Show boundary
                     double r = radius; 
                     
                     double x = r * Math.sin(theta) * Math.cos(phi);
                     double y = r * Math.sin(theta) * Math.sin(phi);
                     double z = r * Math.cos(theta);
                     
                     serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX() + x, player.getY() + y, player.getZ() + z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}