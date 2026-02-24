package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class AllaySPTotem {

    private AllaySPTotem() {
        // Utility class
    }

    private static final String ACTIVE_TAG = "ssc_totem_active";
    private static final double RANGE = 20.0;

    public static void init() {
        UseItemCallback.EVENT.register(AllaySPTotem::onUseItem);
        // Register tick event to handle deactivation if form changes
        ServerTickEvents.START_WORLD_TICK.register(AllaySPTotem::onWorldTick);
    }
    
    private static void onWorldTick(World world) {
        if (world.isClient) return;
        
        // Every 20 ticks (1 second) check active totems to save performance?
        // Or check every tick for instant feedback? 
        // Form change might happen instantly. 
        // A small delay is acceptable, e.g., 10 ticks.
        if (world.getTime() % 10 != 0) return;

        for (PlayerEntity player : world.getPlayers()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // If player is NOT SP Allay, ensure they have no active totems
                if (!isSpAllay(serverPlayer)) {
                    deactivateAllTotems(serverPlayer);
                }
            }
        }
    }

    private static void deactivateAllTotems(ServerPlayerEntity player) {
        // Check main inventory and offhand
        boolean deactivatedAny = false;
        
        // Check main inventory
        for (ItemStack stack : player.getInventory().main) {
            if (isActiveTotem(stack)) {
                deactivateTotem(stack);
                deactivatedAny = true;
            }
        }
        
        // Check offhand
        for (ItemStack stack : player.getInventory().offHand) {
            if (isActiveTotem(stack)) {
                deactivateTotem(stack);
                deactivatedAny = true;
            }
        }
        
        if (deactivatedAny) {
            player.sendMessage(Text.translatable("message.ssc_addon.totem.deactivated"), true);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
    }

    private static void deactivateTotem(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.remove(ACTIVE_TAG);
        if (nbt.contains("Enchantments")) {
             nbt.remove("Enchantments");
        }
        if (nbt.contains("HideFlags")) {
             nbt.remove("HideFlags");
        }
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, net.minecraft.world.World world, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        ItemStack stack = player.getStackInHand(hand);
        
        // Only function for Totem of Undying
        if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
            return TypedActionResult.pass(stack);
        }

        // Must be SP Allay locally checked
        if (!isSpAllay(player)) {
            return TypedActionResult.pass(stack);
        }

        // Toggle Active State
        NbtCompound nbt = stack.getOrCreateNbt();
        boolean isActive = nbt.getBoolean(ACTIVE_TAG);
        
        if (isActive) {
            // Deactivate
            nbt.remove(ACTIVE_TAG);
            
            // Remove glint
            if (nbt.contains("Enchantments")) {
                 nbt.remove("Enchantments");
            }
            if (nbt.contains("HideFlags")) {
                 nbt.remove("HideFlags");
            }
            
            player.sendMessage(Text.translatable("message.ssc_addon.totem.deactivated"), true);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
        } else {
            // Activate
            nbt.putBoolean(ACTIVE_TAG, true);
            
            // Add Glint
            if (!nbt.contains("Enchantments")) {
                 NbtList enchantments = new NbtList();
                 NbtCompound unbreaking = new NbtCompound();
                 unbreaking.putString("id", "minecraft:unbreaking");
                 unbreaking.putShort("lvl", (short)1);
                 enchantments.add(unbreaking);
                 nbt.put("Enchantments", enchantments);
                 nbt.putInt("HideFlags", 1); // Hide enchantments
            }
            
            player.sendMessage(Text.translatable("message.ssc_addon.totem.activated"), true);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 2.0f);
        }
        
        return TypedActionResult.success(stack);
    }

    /**
     * Called by Mixin when an entity would die or use a totem.
     * @param entity The entity attempting to use a totem.
     * @return true if an Allay SP totem was used and prevented death.
     */
    public static boolean tryUseAllayTotem(LivingEntity entity) {
        if (entity.getWorld().isClient) return false;

        // Get nearby players within range
        Box box = entity.getBoundingBox().expand(RANGE);
        List<PlayerEntity> nearbyPlayers = entity.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p instanceof ServerPlayerEntity);

        for (PlayerEntity player : nearbyPlayers) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // a. Check if they are SP Allay
            if (!isSpAllay(serverPlayer)) continue;

            // b. Check whitelist
            // If entity == serverPlayer (self), we SKIP waitlist check (always allowed to save self with carried totem)
            // But wait, if they have totem in hand, vanilla logic handles it. If in inventory, we handle it via Mixed logic.
            // If entity != serverPlayer (ally), check whitelist.
            if (entity != serverPlayer) {
                java.util.Set<String> tags = serverPlayer.getCommandTags();
                boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));
                
                if (whitelistEmpty) {
                    // If whitelist is empty, ONLY other PLAYERS can benefit
                    if (!(entity instanceof PlayerEntity)) continue;
                } else {
                    // If whitelist is not empty, only whitelisted entities benefit
                    if (!AllaySPGroupHeal.shouldHeal(entity, tags)) continue;
                }
            }
            
            // c. Check inventory for Active Totem
            ItemStack activeTotem = findActiveTotem(serverPlayer);
            
            if (!activeTotem.isEmpty()) {
                // d. Consume totem
                activeTotem.decrement(1);
                
                // e. Trigger Effect on DYING ENTITY
                entity.setHealth(1.0F);
                entity.clearStatusEffects();
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1)); // 5 seconds
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1)); // 45 seconds
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0)); // 40 seconds
                
                // Visuals: Totem of Undying particle/sound
                entity.getWorld().sendEntityStatus(entity, (byte)35);
                
                // Notify SP Allay
                serverPlayer.sendMessage(Text.translatable("message.ssc_addon.totem.triggered", entity.getDisplayName()), true);
                
                return true; // Prevent death
            }
        }

        return false; // Did not prevent death
    }

    private static ItemStack findActiveTotem(ServerPlayerEntity player) {
        // Check hands
        if (isActiveTotem(player.getMainHandStack())) return player.getMainHandStack();
        if (isActiveTotem(player.getOffHandStack())) return player.getOffHandStack();
        
        // Check inventory main
        for (ItemStack stack : player.getInventory().main) {
            if (isActiveTotem(stack)) return stack;
        }
        
        return ItemStack.EMPTY;
    }

    private static boolean isActiveTotem(ItemStack stack) {
        // Check if item is Totem and has active tag
        return !stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING) && stack.hasNbt() && stack.getNbt().getBoolean(ACTIVE_TAG);
    }
    
    private static boolean isSpAllay(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            try {
                return PowerHolderComponent.KEY.get(serverPlayer).getPowers().stream()
                        .anyMatch(p -> p.getType().getIdentifier().getNamespace().equals("my_addon") && p.getType().getIdentifier().getPath().contains("form_allay_sp"));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
