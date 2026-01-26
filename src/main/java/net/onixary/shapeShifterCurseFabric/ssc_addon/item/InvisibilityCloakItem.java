package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class InvisibilityCloakItem extends TrinketItem {
    public InvisibilityCloakItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component != null) {
                PlayerFormBase currentForm = component.getCurrentForm();
                if (currentForm != null && currentForm.FormID != null) {
                    return currentForm.FormID.equals(new Identifier("my_addon", "wild_cat_sp"));
                }
            }
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.tooltip").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.special").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
