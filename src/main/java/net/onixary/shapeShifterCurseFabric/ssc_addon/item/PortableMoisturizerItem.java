package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
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
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // 对stack判空，尽管正常游戏时NPE的可能性很小
        if (stack == null){
            return TypedActionResult.fail(stack);
        }
        
        // Toggle Active State
        boolean isActive = isActive(stack);
	    boolean newState = !isActive; // 切换状态
        setActive(stack, !isActive);

		// 形态检查
	    PlayerFormComponent formComponent = RegPlayerFormComponent.PLAYER_FORM.get(player);
	    boolean isValidForm = formComponent != null &&
			    formComponent.getCurrentForm() != null &&
			    formComponent.getCurrentForm().FormID != null &&
			    formComponent.getCurrentForm().FormID.equals(new Identifier("my_addon", "axolotl_sp"));

	    if (newState && !isValidForm) {
		    // 想开启但形态不对
		    player.sendMessage(Text.translatable("message.ssc_addon.moisturizer.off"), true);
	    } else {
		    // 形态正确，或者想关闭
		    player.sendMessage(Text.translatable(newState ?
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
        if (formComponent == null ||
                formComponent.getCurrentForm() == null ||
                formComponent.getCurrentForm().FormID == null ||
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
                    // 考虑到有些模组有骚操作导致maxAir很小然后导致recoveryAmount为0，所以这里加上Math.ceil
                    int recoveryAmount = (int) Math.ceil(maxAir * 0.02); // 2% of max (e.g. 6 air points)
                    
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
        // 1 charge = 1 second
        int minutes = charge / 60;
        int seconds = charge % 60;
        
        // Calculate max time string dynamically
        int maxTotalSeconds = MAX_CHARGE;
        int maxMinutes = maxTotalSeconds / 60;
        int maxSeconds = maxTotalSeconds % 60;
        String maxTimeString = String.format("%02d:%02d", maxMinutes, maxSeconds);
        
        tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.charge", 
                String.format("%02d:%02d", minutes, seconds), 
                maxTimeString).formatted(Formatting.AQUA));
                
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
