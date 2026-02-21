package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import io.github.apace100.apoli.component.PowerHolderComponent;
import java.util.List;

public class AllaySPTotem {

    private static final String ACTIVE_TAG = "ssc_totem_active";
    private static final double RANGE = 20.0;
    
    // Identifier for the SP Allay form
    private static final Identifier ALLAY_SP_ID = new Identifier("my_addon", "allay_sp");

    public static void init() {
        UseItemCallback.EVENT.register(AllaySPTotem::onUseItem);
        ServerLivingEntityEvents.ALLOW_DEATH.register(AllaySPTotem::onAllowDeath);
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
            
            // Remove glint (Enchantments and HideFlags) if checking specific structure
            // Simplified: Remove if existing
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

    private static boolean onAllowDeath(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (entity.getWorld().isClient) return true;

        // Get nearby players
        Box box = entity.getBoundingBox().expand(RANGE);
        List<PlayerEntity> nearbyPlayers = entity.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p instanceof ServerPlayerEntity);

        for (PlayerEntity player : nearbyPlayers) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // a. Check if they are SP Allay
            if (!isSpAllay(serverPlayer)) continue;

            // b. Check whitelist only if entity is NOT the player themselves (or keep logic consistent)
            // Logic: Allay protects OTHERS or SELVES? Usually others. The prompt says "Protect whitelisted players".
            // Since AllaySPGroupHeal handles whitelist logic, let's use it.
            // Note: If AllaySPGroupHeal.shouldHeal returns true, it means they are whitelisted (or owner/friend).
            if (entity != serverPlayer && !AllaySPGroupHeal.shouldHeal(entity, serverPlayer.getCommandTags())) continue;
            
            // Allow self-protection: If entity IS the serverPlayer, we skip the basic check (since an SP Allay should protect itself if it has the totem active)
            // But wait, the standard Totem behavior already protects the player holding it.
            // Why doesn't it trigger?
            // "ServerLivingEntityEvents.ALLOW_DEATH" is called. If we return true, death happens (unless vanilla totem triggers).
            // Vanilla totem logic is inside LivingEntity.tryUseTotem which is called BEFORE death actually happens to check.
            // But `ALLOW_DEATH` event is from Fabric API, typically fired if damage would be fatal.
            // If the player holds the totem in hand, vanilla logic should trigger FIRST.
            // UNLESS our totem is in inventory (not hands). Our feature allows "Consume totem from SP Allay inventory".
            // So if it's in inventory (not main/offhand), vanilla won't see it. We must handle it.
            
            // So, if entity == serverPlayer, we definitely want to protect them if they have a totem in inventory (or hand if vanilla failed?)
            // Actually, if it's in hand, vanilla handles it. If it's in inventory, we handle it.
            
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
                // Using entity.getName() or entity.getDisplayName()
                serverPlayer.sendMessage(Text.translatable("message.ssc_addon.totem.triggered", entity.getDisplayName()), true);
                
                return false; // Prevent death
            }
        }

        return true; // Allow death
    }

    private static ItemStack findActiveTotem(ServerPlayerEntity player) {
        // Check hands
        if (isActiveTotem(player.getMainHandStack())) return player.getMainHandStack();
        if (isActiveTotem(player.getOffHandStack())) return player.getOffHandStack();
        
        // Check inventory main
        for (ItemStack stack : player.getInventory().main) {
            if (isActiveTotem(stack)) return stack;
        }
        // Check inventory offhand is already done in getOffHandStack? No, inventory.offHand is separated usually.
        // But getOffHandStack() covers it.
        
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
                        .anyMatch(p -> p.getType().getIdentifier().toString().contains("form_allay_sp"));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
