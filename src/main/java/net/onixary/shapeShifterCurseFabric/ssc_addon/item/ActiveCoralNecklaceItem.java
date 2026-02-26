package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ActiveCoralNecklaceItem extends TrinketItem {
    public ActiveCoralNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        /*
        // 旧代码
        if (entity instanceof PlayerEntity player) {
            PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component != null) {
                PlayerFormBase currentForm = component.getCurrentForm();
                if (currentForm != null && currentForm.FormID != null) {
                    return currentForm.FormID.equals(new Identifier("my_addon", "axolotl_sp"));
                }
            }
        }
        return false;
        */

        // 新代码
        return FormUtils.isAxolotlSP(entity);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc_addon.active_coral_necklace.desc").formatted(Formatting.BLUE));
        tooltip.add(Text.translatable("item.ssc_addon.active_coral_necklace.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
