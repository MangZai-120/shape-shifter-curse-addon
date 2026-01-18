package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PortableMoisturizerItem extends Item {
    
    // 1.5 hours = 90 minutes = 90 * 60 = 5400 seconds
    public static final int MAX_CHARGE = 5400;
    
    public PortableMoisturizerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        // Toggle Active State
        boolean isActive = isActive(stack);
        setActive(stack, !isActive);
        
        if (!world.isClient) {
            user.sendMessage(Text.translatable(!isActive ? 
                "message.ssc_addon.moisturizer.on" : 
                "message.ssc_addon.moisturizer.off"), true);
        }
        
        return TypedActionResult.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;
        humidifyLogic(stack, world, player);
    }

    private void humidifyLogic(ItemStack stack, World world, PlayerEntity player) {
        // 1. Check if user is in Axolotl SP form
        PlayerFormComponent formComponent = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (formComponent.getCurrentForm() == null || 
            !formComponent.getCurrentForm().FormID.equals(new Identifier("my_addon", "axolotl_sp"))) {
            // Automatically turn off if not in correct form
            if (isActive(stack)) {
                setActive(stack, false);
            }
            return;
        }

        // 2. Logic when Active
        if (isActive(stack)) {
            int currentCharge = getCharge(stack);
            
            if (currentCharge > 0) {
                // Run every second (20 ticks)
                if (world.getTime() % 20 == 0) {
                    // Consume 1 charge
                    setCharge(stack, currentCharge - 1);
                    
                    // Recover 2% Moisture (using Air as moisture)
                    int maxAir = player.getMaxAir();
                    int currentAir = player.getAir();
                    int recoveryAmount = (int)(maxAir * 0.02); // 2% of max (e.g. 6 air points)
                    
                    // Only increase if needed
                    if (currentAir < maxAir) {
                        player.setAir(Math.min(currentAir + recoveryAmount, maxAir));
                    }
                }
            } else {
                // Charge is 0, turn off
                setActive(stack, false);
                player.sendMessage(Text.translatable("message.ssc_addon.moisturizer.empty"), true);
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        // Status
        boolean active = isActive(stack);
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.status")
                .append(Text.translatable(active ? "options.on" : "options.off").formatted(active ? Formatting.GREEN : Formatting.RED)));
        
        // Charge
        int charge = getCharge(stack);
        int totalSeconds = charge; // 1 charge = 1 second
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.charge", 
                String.format("%02d:%02d", minutes, seconds), 
                "10:00").formatted(Formatting.AQUA));
                
        // Instructions
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.usage").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.refill").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.exclusive").formatted(Formatting.DARK_PURPLE));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return isActive(stack);
    }

    // NBT Helpers
    private boolean isActive(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        return nbt.getBoolean("Active");
    }

    private void setActive(ItemStack stack, boolean active) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean("Active", active);
    }

    private int getCharge(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (!nbt.contains("Charge")) {
            return 0; 
        }
        return nbt.getInt("Charge");
    }

    private void setCharge(ItemStack stack, int charge) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("Charge", Math.max(0, Math.min(charge, MAX_CHARGE)));
    }
    
    // Used by Recipe to set full charge
    public static void setFullCharge(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("Charge", MAX_CHARGE);
    }
}
