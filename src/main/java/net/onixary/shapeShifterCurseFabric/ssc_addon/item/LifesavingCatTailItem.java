package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LifesavingCatTailItem extends TrinketItem {
    
    public static final int MAX_COOLDOWN = 3600; // 3 minutes = 3600 ticks
    
    public LifesavingCatTailItem(Settings settings) {
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

    /**
     * 获取当前冷却剩余ticks (客户端使用)
     */
    private int getCooldownRemaining() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                float cooldownProgress = client.player.getItemCooldownManager().getCooldownProgress(this, 0.0f);
                // cooldownProgress: 1.0 = 刚开始冷却, 0.0 = 冷却完成
                return (int)(cooldownProgress * MAX_COOLDOWN);
            }
        } catch (Exception e) {
            // 服务端调用时可能出错，忽略
        }
        return 0;
    }
    
    /**
     * 判断是否在冷却中 (客户端使用)
     */
    private boolean isOnCooldown() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                return client.player.getItemCooldownManager().isCoolingDown(this);
            }
        } catch (Exception e) {
            // 服务端调用时可能出错，忽略
        }
        return false;
    }
    
    /**
     * 根据CD进度获取颜色 (红色->橙色->绿色)
     * progress: 0.0 = 刚开始冷却, 1.0 = 冷却完成
     */
    private int getColorForProgress(float progress) {
        if (progress < 0.33f) {
            // 红色区域 (0% - 33%)
            return 0xFF5555; // 红色
        } else if (progress < 0.66f) {
            // 橙色区域 (33% - 66%)
            return 0xFFAA00; // 橙色
        } else {
            // 绿色区域 (66% - 100%)
            return 0x55FF55; // 绿色
        }
    }
    
    /**
     * 根据CD进度获取Formatting颜色
     */
    private Formatting getFormattingForProgress(float progress) {
        if (progress < 0.33f) {
            return Formatting.RED;
        } else if (progress < 0.66f) {
            return Formatting.GOLD;
        } else {
            return Formatting.GREEN;
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        // 只有在冷却中才显示进度条
        return isOnCooldown();
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        // 返回0-13的值，表示进度条的长度
        // 冷却剩余越少，进度条越长
        int remaining = getCooldownRemaining();
        float progress = 1.0f - ((float)remaining / MAX_COOLDOWN);
        return Math.round(13.0f * progress);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        int remaining = getCooldownRemaining();
        float progress = 1.0f - ((float)remaining / MAX_COOLDOWN);
        return getColorForProgress(progress);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        // "保命猫尾"
        // 装备到腰带栏生效，无法修补、叠加、附魔、交易
        // 在sp野猫受到致命伤害时免疫一次死亡...
        // tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.2").formatted(Formatting.BLUE));
        
        // 显示CD状态
        if (isOnCooldown()) {
            int remainingTicks = getCooldownRemaining();
            int remainingSeconds = remainingTicks / 20;
            float progress = 1.0f - ((float)remainingTicks / MAX_COOLDOWN);
            Formatting color = getFormattingForProgress(progress);
            tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.cooldown", remainingSeconds).formatted(color));
        } else {
            tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.ready").formatted(Formatting.GREEN));
        }
        
        tooltip.add(Text.translatable("item.ssc_addon.lifesaving_cat_tail.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
