package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class AllaySPPortableBeacon {

    private static final String ACTIVE_TAG = "ssc_beacon_active";
    private static final double RANGE = 20.0;
    private static final int COST_INTERVAL = 60; // 3 seconds (60 ticks)
    private static final Identifier MANA_RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");
    private static final Identifier MANA_COOLDOWN_ID = new Identifier("my_addon", "form_allay_sp_mana_cooldown_resource");

    public static void init() {
        UseItemCallback.EVENT.register(AllaySPPortableBeacon::onUseItem);
        ServerTickEvents.START_WORLD_TICK.register(AllaySPPortableBeacon::onWorldTick);
    }

    private static void onWorldTick(World world) {
        if (world.isClient) return;

        // Iterate over all players in the current world (dimension)
        for (PlayerEntity player : world.getPlayers()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                tickPlayer(serverPlayer);
            }
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        // Iterate over inventory to find first active beacon
        ItemStack activeBeacon = ItemStack.EMPTY;

        // Check main inventory (including hotbar) and offhand
        // Priority: MainHand -> OffHand -> Inventory slots
        
        if (isActiveBeacon(player.getMainHandStack())) {
            activeBeacon = player.getMainHandStack();
        } else if (isActiveBeacon(player.getOffHandStack())) {
            activeBeacon = player.getOffHandStack();
        } else {
            // Check other slots
            for (ItemStack stack : player.getInventory().main) {
                if (isActiveBeacon(stack)) {
                    activeBeacon = stack;
                    break;
                }
            }
        }

        if (!activeBeacon.isEmpty()) {
            processActiveBeacon(player, activeBeacon);
        }
    }

    private static boolean isActiveBeacon(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.BEACON) && stack.hasNbt() && stack.getNbt().getBoolean(ACTIVE_TAG);
    }

    private static void processActiveBeacon(ServerPlayerEntity player, ItemStack stack) {
        // Validation: Verify player is still SP Allay. If not, deactivate.
        // This prevents transferring activated beacons to non-SP players.
        if (!isSpAllay(player)) {
            deactivateBeacon(player, stack);
            return;
        }

        // 1. Check Mana (Every 3 seconds / 60 ticks)
        if (player.getWorld().getTime() % COST_INTERVAL == 0) {
            int currentMana = getManaValue(player);
            // Need >= 1 to sustain.
            if (currentMana >= 1) {
                // Consume 1 mana
                setManaValue(player, currentMana - 1);
                // Trigger cooldown (prevent regen) for slightly longer than interval to ensure no gap
                // 3.5 seconds = 70 ticks
                triggerManaCooldown(player, 70); 
            } else {
                // Not enough mana, deactivate
                deactivateBeacon(player, stack);
                player.sendMessage(Text.translatable("message.ssc_addon.beacon.depleted"), true);
                return; // Stop processing effects
            }
        }
        
        // Ensure no regen happens while active (constantly refresh cooldown if needed, or just rely on consumption trigger)
        // Since we trigger consumption every 60 ticks and set cooldown to 70 ticks, it should be covered.
        // But to be safe, we can refresh it frequently.
        if (player.getWorld().getTime() % 20 == 0) {
             triggerManaCooldown(player, 40);
        }

        // 2. Apply Effects (Every 4 seconds / 80 ticks)
        // Haste II (Amplifier 1), Duration 10 seconds (200 ticks)
        // Refresh rate < Duration to keep it continuous.
        if (player.getWorld().getTime() % 80 == 0) { 
             applyEffects(player);
        }
    }

    private static void applyEffects(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(RANGE);
        List<LivingEntity> entities = player.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) <= RANGE * RANGE);

        for (LivingEntity entity : entities) {
            // Apply Haste II to whitelisted entities OR all friendly entities if whitelist is empty
            
            boolean shouldApply = false;
            
            // Check whitelist logic first
            if (AllaySPGroupHeal.isInWhitelist(player, entity)) {
                shouldApply = true;
            }
            // Explicitly allow self-application (though usually covered by whitelist logic)
            else if (entity == player) {
                shouldApply = true;
            }

            if (shouldApply) {
                // Effect: Haste, Duration: 200 ticks (10s), Amplifier: 1 (Haste II)
                // ambient=true, visible=true, showIcon=true
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 200, 1, true, true, true));
            }
        }
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.BEACON)) {
                
                // Toggle logic:
                // If sneaking -> Do nothing (let vanilla behavior happen, i.e., place block)
                // If not sneaking -> Toggle activation (and consume item use)
                
                if (!player.isSneaking()) {
                    if (isSpAllay(serverPlayer)) {
                        toggleBeacon(serverPlayer, stack);
                        return TypedActionResult.success(stack); // Consume the action so block is not placed
                    }
                }
            }
        }
        // Pass to allow vanilla behavior (or other mods)
        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static boolean isSpAllay(ServerPlayerEntity player) {
        // Check for any power containing "form_allay_sp" in its ID
        // This relies on the power structure being consistent with "form_allay_sp" naming convention
        try {
            return PowerHolderComponent.KEY.get(player).getPowers().stream()
                    .anyMatch(p -> p.getType().getIdentifier().getNamespace().equals("my_addon") && p.getType().getIdentifier().getPath().contains("form_allay_sp"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void toggleBeacon(ServerPlayerEntity player, ItemStack stack) {
        if (isActiveBeacon(stack)) {
            deactivateBeacon(player, stack);
        } else {
            activateBeacon(player, stack);
        }
    }

    private static void activateBeacon(ServerPlayerEntity player, ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(ACTIVE_TAG, true);
        
        // Add dummy enchantment to force glint
        if (!nbt.contains("Enchantments", NbtElement.LIST_TYPE)) {
            NbtList enchantments = new NbtList();
            NbtCompound dummyEnchant = new NbtCompound();
            dummyEnchant.putString("id", "minecraft:unbreaking");
            dummyEnchant.putShort("lvl", (short) 1);
            enchantments.add(dummyEnchant);
            nbt.put("Enchantments", enchantments);
            
            // Hide enchantments tooltip so player doesn't see "Unbreaking I"
            // HideFlags 1 hides enchantments
            nbt.putInt("HideFlags", 1);
        }

        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.ssc_addon.beacon.activated"), true);
    } 

    private static void deactivateBeacon(ServerPlayerEntity player, ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(ACTIVE_TAG, false);
        
        // Remove Enchantments only if we added the dummy unbreaking
        if (nbt.contains("Enchantments", NbtElement.LIST_TYPE)) {
            NbtList enchants = nbt.getList("Enchantments", NbtElement.COMPOUND_TYPE);
            // Check if it's just our dummy enchantment or empty list
            // If user enchanted it manually, we should probably keep it, but since beacon is not usually enchantable...
            // Safest logic: if it has HideFlags=1 and Unbreaking I, remove it.
            // Or simpler: remove "Enchantments" entirely if we put it there.
            // Given the context of a beacon item (unenchantable normally), safe to remove if it matches our pattern.
            
            // To be safe and simple: remove "Enchantments" and "HideFlags" entirely if they exist.
            // This resets it to a normal beacon block item.
            nbt.remove("Enchantments");
            nbt.remove("HideFlags");
        }

        player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.ssc_addon.beacon.deactivated"), true);
    }

    // ===== Mana resource read/write =====

    private static int getManaValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
            // Resource not found
        }
        return 0;
    }

    private static void setManaValue(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(Math.max(0, value));
                PowerHolderComponent.syncPower(player, powerType);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }

    private static void triggerManaCooldown(ServerPlayerEntity player, int ticks) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_COOLDOWN_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(ticks);
                PowerHolderComponent.syncPower(player, powerType);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }
}
