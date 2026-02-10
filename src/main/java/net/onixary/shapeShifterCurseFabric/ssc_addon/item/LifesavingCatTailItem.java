package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

// Loot Table imports
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;

import java.util.List;

public class LifesavingCatTailItem extends Item {
    public LifesavingCatTailItem(Settings settings) {
        super(settings);
    }
    
    public static void registerLootTable() {
         LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            // Add Lifesaving Cat Tail to Cat drops (1% chance)
            if (id.equals(new Identifier("minecraft", "entities/cat"))) {
                LootPool.Builder poolBuilder = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0f))
                        .conditionally(net.minecraft.loot.condition.RandomChanceLootCondition.builder(0.01f).build())
                        .with(ItemEntry.builder(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.LIFESAVING_CAT_TAIL));
                tableBuilder.pool(poolBuilder);
            }
         });
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        // "保命猫尾"
        // 装备到腰带栏生效，无法修补、叠加、附魔、交易
        // 在sp野猫受到致命伤害时免疫一次死亡...
        tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.2").formatted(Formatting.BLUE));
        tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
