package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 毒液腺体（跳蛛专属头部饰品，jackcooper）。
 * <p><b>加强</b>：跳蛛施加的中毒效果等级 +1（毒牙/跳杀/毒液技能全部生效）。
 * <b>削弱</b>：中毒持续时间缩短至正常的 70%。</p>
 * <p>数值逻辑在施加侧（{@code VenomSkillManager} / {@code JumpKillManager} / 毒牙 power）：
 * 施加前经 {@link #ampBonus} / {@link #durationScale} 查询佩戴状态。</p>
 *
 * <p>获取途径：废弃矿井（矿车箱子）15% + 地牢 10%。</p>
 */
public class VenomGlandItem extends AccessoryItem {

	/** 中毒持续时间缩放（70%）。 */
	public static final float DURATION_SCALE = 0.7f;

	/** 废弃矿井（矿车箱子）战利品表 id。 */
	private static final net.minecraft.util.Identifier MINESHAFT_LOOT =
			new net.minecraft.util.Identifier("minecraft", "chests/abandoned_mineshaft");

	/** 地牢（怪物房间）战利品表 id。 */
	private static final net.minecraft.util.Identifier DUNGEON_LOOT =
			new net.minecraft.util.Identifier("minecraft", "chests/simple_dungeon");

	public VenomGlandItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册毒液腺体到废弃矿井 / 地牢战利品表（矿井 15% + 地牢 10%，与蛛类栖息地契合）。
	 */
	public static void registerLootTable() {
		net.fabricmc.fabric.api.loot.v2.LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			float chance;
			if (MINESHAFT_LOOT.equals(id)) {
				chance = 0.15F;
			} else if (DUNGEON_LOOT.equals(id)) {
				chance = 0.10F;
			} else {
				return;
			}
			tableBuilder.pool(net.minecraft.loot.LootPool.builder()
					.rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1.0F))
					.conditionally(net.minecraft.loot.condition.RandomChanceLootCondition.builder(chance))
					.with(net.minecraft.loot.entry.ItemEntry.builder(SscAddon.VENOM_GLAND)));
		});
	}

	/** 佩戴者是否正戴着毒液腺体（原生 Trinkets/tclayer + Curios 反射兜底）。 */
	public static boolean isWearingBy(LivingEntity entity) {
		if (net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils.isWearing(entity, SscAddon.VENOM_GLAND)) {
			return true;
		}
		try {
			Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
			Object lazyOptional = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
			if (lazyOptional == null) return false;
			Object resolved = lazyOptional.getClass().getMethod("resolve").invoke(lazyOptional);
			if (!(resolved instanceof java.util.Optional<?> opt) || opt.isEmpty()) return false;
			Object handler = opt.get();
			Object equipped = handler.getClass().getMethod("isEquipped", net.minecraft.item.Item.class).invoke(handler, SscAddon.VENOM_GLAND);
			return Boolean.TRUE.equals(equipped);
		} catch (Throwable ignored) {
			return false;
		}
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return AddonAccessoryGuard.canEquip(entity, e -> FormUtils.isForm(e, FormIdentifiers.SPIDER_SALTICIDAE));
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.venom_gland.tooltip_1").formatted(Formatting.DARK_GREEN));
		tooltip.add(Text.translatable("item.ssc_addon.venom_gland.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.venom_gland.tooltip_exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
