package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlueFireAmuletItem extends TrinketItem {
    public BlueFireAmuletItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component != null) {
                PlayerFormBase currentForm = component.getCurrentForm();
                if (currentForm != null && currentForm.FormID != null) {
                    return currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_sp")) ||
                           currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
                }
            }
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc_addon.blue_fire_amulet.tooltip_1").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.ssc_addon.blue_fire_amulet.tooltip_2").formatted(Formatting.GRAY));
        // tooltip.add(Text.translatable("item.ssc_addon.blue_fire_amulet.tooltip.exclusive").formatted(Formatting.AQUA));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
