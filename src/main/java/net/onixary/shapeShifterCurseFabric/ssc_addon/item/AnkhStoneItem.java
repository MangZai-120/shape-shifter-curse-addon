package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.additional_power.VirtualTotemPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 安卡纹石 - SP阿努比斯之狼专属饰品（戒指槽）
 * 复活被动触发时：消除凋零和虚弱效果、减少80%冷却、物品消耗并播放碎裂音效
 */
public class AnkhStoneItem extends TrinketItem {

	public AnkhStoneItem(Settings settings) {
		super(settings);
	}

	/**
	 * 复活触发后的安卡纹石效果处理，由 AnkhStoneTotemMixin 调用
	 */
	public static void onRevival(LivingEntity entity) {
		if (!(entity instanceof ServerPlayerEntity player)) return;
		if (!FormUtils.isAnubisWolfSP(player)) return;

		// 检查是否装备了安卡纹石
		Optional<TrinketComponent> trinketOpt = TrinketsApi.getTrinketComponent(player);
		if (trinketOpt.isEmpty()) return;
		TrinketComponent component = trinketOpt.get();
		if (!component.isEquipped(SscAddon.ANKH_STONE)) return;

		// 消除凋零和虚弱效果（保留火焰抗性）
		player.removeStatusEffect(StatusEffects.WITHER);
		player.removeStatusEffect(StatusEffects.WEAKNESS);

		// 减少 VirtualTotemPower 冷却 80%
		List<VirtualTotemPower> powers = PowerHolderComponent.getPowers(player, VirtualTotemPower.class);
		for (VirtualTotemPower power : powers) {
			int remaining = power.getRemainingTicks();
			int reduction = (int) (remaining * 0.8);
			power.modify(-reduction);
			PowerHolderComponent.syncPower(player, power.getType());
		}

		// 消耗安卡纹石（只消耗第一个）
		component.getEquipped(SscAddon.ANKH_STONE).stream().findFirst().ifPresent(pair -> pair.getRight().decrement(1));

		// 播放物品碎裂音效
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_BREAK, player.getSoundCategory(), 1.0f, 1.0f);
	}

	/**
	 * 注册安卡纹石到沙漠神殿战利品表（15%概率，1-2个）
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (id.equals(new Identifier("minecraft", "chests/desert_pyramid"))) {
				LootPool.Builder poolBuilder = LootPool.builder()
						.rolls(UniformLootNumberProvider.create(1.0f, 2.0f))
						.conditionally(RandomChanceLootCondition.builder(0.15f).build())
						.with(ItemEntry.builder(SscAddon.ANKH_STONE));
				tableBuilder.pool(poolBuilder);
			}
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isAnubisWolfSP(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.ankh_stone.tooltip_1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.ankh_stone.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.ankh_stone.tooltip_3").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.ankh_stone.tooltip_4").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.ankh_stone.tooltip_5").formatted(Formatting.RED));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
