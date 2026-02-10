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

public class FrostAmuletItem extends TrinketItem {
    public FrostAmuletItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component != null) {
                PlayerFormBase currentForm = component.getCurrentForm();
                if (currentForm != null && currentForm.FormID != null) {
                    return currentForm.FormID.equals(new Identifier("my_addon", "snow_fox_sp"));
                }
            }
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        // "霜之护符"
        // 远程形态的法术冰球在命中后拥有一次追踪效果...
        // tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.2").formatted(Formatting.BLUE));
        tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.exclusive").formatted(Formatting.AQUA));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
