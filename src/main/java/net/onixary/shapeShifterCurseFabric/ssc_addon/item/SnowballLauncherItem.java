package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.TrinketsApi;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SnowballLauncherItem extends Item {
    public static final int MAX_AMMO = 20;

    public SnowballLauncherItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // Start using (holding down)
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        // Fire every 7 ticks (approx 1.15x speed of Bottled Blizzard which is 8 ticks)
        if (usedTicks % 7 == 0) {
            fire(world, user, stack);
        }
    }
    
    private void fire(World world, LivingEntity entity, ItemStack stack) {
        int ammo = getAmmo(stack);
        boolean isCreative = false;
        
        if (entity instanceof PlayerEntity player) {
             isCreative = player.getAbilities().creativeMode;
        }

        if (ammo <= 0 && !isCreative) {
            if (entity instanceof PlayerEntity player) {
               player.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, 1.0F, 1.2F);
            }
            entity.stopUsingItem(); 
            return;
        }

        if (!world.isClient) {
             SnowballEntity snowball = new SnowballEntity(world, entity);
             snowball.setItem(new ItemStack(net.minecraft.item.Items.SNOWBALL));
             // 2.25F velocity
             snowball.setVelocity(entity, entity.getPitch(), entity.getYaw(), 0.0F, 2.25F, 1.0F);
             world.spawnEntity(snowball);
             
             world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_DISPENSER_LAUNCH, SoundCategory.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.2F + 0.9F));
             
             // Play Amethyst sound if Portable Fridge is equipped
             TrinketsApi.getTrinketComponent(entity).ifPresent(component -> {
                 if (component.isEquipped(SscAddon.PORTABLE_FRIDGE)) {
                     world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_FALL, SoundCategory.PLAYERS, 1.0F, 1.0F);
                 }
             });

             if (!isCreative) {
                 setAmmo(stack, ammo - 1);
             }
        }
    }

    public static int getAmmo(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null ? nbt.getInt("Ammo") : 0;
    }
    
    public static void setAmmo(ItemStack stack, int ammo) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("Ammo", Math.min(ammo, MAX_AMMO));
    }
    
    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.min(13, Math.round((float)getAmmo(stack) / (float)MAX_AMMO * 13.0F));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xA0E0FF; 
    }
    
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int ammo = getAmmo(stack);
        tooltip.add(Text.translatable("tooltip.ssc_addon.launcher.ammo", ammo, MAX_AMMO).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.ssc_addon.launcher.usage").formatted(Formatting.GOLD));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
