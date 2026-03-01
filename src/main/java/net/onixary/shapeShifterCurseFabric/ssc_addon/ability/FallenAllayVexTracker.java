package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Tick handler for SP Fallen Allay vex tracking.
 * Checks if a player's summoned vexes are all dead/gone,
 * and if so, triggers the 20-second (400 tick) cooldown.
 *
 * Called once per server tick per player from the main tick handler.
 */
public class FallenAllayVexTracker {

    private FallenAllayVexTracker() {}

    public static void tick(ServerPlayerEntity player) {
        // Only process players who have active vexes
        if (!player.getCommandTags().contains("ssc_vex_active")) return;

        ServerWorld world = player.getServerWorld();
        String ownerTag = "owner:" + player.getUuidAsString();

        // Search for any surviving vexes owned by this player
        boolean hasVex = false;
        for (Entity e : world.getEntitiesByClass(VexEntity.class,
                player.getBoundingBox().expand(256.0), Entity::isAlive)) {
            if (e.getCommandTags().contains("ssc_fallen_allay_vex")
                    && e.getCommandTags().contains(ownerTag)) {
                hasVex = true;
                break;
            }
        }

        if (!hasVex) {
            // All vexes are gone — remove tracking tag and start 20s cooldown
            player.getCommandTags().remove("ssc_vex_active");
            try {
                PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
                PowerType<?> powerType = PowerTypeRegistry.get(
                        new Identifier("my_addon", "form_fallen_allay_sp_vex_cd"));
                Power power = component.getPower(powerType);
                if (power instanceof VariableIntPower vip) {
                    vip.setValue(400); // 20 seconds = 400 ticks
                    PowerHolderComponent.syncPower(player, power.getType());
                }
            } catch (Exception ignored) {
                // Power not found (player might not be Fallen Allay form)
            }
        }
    }
}
