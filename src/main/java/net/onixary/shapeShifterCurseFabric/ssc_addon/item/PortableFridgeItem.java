package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PortableFridgeItem extends TrinketItem {

    public static final int MAX_CHARGE = 64;

    public PortableFridgeItem(Settings settings) {
        super(settings);
    }

    public static int getCharge(ItemStack stack) {
        if (!stack.hasNbt()) return 0;
	    if (stack.getNbt() != null) {
		    return stack.getNbt().getInt("Charge");
	    }
	    return 0;
    }

    public static void setCharge(ItemStack stack, int amount) {
        stack.getOrCreateNbt().putInt("Charge", Math.max(0, Math.min(amount, MAX_CHARGE)));
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
             PlayerFormComponent formComponent = RegPlayerFormComponent.PLAYER_FORM.get(player);
             if (formComponent != null && formComponent.getCurrentForm() != null) {
                 return formComponent.getCurrentForm().FormID.equals(new Identifier("my_addon", "snow_fox_sp"));
             }
        }
        return false;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
             user.sendMessage(Text.translatable("item.ssc_addon.portable_fridge.charge", getCharge(user.getStackInHand(hand)), MAX_CHARGE), true);
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity.getWorld().isClient) return;
        
        // 1. Logic: Refill Launcher every 0.5s (10 ticks)
        if (entity.age % 10 == 0 && entity instanceof PlayerEntity player) {
            int currentCharge = getCharge(stack);
            
            if (currentCharge > 0) {
                // Find Snowball Launcher in inventory
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack invStack = player.getInventory().getStack(i);
                    if (invStack.getItem() instanceof SnowballLauncherItem) {
                        int currentAmmo = SnowballLauncherItem.getAmmo(invStack);
                        if (currentAmmo < SnowballLauncherItem.MAX_AMMO) {
                            SnowballLauncherItem.setAmmo(invStack, currentAmmo + 1);
                            setCharge(stack, currentCharge - 1);
                            // Only refill one at a time per tick cycle
                            break; 
                        }
                    }
                }
            }
        }

        // 2. Logic: Self-regenerate 1 charge every 2s (40 ticks)
        if (entity.age % 40 == 0) {
            int currentCharge = getCharge(stack);
            if (currentCharge < MAX_CHARGE) {
                setCharge(stack, currentCharge + 1);
            }
        }
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * getCharge(stack) / MAX_CHARGE);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0x00FFFF; // Cyan color for ice/snow
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc_addon.portable_fridge.desc").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.ssc_addon.portable_fridge.status", getCharge(stack), MAX_CHARGE).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ssc_addon.portable_fridge.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
