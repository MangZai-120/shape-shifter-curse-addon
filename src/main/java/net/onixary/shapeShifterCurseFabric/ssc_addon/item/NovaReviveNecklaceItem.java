/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 朔望专属项链：强化九命复活（回血更多、无敌更久、复活瞬间震退并减速周围敌人）。效果在 NineLivesManager 生效。 */
public class NovaReviveNecklaceItem extends AccessoryItem {
    public NovaReviveNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
        // 朔望专属；登录装载瞬间（age==0）宽容放行，由 AddonAccessoryGuard.tick 兜底卸下
        return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity,
                e -> FormUtils.isForm(e, FormIdentifiers.OCELOT_NOVA));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc_addon.nova_revive_necklace.desc").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.ssc_addon.nova_revive_necklace.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
